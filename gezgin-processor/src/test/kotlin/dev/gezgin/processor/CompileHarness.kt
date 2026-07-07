package dev.gezgin.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.tschuchort.compiletesting.symbolProcessorProviders
import java.io.ByteArrayOutputStream
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Shared compile-test harness for every later `:gezgin-processor` task. Spins up a real KSP2
 * compilation (via kctfork) with [GezginProcessorProvider] registered and `:gezgin-core`'s jvm
 * classes visible on the classpath (through `inheritClassPath`).
 */
@OptIn(ExperimentalCompilerApi::class)
object CompileHarness {

    /** Raw compiler + KSP message output from the most recent [compileGezgin] call. */
    val lastMessages: ByteArrayOutputStream = ByteArrayOutputStream()

    fun compileGezgin(vararg sources: SourceFile): JvmCompilationResult {
        lastMessages.reset()
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += GezginProcessorProvider()
            }
            inheritClassPath = true
            messageOutputStream = lastMessages
        }
        return compilation.compile()
    }

    /** Convenience accessor mirroring the brief's `result.generatedSourceFor(name)` ask. */
    fun JvmCompilationResult.generatedSourceFor(fileName: String) =
        sourcesGeneratedBySymbolProcessor.firstOrNull { it.name == fileName }
}
