package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 2.2 gate: compiles [SHOP_SOURCE] with `gezgin.dumpModel=true` and asserts the exact,
 * line-by-line [dev.gezgin.processor.model.GraphModel] dump produced by [ModelReader]. Covers
 * nesting-based membership, outer-to-inner flow chains that skip intervening `@NavGraph`s, the
 * `Self::class` sentinel on `@ReplaceTo.clearUpTo` (indirectly, via the absence case here) and
 * `ResultFlow<T>`/`ResultRoute<T>` type-argument resolution.
 */
@OptIn(ExperimentalCompilerApi::class)
class ModelReaderTest {

    private fun dumpShopModel(): List<String> {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.dumpModel" to "true"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val dumpFile = findGeneratedResource("GezginModelDump.txt")
        assertNotNull(dumpFile, "expected GezginModelDump.txt to be generated; compiler messages:\n${result.messages}")
        return dumpFile.readText().lines()
    }

    @Test
    fun `graphs are dumped with flow, result-type and membership facts`() {
        val lines = dumpShopModel()

        assertEquals(
            listOf(
                "graph dev.gezgin.shop.CheckoutFlow flow=true resultFlow=true " +
                    "resultType=dev.gezgin.shop.OrderId start=dev.gezgin.shop.CheckoutFlow.Cart " +
                    "parentFlow=- members=dev.gezgin.shop.CheckoutFlow.Cart," +
                    "dev.gezgin.shop.CheckoutFlow.PayAuthFlow,dev.gezgin.shop.CheckoutFlow.Payment",
                "graph dev.gezgin.shop.CheckoutFlow.PayAuthFlow flow=true resultFlow=false " +
                    "resultType=- start=dev.gezgin.shop.CheckoutFlow.PayAuthFlow.Otp " +
                    "parentFlow=dev.gezgin.shop.CheckoutFlow " +
                    "members=dev.gezgin.shop.CheckoutFlow.PayAuthFlow.Otp",
                "graph dev.gezgin.shop.HomeGraph flow=false resultFlow=false resultType=- start=- " +
                    "parentFlow=- members=dev.gezgin.shop.HomeGraph.Catalog," +
                    "dev.gezgin.shop.HomeGraph.Feed,dev.gezgin.shop.HomeGraph.Product",
            ),
            lines.filter { it.startsWith("graph ") },
        )
    }

    @Test
    fun `routes are dumped with chain, start, noBack and ctor param facts`() {
        val lines = dumpShopModel()

        assertEquals(
            listOf(
                "route dev.gezgin.shop.CheckoutFlow.Cart graph=dev.gezgin.shop.CheckoutFlow " +
                    "chain=dev.gezgin.shop.CheckoutFlow start=true noBack=false resultType=- params=-",
                "route dev.gezgin.shop.CheckoutFlow.PayAuthFlow.Otp " +
                    "graph=dev.gezgin.shop.CheckoutFlow.PayAuthFlow " +
                    "chain=dev.gezgin.shop.CheckoutFlow,dev.gezgin.shop.CheckoutFlow.PayAuthFlow " +
                    "start=true noBack=false resultType=- params=-",
                "route dev.gezgin.shop.CheckoutFlow.Payment graph=dev.gezgin.shop.CheckoutFlow " +
                    "chain=dev.gezgin.shop.CheckoutFlow start=false noBack=false resultType=- params=-",
                "route dev.gezgin.shop.HomeGraph.Catalog graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=false resultType=- params=-",
                "route dev.gezgin.shop.HomeGraph.Feed graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=false resultType=- params=-",
                "route dev.gezgin.shop.HomeGraph.Product graph=dev.gezgin.shop.HomeGraph " +
                    "chain=- start=false noBack=true resultType=- params=id:kotlin.String",
            ),
            lines.filter { it.startsWith("route ") },
        )
    }

    @Test
    fun `forward edges are dumped with kind, target and modifier facts`() {
        val lines = dumpShopModel()

        assertEquals(
            listOf(
                "edge dev.gezgin.shop.CheckoutFlow.Cart GO_TO dev.gezgin.shop.CheckoutFlow.Payment " +
                    "singleTop=true clearUpTo=- inclusive=false name=-",
                "edge dev.gezgin.shop.HomeGraph.Feed GO_TO dev.gezgin.shop.HomeGraph.Product " +
                    "singleTop=true clearUpTo=- inclusive=false name=-",
                "edge dev.gezgin.shop.HomeGraph.Feed GO_FOR_RESULT dev.gezgin.shop.CheckoutFlow " +
                    "singleTop=false clearUpTo=- inclusive=false name=-",
            ),
            lines.filter { it.startsWith("edge ") },
        )
    }

    @Test
    fun `back edges are dumped with kind and target facts`() {
        val lines = dumpShopModel()

        assertEquals(
            listOf(
                "backedge dev.gezgin.shop.CheckoutFlow.Payment QUIT target=- inclusive=false",
                "backedge dev.gezgin.shop.CheckoutFlow.Payment BACK_TO_START target=- inclusive=false",
                "backedge dev.gezgin.shop.HomeGraph.Product BACK_TO " +
                    "target=dev.gezgin.shop.HomeGraph.Feed inclusive=false",
            ),
            lines.filter { it.startsWith("backedge ") },
        )
    }
}
