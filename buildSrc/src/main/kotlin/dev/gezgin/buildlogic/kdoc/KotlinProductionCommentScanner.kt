package dev.gezgin.buildlogic.kdoc

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory

data class ProductionCommentFinding(
  val path: String,
  val line: Int,
  val kind: ProductionCommentFindingKind,
  val text: String,
)

enum class ProductionCommentFindingKind {
  INTERNAL_HISTORY,
  TURKISH_HISTORY,
  MALFORMED_PROSE,
}

class KotlinProductionCommentScanner : AutoCloseable {
  private val disposable = Disposer.newDisposable("gezgin-production-comment-scanner")
  private val environment =
    KotlinCoreEnvironment.createForProduction(
      disposable,
      CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
      },
      EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
  private val psiFactory = KtPsiFactory(environment.project, markGenerated = false)

  override fun close() {
    Disposer.dispose(disposable)
  }

  fun scan(input: KotlinSourceInput): List<ProductionCommentFinding> {
    val file = psiFactory.createFile(input.path.substringAfterLast('/'), input.content)
    return PsiTreeUtil.collectElementsOfType(file, PsiComment::class.java).flatMap { comment ->
      val text = comment.text
      val offset = comment.textOffset.coerceAtLeast(0).coerceAtMost(input.content.length)
      val line = input.content.substring(0, offset).count { it == '\n' } + 1
      buildList {
        if (text.containsInternalHistory()) {
          add(
            ProductionCommentFinding(
              input.path,
              line,
              ProductionCommentFindingKind.INTERNAL_HISTORY,
              text,
            )
          )
        }
        if (TURKISH_HISTORY.containsMatchIn(text)) {
          add(
            ProductionCommentFinding(
              input.path,
              line,
              ProductionCommentFindingKind.TURKISH_HISTORY,
              text,
            )
          )
        }
        if (text.containsMalformedProse()) {
          add(
            ProductionCommentFinding(
              input.path,
              line,
              ProductionCommentFindingKind.MALFORMED_PROSE,
              text,
            )
          )
        }
      }
    }
  }

  private fun String.containsInternalHistory(): Boolean =
    WITHOUT_INLINE_CODE.replace(this, "").let { prose ->
      STALE_REFERENCE.containsMatchIn(prose) ||
        COMPACT_TOKEN.findAll(prose).any { token -> token.value.looksLikeCompactInternalId() }
    }

  private fun String.looksLikeCompactInternalId(): Boolean {
    val parts = split('-')
    return parts.size >= 3 || parts.any { it.length == 1 || it.any(Char::isDigit) }
  }

  private fun String.containsMalformedProse(): Boolean {
    if (
      DUPLICATE_WORD.containsMatchIn(this) ||
        EMPTY_PUNCTUATION.containsMatchIn(this) ||
        LIST_FRAGMENT.containsMatchIn(this) ||
        DUPLICATE_FUTURE.containsMatchIn(this)
    ) {
      return true
    }
    if (startsWith("/**") && LOWERCASE_KDOC_START.containsMatchIn(this)) return true
    var depth = 0
    WITHOUT_INLINE_CODE.replace(this, "").forEach { character ->
      when (character) {
        '(' -> depth++
        ')' -> {
          if (depth == 0) return true
          depth--
        }
      }
    }
    return depth != 0
  }

  private companion object {
    val STALE_REFERENCE =
      Regex(
        "(?i:§\\s*[a-z]?\\d+(?:\\.\\d+)*)|" +
          "(?i:\\b(?:spec|task|phase|faz)\\s*(?:[-#:]\\s*)?\\d+(?:[.-]\\d+)*\\b)|" +
          "\\b(?:m\\d+|m[A-Z]{1,2}\\d+|[MCRNK]\\d+[\u2032']?)\\b|" +
          "(?i:\\b(?:final[- ]review|jar[- ](?:verified|doğrulandı)|test provenance|review provenance)\\b)"
      )
    val COMPACT_TOKEN = Regex("\\b[A-Z0-9]{1,2}(?:-[A-Z0-9]{1,2})+\\b|\\bm[A-Z]{1,2}\\d+\\b")
    val TURKISH_HISTORY =
      Regex(
        "\\b(?:faz|görev|gorev|aşama|asama|rapor|bulgu|inceleme|devir|spike|karar|" +
          "kanıt|kanit|doğrulandı|dogrulandi)\\b",
        RegexOption.IGNORE_CASE,
      )
    val DUPLICATE_WORD = Regex("\\b([A-Za-z]{3,})\\s+\\1\\b", RegexOption.IGNORE_CASE)
    val EMPTY_PUNCTUATION = Regex("[,;:]\\s*\\)")
    val LIST_FRAGMENT = Regex("(?m)^\\s*\\*\\s+(?:no|and|or|the)\\s*$", RegexOption.IGNORE_CASE)
    val DUPLICATE_FUTURE =
      Regex("\\b(?:later in the future|ileride gelecekte)\\b", RegexOption.IGNORE_CASE)
    val LOWERCASE_KDOC_START = Regex("(?s)^/\\*\\*\\s*\\n?\\s*\\*?\\s+[a-z][a-z-]+\\b")
    val WITHOUT_INLINE_CODE = Regex("`[^`]*`")
  }
}
