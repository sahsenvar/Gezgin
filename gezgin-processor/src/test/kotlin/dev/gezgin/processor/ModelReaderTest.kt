package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 2.2 gate: compiles [SHOP_SOURCE] with `gezgin.dumpModel=true` and asserts the exact,
 * line-by-line [dev.gezgin.processor.model.GraphModel] dump produced by [ModelReader]. Covers
 * nesting-based membership, outer-to-inner flow chains that skip intervening `@NavGraph`s,
 * `@Repeatable` flattening, the `Self::class` sentinel on `@ReplaceTo.clearUpTo`, result types
 * resolved through intermediate base classes, and nullable/defaulted ctor params.
 *
 * The four `* section is dumped *` tests pin every line of their section exactly; the focused
 * `reading rule` tests re-assert the individual lines that motivated each rule so a regression
 * names the broken rule directly.
 */
@OptIn(ExperimentalCompilerApi::class)
class ModelReaderTest {

    private companion object {
        /** Dump lines for [SHOP_SOURCE]; compiled once and shared across tests. */
        val shopDump: List<String> by lazy {
            val result = compileGezgin(
                SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
                kspArgs = mapOf("gezgin.dumpModel" to "true"),
            )
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

            val dumpFile = findGeneratedResource("GezginModelDump.txt")
            assertNotNull(
                dumpFile,
                "expected GezginModelDump.txt to be generated; compiler messages:\n${result.messages}",
            )
            dumpFile.readText().lines()
        }
    }

    // region Exact section pins

    @Test
    fun `graph section is dumped with flow, result-type and membership facts`() {
        assertEquals(
            listOf(
                "graph dev.gezgin.shop.CheckoutFlow flow=true resultFlow=true " +
                    "resultType=dev.gezgin.shop.OrderId start=dev.gezgin.shop.CheckoutFlow.Cart " +
                    "parentFlow=- members=dev.gezgin.shop.CheckoutFlow.Cart," +
                    "dev.gezgin.shop.CheckoutFlow.CheckoutPages," +
                    "dev.gezgin.shop.CheckoutFlow.PayAuthFlow,dev.gezgin.shop.CheckoutFlow.Payment",
                "graph dev.gezgin.shop.CheckoutFlow.CheckoutPages flow=false resultFlow=false " +
                    "resultType=- start=- parentFlow=dev.gezgin.shop.CheckoutFlow " +
                    "members=dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow",
                "graph dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow flow=true resultFlow=false " +
                    "resultType=- start=dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow.GiftPick " +
                    "parentFlow=dev.gezgin.shop.CheckoutFlow " +
                    "members=dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow.GiftPick",
                "graph dev.gezgin.shop.CheckoutFlow.PayAuthFlow flow=true resultFlow=false " +
                    "resultType=- start=dev.gezgin.shop.CheckoutFlow.PayAuthFlow.Otp " +
                    "parentFlow=dev.gezgin.shop.CheckoutFlow " +
                    "members=dev.gezgin.shop.CheckoutFlow.PayAuthFlow.Otp",
                "graph dev.gezgin.shop.HomeGraph flow=false resultFlow=false resultType=- start=- " +
                    "parentFlow=- members=dev.gezgin.shop.HomeGraph.AddressPicker," +
                    "dev.gezgin.shop.HomeGraph.Catalog,dev.gezgin.shop.HomeGraph.Deals," +
                    "dev.gezgin.shop.HomeGraph.Feed,dev.gezgin.shop.HomeGraph.Product," +
                    "dev.gezgin.shop.HomeGraph.Promo",
            ),
            shopDump.filter { it.startsWith("graph ") },
        )
    }

    @Test
    fun `route section is dumped with chain, start, noBack and ctor param facts`() {
        assertEquals(
            listOf(
                "route dev.gezgin.shop.CheckoutFlow.Cart graph=dev.gezgin.shop.CheckoutFlow " +
                    "chain=dev.gezgin.shop.CheckoutFlow start=true noBack=false resultType=- params=-",
                "route dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow.GiftPick " +
                    "graph=dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow " +
                    "chain=dev.gezgin.shop.CheckoutFlow,dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow " +
                    "start=true noBack=false resultType=- params=-",
                "route dev.gezgin.shop.CheckoutFlow.PayAuthFlow.Otp " +
                    "graph=dev.gezgin.shop.CheckoutFlow.PayAuthFlow " +
                    "chain=dev.gezgin.shop.CheckoutFlow,dev.gezgin.shop.CheckoutFlow.PayAuthFlow " +
                    "start=true noBack=false resultType=- params=-",
                "route dev.gezgin.shop.CheckoutFlow.Payment graph=dev.gezgin.shop.CheckoutFlow " +
                    "chain=dev.gezgin.shop.CheckoutFlow start=false noBack=false resultType=- params=-",
                "route dev.gezgin.shop.HomeGraph.AddressPicker graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=false resultType=dev.gezgin.shop.OrderId " +
                    "params=hint:kotlin.String?=",
                "route dev.gezgin.shop.HomeGraph.Catalog graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=false resultType=- params=-",
                "route dev.gezgin.shop.HomeGraph.Deals graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=false resultType=- params=-",
                "route dev.gezgin.shop.HomeGraph.Feed graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=false resultType=- params=-",
                "route dev.gezgin.shop.HomeGraph.Product graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=true resultType=- params=id:kotlin.String",
                "route dev.gezgin.shop.HomeGraph.Promo graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=false resultType=- params=-",
            ),
            shopDump.filter { it.startsWith("route ") },
        )
    }

