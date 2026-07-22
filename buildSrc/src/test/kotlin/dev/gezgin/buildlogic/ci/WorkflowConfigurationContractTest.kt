package dev.gezgin.buildlogic.ci

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowConfigurationContractTest {
  private val projectRoot: Path =
    Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().let { workingDirectory ->
      if (workingDirectory.fileName.toString() == "buildSrc") workingDirectory.parent
      else workingDirectory
    }

  @Test
  fun `all workflow actions are pinned and use least privilege defaults`() {
    workflowFiles().forEach { workflow ->
      val contents = workflow.readText()
      assertContains(contents, "permissions: read-all", message = workflow.toString())
      assertFalse(Regex("uses:\\s+[^\\s@]+@v\\d+").containsMatchIn(contents), workflow.toString())
      Regex("(?m)^\\s*-?\\s*uses:\\s+([^\\s#]+)")
        .findAll(contents)
        .map { it.groupValues[1] }
        .forEach { action ->
          assertTrue(
            Regex("^[^@]+@[0-9a-f]{40}$").matches(action),
            "$action in ${workflow.fileName} is not pinned to a full commit SHA",
          )
        }
    }
  }

  @Test
  fun `ci validates wrapper and runs complete JDK 17 gates`() {
    val workflow = text(".github/workflows/ci.yml")
    assertContains(workflow, "gradle/actions/wrapper-validation@")
    assertContains(workflow, "java-version: '17'")
    assertContains(workflow, "./gradlew -p buildSrc test")
    assertContains(workflow, "check apiCheck")
    assertContains(workflow, "checkPublicApiKDoc")
    assertContains(workflow, "dokkaGenerate")
    assertContains(workflow, ":sample:app:assembleDebug")
    assertContains(workflow, "./gradle/verify-release-publications.sh")
    assertFalse(workflow.contains("MAVEN_CENTRAL_USERNAME"))
  }

  @Test
  fun `quality generates and retains all Kover reports with optional Codecov`() {
    val workflow = text(".github/workflows/quality.yml")
    assertContains(workflow, "spotlessCheck")
    assertContains(workflow, "koverXmlReport")
    assertContains(workflow, "koverHtmlReport")
    assertContains(workflow, "koverVerify")
    assertContains(workflow, "actions/upload-artifact@")
    assertContains(workflow, "CODECOV_TOKEN")
    assertContains(workflow, "fail_ci_if_error: false")
    assertContains(workflow, "env.HAS_CODECOV_TOKEN == 'true'")
  }

  @Test
  fun `docs codeql and repository policy workflows are present and scoped`() {
    val docs = text(".github/workflows/docs.yml")
    assertContains(docs, "branches: [ main ]")
    assertContains(docs, "assembleDokkaSite")
    assertContains(docs, "pages: write")
    assertContains(docs, "id-token: write")

    val codeql = text(".github/workflows/codeql.yml")
    assertContains(codeql, "security-events: write")
    assertContains(codeql, "languages: java-kotlin")
    assertContains(codeql, "compileKotlinJvm")
    assertContains(codeql, "compileDebugKotlinAndroid")

    assertContains(text(".github/workflows/semantic-pr.yml"), "action-semantic-pull-request@")
    assertContains(text(".github/workflows/labeler.yml"), "actions/labeler@")
    val stale = text(".github/workflows/stale.yml")
    assertContains(stale, "days-before-close: -1")
    assertContains(stale, "close-issue-reason: not_planned")
    assertContains(stale, "delete-branch: false")

    val dependabot = text(".github/dependabot.yml")
    assertContains(dependabot, "package-ecosystem: gradle")
    assertContains(dependabot, "package-ecosystem: github-actions")
    assertContains(dependabot, "interval: weekly")
    assertFalse(Files.exists(projectRoot.resolve(".github/workflows/dependabot-auto-merge.yml")))
    assertFalse(Files.exists(projectRoot.resolve(".github/workflows/release.yml")))
  }

  @Test
  fun `Dokka site assembly uses a configuration cache safe typed task`() {
    val rootBuild = text("build.gradle.kts")
    assertContains(rootBuild, "import dev.gezgin.buildlogic.docs.AssembleDokkaSiteTask")
    assertContains(rootBuild, "tasks.register<AssembleDokkaSiteTask>(\"assembleDokkaSite\")")
    val registration =
      rootBuild
        .substringAfter("tasks.register<AssembleDokkaSiteTask>(\"assembleDokkaSite\")")
        .substringBefore("publishedProjects.forEach")
    assertFalse(
      registration.contains("doLast"),
      "The root script must not be captured by task actions",
    )

    val implementation =
      text("buildSrc/src/main/kotlin/dev/gezgin/buildlogic/docs/AssembleDokkaSiteTask.kt")
    assertContains(implementation, "@get:Input")
    assertContains(implementation, "moduleNames: ListProperty<String>")
    assertContains(implementation, "moduleDocumentationPaths: MapProperty<String, String>")
    assertContains(implementation, "@get:InputFiles")
    assertContains(implementation, "@get:OutputDirectory")
    assertContains(implementation, "@TaskAction")
    assertFalse(
      implementation.contains("Project"),
      "Task actions must not capture a Gradle Project",
    )
  }

  @Test
  fun `labeler covers published modules documentation build and CI`() {
    val labeler = text(".github/labeler.yml")
    listOf("gezgin-core", "gezgin-mvi", "gezgin-processor", "gezgin-test").forEach {
      assertContains(labeler, "'$it/**'")
    }
    assertContains(labeler, "'docs/**'")
    assertContains(labeler, "'**/*.gradle.kts'")
    assertContains(labeler, "'.github/**'")
  }

  private fun workflowFiles(): List<Path> {
    val workflowDirectory = projectRoot.resolve(".github/workflows")
    return Files.list(workflowDirectory).use { paths ->
      paths
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".yml") }
        .sorted()
        .toList()
    }
  }

  private fun text(relativePath: String): String {
    val path = projectRoot.resolve(relativePath)
    assertTrue(Files.isRegularFile(path), "Missing project file: $relativePath")
    return path.readText()
  }
}
