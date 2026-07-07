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
}
