package dev.gezgin.buildlogic.kdoc

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class IncludedPublicKDocInventoryContractTest {
  private val projectRoot: Path =
    Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().let { workingDirectory ->
      if (workingDirectory.fileName.toString() == "buildSrc") workingDirectory.parent
      else workingDirectory
    }

  @Test
  fun `included public KDoc token inventory has no unexplained compact ids or prose artifacts`() {
    val findings = mutableSetOf<String>()
    KotlinPublicApiScanner().use { scanner ->
      publishedModules.forEach { module ->
        Files.walk(projectRoot.resolve(module).resolve("src")).use { paths ->
          paths
            .filter(Path::isRegularFile)
            .filter { path ->
              path.extension == "kt" &&
                !testSourceSet.containsMatchIn(path.toString().replace('\\', '/').lowercase())
            }
            .forEach { path ->
              val relativePath = projectRoot.relativize(path).toString().replace('\\', '/')
              val result =
                scanner.scan(KotlinSourceInput(path = relativePath, content = path.readText()))
              result.declarations
                .asSequence()
                .filterNot(PublicApiDeclaration::excluded)
                .mapNotNull { declaration -> declaration.kDocText?.let { declaration to it } }
                .forEach { (declaration, kDoc) ->
                  inventoryCompactIds(kDoc).forEach { token ->
                    findings += "$relativePath:${declaration.line}: compact id '$token'"
                  }
                  inventoryProse(kDoc).forEach { defect ->
                    findings += "$relativePath:${declaration.line}: $defect"
                  }
                }
            }
        }
      }
    }

    assertEquals(emptySet(), findings, findings.sorted().joinToString(separator = "\n"))
  }

  private fun inventoryCompactIds(kDoc: String): Set<String> =
    token.findAll(kDoc).map(MatchResult::value).filter(::looksLikeInternalId).toSet()

  private fun looksLikeInternalId(candidate: String): Boolean {
    if (
      candidate.length >= 3 &&
        candidate[0] == 'm' &&
        candidate[1].isUpperCase() &&
        candidate.drop(2).all(Char::isDigit)
    ) {
      return true
    }
    val parts = candidate.split('-')
    if (parts.size < 2 || parts.first().length !in 1..2) return false
    val validShape =
      parts
        .mapIndexed { index, part ->
          val letters = part.takeWhile(Char::isLetter)
          val digits = part.drop(letters.length)
          letters.length in 1..2 &&
            letters.all(Char::isUpperCase) &&
            digits.all(Char::isDigit) &&
            (digits.isEmpty() || index == parts.lastIndex)
        }
        .all { it }
    return validShape &&
      (parts.size >= 3 || parts.any { part -> part.length == 1 || part.any(Char::isDigit) })
  }

  private fun inventoryProse(kDoc: String): Set<String> {
    val findings = mutableSetOf<String>()
    val words = word.findAll(kDoc).toList()
    words.zipWithNext().forEach { (left, right) ->
      val separator = kDoc.substring(left.range.last + 1, right.range.first)
      if (
        left.value.length >= 3 &&
          left.value.equals(right.value, ignoreCase = true) &&
          separator.all(Char::isWhitespace)
      ) {
        findings += "duplicate word '${left.value.lowercase()}'"
      }
    }
    var depth = 0
    withoutInlineCode.replace(kDoc, "").forEach { character ->
      when (character) {
        '(' -> depth++
        ')' -> if (depth == 0) findings += "unmatched ')'" else depth--
      }
    }
    if (depth != 0) findings += "unmatched '('"
    return findings
  }

  private companion object {
    val publishedModules = listOf("gezgin-core", "gezgin-mvi", "gezgin-processor", "gezgin-test")
    val token = Regex("[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)+|[A-Za-z][A-Za-z0-9]*")
    val word = Regex("[A-Za-z]+")
    val withoutInlineCode = Regex("`[^`]*`")
    val testSourceSet = Regex("/src/[^/]*test/")
  }
}
