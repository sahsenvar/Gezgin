package dev.gezgin.buildlogic.kdoc

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

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
  override fun close() = Unit

  fun scan(input: KotlinSourceInput): List<ProductionCommentFinding> {
    return input.commentBlocks().flatMap { comment ->
      val text = comment.text
      val line = input.content.substring(0, comment.offset).count { it == '\n' } + 1
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

  private fun KotlinSourceInput.commentBlocks(): List<CommentBlock> {
    val lexer = KotlinLexer()
    lexer.start(content)
    val comments = buildList {
      while (lexer.tokenType != null) {
        if (lexer.tokenType !in COMMENT_TOKENS) {
          lexer.advance()
          continue
        }
        add(CommentToken(lexer.tokenStart, lexer.tokenEnd, lexer.tokenType == KtTokens.EOL_COMMENT))
        lexer.advance()
      }
    }
    return comments.fold(mutableListOf()) { blocks, token ->
      val previous = blocks.lastOrNull()
      val gap = previous?.let { content.substring(it.end, token.start) }
      if (
        previous?.lineComment == true && token.lineComment && gap?.matches(LINE_COMMENT_GAP) == true
      ) {
        blocks[blocks.lastIndex] =
          previous.copy(end = token.end, text = content.substring(previous.offset, token.end))
      } else {
        blocks +=
          CommentBlock(
            offset = token.start,
            end = token.end,
            lineComment = token.lineComment,
            text = content.substring(token.start, token.end),
          )
      }
      blocks
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
    var inlineCodeIndex = 0
    val prose = WITHOUT_INLINE_CODE.replace(this) { "KDOCSPAN${inlineCodeIndex++}" }
    if (
      DUPLICATE_WORD.containsMatchIn(prose) ||
        EMPTY_PUNCTUATION.containsMatchIn(prose) ||
        EMPTY_PARENTHESES.containsMatchIn(prose) ||
        OPENING_PUNCTUATION.containsMatchIn(prose) ||
        INNER_PARENTHESES_WHITESPACE.containsMatchIn(prose) ||
        LIST_FRAGMENT.containsMatchIn(prose) ||
        ORPHAN_POSSESSIVE.containsMatchIn(prose) ||
        SEPARATED_POSSESSIVE.containsMatchIn(prose) ||
        hasDoubledCommentPrefixWhitespace() ||
        INTERNAL_DOUBLE_HYPHEN.containsMatchIn(prose) ||
        MALFORMED_RULE_LABEL.containsMatchIn(prose) ||
        DUPLICATE_FUTURE.containsMatchIn(prose) ||
        prose.containsLowercaseSentenceFragment()
    ) {
      return true
    }
    if (startsWith("/**") && LOWERCASE_KDOC_START.containsMatchIn(prose)) return true
    var depth = 0
    prose.forEach { character ->
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

  private fun String.containsLowercaseSentenceFragment(): Boolean =
    LOWERCASE_SENTENCE_FRAGMENT.findAll(this).any { match ->
      val prefix = substring(0, match.range.first).trimEnd()
      val previousToken = prefix.takeLastWhile { it.isLetter() || it == '.' }.lowercase()
      previousToken !in SAFE_ABBREVIATIONS
    }

  private fun String.hasDoubledCommentPrefixWhitespace(): Boolean {
    if (startsWith("//")) return startsWith("//  ") && getOrNull(4)?.isWhitespace() == false
    if (!startsWith("/**")) return false
    val firstContentLine =
      lineSequence().drop(1).firstOrNull { line ->
        line.trim().removePrefix("*").removeSuffix("*/").isNotBlank()
      } ?: return false
    return KDoc_DOUBLED_PREFIX_WHITESPACE.containsMatchIn(firstContentLine)
  }

  private companion object {
    val COMMENT_TOKENS = setOf(KtTokens.EOL_COMMENT, KtTokens.BLOCK_COMMENT, KtTokens.DOC_COMMENT)
    val LINE_COMMENT_GAP = Regex("[ \\t]*\\r?\\n[ \\t]*")
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
    val EMPTY_PARENTHESES = Regex("(?<=\\s)\\(\\s*\\)")
    val OPENING_PUNCTUATION = Regex("\\(\\s*[,;:]")
    val INNER_PARENTHESES_WHITESPACE = Regex("\\([ \\t]+\\S|\\S[ \\t]+\\)")
    val LIST_FRAGMENT = Regex("(?m)^\\s*\\*\\s+(?:no|and|or|the)\\s*$", RegexOption.IGNORE_CASE)
    val ORPHAN_POSSESSIVE = Regex("(?m)^\\s*\\*\\s+['’]s\\b", RegexOption.IGNORE_CASE)
    val SEPARATED_POSSESSIVE = Regex("\\b[A-Za-z][A-Za-z0-9]*\\s+['’]s\\b")
    val KDoc_DOUBLED_PREFIX_WHITESPACE = Regex("^\\s*\\*[ \\t]{2}\\S")
    val INTERNAL_DOUBLE_HYPHEN =
      Regex("\\b(?:spec|review|test|phase)--[A-Za-z]", RegexOption.IGNORE_CASE)
    val MALFORMED_RULE_LABEL = Regex("\\b(?:problem|rule)\\s+\\d+\\b", RegexOption.IGNORE_CASE)
    val LOWERCASE_SENTENCE_FRAGMENT = Regex("[.!?]\\s+([a-z])\\w*\\b")
    val SAFE_ABBREVIATIONS = setOf("e.g", "i.e", "etc", "vs", "cf", "bkz")
    val DUPLICATE_FUTURE =
      Regex("\\b(?:later in the future|ileride gelecekte)\\b", RegexOption.IGNORE_CASE)
    val LOWERCASE_KDOC_START = Regex("(?s)^/\\*\\*\\s*\\n?\\s*\\*?\\s+[a-z][a-z-]+\\b")
    val WITHOUT_INLINE_CODE = Regex("`[^`]*`")
  }

  private data class CommentToken(val start: Int, val end: Int, val lineComment: Boolean)

  private data class CommentBlock(
    val offset: Int,
    val end: Int,
    val lineComment: Boolean,
    val text: String,
  )
}
