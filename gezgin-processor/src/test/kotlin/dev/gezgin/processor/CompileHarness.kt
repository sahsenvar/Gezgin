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

  fun compileGezgin(
    vararg sources: SourceFile,
    kspArgs: Map<String, String> = emptyMap(),
  ): JvmCompilationResult {
    lastMessages.reset()
    val compilation =
      KotlinCompilation().apply {
        this.sources = sources.toList()
        configureKsp(useKsp2 = true) {
          symbolProcessorProviders += GezginProcessorProvider()
          processorOptions.putAll(kspArgs)
        }
        inheritClassPath = true
        messageOutputStream = lastMessages
        // Task 3.4: entry codegen calls into compose-runtime/-foundation inline accessors
        // (`current`, etc) — those jars are built up to JVM 17 bytecode, which kctfork's own
        // default (1.8) can't inline into. Match the highest floor actually seen.
        jvmTarget = "17"
        // A lambda parameter typed `@Composable (R) -> Unit` (GezginEntryScope.register's
        // `content`) ICEs the invokedynamic lambda-metafactory backend without the real
        // compose-compiler plugin registered (no plugin here — kctfork has none, Task 3.4
        // brief's finding); classic anonymous-class lambda codegen sidesteps it entirely.
        kotlincArguments += listOf("-Xlambdas=class", "-Xsam-conversions=class")
      }
    lastCompilation = compilation
    return compilation.compile()
  }

  /**
   * Compiles ONE module in isolation (its own KSP round), optionally against a prior module's
   * compiled output on the classpath — the closest kctfork can get to the spec §3.3 multi-module
   * layout within its single-compilation-unit model. Feeding a `:navigation` module's
   * [JvmCompilationResult.outputDirectory] as [extraClasspath] to a second (feature) compilation
   * makes the navigation routes/navigators visible as CLASSPATH symbols — so the feature's own KSP
   * `getSymbolsWithAnnotation` sees `@Screen`s but NO graphs (graphs are compiled classes, not
   * sources), exactly the cross-module condition [dev.gezgin.processor.GezginProcessor] must
   * handle.
   *
   * Each call captures its own messages (via [JvmCompilationResult.messages]); unlike
   * [compileGezgin] it does NOT touch [lastMessages], so a two-stage test can read each stage's
   * output independently.
   */
  fun compileGezginModule(
    vararg sources: SourceFile,
    kspArgs: Map<String, String> = emptyMap(),
    extraClasspath: List<File> = emptyList(),
  ): JvmCompilationResult {
    val compilation =
      KotlinCompilation().apply {
        this.sources = sources.toList()
        configureKsp(useKsp2 = true) {
          symbolProcessorProviders += GezginProcessorProvider()
          processorOptions.putAll(kspArgs)
        }
        inheritClassPath = true
        classpaths += extraClasspath
        jvmTarget = "17"
        kotlincArguments += listOf("-Xlambdas=class", "-Xsam-conversions=class")
      }
    lastCompilation = compilation
    return compilation.compile()
  }

  /** Convenience accessor mirroring the brief's `result.generatedSourceFor(name)` ask. */
  fun JvmCompilationResult.generatedSourceFor(fileName: String) =
    sourcesGeneratedBySymbolProcessor.firstOrNull { it.name == fileName }

  /**
   * Locates a non-kt/java KSP-generated resource (e.g. the `gezgin.dumpModel` test dump) by file
   * name anywhere under the most recent [compileGezgin] call's working directory. Such files don't
   * participate in compilation, so they aren't exposed via [sourcesGeneratedBySymbolProcessor] and
   * must be found on disk instead.
   */
  fun findGeneratedResource(fileName: String): File? =
    lastCompilation?.workingDir?.walkTopDown()?.firstOrNull { it.isFile && it.name == fileName }
}
