package dev.gezgin.processor

import com.squareup.kotlinpoet.ClassName
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.core.FlowType
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.Route
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 2.4 gate for [dev.gezgin.processor.codegen.TopologyCodegen]: compiles [SHOP_SOURCE], loads
 * the generated `dev.gezgin.shop.GezginGeneratedKt` facade via reflection off the compilation's own
 * [ClassLoader], and asserts against the real [GezginTopology] runtime API (`flowChain`/`startOf`/
 * `edges`) — `inheritClassPath` puts `:gezgin-core`'s classes on the same classloader lineage as the
 * generated code, so the loaded `GezginTopology` instance casts directly to the compile-time type
 * without any field-by-field reflection fallback being necessary (verified below: the cast succeeds).
 *
 * `emitSerializers=false` is passed everywhere except [golden text for GezginSerializers contains
 * expected shape], which never live-loads the result — it only inspects the generated *source text*
 * (`gezgin.emitSerializers` gates `GezginSerializers.kt` specifically because `subclass(X::class)`
 * would need a real `.serializer()`-bearing `X` to ever be *used* at runtime by a consumer, which
 * the kctfork test fixture can't fully provide).
 */
@OptIn(ExperimentalCompilerApi::class)
class TopologyCodegenTest {

    /** JVM binary name for a KSP-style dotted fqName (`Outer.Inner` nesting → `Outer$Inner`). */
    private fun binaryNameOf(fqName: String): String {
        val className = ClassName.bestGuess(fqName)
        val prefix = className.packageName.takeIf { it.isNotEmpty() }?.plus(".").orEmpty()
        return prefix + className.simpleNames.joinToString("$")
    }

    private fun ClassLoader.routeClassOf(fqName: String): Class<*> = loadClass(binaryNameOf(fqName))

    @Suppress("UNCHECKED_CAST")
    private fun ClassLoader.kClassOf(fqName: String): KClass<out Route> =
        routeClassOf(fqName).kotlin as KClass<out Route>

    @Test
    fun `SHOP_SOURCE compiles and GezginTopology loads via reflection off the compiled classloader`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val loader = result.classLoader
        val generatedFacade = loader.loadClass("dev.gezgin.shop.GezginGeneratedKt")
        val raw = generatedFacade.getMethod("getGezginTopology").invoke(null)

        // Binding decision (see class KDoc): this cast is expected to succeed because
        // inheritClassPath shares :gezgin-core's GezginTopology class with the compiled result.
        val topology = raw as GezginTopology

        val cart = loader.kClassOf("dev.gezgin.shop.CheckoutFlow.Cart")
        val otp = loader.kClassOf("dev.gezgin.shop.CheckoutFlow.PayAuthFlow.Otp")
        val giftPick = loader.kClassOf("dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow.GiftPick")

        assertEquals(
            listOf(FlowType("dev.gezgin.shop.CheckoutFlow", isResultFlow = true)),
            topology.flowChain(cart),
        )
        assertEquals(
            listOf(
                FlowType("dev.gezgin.shop.CheckoutFlow", isResultFlow = true),
                FlowType("dev.gezgin.shop.CheckoutFlow.PayAuthFlow", isResultFlow = false),
            ),
            topology.flowChain(otp),
        )
        assertEquals(
            listOf(
                FlowType("dev.gezgin.shop.CheckoutFlow", isResultFlow = true),
                FlowType("dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow", isResultFlow = false),
            ),
            topology.flowChain(giftPick),
        )

        assertEquals(cart.java, topology.startOf("dev.gezgin.shop.CheckoutFlow").java)
        assertEquals(otp.java, topology.startOf("dev.gezgin.shop.CheckoutFlow.PayAuthFlow").java)
        assertEquals(giftPick.java, topology.startOf("dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow").java)

        val feedToCheckoutFlow = topology.edges["Feed→CheckoutFlow"]
        assertNotNull(feedToCheckoutFlow, "expected a Feed→CheckoutFlow edge (Feed's @GoForResult(CheckoutFlow::class))")
        assertEquals("Feed→CheckoutFlow", feedToCheckoutFlow.id)
        // Presence only — never invoke the (test-stub) serializer.
        assertNotNull(feedToCheckoutFlow.resultSerializer)
    }

    @Test
    fun `golden text for GezginSerializers contains expected shape`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "true"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val serializersSource = result.generatedSourceFor("GezginSerializers.kt")
        assertNotNull(serializersSource, "expected a generated GezginSerializers.kt")
        val text = serializersSource.readText()

        assertTrue("polymorphic(Route::class)" in text, text)
        // KotlinPoet only auto-imports top-level types, so a nested route like `Feed` (nested in
        // `HomeGraph`) is emitted qualified by its enclosing simple name(s) — `HomeGraph.Feed`, not
        // a bare `Feed` — which is also the only form that would actually resolve/compile here.
        assertTrue("subclass(HomeGraph.Feed::class)" in text, text)
        assertFalse("BasePicker" in text, text)
    }
}
