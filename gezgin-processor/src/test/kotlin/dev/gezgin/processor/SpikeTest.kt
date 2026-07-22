package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)

/**
 * Task 2.0 spike gate. Two things must hold for every later `:gezgin-processor` task to be able to
 * build on [CompileHarness]:
 * 1. A source implementing `dev.gezgin.core.Route` compiles OK — proves `inheritClassPath` actually
 *    exposes `:gezgin-core`'s jvm classes to the in-memory compilation.
 * 2. The processor's log message shows up in the compilation output — proves KSP2 actually invoked
 *    [GezginProcessor], not just that the provider was registered.
 */
class SpikeTest {

  @Test
  fun `source implementing Route compiles against inherited classpath`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "SampleGraph.kt",
          """
          package dev.gezgin.sample

          import dev.gezgin.core.Route

          class SampleGraph : Route
          """
            .trimIndent(),
        )
      )

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }

  @Test
  fun `processor runs and logs during KSP2 compilation`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "SampleGraph2.kt",
          """
          package dev.gezgin.sample

          import dev.gezgin.core.Route

          class SampleGraph2 : Route
          """
            .trimIndent(),
        )
      )

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "Gezgin processor alive")
  }
}
