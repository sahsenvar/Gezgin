package dev.gezgin.sample.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowcaseRestoreKeyTest {
  @Test
  fun `showcase host uses one explicit stable restore namespace`() {
    assertEquals("sample-showcase", SHOWCASE_RESTORE_KEY)
  }

  @Test
  fun `showcase rememberNavigator host wires the stable restore namespace`() {
    val source = mainActivitySource()
    val arguments = callArguments(source, "rememberNavigator(")

    assertTrue(
      Regex("""\brestoreKey\s*=\s*SHOWCASE_RESTORE_KEY\b""").containsMatchIn(arguments),
      "GezginShowcaseApp must pass SHOWCASE_RESTORE_KEY to its rememberNavigator host call",
    )
  }
}

private fun mainActivitySource(): String =
  sequenceOf(
      File("sample/app/src/main/kotlin/dev/gezgin/sample/app/MainActivity.kt"),
      File("src/main/kotlin/dev/gezgin/sample/app/MainActivity.kt"),
    )
    .firstOrNull(File::isFile)
    ?.readText() ?: error("MainActivity.kt was not found from ${File(".").absolutePath}")

private fun callArguments(source: String, callPrefix: String): String {
  val start = source.indexOf(callPrefix)
  require(start >= 0) { "Host call '$callPrefix' was not found" }
  val argumentsStart = start + callPrefix.length
  var depth = 1
  for (index in argumentsStart until source.length) {
    when (source[index]) {
      '(' -> depth += 1
      ')' -> {
        depth -= 1
        if (depth == 0) return source.substring(argumentsStart, index)
      }
    }
  }
  error("Host call '$callPrefix' has unbalanced parentheses")
}
