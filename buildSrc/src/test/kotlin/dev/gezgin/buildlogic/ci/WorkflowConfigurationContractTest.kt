package dev.gezgin.buildlogic.ci

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
    assertContains(workflow, "./gradle/release/test-release-scripts.sh")
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
    assertTrue(Files.isRegularFile(projectRoot.resolve(".github/workflows/release.yml")))
  }

  @Test
  fun `CodeQL init and analyze use the same verified release`() {
    val workflow = text(".github/workflows/codeql.yml")
    val actionCommits =
      Regex("github/codeql-action/(init|analyze)@([0-9a-f]{40})")
        .findAll(workflow)
        .associate { match -> match.groupValues[1] to match.groupValues[2] }

    assertEquals(setOf("init", "analyze"), actionCommits.keys)
    assertEquals(
      "99df26d4f13ea111d4ec1a7dddef6063f76b97e9",
      actionCommits.getValue("init"),
      "CodeQL must use the peeled immutable commit for v4.37.0",
    )
    assertEquals(actionCommits.getValue("init"), actionCommits.getValue("analyze"))
  }

  @Test
  fun `release is a strict ordered stable tag workflow`() {
    val workflow = text(".github/workflows/release.yml")
    assertContains(workflow, "tags: [ 'v*' ]")
    assertContains(workflow, "group: release-${'$'}{{ github.ref }}")
    assertContains(workflow, "RELEASE_TAG: ${'$'}{{ github.ref_name }}")
    assertContains(workflow, "./gradle/release/validate-release.sh \"${'$'}RELEASE_TAG\"")
    assertContains(workflow, "needs: validate")
    assertContains(workflow, "needs: publish")
    assertContains(workflow, "needs: central-smoke")
    assertContains(workflow, "./gradlew publishAndReleaseToMavenCentral --no-configuration-cache")
    assertContains(workflow, "./gradle/release/smoke-maven-central.sh")
    assertContains(workflow, "./gradle/release/extract-release-notes.sh")
    assertContains(workflow, "contents: write")
    Regex("(?ms)^\\s+run:\\s*(?:[>|]-?\\s*)?(.*?)(?=^\\s{6}- name:|^\\s{2}[a-zA-Z-]+:|\\z)")
      .findAll(workflow)
      .forEach { runBlock ->
        assertFalse(
          runBlock.value.contains("${'$'}{{"),
          "Untrusted GitHub expressions must be passed through env, not interpolated into shell",
        )
      }
  }

  @Test
  fun `release maps exactly the five repository secrets without command interpolation`() {
    val workflow = text(".github/workflows/release.yml")
    val requiredSecrets =
      listOf(
        "MAVEN_CENTRAL_USERNAME",
        "MAVEN_CENTRAL_PASSWORD",
        "SIGNING_IN_MEMORY_KEY",
        "SIGNING_IN_MEMORY_KEY_PASSWORD",
        "SIGNING_IN_MEMORY_KEY_ID",
      )
    requiredSecrets.forEach { secret ->
      assertContains(workflow, "secrets.$secret")
      assertFalse(workflow.contains("run: ${'$'}{{ secrets.$secret }}"))
    }
    assertEquals(
      requiredSecrets,
      Regex("secrets\\.([A-Z0-9_]+)").findAll(workflow).map { it.groupValues[1] }.toList(),
    )
    assertFalse(workflow.contains("echo ${'$'}{{ secrets."))
  }

  @Test
  fun `release helper scripts enforce repository only Central smoke`() {
    val validator = text("gradle/release/validate-release.sh")
    assertContains(validator, "VERSION_NAME")
    assertContains(validator, "CHANGELOG.md")
    assertContains(validator, "Stable releases only")
    assertFalse(validator.contains("grep -E"))

    val extractor = text("gradle/release/extract-release-notes.sh")
    assertFalse(extractor.contains("awk -v version"))

    val smoke = text("gradle/release/smoke-maven-central.sh")
    assertContains(smoke, "compileDebugUnitTestKotlin")
    assertContains(smoke, "gradle-9.4.1-bin.zip")
    assertFalse(smoke.contains("mavenLocal"))
    assertFalse(smoke.contains("includeBuild"))
    assertContains(smoke, "wait-for-maven-central.sh")

    val waitForCentral = text("gradle/release/wait-for-maven-central.sh")
    listOf("gezgin-core", "gezgin-processor", "gezgin-mvi", "gezgin-test").forEach {
      assertContains(waitForCentral, it)
    }
    assertContains(waitForCentral, "MAX_WAIT_SECONDS")
    assertContains(waitForCentral, ":-1800")
    assertContains(waitForCentral, "RETRY_SECONDS")
    assertContains(waitForCentral, ":-30")
    assertContains(waitForCentral, "remaining")
    assertContains(text("gradle/release/test-release-scripts.sh"), "0x1x0")
    assertContains(text("gradle/release/test-release-scripts.sh"), "v${'$'}(touch")
  }

  @Test
  fun `maintained release documentation uses the final coordinates and API`() {
    val maintainedDocs =
      listOf(
        "README.md",
        "README.tr.md",
        "CHANGELOG.md",
        "sample/README.md",
        "docs/gezgin-by-example.md",
        "docs/gezgin-design.md",
        "docs/gezgin-zad-readiness-handoff.md",
        "docs/gezgin-zad-root-integration-spec.md",
      )
    val contents = maintainedDocs.associateWith(::text)
    contents.forEach { (path, content) ->
      assertFalse(content.contains("dev.gezgin:"), path)
      assertFalse(content.contains("0.1.0-alpha04"), path)
      assertFalse(content.contains("ScreenEffect"), path)
    }

    listOf("README.md", "README.tr.md", "docs/gezgin-zad-readiness-handoff.md").forEach {
      val content = contents.getValue(it)
      listOf("gezgin-core", "gezgin-processor", "gezgin-mvi", "gezgin-test").forEach { module ->
        assertContains(content, "io.github.sahsenvar:$module:0.1.0", message = it)
      }
    }
    assertContains(contents.getValue("README.md"), "ExperimentalGezginMigrationApi")
    assertContains(contents.getValue("README.md"), "@EffectHandler(route)")
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
