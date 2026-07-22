package dev.gezgin.buildlogic.publishing

import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.Path
import java.io.StringReader
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Verifies the complete repository shape produced for the public 0.1.0 release. */
object ReleasePublicationVerifier {
    private const val groupId = "io.github.sahsenvar"
    private const val version = "0.1.0"

    fun verify(repository: Path, requireSignatures: Boolean): VerificationSummary {
        check(Files.isDirectory(repository)) { "release repository does not exist: $repository" }
        val groupDirectory = repository.resolve(groupId.replace('.', '/'))
        check(Files.isDirectory(groupDirectory)) { "release group directory does not exist: $groupDirectory" }

        val actualCoordinates = Files.list(groupDirectory).use { paths ->
            paths.filter(Files::isDirectory)
                .filter { Files.isDirectory(it.resolve(version)) }
                .map { it.fileName.toString() }
                .toList()
                .toSet()
        }
        val expectedCoordinates = expectedArtifacts.mapTo(linkedSetOf()) { it.artifactId }
        val unexpected = actualCoordinates - expectedCoordinates
        val missing = expectedCoordinates - actualCoordinates
        check(unexpected.isEmpty() && missing.isEmpty()) {
            "publication coordinates differ; unexpected coordinates=$unexpected, missing coordinates=$missing"
        }

        val expectedSignatures = linkedSetOf<Path>()
        expectedArtifacts.forEach { artifact ->
            val versionDirectory = groupDirectory.resolve(artifact.artifactId).resolve(version)
            val baseName = "${artifact.artifactId}-$version"
            val pom = versionDirectory.resolve("$baseName.pom")
            val module = versionDirectory.resolve("$baseName.module")
            val sources = versionDirectory.resolve("$baseName-sources.jar")
            val javadoc = versionDirectory.resolve("$baseName-javadoc.jar")
            val primary = versionDirectory.resolve("$baseName${artifact.primaryExtension}")
            val signable = listOf(pom, module, sources, javadoc, primary)

            signable.forEach { file ->
                check(Files.isRegularFile(file) && Files.size(file) > 0L) {
                    "missing or empty publication artifact: $file"
                }
                expectedSignatures.add(signatureOf(file))
            }
            verifyArchive(sources, "non-empty sources jar") { it.endsWith(".kt") }
            verifyArchive(javadoc, "non-empty Dokka javadoc jar") {
                it == "index.html" || it.endsWith("/index.html")
            }
            verifyArchive(primary, "non-empty primary archive") { true }
            verifyPom(pom, artifact)
            verifyModuleMetadata(module, artifact)

            if (artifact.targets.isNotEmpty()) {
                val toolingMetadata = versionDirectory.resolve("$baseName-kotlin-tooling-metadata.json")
                check(Files.isRegularFile(toolingMetadata) && Files.size(toolingMetadata) > 0L) {
                    "missing Kotlin tooling metadata: $toolingMetadata"
                }
                expectedSignatures.add(signatureOf(toolingMetadata))
            }
        }

        if (requireSignatures) {
            expectedSignatures.forEach { signature ->
                check(Files.isRegularFile(signature) && Files.size(signature) > 0L) {
                    "missing signature for ${signature.fileName.toString().removeSuffix(".asc")}: $signature"
                }
            }
            val actualSignatures = Files.walk(groupDirectory).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter { it.fileName.toString().endsWith(".asc") }
                    .map { repository.relativize(it) }
                    .toList()
                    .toSet()
            }
            val relativeExpectedSignatures = expectedSignatures.mapTo(linkedSetOf()) { repository.relativize(it) }
            check(actualSignatures == relativeExpectedSignatures) {
                "detached signature set differs; unexpected=${actualSignatures - relativeExpectedSignatures}, " +
                    "missing=${relativeExpectedSignatures - actualSignatures}"
            }
        }