    @Test
    fun `edge section is dumped with kind, target and modifier facts`() {
        assertEquals(
            listOf(
                "edge dev.gezgin.shop.CheckoutFlow.Cart GO_TO dev.gezgin.shop.CheckoutFlow.Payment " +
                    "singleTop=true clearUpTo=- inclusive=false name=-",
                "edge dev.gezgin.shop.HomeGraph.Deals GO_TO dev.gezgin.shop.HomeGraph.Catalog " +
                    "singleTop=true clearUpTo=- inclusive=false name=-",
                "edge dev.gezgin.shop.HomeGraph.Deals GO_TO dev.gezgin.shop.HomeGraph.Product " +
                    "singleTop=true clearUpTo=- inclusive=false name=-",
                "edge dev.gezgin.shop.HomeGraph.Feed GO_TO dev.gezgin.shop.HomeGraph.Product " +
                    "singleTop=true clearUpTo=- inclusive=false name=-",
                "edge dev.gezgin.shop.HomeGraph.Feed GO_FOR_RESULT dev.gezgin.shop.CheckoutFlow " +
                    "singleTop=false clearUpTo=- inclusive=false name=-",
                "edge dev.gezgin.shop.HomeGraph.Promo REPLACE_TO dev.gezgin.shop.HomeGraph.Catalog " +
                    "singleTop=false clearUpTo=- inclusive=true name=-",
                "edge dev.gezgin.shop.HomeGraph.Promo REPLACE_TO dev.gezgin.shop.HomeGraph.Product " +
                    "singleTop=false clearUpTo=dev.gezgin.shop.HomeGraph.Feed inclusive=false name=viaFeed",
            ),
            shopDump.filter { it.startsWith("edge ") },
        )
    }

    @Test
    fun `backedge section is dumped with kind and target facts`() {
        assertEquals(
            listOf(
                "backedge dev.gezgin.shop.CheckoutFlow.Payment QUIT target=- inclusive=false",
                "backedge dev.gezgin.shop.CheckoutFlow.Payment BACK_TO_START target=- inclusive=false",
                "backedge dev.gezgin.shop.HomeGraph.Product BACK_TO " +
                    "target=dev.gezgin.shop.HomeGraph.Feed inclusive=false",
            ),
            shopDump.filter { it.startsWith("backedge ") },
        )
    }

    // endregion

    // region Focused reading-rule pins

    @Test
    fun `reading rule - repeatable annotations flatten to one edge each`() {
        assertEquals(
            listOf(
                "edge dev.gezgin.shop.HomeGraph.Deals GO_TO dev.gezgin.shop.HomeGraph.Catalog " +
                    "singleTop=true clearUpTo=- inclusive=false name=-",
                "edge dev.gezgin.shop.HomeGraph.Deals GO_TO dev.gezgin.shop.HomeGraph.Product " +
                    "singleTop=true clearUpTo=- inclusive=false name=-",
            ),
            shopDump.filter { it.startsWith("edge dev.gezgin.shop.HomeGraph.Deals ") },
            "both repeated @GoTo's must survive as distinct edges (KSP container synthesis would drop them)",
        )
    }

    @Test
    fun `reading rule - ReplaceTo Self sentinel maps to null and explicit args are read`() {
        assertEquals(
            listOf(
                // Default clearUpTo=Self::class → sentinel → dumped as '-'; default inclusive=true.
                "edge dev.gezgin.shop.HomeGraph.Promo REPLACE_TO dev.gezgin.shop.HomeGraph.Catalog " +
                    "singleTop=false clearUpTo=- inclusive=true name=-",
                // Explicit clearUpTo/inclusive/name must all be read from the annotation call.
                "edge dev.gezgin.shop.HomeGraph.Promo REPLACE_TO dev.gezgin.shop.HomeGraph.Product " +
                    "singleTop=false clearUpTo=dev.gezgin.shop.HomeGraph.Feed inclusive=false name=viaFeed",
            ),
            shopDump.filter { it.startsWith("edge dev.gezgin.shop.HomeGraph.Promo ") },
        )
    }

    @Test
    fun `reading rule - ResultRoute type arg resolves through an intermediate base class`() {
        assertContains(
            shopDump,
            "route dev.gezgin.shop.HomeGraph.AddressPicker graph=dev.gezgin.shop.HomeGraph " +
                "chain=- start=false noBack=false resultType=dev.gezgin.shop.OrderId " +
                "params=hint:kotlin.String?=",
            "AddressPicker implements ResultRoute<OrderId> only via BasePicker; its resultType " +
                "must still resolve to OrderId and its nullable, defaulted ctor param must dump " +
                "as hint:kotlin.String?=",
        )
    }

    @Test
    fun `reading rule - flow chain skips an intervening NavGraph`() {
        assertContains(
            shopDump,
            "route dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow.GiftPick " +
                "graph=dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow " +
                "chain=dev.gezgin.shop.CheckoutFlow,dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow " +
                "start=true noBack=false resultType=- params=-",
            "GiftPick's graph must be its immediate enclosing GiftFlow while its flow chain " +
                "skips the intervening @NavGraph CheckoutPages",
        )
        assertContains(
            shopDump,
            "graph dev.gezgin.shop.CheckoutFlow.CheckoutPages flow=false resultFlow=false " +
                "resultType=- start=- parentFlow=dev.gezgin.shop.CheckoutFlow " +
                "members=dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow",
            "CheckoutPages' parentFlow must be the enclosing CheckoutFlow",
        )
    }

    // endregion
}
