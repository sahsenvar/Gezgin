package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.core.NavResult
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.compileGezginModule
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import dev.gezgin.processor.fixtures.TEST_API_RUNNER_SOURCE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 2.6 gate for [dev.gezgin.processor.codegen.TestApiCodegen]: §13's typed test API —
 * `GezginTestNavigator.fromX()` extensions, one per source route [NavigatorCodegen] itself
 * generates a navigator for, emitted ONLY when the `gezgin.emitTestAccessors=true` KSP option is
 * set (default `false` — production modules never depend on `:gezgin-test`).
 */
@OptIn(ExperimentalCompilerApi::class)
class TestApiCodegenTest {

  @Test
  fun `typed fromX round-trip through generated GezginTestNavigator accessors`() {
    val result =
      compileGezgin(
        SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
        SourceFile.kotlin("TestApiRunner.kt", TEST_API_RUNNER_SOURCE),
        kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitTestAccessors" to "true"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val delivered =
      result.classLoader
        .loadClass("dev.gezgin.shop.TestApiRunnerKt")
        .getMethod("typedTestApiScenario")
        .invoke(null)

    assertTrue(delivered is NavResult.Value<*>, "expected a delivered Value, got $delivered")
    val orderId = (delivered as NavResult.Value<*>).value
    val innerValue =
      orderId!!.javaClass.getDeclaredField("value").apply { isAccessible = true }.get(orderId)
    assertEquals("o1", innerValue)
  }

  @Test
  fun `fromX golden — emitted for Feed Catalog and bare About`() {
    val result =
      compileGezgin(
        SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
        kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitTestAccessors" to "true"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val accessorsSource = result.generatedSourceFor("GezginTestAccessors.kt")
    assertNotNull(
      accessorsSource,
      "expected GezginTestAccessors.kt with gezgin.emitTestAccessors=true",
    )
    val text = accessorsSource.readText()
    assertTrue("fromFeed()" in text, text)
    assertTrue("fromCatalog()" in text, text)
    assertTrue("fromAbout()" in text, text)
  }

  /**
   * F-MAJOR-2 root-cause + fix, reproduced across the spec §3.3 multi-module split (graphs in
   * `main`, tests in a source set that only SEES `main` as compiled classes). The canonical fix is
   * to set `gezgin.emitTestAccessors=true` on the round that OWNS the graphs (the `main` KSP round)
   * rather than on a downstream test round that sees the graphs only as binary classpath entries.
   *
   * Stage 1 = the nav module's `main` round: graphs present → accessors emitted here (typed,
   * calling the right `raw.xNavigator(entryIdOf(...))` factory). Stage 2 = a SEPARATE round that
   * has NO graph sources (they are compiled classes on its classpath) and does NOT re-set the flag:
   * it consumes the stage-1 `fromX()` accessors cross-module (compiles clean) and — the locked-in
   * root cause — emits NO accessors of its own, because `getSymbolsWithAnnotation` never
   * re-discovers `@NavGraph` off binary classpath.
   */
  @Test
  fun `fromX emitted in the graph-owning main round, consumed cross-module, not re-emitted off classpath`() {
    // Stage 1 — the "nav module" main round: graphs are in-source here, flag ON.
    val navModule =
      compileGezginModule(
        SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
        kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitTestAccessors" to "true"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, navModule.exitCode, navModule.messages)

    val accessors = navModule.generatedSourceFor("GezginTestAccessors.kt")
    assertNotNull(
      accessors,
      "graph-owning main round must emit GezginTestAccessors.kt: ${navModule.messages}",
    )
    val text = accessors.readText()
    // Typed + calls the right factory through the nearest-entry resolver (not a raw string
    // handoff).
    // kotlinpoet renders the single-`return` body as an expression body (`= raw.…`).
    assertTrue(
      "public fun GezginTestNavigator.fromFeed(): FeedNavigator = " +
        "raw.feedNavigator(entryIdOf(HomeGraph.Feed::class))" in text,
      text,
    )

    // Stage 2 — a downstream round (the module's test source set analog): NO graph sources, flag
    // NOT set,
    // stage-1 output on the classpath. It calls nav.fromFeed()/fromCart()/fromPayment() from stage
    // 1.
    val testModule =
      compileGezginModule(
        SourceFile.kotlin("TestApiRunner.kt", TEST_API_RUNNER_SOURCE),
        extraClasspath = listOf(navModule.outputDirectory),
      )
    assertEquals(
      KotlinCompilation.ExitCode.OK,
      testModule.exitCode,
      "typed fromX() accessors from the main round must be consumable cross-module: ${testModule.messages}",
    )
    // Root cause locked in: a round that sees graphs only as binary classpath entries emits nothing
    // —
    // exactly why the flag must ride the main (graph-owning) round, not a downstream test round.
    assertNull(
      testModule.generatedSourceFor("GezginTestAccessors.kt"),
      "off-classpath round must NOT re-emit accessors (no @NavGraph re-discovered): ${testModule.messages}",
    )
  }

  @Test
  fun `option off (default) — no GezginTestAccessors file emitted`() {
    val result =
      compileGezgin(
        SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
        kspArgs = mapOf("gezgin.emitSerializers" to "false"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    assertNull(
      result.generatedSourceFor("GezginTestAccessors.kt"),
      "gezgin.emitTestAccessors defaults to false — no test-accessor file should be emitted",
    )
  }
}
