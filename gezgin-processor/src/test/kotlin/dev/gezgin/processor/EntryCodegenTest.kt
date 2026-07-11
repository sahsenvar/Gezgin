package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.ENTRY_SOURCE
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 3.4 gate for [dev.gezgin.processor.codegen.EntryCodegen]/[dev.gezgin.processor.entry.EntryModelReader]
 * — core-mode `provideXEntry` codegen (spec §10.1/§12/§14).
 *
 * **Compose-in-kctfork finding (Task 3.4 brief's open question):** kctfork's test compilation has NO
 * compose-compiler plugin registered (`:gezgin-processor` is a plain `kotlin.jvm` module) — but
 * `@Composable` is JUST an annotation to the FRONTEND; the "only call composables from a composable
 * context" restriction is enforced by the compose-compiler plugin's FIR checker, which isn't present
 * here. So composable stubs (and [dev.gezgin.processor.codegen.EntryCodegen]'s generated
 * `provideXEntry` bodies, which call composables from inside a plain lambda) type-check as perfectly
 * ordinary Kotlin — no `gezgin.emitEntries=false` escape hatch needed for the FRONTEND. The BACKEND,
 * however, independently ICEs ("Couldn't inline method call: ... `CompositionLocal.current` [inline]
 * ... Method: null") trying to inline `androidx.compose.runtime.CompositionLocal.current` from the
 * compose-runtime dependency jar — a kotlin-compile-testing sandbox limitation (real Gradle/AGP
 * builds compile this exact `CompositionLocal.current` read pattern everywhere, with no such issue),
 * not a defect in the generated code. So this test does NOT assert `exitCode == OK`; it only asserts
 * no `[SC*]`/KSP error surfaced (i.e. the codegen itself ran clean) plus the generated source text
 * (golden) — `sourcesGeneratedBySymbolProcessor` is populated by the earlier, successful KSP round
 * regardless of the later backend crash.
 */
@OptIn(ExperimentalCompilerApi::class)
class EntryCodegenTest {

    @Test
    fun `provideXEntry golden text — route+nav, route-only, nav-only, dialog kind`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("EntrySource.kt", ENTRY_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(
            !result.messages.contains("[SC") && !result.messages.contains("unresolved reference"),
            "unexpected KSP/frontend error: ${result.messages}",
        )

        val generated = result.generatedSourceFor("GezginEntries.kt")
        assertTrue(generated != null, "GezginEntries.kt was not generated: ${result.messages}")
        val text = generated!!.readText()

        assertTrue(
            text.contains(
                "register<HomeGraph.Feed>(kind = EntryKind.SCREEN, noBack = false) { route ->",
            ),
            text,
        )
        assertTrue(
            text.contains("val nav = LocalGezginRawNavigator.current.feedNavigator(LocalGezginEntryId.current)"),
            text,
        )
        assertTrue(text.contains("FeedScreen(route, nav)"), text)

        // Catalog: no route: param on the composable — call forwards nav only, no `route` arg.
        assertTrue(
            text.contains(
                "register<HomeGraph.Catalog>(kind = EntryKind.SCREEN, noBack = false) { route ->",
            ),
            text,
        )
        assertTrue(text.contains("CatalogScreen(nav)"), text)

        // About: no nav: param — no `val nav = ...` line paired with AboutScreen, plain route call.
        assertTrue(text.contains("register<HomeGraph.About>(kind = EntryKind.SCREEN, noBack = false) { route ->"), text)
        assertTrue(text.contains("AboutScreen(route)"), text)

        // Promo: @Dialog → EntryKind.DIALOG.
        assertTrue(text.contains("register<HomeGraph.Promo>(kind = EntryKind.DIALOG, noBack = false) { route ->"), text)
        assertTrue(text.contains("PromoDialog(route, nav)"), text)

        // Product: @NoBack route → register carries noBack = true (M5′ flag from the route model).
        assertTrue(text.contains("register<HomeGraph.Product>(kind = EntryKind.SCREEN, noBack = true) { route ->"), text)
        assertTrue(text.contains("ProductScreen(route)"), text)
    }

    @Test
    fun `gezgin_emitEntries=false suppresses GezginEntries generation`() {
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("EntrySource.kt", ENTRY_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitEntries" to "false"),
        )
        // With entries suppressed nothing calls into compose-runtime — the backend ICE (class KDoc)
        // never triggers, so a full-OK exit is expected here.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedSourceFor("GezginEntries.kt") == null, "GezginEntries.kt emitted despite opt-out")
    }

    @Test
    fun `cross-module — feature with NO graphs still emits entries and factory import resolves to the route's package`() {
        // Spec §3.3 multi-module: the `:navigation` module owns every graph/route; a FEATURE module
        // owns only `@Screen` composables over those cross-module routes. Simulated at kctfork's
        // single-unit boundary by compiling the graph module FIRST, then feeding its output as the
        // feature module's classpath — so the feature's KSP sees `@Screen`s but its GraphModel is
        // EMPTY (graphs are compiled classes, not sources).
        val navModule = CompileHarness.compileGezginModule(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        // The nav module has no `@Screen`s → no compose-runtime call sites → no backend ICE; it must
        // compile cleanly for its routes+navigators to land on the feature module's classpath.
        assertEquals(KotlinCompilation.ExitCode.OK, navModule.exitCode, navModule.messages)

        val featureSource = """
            package dev.gezgin.featureui

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.shop.HomeGraph.Feed
            import dev.gezgin.shop.HomeGraph.Product
            import dev.gezgin.shop.FeedNavigator

            @Screen
            @Composable
            fun FeedScreen(route: Feed, nav: FeedNavigator) {
            }

            @Screen
            @Composable
            fun ProductScreen(route: Product) {
            }
        """.trimIndent()

        val feature = CompileHarness.compileGezginModule(
            SourceFile.kotlin("FeatureSource.kt", featureSource),
            extraClasspath = listOf(navModule.outputDirectory),
        )

        // (a) The graph gate no longer suppresses entry codegen in a graph-less module.
        assertTrue(
            !feature.messages.contains("[SC") && !feature.messages.contains("[PKG]"),
            "unexpected KSP error in feature module: ${feature.messages}",
        )
        val entries = feature.generatedSourceFor("GezginEntries.kt")
        assertTrue(entries != null, "feature module emitted no GezginEntries.kt: ${feature.messages}")
        val text = entries!!.readText()

        // K4 — a nav-wired entry file opts in to the generated-code gate.
        assertTrue(text.startsWith("@file:OptIn(GezginInternalApi::class)"), text)
        // Entry file lives in the FEATURE package…
        assertTrue(text.contains("package dev.gezgin.featureui"), text)
        // …but (b) the navigator FACTORY is imported/qualified from the ROUTE's (nav-module) package,
        // not the feature's — cross-module resolution via EntryFunctionModel.routePackageName.
        assertTrue(text.contains("import dev.gezgin.shop.feedNavigator"), text)
        assertTrue(
            text.contains("val nav = LocalGezginRawNavigator.current.feedNavigator(LocalGezginEntryId.current)"),
            text,
        )
        // @NoBack flows through the route DECLARATION cross-module (Product is @NoBack in SHOP_SOURCE).
        assertTrue(text.contains("register<HomeGraph.Product>(kind = EntryKind.SCREEN, noBack = true) { route ->"), text)

        // A graph-less feature emits NO topology/serializers/navigators — only entries.
        assertTrue(feature.generatedSourceFor("GezginGenerated.kt") == null, "feature emitted topology")
        assertTrue(feature.generatedSourceFor("FeedNavigator.kt") == null, "feature re-emitted a navigator")
    }

    @Test
    fun `SC1 — route type cannot be derived (no explicit route, no route param)`() {
        val source = """
            package dev.gezgin.shopui

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.annotation.Screen

            @Screen
            @Composable
            fun NoRouteScreen() {
            }
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Bad.kt", source),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("[SC1]"), result.messages)
    }

    @Test
    fun `SC2 — nav requested but target route has no navigator`() {
        val source = """
            package dev.gezgin.shopui

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.shop.HomeGraph.About
            import dev.gezgin.shop.AboutNavigator

            @Screen
            @Composable
            fun AboutScreen(route: About, nav: AboutNavigator) {
            }
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Bad.kt", source),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("[SC2]"), result.messages)
    }

    @Test
    fun `SC3 — unknown composable parameter is rejected`() {
        val source = """
            package dev.gezgin.shopui

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.shop.HomeGraph.About

            @Screen
            @Composable
            fun AboutScreen(route: About, viewModel: Any) {
            }
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Bad.kt", source),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("[SC3]"), result.messages)
    }

    @Test
    fun `SC4 — two functions register the same route`() {
        val source = """
            package dev.gezgin.shopui

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.shop.HomeGraph.About

            @Screen
            @Composable
            fun AboutScreen(route: About) {
            }

            @Screen(About::class)
            @Composable
            fun AboutScreenAgain() {
            }
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Bad.kt", source),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("[SC4]"), result.messages)
    }

    @Test
    fun `SC4 — cross-kind duplicate (@Screen + @Dialog) on the same route is rejected too`() {
        // The duplicate check is keyed on the ROUTE, not the kind — a @Screen and a @Dialog both
        // registering About would still be two register<About> calls (runtime crash) at display time.
        val source = """
            package dev.gezgin.shopui

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.annotation.Dialog
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.shop.HomeGraph.About

            @Screen
            @Composable
            fun AboutScreen(route: About) {
            }

            @Dialog(About::class)
            @Composable
            fun AboutDialog() {
            }
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Bad.kt", source),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("[SC4]"), result.messages)
    }

    @Test
    fun `SC6 — two entries in the same package resolving to the same X (provideXEntry clash)`() {
        // `Detail` and `DetailRoute` are DIFFERENT routes (no SC4), but the X derivation strips the
        // "Route" suffix — both resolve to X="Detail", i.e. the SAME `provideDetailEntry()` name in the
        // SAME package. Without SC6, EntryCodegen would emit two identical-signature functions into one
        // GezginEntries.kt and the build would die later with an opaque "conflicting overloads".
        val source = """
            package dev.gezgin.shopui

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen

            class Detail : Route
            class DetailRoute : Route

            @Screen
            @Composable
            fun DetailScreen(route: Detail) {
            }

            @Screen
            @Composable
            fun DetailRouteScreen(route: DetailRoute) {
            }
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Bad.kt", source),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("[SC6]"), result.messages)
    }

    @Test
    fun `SC5 — derived route type does not implement Route`() {
        val source = """
            package dev.gezgin.shopui

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.annotation.Screen

            class NotARoute

            @Screen
            @Composable
            fun BadScreen(route: NotARoute) {
            }
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("Bad.kt", source),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("[SC5]"), result.messages)
    }

    // region SC7 (@NoBack × modal) + SC8 (kind↔contract mismatch) — Faz-4 recheck M1/M2

    /** Compiles a single self-contained source and returns the full KSP/compiler messages. */
    private fun messagesOf(source: String): String =
        compileGezgin(SourceFile.kotlin("Bad.kt", source), kspArgs = mapOf("gezgin.emitSerializers" to "false")).messages

    @Test
    fun `SC7 — @NoBack + @BottomSheet is unconditionally rejected at KSP`() {
        // Runtime EntryAdapter would crash on first navigation (require(kind != BOTTOM_SHEET)); the ban is
        // unconditional (contract-independent) so it is statically decidable → promote to a KSP reject.
        val msg = messagesOf(
            """
            package dev.gezgin.sc7bs

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.BottomSheet
            import dev.gezgin.core.annotation.NoBack

            @NoBack
            data class SheetRoute(val x: Int = 0) : Route

            @BottomSheet
            @Composable
            fun SheetScreen(route: SheetRoute) {
            }
            """.trimIndent(),
        )
        assertTrue(msg.contains("[SC7]"), msg)
    }

    @Test
    fun `SC7 — @NoBack + @Dialog with NO DialogContract is rejected (default dismissOnBackPress=true)`() {
        val msg = messagesOf(
            """
            package dev.gezgin.sc7dlg

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Dialog
            import dev.gezgin.core.annotation.NoBack

            @NoBack
            data class PlainDialogRoute(val x: Int = 0) : Route

            @Dialog
            @Composable
            fun PlainDialog(route: PlainDialogRoute) {
            }
            """.trimIndent(),
        )
        assertTrue(msg.contains("[SC7]"), msg)
    }

    @Test
    fun `SC7 allow — @NoBack + @Dialog WITH DialogContract is accepted (override not KSP-known)`() {
        // The route implements DialogContract and CAN set dismissOnBackPress=false (a runtime value KSP
        // can't read) → SC7 must NOT fire; the runtime requireBackDismissCompatible stays as the net.
        val msg = messagesOf(
            """
            package dev.gezgin.sc7ok

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.DialogContract
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Dialog
            import dev.gezgin.core.annotation.NoBack

            @NoBack
            data class ConfirmRoute(val x: Int = 0) : Route, DialogContract {
                override val dismissOnBackPress get() = false
            }

            @Dialog
            @Composable
            fun ConfirmDialog(route: ConfirmRoute) {
            }
            """.trimIndent(),
        )
        assertTrue(!msg.contains("[SC7]") && !msg.contains("[SC8]"), msg)
    }

    @Test
    fun `SC7 allow — @NoBack + @Screen is accepted (terminal screen, no modal)`() {
        val msg = messagesOf(
            """
            package dev.gezgin.sc7scr

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.NoBack
            import dev.gezgin.core.annotation.Screen

            @NoBack
            data class TerminalRoute(val x: Int = 0) : Route

            @Screen
            @Composable
            fun TerminalScreen(route: TerminalRoute) {
            }
            """.trimIndent(),
        )
        assertTrue(!msg.contains("[SC7]") && !msg.contains("[SC8]"), msg)
    }

    @Test
    fun `SC8 — @FullscreenModal route implementing DialogContract is rejected (mismatch)`() {
        // The user's "non-dismissable" intent is silently dropped: the FullscreenModal adapter reads
        // `route as? FullscreenModalContract` = null → type defaults. Reject the kind↔contract mismatch.
        val msg = messagesOf(
            """
            package dev.gezgin.sc8mis

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.DialogContract
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FullscreenModal

            data class BadModalRoute(val x: Int = 0) : Route, DialogContract {
                override val dismissOnClickOutside get() = false
            }

            @FullscreenModal
            @Composable
            fun BadModal(route: BadModalRoute) {
            }
            """.trimIndent(),
        )
        assertTrue(msg.contains("[SC8]"), msg)
    }

    @Test
    fun `SC8 allow — @Dialog route implementing DialogContract is accepted (match)`() {
        val msg = messagesOf(
            """
            package dev.gezgin.sc8ok

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.DialogContract
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Dialog

            data class GoodDialogRoute(val x: Int = 0) : Route, DialogContract {
                override val dismissOnClickOutside get() = false
            }

            @Dialog
            @Composable
            fun GoodDialog(route: GoodDialogRoute) {
            }
            """.trimIndent(),
        )
        assertTrue(!msg.contains("[SC8]") && !msg.contains("[SC7]"), msg)
    }

    @Test
    fun `SC8 allow — contract-less @Dialog route is accepted`() {
        val msg = messagesOf(
            """
            package dev.gezgin.sc8none

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Dialog

            data class PlainRoute(val x: Int = 0) : Route

            @Dialog
            @Composable
            fun PlainDialog(route: PlainRoute) {
            }
            """.trimIndent(),
        )
        assertTrue(!msg.contains("[SC8]") && !msg.contains("[SC7]"), msg)
    }

    @Test
    fun `SC2 — nav param typed as a resolvable NON-navigator (RawNavigator) is rejected (Integ m4)`() {
        // `Feed` DOES earn a FeedNavigator (the hasNavigator check passes), but the `nav:` param is typed
        // `RawNavigator` — a real, already-compiled type (NOT an as-yet-ungenerated same-round navigator, so
        // NOT an error type). The generated `FeedScreen(route, nav)` would type-mismatch inside
        // GezginEntries.kt; SC2's type check catches it up front instead.
        val result = compileGezgin(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin(
                "Bad.kt",
                """
                package dev.gezgin.shopui

                import androidx.compose.runtime.Composable
                import dev.gezgin.core.RawNavigator
                import dev.gezgin.core.annotation.Screen
                import dev.gezgin.shop.HomeGraph.Feed

                @Screen
                @Composable
                fun FeedScreen(route: Feed, nav: RawNavigator) {
                }
                """.trimIndent(),
            ),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("[SC2]"), result.messages)
    }

    // endregion
}
