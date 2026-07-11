package dev.gezgin.processor

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class ProductionErrorMessageLanguageTest {

    @Test
    fun `production string literals are English`() {
        val repoRoot = repoRoot()
        val sourceRoots = listOf(
            "gezgin-core/src/commonMain/kotlin",
            "gezgin-core/src/androidMain/kotlin",
            "gezgin-core/src/jvmMain/kotlin",
            "gezgin-mvi/src/commonMain/kotlin",
            "gezgin-test/src/commonMain/kotlin",
            "gezgin-processor/src/main/kotlin",
        ).map(repoRoot::resolve)

        val offenders = sourceRoots
            .filter(Files::exists)
            .flatMap(::kotlinFiles)
            .flatMap { file ->
                extractStringLiterals(Files.readString(file)).filter { it.text.hasTurkishLetter() }.map { literal ->
                    "${repoRoot.relativize(file)}:${literal.line}: ${literal.text.preview()}"
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "Production string literals must use English. Offenders:\n${offenders.joinToString("\n")}",
        )
    }

    private fun repoRoot(): Path {
        var dir = Paths.get("").toAbsolutePath()
        while (!Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = requireNotNull(dir.parent) { "Could not find repository root from ${Paths.get("").toAbsolutePath()}" }
        }
        return dir
    }

    private fun kotlinFiles(root: Path): List<Path> =
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .toList()
        }

    private data class StringLiteral(val line: Int, val text: String)

    private fun extractStringLiterals(source: String): List<StringLiteral> {
        val result = mutableListOf<StringLiteral>()
        var i = 0
        var line = 1

        while (i < source.length) {
            when {
                source.startsWith("//", i) -> {
                    i += 2
                    while (i < source.length && source[i] != '\n') i++
                }
                source.startsWith("/*", i) -> {
                    i += 2
                    while (i < source.length && !source.startsWith("*/", i)) {
                        if (source[i] == '\n') line++
                        i++
                    }
                    if (i < source.length) i += 2
                }
                source.startsWith("\"\"\"", i) -> {
                    val startLine = line
                    i += 3
                    val text = StringBuilder()
                    while (i < source.length && !source.startsWith("\"\"\"", i)) {
                        val c = source[i++]
                        if (c == '\n') line++
                        text.append(c)
                    }
                    if (i < source.length) i += 3
                    result += StringLiteral(startLine, text.toString())
                }
                source[i] == '"' -> {
                    val startLine = line
                    i++
                    val text = StringBuilder()
                    while (i < source.length) {
                        val c = source[i++]
                        when {
                            c == '\\' && i < source.length -> {
                                text.append(c)
                                text.append(source[i++])
                            }
                            c == '"' -> break
                            else -> {
                                if (c == '\n') line++
                                text.append(c)
                            }
                        }
                    }
                    result += StringLiteral(startLine, text.toString())
                }
                else -> {
                    if (source[i] == '\n') line++
                    i++
                }
            }
        }

        return result
    }

    private fun String.hasTurkishLetter(): Boolean = any { it.code in turkishCodePoints }

    private fun String.preview(): String =
        replace('\n', ' ').let { if (it.length <= 160) it else it.take(157) + "..." }

    private companion object {
        private val turkishCodePoints = setOf(
            0x011F,
            0x011E,
            0x0131,
            0x0130,
            0x015F,
            0x015E,
            0x00FC,
            0x00DC,
            0x00F6,
            0x00D6,
            0x00E7,
            0x00C7,
        )
    }
}
