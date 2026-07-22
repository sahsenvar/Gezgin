package dev.gezgin.buildlogic.ci

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals

class MaintainedBuildTextContractTest {
  private val projectRoot: Path =
    Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().let { workingDirectory ->
      if (workingDirectory.fileName.toString() == "buildSrc") workingDirectory.parent
      else workingDirectory
    }

  @Test
  fun `maintained build and configuration comments contain no implementation history labels`() {
    val findings =
      Files.walk(projectRoot).use { paths ->
        paths
          .filter(Path::isRegularFile)
          .filter(::isMaintainedBuildText)
          .flatMap { path ->
            path
              .readLines()
              .mapIndexedNotNull { index, line ->
                val comment = line.commentText() ?: return@mapIndexedNotNull null
                if (implementationHistory.containsMatchIn(comment)) {
                  "${projectRoot.relativize(path)}:${index + 1}: ${comment.trim()}"
                } else {
                  null
                }
              }
              .stream()
          }
          .sorted()
          .toList()
      }

    assertEquals(emptyList(), findings, findings.joinToString(separator = "\n"))
  }

  private fun isMaintainedBuildText(path: Path): Boolean {
    val relative = projectRoot.relativize(path).toString().replace('\\', '/')
    if (
      relative.startsWith(".git/") ||
        relative.startsWith(".gradle/") ||
        relative.contains("/build/") ||
        relative.startsWith("docs/superpowers/")
    ) {
      return false
    }
    return relative.endsWith(".gradle.kts") ||
      relative == "gradle/libs.versions.toml" ||
      relative.startsWith("buildSrc/src/main/") && relative.endsWith(".kt") ||
      relative.startsWith("gradle/") && relative.endsWith(".sh") ||
      relative.startsWith(".github/") &&
        (relative.endsWith(".yml") || relative.endsWith(".yaml")) ||
      relative.endsWith(".properties")
  }

  private fun String.commentText(): String? {
    val trimmed = trimStart()
    return when {
      trimmed.startsWith("//") -> trimmed.removePrefix("//")
      trimmed.startsWith("#") && !trimmed.startsWith("#!") -> trimmed.removePrefix("#")
      trimmed.startsWith("*") -> trimmed.removePrefix("*")
      else -> null
    }
  }

  private companion object {
    val implementationHistory =
      Regex(
        "\\b(?:faz\\s*\\d|task\\s*[-#:]?\\s*\\d|spike\\s+task|final[- ]review|" +
          "deliverable|review\\s+checkpoint|process[- ]history)\\b",
        RegexOption.IGNORE_CASE,
      )
  }
}
