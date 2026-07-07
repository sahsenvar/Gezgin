package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.core.NavResult
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import dev.gezgin.processor.fixtures.TEST_API_RUNNER_SOURCE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("TestApiRunner.kt", TEST_API_RUNNER_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitTestAccessors" to "true"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val delivered = result.classLoader
            .loadClass("dev.gezgin.shop.TestApiRunnerKt")
            .getMethod("typedTestApiScenario")
            .invoke(null)

        assertTrue(delivered is NavResult.Value<*>, "expected a delivered Value, got $delivered")
        val orderId = (delivered as NavResult.Value<*>).value
        val innerValue = orderId!!.javaClass.getDeclaredField("value").apply { isAccessible = true }.get(orderId)
        assertEquals("o1", innerValue)
    }

    @Test
    fun `fromX golden — emitted for Feed and Catalog, NOT for bare About`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitTestAccessors" to "true"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val accessorsSource = result.generatedSourceFor("GezginTestAccessors.kt")
        assertNotNull(accessorsSource, "expected GezginTestAccessors.kt with gezgin.emitTestAccessors=true")
        val text = accessorsSource.readText()
        assertTrue("fromFeed()" in text, text)
        assertTrue("fromCatalog()" in text, text)
        assertFalse("fromAbout" in text, text)
    }

    @Test
    fun `option off (default) — no GezginTestAccessors file emitted`() {
        val result = compileGezgin(
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
