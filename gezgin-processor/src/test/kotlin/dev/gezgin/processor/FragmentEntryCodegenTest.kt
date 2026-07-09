package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.FRAGMENT_NAV_SPLIT_SOURCE
import dev.gezgin.processor.fixtures.FRAGMENT_ROUTES
import dev.gezgin.processor.fixtures.FRAGMENT_SOURCE
import dev.gezgin.processor.fixtures.FRAGMENT_STUB
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 6.2 gate for [dev.gezgin.processor.codegen.FragmentEntryCodegen] — the `AndroidFragment`-hosting
 * `provideXEntry` codegen for `@FragmentScreen` (spec §11.1, screen-only §11.2).
 *
 * **kctfork caveat (same as [EntryCodegenTest]/[MviEntryCodegenTest]):** the emitted body calls
 * `androidx.fragment.compose.AndroidFragment` (an inline `@Composable`, not on the classpath here) and the
 * gezgin-core `androidMain` `toBundle`/`bindGezgin` glue (androidMain, also not on the jvm test classpath),
 * plus the compose-runtime inline accessors the plugin-less kctfork BACKEND can't inline — so this test does
 * NOT assert `exitCode == OK`. It asserts no `[FS*]`/`[SC*]` KSP error (codegen ran clean) plus the generated
 * `GezginFragmentEntries.kt` golden TEXT, which the successful KSP round populates regardless of the later
 * compile failure (`sourcesGeneratedBySymbolProcessor` reads the KSP output dir). Reuses the Task-6.1
 * fixtures — routes in a DIFFERENT package than the Fragments, so cross-module factory qualification shows.
 */
@OptIn(ExperimentalCompilerApi::class)
class FragmentEntryCodegenTest {

    private fun generateFragmentEntries(): String {
        val result = compileGezgin(
            SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB),
            SourceFile.kotlin("FragmentRoutes.kt", FRAGMENT_ROUTES),
            SourceFile.kotlin("FragmentSource.kt", FRAGMENT_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(
            !result.messages.contains("[FS") && !result.messages.contains("[SC"),
            "unexpected KSP [FS|SC] error: ${result.messages}",
        )
        val gen = result.generatedSourceFor("GezginFragmentEntries.kt")
        assertNotNull(gen, "GezginFragmentEntries.kt not generated: ${result.messages}")
        return gen.readText()
    }

    @Test
    fun `provideXEntry golden text — AndroidFragment host, toBundle args, onUpdate bind, cross-module-optimistic nav`() {
        val text = generateFragmentEntries()

        // File lives in the FRAGMENT's own package (like core-mode's composable-package grouping).
        assertTrue(text.startsWith("package dev.gezgin.fragui"), text)

        // FQ-imported symbols: AndroidFragment (androidx.fragment, no processor dep), the gezgin-core
        // runtime glue, and the navigator FACTORY qualified against the ROUTE's package (cross-module-safe).
        assertContains(text, "import androidx.fragment.compose.AndroidFragment")
        assertContains(text, "import dev.gezgin.core.fragment.toBundle")
        assertContains(text, "import dev.gezgin.core.fragment.bindGezgin")
        assertContains(text, "import dev.gezgin.fragroutes.orderChainNavigator")

        // OrderChain: these routes are NOT in this module's model (no graphs here) → the nav-wiring guard's
        // `?: true` cross-module-optimistic fallback assumes a navigator → nav wiring emitted (SC2/MV7 parity).
        assertContains(text, "public fun GezginEntryScope.provideOrderChainEntry()")
        assertContains(text, "register<OrderChainRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->")
        assertContains(text, "val raw = LocalGezginRawNavigator.current")
        assertContains(text, "val nav = raw.orderChainNavigator(LocalGezginEntryId.current)")
        assertContains(text, "AndroidFragment<OrderChainFragment>(")
        assertContains(text, "arguments = route.toBundle(raw),")
        assertContains(text, "onUpdate = { fragment -> bindGezgin(fragment, route, nav) },")

        // Archived: @NoBack route → register carries noBack = true (read off the route decl, cross-module).
        assertContains(text, "public fun GezginEntryScope.provideArchivedEntry()")
        assertContains(text, "register<ArchivedRoute>(kind = EntryKind.SCREEN, noBack = true) { route ->")
        assertContains(text, "val nav = raw.archivedNavigator(LocalGezginEntryId.current)")
        assertContains(text, "AndroidFragment<ArchivedFragment>(")
    }

    /**
     * Fix-round (FS5 / SC2-MV7 parity): nav wiring is CONDITIONAL on the route earning a navigator. Compiled
     * with [SHOP_SOURCE] so `Feed` (has edges → `FeedNavigator`) and `About` (bare `@NavGraph` member → no
     * navigator) are both IN the model with a KNOWN navigator status — the two branches of the guard.
     */
    @Test
    fun `nav wiring is conditional — edge-less route emits no nav, route with navigator keeps it`() {
        val result = compileGezgin(
            SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB),
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("FragmentNavSplitSource.kt", FRAGMENT_NAV_SPLIT_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(
            !result.messages.contains("[FS") && !result.messages.contains("[SC"),
            "unexpected KSP [FS|SC] error: ${result.messages}",
        )
        val gen = result.generatedSourceFor("GezginFragmentEntries.kt")
        assertNotNull(gen, "GezginFragmentEntries.kt not generated: ${result.messages}")
        val text = gen.readText()

        // (b) WITH navigator — Feed earns a FeedNavigator (has @GoTo/@GoForResult edges): nav wiring emitted
        // EXACTLY as before (regression pin — the fix must not silently change the working case).
        assertContains(text, "public fun GezginEntryScope.provideFeedEntry()")
        assertContains(text, "import dev.gezgin.shop.feedNavigator")
        assertContains(text, "val nav = raw.feedNavigator(LocalGezginEntryId.current)")
        assertContains(text, "onUpdate = { fragment -> bindGezgin(fragment, route, nav) },")

        // (a) NO navigator — About is a bare @NavGraph member: nav wiring SUPPRESSED. No `val nav`, no
        // `aboutNavigator` factory reference/import anywhere; binds via the 2-arg no-nav bindGezgin overload.
        assertContains(text, "public fun GezginEntryScope.provideAboutEntry()")
        assertContains(text, "register<HomeGraph.About>(kind = EntryKind.SCREEN, noBack = false) { route ->")
        assertContains(text, "AndroidFragment<AboutFragment>(")
        assertContains(text, "onUpdate = { fragment -> bindGezgin(fragment, route) },")
        assertFalse(
            text.contains("aboutNavigator"),
            "edge-less About must NOT wire a navigator (no `val nav`, no aboutNavigator factory): $text",
        )
        // The register body still emits sensibly for About: `raw` is still bound (route.toBundle needs it).
        assertContains(text, "arguments = route.toBundle(raw),")
    }

    @Test
    fun `gezgin_emitEntries=false suppresses GezginFragmentEntries generation`() {
        val result = compileGezgin(
            SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB),
            SourceFile.kotlin("FragmentRoutes.kt", FRAGMENT_ROUTES),
            SourceFile.kotlin("FragmentSource.kt", FRAGMENT_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitEntries" to "false"),
        )
        assertTrue(
            result.generatedSourceFor("GezginFragmentEntries.kt") == null,
            "GezginFragmentEntries.kt emitted despite opt-out",
        )
    }
}
