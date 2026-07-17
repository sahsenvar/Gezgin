package dev.gezgin.sample.shopr

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ShoprRestoreKeyTest {
    @Test
    fun `shopr rememberNavigator host wires its stable restore namespace`() {
        val source = mainActivitySource()
        assertTrue(
            Regex("""private\s+const\s+val\s+SHOPR_RESTORE_KEY\s*=\s*"shopr-main"""")
                .containsMatchIn(source),
            "Shopr host must retain its explicit stable restore namespace",
        )

        val arguments = callArguments(source, "val navigator = rememberNavigator(")
        assertTrue(
            Regex("""\brestoreKey\s*=\s*SHOPR_RESTORE_KEY\b""").containsMatchIn(arguments),
            "Shopr host must pass SHOPR_RESTORE_KEY to its rememberNavigator call",
        )
    }
}

private fun mainActivitySource(): String = sequenceOf(
    File("sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/MainActivity.kt"),
    File("src/main/kotlin/dev/gezgin/sample/shopr/MainActivity.kt"),
).firstOrNull(File::isFile)?.readText()
    ?: error("MainActivity.kt was not found from ${File(".").absolutePath}")

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