        return VerificationSummary(
            coordinateCount = expectedArtifacts.size,
            signatureCount = if (requireSignatures) expectedSignatures.size else 0,
        )
    }

    private fun verifyArchive(path: Path, label: String, requiredEntry: (String) -> Boolean) {
        val hasPayload = runCatching {
            JarFile(path.toFile()).use { archive ->
                archive.entries().asSequence().any { entry -> !entry.isDirectory && requiredEntry(entry.name) }
            }
        }.getOrElse { false }
        check(hasPayload) { "$label is missing content: $path" }
    }

    private fun verifyPom(path: Path, artifact: ExpectedArtifact) {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val source = InputSource(StringReader(Files.readString(path).trimStart())).apply {
            systemId = path.toUri().toString()
        }
        val project = factory.newDocumentBuilder().parse(source).documentElement
        check(project.childText("groupId") == groupId) { "invalid POM groupId for ${artifact.artifactId}" }
        check(project.childText("artifactId") == artifact.artifactId) {
            "invalid POM artifactId for ${artifact.artifactId}"
        }
        check(project.childText("version") == version) { "invalid POM version for ${artifact.artifactId}" }
        listOf("name", "description", "url", "licenses", "developers", "scm").forEach { field ->
            check(project.child(field) != null) { "POM metadata field '$field' is missing for ${artifact.artifactId}" }
        }

        val actualDependencies = project.child("dependencies")
            ?.children("dependency")
            .orEmpty()
            .filter { it.childText("groupId") == groupId }
            .mapTo(linkedSetOf()) {
                "${it.childText("groupId")}:${it.childText("artifactId")}:${it.childText("version")}"
            }
        val expectedDependencies = artifact.pomProjectDependency
            ?.let { setOf("$groupId:$it:$version") }
            .orEmpty()
        check(actualDependencies == expectedDependencies) {
            "POM project dependencies differ for ${artifact.artifactId}; " +
                "expected=$expectedDependencies, actual=$actualDependencies"
        }
    }

    private fun verifyModuleMetadata(path: Path, artifact: ExpectedArtifact) {
        val metadata = JsonSlurper().parse(path.toFile()) as Map<*, *>
        val component = metadata["component"] as? Map<*, *> ?: error("module component missing: $path")
        check(
            component["group"] == groupId &&
                component["module"] == artifact.componentArtifactId &&
                component["version"] == version
        ) {
            "invalid module component for ${artifact.artifactId}: $component"
        }
        val variants = metadata["variants"] as? List<*> ?: error("module variants missing: $path")
        val actualDependencies = variants.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .flatMap { (it["dependencies"] as? List<*>).orEmpty().asSequence() }
            .mapNotNull { it as? Map<*, *> }
            .filter { it["group"] == groupId }
            .map { dependency ->
                val dependencyVersion = (dependency["version"] as? Map<*, *>)?.get("requires")
                "${dependency["group"]}:${dependency["module"]}:$dependencyVersion"
            }
            .toSet()
        val expectedDependencies = artifact.moduleProjectDependency
            ?.let { setOf("$groupId:$it:$version") }
            .orEmpty()
        check(actualDependencies == expectedDependencies) {
            "module project dependencies differ for ${artifact.artifactId}; " +
                "expected=$expectedDependencies, actual=$actualDependencies"
        }

        val actualTargets = variants.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull { it["available-at"] as? Map<*, *> }
            .filter { it["group"] == groupId && it["version"] == version }
            .mapNotNull { it["module"] as? String }
            .toSet()
        check(actualTargets == artifact.targets) {
            "module available-at targets differ for ${artifact.artifactId}; " +
                "expected=${artifact.targets}, actual=$actualTargets"
        }
    }

    private fun signatureOf(path: Path): Path = path.resolveSibling("${path.fileName}.asc")

    private fun Element.child(name: String): Element? =
        (0 until childNodes.length)
            .asSequence()
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .firstOrNull { it.localName == name || it.nodeName == name }

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length)
            .asSequence()
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.localName == name || it.nodeName == name }
            .toList()

    private fun Element.childText(name: String): String? = child(name)?.textContent?.trim()

    data class VerificationSummary(
        val coordinateCount: Int,
        val signatureCount: Int,
    )

    private data class ExpectedArtifact(
        val artifactId: String,
        val primaryExtension: String,
        val pomProjectDependency: String? = null,
        val targets: Set<String> = emptySet(),
        val componentArtifactId: String = artifactId,
        val moduleProjectDependency: String? = pomProjectDependency,
    )

    private val expectedArtifacts = listOf(
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
