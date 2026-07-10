package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.RUNNER_SOURCE
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import dev.gezgin.processor.fixtures.UNDECLARED_EDGE_RUNNER_SOURCE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 2.5 gate for [dev.gezgin.processor.codegen.NavigatorCodegen]: the codegen's core value
 * proposition is "tanımsız yere gidiş DERLENMEZ" — an edge that was never declared on a source
 * route has no corresponding generated method, so calling it is an unresolved reference at COMPILE
 * time, not a runtime failure. [undeclaredEdgeCallDoesNotCompile] pins the negative direction; the
 * other tests pin the positive one (the generated API compiles AND behaves like the real
 * [RawNavigator] it wraps).
 */
@OptIn(ExperimentalCompilerApi::class, GezginInternalApi::class)
class NavigatorCodegenTest {

    /** Kotlin `data object`s expose their singleton via a static `INSTANCE` field. */
    private fun ClassLoader.dataObjectInstance(binaryName: String): Any =
        loadClass(binaryName).getField("INSTANCE").get(null)

    @Test
    fun `typed round-trip through generated navigators — real RawNavigator, real generated API`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Runner.kt", RUNNER_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val loader = result.classLoader
        val topology = loader.loadClass("dev.gezgin.shop.GezginGeneratedKt")
            .getMethod("getGezginTopology")
            .invoke(null)

        val feedInstance = loader.dataObjectInstance("dev.gezgin.shop.HomeGraph\$Feed") as Route
        val raw = RawNavigator(
            start = feedInstance,
            topology = topology as dev.gezgin.core.GezginTopology,
        )
        val feedEntryId = raw.currentEntryId

        val runnerClass = loader.loadClass("dev.gezgin.shop.RunnerKt")

        // (1) Feed.launchCheckout() → generated FeedNavigator pushes CheckoutFlow's start (Cart):
        // the flow start really got pushed (raw.backStack grows by exactly the new flow entry).
        runnerClass.getMethod("launchCheckout", RawNavigator::class.java).invoke(null, raw)
        assertEquals(2, raw.backStack.value.size, "expected [Feed, Cart] after entering CheckoutFlow")

        // (2) Cart.goToPayment() then Payment.quitWith(OrderId("done")) — both via generated
        // navigators — tears CheckoutFlow down (back to [Feed]) and delivers the result.
        runnerClass.getMethod("finishCheckout", RawNavigator::class.java).invoke(null, raw)
        assertEquals(1, raw.backStack.value.size, "expected CheckoutFlow torn down back to [Feed]")

        val delivered: NavResult<Any?> = runBlocking {
            raw.results<Any?>(feedEntryId, "dev.gezgin.shop.HomeGraph.Feed→dev.gezgin.shop.CheckoutFlow").first()
        }
        assertTrue(delivered is NavResult.Value<*>, "expected a delivered Value, got $delivered")
        val orderId = (delivered as NavResult.Value<*>).value
        val innerValue = orderId!!.javaClass.getDeclaredField("value").apply { isAccessible = true }.get(orderId)
        assertEquals("done", innerValue)
    }

    @Test
    fun `undeclared edge call does not compile`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("BadRunner.kt", UNDECLARED_EDGE_RUNNER_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(result.exitCode != KotlinCompilation.ExitCode.OK, "undeclared edge call must not compile")
        assertTrue(
            result.messages.contains("unresolved reference", ignoreCase = true),
            "expected an unresolved-reference error, got: ${result.messages}",
        )
    }

    @Test
    fun `NoBack source has no back() member — golden text`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val productSource = result.generatedSourceFor("ProductNavigator.kt")
        assertNotNull(productSource, "@NoBack source Product should still get a navigator (has @BackTo)")
        val text = productSource.readText()
        assertFalse("fun back()" in text, text)
        assertTrue("fun backToFeed()" in text, text)
    }

    @Test
    fun `bare route with no edges, back-annotations, or result contract gets no navigator`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        assertNull(
            result.generatedSourceFor("AboutNavigator.kt"),
            "About declares no edges/back-annotations/result-contract — no navigator should be generated",
        )
    }

    @Test
    fun `screen-mode named GoForResult round-trip — launch, backWithResult, results property`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Runner.kt", RUNNER_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val loader = result.classLoader
        val topology = loader.loadClass("dev.gezgin.shop.GezginGeneratedKt")
            .getMethod("getGezginTopology")
            .invoke(null)
        val feedInstance = loader.dataObjectInstance("dev.gezgin.shop.HomeGraph\$Feed") as Route
        val raw = RawNavigator(start = feedInstance, topology = topology as dev.gezgin.core.GezginTopology)

        val delivered = loader.loadClass("dev.gezgin.shop.RunnerKt")
            .getMethod("pickAddressScenario", RawNavigator::class.java)
            .invoke(null, raw)

        assertTrue(delivered is NavResult.Value<*>, "expected a delivered Value, got $delivered")
        val orderId = (delivered as NavResult.Value<*>).value
        val innerValue = orderId!!.javaClass.getDeclaredField("value").apply { isAccessible = true }.get(orderId)
        assertEquals("addr-1", innerValue)
        // backWithResult popped AddressPicker → stack is back to [Feed, Catalog].
        assertEquals(2, raw.backStack.value.size)
    }

    @Test
    fun `named GoForResult substitutes X across the generated triple — golden text`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val catalogSource = result.generatedSourceFor("CatalogNavigator.kt")
        assertNotNull(catalogSource, "Catalog now declares a @GoForResult edge — navigator expected")
        val text = catalogSource.readText()
        assertTrue("fun launchPickAddress(" in text, text)
        assertTrue("val pickAddressResults:" in text, text)
        assertTrue("fun goToPickAddressForResult(" in text, text)
        assertTrue(
            "\"dev.gezgin.shop.HomeGraph.Catalog→dev.gezgin.shop.HomeGraph.AddressPicker#pickAddress\"" in text,
            text,
        )

        // M3 — üretilen tipli backWithResult sahibi entry'yi PİNLER: `raw.backWithResult(entryId, result)`
        // (call-time-top `raw.backWithResult(result)` DEĞİL). AddressPicker = @GoForResult hedefi (ResultRoute).
        val pickerSource = result.generatedSourceFor("AddressPickerNavigator.kt")
        assertNotNull(pickerSource, "AddressPicker is a ResultRoute target — navigator with backWithResult expected")
        val pickerText = pickerSource.readText()
        assertTrue("raw.backWithResult(entryId, result)" in pickerText, pickerText)
        assertFalse("raw.backWithResult(result)" in pickerText, pickerText)
    }

    @Test
    fun `name= override replaces the derived method name — golden text`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val promoSource = result.generatedSourceFor("PromoNavigator.kt")
        assertNotNull(promoSource)
        val text = promoSource.readText()
        assertTrue("fun viaFeed(" in text, text)
        assertFalse("replaceToProduct" in text, text)
        assertTrue("fun replaceToCatalog()" in text, text)
    }
}
