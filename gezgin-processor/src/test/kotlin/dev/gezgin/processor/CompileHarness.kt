package dev.gezgin.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.tschuchort.compiletesting.symbolProcessorProviders
import java.io.ByteArrayOutputStream
import java.io.File
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

    private var lastCompilation: KotlinCompilation? = null

    fun compileGezgin(vararg sources: SourceFile, kspArgs: Map<String, String> = emptyMap()): JvmCompilationResult {
        lastMessages.reset()
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += GezginProcessorProvider()
                processorOptions.putAll(kspArgs)
            }
            inheritClassPath = true
            messageOutputStream = lastMessages
        }
        lastCompilation = compilation
        return compilation.compile()
    }

    /** Convenience accessor mirroring the brief's `result.generatedSourceFor(name)` ask. */
    fun JvmCompilationResult.generatedSourceFor(fileName: String) =
        sourcesGeneratedBySymbolProcessor.firstOrNull { it.name == fileName }

    /**
     * Locates a non-kt/java KSP-generated resource (e.g. the `gezgin.dumpModel` test dump) by file
     * name anywhere under the most recent [compileGezgin] call's working directory. Such files
     * don't participate in compilation, so they aren't exposed via [sourcesGeneratedBySymbolProcessor]
     * and must be found on disk instead.
     */
    fun findGeneratedResource(fileName: String): File? =
        lastCompilation?.workingDir?.walkTopDown()?.firstOrNull { it.isFile && it.name == fileName }
}
