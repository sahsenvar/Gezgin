package dev.gezgin.buildlogic.publishing

import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.fail
import org.junit.jupiter.api.io.TempDir

class ReleasePublicationVerifierTest {
  @TempDir lateinit var temporaryDirectory: Path

  @Test
  fun `accepts the exact unsigned publication graph`() {
    val repository = publicationRepository(signatures = false)

    verifyRepository(repository, requireSignatures = false)
  }

  @Test
  fun `rejects an unexpected coordinate`() {
    val repository = publicationRepository(signatures = false)
    repository.resolve("io/github/sahsenvar/gezgin-extra/0.2.1").createDirectories()

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "unexpected coordinates")
    assertContains(failure.message.orEmpty(), "gezgin-extra")
  }

  @Test
  fun `rejects a broken project dependency mapping`() {
    val repository = publicationRepository(signatures = false)
    val pom =
      repository.resolve("io/github/sahsenvar/gezgin-mvi-jvm/0.2.1/gezgin-mvi-jvm-0.2.1.pom")
    pom.writeText(pom.toFile().readText().replace("gezgin-core-jvm", "gezgin-core-android"))

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "POM project dependencies")
    assertContains(failure.message.orEmpty(), "gezgin-mvi-jvm")
  }

  @Test
  fun `rejects incomplete Central POM metadata`() {
    val repository = publicationRepository(signatures = false)
    val pom = pomPath(repository, "gezgin-core")
    pom.writeText(
      pom
        .toFile()
        .readText()
        .replace(
          "https://www.apache.org/licenses/LICENSE-2.0.txt",
          "https://example.invalid/license",
        )
        .replace("<name>Şahan Şenvar</name>", "<name>Wrong Developer</name>")
    )

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "POM metadata")
    assertContains(failure.message.orEmpty(), "gezgin-core")
  }

  @Test
  fun `rejects duplicate internal dependencies`() {
    val repository = publicationRepository(signatures = false)
    val pom = pomPath(repository, "gezgin-mvi-jvm")
    val internalDependency =
      dependencyXml(PomDependency("io.github.sahsenvar", "gezgin-core-jvm", "0.2.1", "compile"))
    pom.writeText(
      pom.toFile().readText().replace("</dependencies>", "$internalDependency\n</dependencies>")
    )

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "duplicate POM dependency")
    assertContains(failure.message.orEmpty(), "gezgin-core-jvm")
  }

  @Test
  fun `rejects the wrong internal dependency scope`() {
    val repository = publicationRepository(signatures = false)
    val pom = pomPath(repository, "gezgin-mvi-jvm")
    val expected =
      dependencyXml(PomDependency("io.github.sahsenvar", "gezgin-core-jvm", "0.2.1", "compile"))
    val wrongScope =
      dependencyXml(PomDependency("io.github.sahsenvar", "gezgin-core-jvm", "0.2.1", "runtime"))
    pom.writeText(pom.toFile().readText().replace(expected, wrongScope))

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "POM project dependencies")
    assertContains(failure.message.orEmpty(), "gezgin-mvi-jvm")
  }

  @Test
  fun `rejects a changed required external dependency`() {
    val repository = publicationRepository(signatures = false)
    val pom = pomPath(repository, "gezgin-processor")
    pom.writeText(
      pom.toFile().readText().replace("<version>2.3.10</version>", "<version>0.0.0</version>")
    )

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "external POM dependencies differ")
    assertContains(failure.message.orEmpty(), "gezgin-processor")
  }

  @Test
  fun `rejects an unexpected external POM dependency`() {
    val repository = publicationRepository(signatures = false)
    val pom = pomPath(repository, "gezgin-processor")
    val unexpected = dependencyXml(PomDependency("com.example", "unexpected", "1.0.0", "runtime"))
    pom.writeText(
      pom.toFile().readText().replace("</dependencies>", "$unexpected\n</dependencies>")
    )

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "external POM dependencies differ")
    assertContains(failure.message.orEmpty(), "group=com.example")
    assertContains(failure.message.orEmpty(), "artifact=unexpected")
  }

  @Test
  fun `rejects an unexpected external Gradle module dependency`() {
    val repository = publicationRepository(signatures = false)
    val module = repository.resolve("io/github/sahsenvar/gezgin-mvi/0.2.1/gezgin-mvi-0.2.1.module")
    val unexpected =
      """{"group":"com.example","module":"unexpected","version":{"requires":"1.0.0"}},"""
    module.writeText(
      module.toFile().readText().replace("\"dependencies\": [{", "\"dependencies\": [$unexpected{")
    )

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "external module dependencies differ")
    assertContains(failure.message.orEmpty(), "group=com.example")
    assertContains(failure.message.orEmpty(), "module=unexpected")
  }

  @Test
  fun `rejects empty sources or Dokka jars`() {
    val repository = publicationRepository(signatures = false)
    val sources =
      repository.resolve("io/github/sahsenvar/gezgin-core/0.2.1/gezgin-core-0.2.1-sources.jar")
    JarOutputStream(Files.newOutputStream(sources)).close()

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = false)
      }

    assertContains(failure.message.orEmpty(), "non-empty sources jar")
  }

  @Test
  fun `requires every detached signature when signing verification is enabled`() {
    val repository = publicationRepository(signatures = true)
    repository
      .resolve("io/github/sahsenvar/gezgin-processor/0.2.1/gezgin-processor-0.2.1.pom.asc")
      .deleteExisting()

    val failure =
      assertFailsWith<IllegalStateException> {
        verifyRepository(repository, requireSignatures = true)
      }

    assertContains(failure.message.orEmpty(), "missing signature")
    assertContains(failure.message.orEmpty(), "gezgin-processor-0.2.1.pom")
  }

  private fun publicationRepository(signatures: Boolean): Path {
    val repository = temporaryDirectory.resolve("repository")
    expectedArtifacts.forEach { artifact ->
      val versionDirectory =
        repository
          .resolve("io/github/sahsenvar/${artifact.artifactId}/0.2.1")
          .also(Files::createDirectories)
      val base = versionDirectory.resolve("${artifact.artifactId}-0.2.1")
      base.resolveSibling("${base.fileName}.pom").writeText(pom(artifact))
      base.resolveSibling("${base.fileName}.module").writeText(moduleMetadata(artifact))
      jar(base.resolveSibling("${base.fileName}-sources.jar"), "sample.kt")
      jar(base.resolveSibling("${base.fileName}-javadoc.jar"), "index.html")
      jar(base.resolveSibling("${base.fileName}${artifact.primaryExtension}"), "payload.bin")

      val signable =
        listOf(
          base.resolveSibling("${base.fileName}.pom"),
          base.resolveSibling("${base.fileName}.module"),
          base.resolveSibling("${base.fileName}-sources.jar"),
          base.resolveSibling("${base.fileName}-javadoc.jar"),
          base.resolveSibling("${base.fileName}${artifact.primaryExtension}"),
        )
      if (artifact.targets.isNotEmpty()) {
        val tooling = base.resolveSibling("${base.fileName}-kotlin-tooling-metadata.json")
        tooling.writeText("{}")
        if (signatures) tooling.resolveSibling("${tooling.fileName}.asc").writeText("signature")
      }
      if (signatures) {
        signable.forEach { file ->
          file.resolveSibling("${file.fileName}.asc").writeText("signature")
        }
      }
    }
    return repository
  }

  private fun pom(artifact: ExpectedArtifact): String {
    val dependencies =
      buildList {
          artifact.pomProjectDependency?.let { dependencyArtifact ->
            add(
              PomDependency(
                "io.github.sahsenvar",
                dependencyArtifact,
                "0.2.1",
                artifact.internalPomScope,
              )
            )
          }
          addAll(requiredExternalDependencies(artifact.artifactId))
        }
        .joinToString(
          separator = "\n",
          prefix = "<dependencies>\n",
          postfix = "\n</dependencies>",
        ) {
          dependencyXml(it)
        }
    return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>io.github.sahsenvar</groupId>
              <artifactId>${artifact.artifactId}</artifactId>
              <version>0.2.1</version>
              <name>${artifact.projectName}</name>
              <description>${artifact.description}</description>
              <url>https://github.com/sahsenvar/Gezgin</url>
              <licenses><license><name>The Apache License, Version 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0.txt</url><distribution>repo</distribution></license></licenses>
              <developers><developer><id>sahsenvar</id><name>Şahan Şenvar</name><url>https://github.com/sahsenvar</url></developer></developers>
              <scm><url>https://github.com/sahsenvar/Gezgin</url><connection>scm:git:https://github.com/sahsenvar/Gezgin.git</connection><developerConnection>scm:git:ssh://git@github.com/sahsenvar/Gezgin.git</developerConnection></scm>
              $dependencies
            </project>
        """
      .trimIndent()
  }

  private fun dependencyXml(dependency: PomDependency): String =
    """
        <dependency>
          <groupId>${dependency.group}</groupId>
          <artifactId>${dependency.artifact}</artifactId>
          <version>${dependency.version}</version>
          <scope>${dependency.scope}</scope>
        </dependency>
        """
      .trimIndent()

  private fun pomPath(repository: Path, artifactId: String): Path =
    repository.resolve("io/github/sahsenvar/$artifactId/0.2.1/$artifactId-0.2.1.pom")

  private fun moduleMetadata(artifact: ExpectedArtifact): String {
    val moduleDependencies = buildList {
      artifact.moduleProjectDependency?.let { dependencyArtifact ->
        add(ModuleDependency("io.github.sahsenvar", dependencyArtifact, "0.2.1"))
      }
      addAll(expectedModuleExternalDependencies(artifact.artifactId))
    }
    val dependencies =
      if (moduleDependencies.isEmpty()) {
        ""
      } else {
        moduleDependencies.joinToString(prefix = "\"dependencies\": [", postfix = "],") { dependency
          ->
          """{"group":"${dependency.group}","module":"${dependency.module}","version":{"requires":"${dependency.version}"}}"""
        }
      }
    val variants =
      if (artifact.targets.isEmpty()) {
        """[{ "name": "api", $dependencies "attributes": {} }]"""
      } else {
        artifact.targets.joinToString(prefix = "[", postfix = "]") { target ->
          """
                {
                  "name": "$target-published",
                  $dependencies
                  "attributes": {},
                  "available-at": {
                    "url": "../../$target/0.2.1/$target-0.2.1.module",
                    "group": "io.github.sahsenvar",
                    "module": "$target",
                    "version": "0.2.1"
                  }
                }
                """
            .trimIndent()
        }
      }
    return """
            {
              "formatVersion": "1.1",
              "component": {
                "group": "io.github.sahsenvar",
                "module": "${artifact.componentArtifactId}",
                "version": "0.2.1"
              },
              "variants": $variants
            }
        """
      .trimIndent()
  }

  private fun jar(path: Path, entryName: String) {
    JarOutputStream(Files.newOutputStream(path)).use { output ->
      output.putNextEntry(JarEntry(entryName))
      output.write("fixture".toByteArray())
      output.closeEntry()
    }
  }

  private fun verifyRepository(repository: Path, requireSignatures: Boolean) {
    val verifierClass =
      runCatching { Class.forName("dev.gezgin.buildlogic.publishing.ReleasePublicationVerifier") }
        .getOrElse { fail("ReleasePublicationVerifier is not implemented") }
    val verifier = verifierClass.getField("INSTANCE").get(null)
    val verify =
      verifierClass.getMethod(
        "verify",
        Path::class.java,
        String::class.java,
        Boolean::class.javaPrimitiveType,
      )
    try {
      verify.invoke(verifier, repository, "0.2.1", requireSignatures)
    } catch (failure: InvocationTargetException) {
      throw failure.targetException
    }
  }

  private data class ExpectedArtifact(
    val artifactId: String,
    val primaryExtension: String,
    val pomProjectDependency: String? = null,
    val targets: Set<String> = emptySet(),
    val componentArtifactId: String = artifactId,
    val moduleProjectDependency: String? = pomProjectDependency,
    val internalPomScope: String = if (targets.isNotEmpty()) "runtime" else "compile",
    val projectName: String = componentArtifactId,
    val description: String = descriptionFor(componentArtifactId),
  )

  private data class PomDependency(
    val group: String,
    val artifact: String,
    val version: String,
    val scope: String,
  )

  private data class ModuleDependency(val group: String, val module: String, val version: String)

  private companion object {
    fun descriptionFor(projectName: String): String =
      when (projectName) {
        "gezgin-core" ->
          "DI-agnostic Kotlin Multiplatform navigation runtime and Compose display layer."
        "gezgin-mvi" -> "Optional MVI bindings and generated route effect handlers for Gezgin."
        "gezgin-test" -> "UI-free typed navigation test utilities for Gezgin applications."
        "gezgin-processor" ->
          "KSP2 processor that generates typed Gezgin navigators and entry providers."
        else -> error("Unknown published project: $projectName")
      }

    fun requiredExternalDependencies(artifactId: String): List<PomDependency> =
      when (artifactId) {
        "gezgin-core" ->
          listOf(
            PomDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.11.0", "runtime"),
            PomDependency(
              "org.jetbrains.kotlinx",
              "kotlinx-serialization-json",
              "1.9.0",
              "runtime",
            ),
            PomDependency("org.jetbrains.compose.runtime", "runtime", "1.11.1", "runtime"),
            PomDependency("org.jetbrains.compose.foundation", "foundation", "1.11.1", "runtime"),
            PomDependency("org.jetbrains.compose.material3", "material3", "1.9.0", "runtime"),
            PomDependency("androidx.navigation3", "navigation3-runtime", "1.0.0", "runtime"),
            PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "runtime"),
          )
        "gezgin-core-android" ->
          listOf(
            PomDependency("androidx.navigation3", "navigation3-ui-android", "1.0.0", "compile"),
            PomDependency(
              "androidx.lifecycle",
              "lifecycle-viewmodel-navigation3-android",
              "2.10.0",
              "compile",
            ),
            PomDependency(
              "androidx.lifecycle",
              "lifecycle-viewmodel-compose-android",
              "2.10.0",
              "compile",
            ),
            PomDependency(
              "org.jetbrains.kotlinx",
              "kotlinx-coroutines-core-jvm",
              "1.11.0",
              "compile",
            ),
            PomDependency(
              "org.jetbrains.kotlinx",
              "kotlinx-serialization-json-jvm",
              "1.9.0",
              "compile",
            ),
            PomDependency("org.jetbrains.compose.runtime", "runtime", "1.11.1", "compile"),
            PomDependency("org.jetbrains.compose.foundation", "foundation", "1.11.1", "compile"),
            PomDependency("org.jetbrains.compose.material3", "material3", "1.9.0", "compile"),
            PomDependency(
              "androidx.navigation3",
              "navigation3-runtime-android",
              "1.0.0",
              "compile",
            ),
            PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "compile"),
            PomDependency("androidx.fragment", "fragment-compose", "1.8.9", "runtime"),
          )
        "gezgin-core-jvm" ->
          listOf(
            PomDependency(
              "org.jetbrains.androidx.navigation3",
              "navigation3-ui-desktop",
              "1.2.0-alpha02",
              "compile",
            ),
            PomDependency(
              "org.jetbrains.androidx.lifecycle",
              "lifecycle-viewmodel-navigation3-desktop",
              "2.11.0",
              "compile",
            ),
            PomDependency(
              "org.jetbrains.androidx.lifecycle",
              "lifecycle-viewmodel-compose-desktop",
              "2.11.0",
              "compile",
            ),
            PomDependency(
              "org.jetbrains.kotlinx",
              "kotlinx-coroutines-core-jvm",
              "1.11.0",
              "compile",
            ),
            PomDependency(
              "org.jetbrains.kotlinx",
              "kotlinx-serialization-json-jvm",
              "1.9.0",
              "compile",
            ),
            PomDependency("org.jetbrains.compose.runtime", "runtime-desktop", "1.11.1", "compile"),
            PomDependency(
              "org.jetbrains.compose.foundation",
              "foundation-desktop",
              "1.11.1",
              "compile",
            ),
            PomDependency(
              "org.jetbrains.compose.material3",
              "material3-desktop",
              "1.9.0",
              "compile",
            ),
            PomDependency(
              "androidx.navigation3",
              "navigation3-runtime-desktop",
              "1.2.0-alpha04",
              "compile",
            ),
            PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "compile"),
          )
        "gezgin-mvi" ->
          listOf(PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "runtime"))
        "gezgin-mvi-android" ->
          listOf(
            PomDependency(
              "androidx.lifecycle",
              "lifecycle-viewmodel-compose-android",
              "2.10.0",
              "compile",
            ),
            PomDependency(
              "androidx.lifecycle",
              "lifecycle-runtime-compose-android",
              "2.10.0",
              "compile",
            ),
            PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "compile"),
          )
        "gezgin-mvi-jvm" ->
          listOf(
            PomDependency(
              "org.jetbrains.androidx.lifecycle",
              "lifecycle-viewmodel-compose-desktop",
              "2.11.0",
              "compile",
            ),
            PomDependency(
              "org.jetbrains.androidx.lifecycle",
              "lifecycle-runtime-compose-desktop",
              "2.11.0",
              "compile",
            ),
            PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "compile"),
          )
        "gezgin-test" ->
          listOf(PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "runtime"))
        "gezgin-test-android",
        "gezgin-test-jvm" ->
          listOf(PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "compile"))
        "gezgin-processor" ->
          listOf(
            PomDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "compile"),
            PomDependency("com.google.devtools.ksp", "symbol-processing-api", "2.3.10", "runtime"),
            PomDependency("com.squareup", "kotlinpoet-jvm", "2.2.0", "runtime"),
            PomDependency("com.squareup", "kotlinpoet-ksp", "2.2.0", "runtime"),
          )
        else -> error("Unknown publication: $artifactId")
      }

    fun expectedModuleExternalDependencies(artifactId: String): List<ModuleDependency> =
      when (artifactId) {
        "gezgin-core" ->
          listOf(
            ModuleDependency("androidx.navigation3", "navigation3-runtime", "1.0.0"),
            ModuleDependency("org.jetbrains.compose.foundation", "foundation", "1.11.1"),
            ModuleDependency("org.jetbrains.compose.material3", "material3", "1.9.0"),
            ModuleDependency("org.jetbrains.compose.runtime", "runtime", "1.11.1"),
            ModuleDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"),
            ModuleDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.11.0"),
            ModuleDependency("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.9.0"),
          )
        "gezgin-core-android" ->
          listOf(
            ModuleDependency("androidx.fragment", "fragment-compose", "1.8.9"),
            ModuleDependency("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.10.0"),
            ModuleDependency("androidx.lifecycle", "lifecycle-viewmodel-navigation3", "2.10.0"),
            ModuleDependency("androidx.navigation3", "navigation3-runtime", "1.0.0"),
            ModuleDependency("androidx.navigation3", "navigation3-ui", "1.0.0"),
            ModuleDependency("org.jetbrains.compose.foundation", "foundation", "1.11.1"),
            ModuleDependency("org.jetbrains.compose.material3", "material3", "1.9.0"),
            ModuleDependency("org.jetbrains.compose.runtime", "runtime", "1.11.1"),
            ModuleDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"),
            ModuleDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.11.0"),
            ModuleDependency("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.9.0"),
          )
        "gezgin-core-jvm" ->
          listOf(
            ModuleDependency("androidx.navigation3", "navigation3-runtime", "1.0.0"),
            ModuleDependency(
              "org.jetbrains.androidx.lifecycle",
              "lifecycle-viewmodel-compose",
              "2.11.0",
            ),
            ModuleDependency(
              "org.jetbrains.androidx.lifecycle",
              "lifecycle-viewmodel-navigation3",
              "2.11.0",
            ),
            ModuleDependency(
              "org.jetbrains.androidx.navigation3",
              "navigation3-ui",
              "1.2.0-alpha02",
            ),
            ModuleDependency("org.jetbrains.compose.foundation", "foundation", "1.11.1"),
            ModuleDependency("org.jetbrains.compose.material3", "material3", "1.9.0"),
            ModuleDependency("org.jetbrains.compose.runtime", "runtime", "1.11.1"),
            ModuleDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"),
            ModuleDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.11.0"),
            ModuleDependency("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.9.0"),
          )
        "gezgin-mvi" -> listOf(ModuleDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"))
        "gezgin-mvi-android" ->
          listOf(
            ModuleDependency("androidx.lifecycle", "lifecycle-runtime-compose", "2.10.0"),
            ModuleDependency("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.10.0"),
            ModuleDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"),
          )
        "gezgin-mvi-jvm" ->
          listOf(
            ModuleDependency(
              "org.jetbrains.androidx.lifecycle",
              "lifecycle-runtime-compose",
              "2.11.0",
            ),
            ModuleDependency(
              "org.jetbrains.androidx.lifecycle",
              "lifecycle-viewmodel-compose",
              "2.11.0",
            ),
            ModuleDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"),
          )
        "gezgin-test",
        "gezgin-test-android",
        "gezgin-test-jvm" ->
          listOf(ModuleDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"))
        "gezgin-processor" ->
          listOf(
            ModuleDependency("com.google.devtools.ksp", "symbol-processing-api", "2.3.10"),
            ModuleDependency("com.squareup", "kotlinpoet", "2.2.0"),
            ModuleDependency("com.squareup", "kotlinpoet-ksp", "2.2.0"),
            ModuleDependency("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"),
          )
        else -> error("Unknown publication: $artifactId")
      }

    val expectedArtifacts =
      listOf(
        ExpectedArtifact(
          "gezgin-core",
          ".jar",
          targets = setOf("gezgin-core-android", "gezgin-core-jvm"),
        ),
        ExpectedArtifact("gezgin-core-android", ".aar", componentArtifactId = "gezgin-core"),
        ExpectedArtifact("gezgin-core-jvm", ".jar", componentArtifactId = "gezgin-core"),
        ExpectedArtifact(
          "gezgin-mvi",
          ".jar",
          "gezgin-core",
          setOf("gezgin-mvi-android", "gezgin-mvi-jvm"),
        ),
        ExpectedArtifact(
          "gezgin-mvi-android",
          ".aar",
          "gezgin-core-android",
          componentArtifactId = "gezgin-mvi",
          moduleProjectDependency = "gezgin-core",
        ),
        ExpectedArtifact(
          "gezgin-mvi-jvm",
          ".jar",
          "gezgin-core-jvm",
          componentArtifactId = "gezgin-mvi",
          moduleProjectDependency = "gezgin-core",
        ),
        ExpectedArtifact(
          "gezgin-test",
          ".jar",
          "gezgin-core",
          setOf("gezgin-test-android", "gezgin-test-jvm"),
        ),
        ExpectedArtifact(
          "gezgin-test-android",
          ".aar",
          "gezgin-core-android",
          componentArtifactId = "gezgin-test",
          moduleProjectDependency = "gezgin-core",
        ),
        ExpectedArtifact(
          "gezgin-test-jvm",
          ".jar",
          "gezgin-core-jvm",
          componentArtifactId = "gezgin-test",
          moduleProjectDependency = "gezgin-core",
        ),
        ExpectedArtifact("gezgin-processor", ".jar"),
      )
  }
}
