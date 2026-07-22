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
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `accepts the exact unsigned publication graph`() {
        val repository = publicationRepository(signatures = false)

        verifyRepository(repository, requireSignatures = false)
    }

    @Test
    fun `rejects an unexpected coordinate`() {
        val repository = publicationRepository(signatures = false)
        repository.resolve("io/github/sahsenvar/gezgin-extra/0.1.0").createDirectories()

        val failure = assertFailsWith<IllegalStateException> {
            verifyRepository(repository, requireSignatures = false)
        }

        assertContains(failure.message.orEmpty(), "unexpected coordinates")
        assertContains(failure.message.orEmpty(), "gezgin-extra")
    }

    @Test
    fun `rejects a broken project dependency mapping`() {
        val repository = publicationRepository(signatures = false)
        val pom = repository.resolve(
            "io/github/sahsenvar/gezgin-mvi-jvm/0.1.0/gezgin-mvi-jvm-0.1.0.pom",
        )
        pom.writeText(pom.toFile().readText().replace("gezgin-core-jvm", "gezgin-core-android"))

        val failure = assertFailsWith<IllegalStateException> {
            verifyRepository(repository, requireSignatures = false)
        }

        assertContains(failure.message.orEmpty(), "POM project dependencies")
        assertContains(failure.message.orEmpty(), "gezgin-mvi-jvm")
    }

    @Test
    fun `rejects empty sources or Dokka jars`() {
        val repository = publicationRepository(signatures = false)
        val sources = repository.resolve(
            "io/github/sahsenvar/gezgin-core/0.1.0/gezgin-core-0.1.0-sources.jar",
        )
        JarOutputStream(Files.newOutputStream(sources)).close()

        val failure = assertFailsWith<IllegalStateException> {
            verifyRepository(repository, requireSignatures = false)
        }

        assertContains(failure.message.orEmpty(), "non-empty sources jar")
    }

    @Test
    fun `requires every detached signature when signing verification is enabled`() {
        val repository = publicationRepository(signatures = true)
        repository.resolve(
            "io/github/sahsenvar/gezgin-processor/0.1.0/gezgin-processor-0.1.0.pom.asc",
        ).deleteExisting()

        val failure = assertFailsWith<IllegalStateException> {
            verifyRepository(repository, requireSignatures = true)
        }

        assertContains(failure.message.orEmpty(), "missing signature")
        assertContains(failure.message.orEmpty(), "gezgin-processor-0.1.0.pom")
    }

    private fun publicationRepository(signatures: Boolean): Path {
        val repository = temporaryDirectory.resolve("repository")
        expectedArtifacts.forEach { artifact ->
            val versionDirectory = repository.resolve(
                "io/github/sahsenvar/${artifact.artifactId}/0.1.0",
            ).also(Files::createDirectories)
            val base = versionDirectory.resolve("${artifact.artifactId}-0.1.0")
            base.resolveSibling("${base.fileName}.pom").writeText(pom(artifact))
            base.resolveSibling("${base.fileName}.module").writeText(moduleMetadata(artifact))
            jar(base.resolveSibling("${base.fileName}-sources.jar"), "sample.kt")
            jar(base.resolveSibling("${base.fileName}-javadoc.jar"), "index.html")
            jar(base.resolveSibling("${base.fileName}${artifact.primaryExtension}"), "payload.bin")

            val signable = listOf(
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
        val dependency = artifact.pomProjectDependency?.let { dependencyArtifact ->
            """
            <dependencies>
              <dependency>
                <groupId>io.github.sahsenvar</groupId>
                <artifactId>$dependencyArtifact</artifactId>
                <version>0.1.0</version>
                <scope>compile</scope>
              </dependency>
            </dependencies>
            """.trimIndent()
        }.orEmpty()
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>io.github.sahsenvar</groupId>
              <artifactId>${artifact.artifactId}</artifactId>
              <version>0.1.0</version>
              <name>${artifact.artifactId}</name>
              <description>Gezgin publication fixture.</description>
              <url>https://github.com/sahsenvar/Gezgin</url>
              <licenses><license><name>The Apache License, Version 2.0</name></license></licenses>
              <developers><developer><id>sahsenvar</id></developer></developers>
              <scm><connection>scm:git:https://github.com/sahsenvar/Gezgin.git</connection></scm>
              $dependency
            </project>
        """.trimIndent()
    }

    private fun moduleMetadata(artifact: ExpectedArtifact): String {
        val dependencies = artifact.moduleProjectDependency?.let { dependencyArtifact ->
            """
            "dependencies": [{
              "group": "io.github.sahsenvar",
              "module": "$dependencyArtifact",
              "version": { "requires": "0.1.0" }
            }],
            """.trimIndent()
        }.orEmpty()
        val variants = if (artifact.targets.isEmpty()) {
            """[{ "name": "api", $dependencies "attributes": {} }]"""
        } else {
            artifact.targets.joinToString(prefix = "[", postfix = "]") { target ->
                """
                {
                  "name": "$target-published",
                  $dependencies
                  "attributes": {},
                  "available-at": {
                    "url": "../../$target/0.1.0/$target-0.1.0.module",
                    "group": "io.github.sahsenvar",
                    "module": "$target",
                    "version": "0.1.0"
                  }
                }
                """.trimIndent()
            }
        }
        return """
            {
              "formatVersion": "1.1",
              "component": {
                "group": "io.github.sahsenvar",
                "module": "${artifact.componentArtifactId}",
                "version": "0.1.0"
              },
              "variants": $variants
            }
        """.trimIndent()
    }

    private fun jar(path: Path, entryName: String) {
        JarOutputStream(Files.newOutputStream(path)).use { output ->
            output.putNextEntry(JarEntry(entryName))
            output.write("fixture".toByteArray())
            output.closeEntry()
        }
    }

    private fun verifyRepository(repository: Path, requireSignatures: Boolean) {
        val verifierClass = runCatching {
            Class.forName("dev.gezgin.buildlogic.publishing.ReleasePublicationVerifier")
        }.getOrElse {
            fail("ReleasePublicationVerifier is not implemented")
        }
        val verifier = verifierClass.getField("INSTANCE").get(null)
        val verify = verifierClass.getMethod("verify", Path::class.java, Boolean::class.javaPrimitiveType)
        try {
            verify.invoke(verifier, repository, requireSignatures)
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
    )

    private companion object {
        val expectedArtifacts = listOf(
            ExpectedArtifact("gezgin-core", ".jar", targets = setOf("gezgin-core-android", "gezgin-core-jvm")),
            ExpectedArtifact("gezgin-core-android", ".aar", componentArtifactId = "gezgin-core"),
            ExpectedArtifact("gezgin-core-jvm", ".jar", componentArtifactId = "gezgin-core"),
            ExpectedArtifact("gezgin-mvi", ".jar", "gezgin-core", setOf("gezgin-mvi-android", "gezgin-mvi-jvm")),
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
            ExpectedArtifact("gezgin-test", ".jar", "gezgin-core", setOf("gezgin-test-android", "gezgin-test-jvm")),
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
