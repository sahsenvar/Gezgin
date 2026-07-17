package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.FRAGMENT_DISPLAY_ONLY_SOURCE
import dev.gezgin.processor.fixtures.FRAGMENT_NAV_SPLIT_SOURCE
import dev.gezgin.processor.fixtures.FRAGMENT_ROUTE_NAVIGATOR_STUBS
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
            // Stand-in for the routes' cross-module (already-compiled) navigators, so the nav-wiring probe
            // resolves `OrderChainNavigator`/`ArchivedNavigator` on the classpath → nav wiring emitted.
            SourceFile.kotlin("FragmentRouteNavigatorStubs.kt", FRAGMENT_ROUTE_NAVIGATOR_STUBS),
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
    fun `provideXEntry golden text — AndroidFragment host, toBundle args, onUpdate bind, cross-module probe nav`() {
        val text = generateFragmentEntries()

        // K4 — the fragment entry file opts in to the generated-code gate (route.toBundle/bindGezgin are gated).
        assertTrue(text.startsWith("@file:OptIn(GezginInternalApi::class)"), text)
        // File lives in the FRAGMENT's own package (like core-mode's composable-package grouping).
        assertTrue(text.contains("package dev.gezgin.fragui"), text)

        // FQ-imported symbols: AndroidFragment (androidx.fragment, no processor dep), the gezgin-core
        // runtime glue, and the navigator FACTORY qualified against the ROUTE's package (cross-module-safe).
        assertContains(text, "import androidx.fragment.compose.AndroidFragment")
        // mN1 — the route→Bundle encode is wrapped in `remember(route) { … }` (compose-runtime import).
        assertContains(text, "import androidx.compose.runtime.remember")
        assertContains(text, "import dev.gezgin.core.fragment.toBundle")
        assertContains(text, "import dev.gezgin.core.fragment.bindGezgin")
        assertContains(text, "import dev.gezgin.fragroutes.orderChainNavigator")

        // OrderChain: these routes are NOT in this module's model (no graphs here) → the cross-module branch
        // PROBES the classpath, finds the `OrderChainNavigator` stub (models an already-compiled cross-module
        // navigator) → nav wiring emitted (SC2/MV7 parity, now deterministic instead of `?: true` optimism).
        assertContains(text, "public fun GezginEntryScope.provideOrderChainEntry()")
        assertContains(text, "register<OrderChainRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->")
        assertContains(text, "val raw = LocalGezginRawNavigator.current")
        assertContains(text, "val nav = raw.orderChainNavigator(LocalGezginEntryId.current)")
        assertContains(text, "AndroidFragment<OrderChainFragment>(")
        assertContains(text, "arguments = remember(route) { route.toBundle(raw) },")
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

        // (a) NO navigator — LockedRoute is an explicit bare @NoBack route: nav wiring SUPPRESSED.
        assertContains(text, "public fun GezginEntryScope.provideLockedEntry()")
        assertContains(text, "register<LockedRoute>(kind = EntryKind.SCREEN, noBack = true) { route ->")
        assertContains(text, "AndroidFragment<LockedFragment>(")
        assertContains(text, "onUpdate = { fragment -> bindGezgin(fragment, route) },")
        assertFalse(
            text.contains("lockedNavigator"),
            "bare @NoBack route must NOT wire a navigator: $text",
        )
        // The register body still emits sensibly: `raw` is bound because route.toBundle needs it.
        assertContains(text, "arguments = remember(route) { route.toBundle(raw) },")
    }

    /**
     * Fix-round (Important 3 — cross-module display-only gap FS5's own tests never caught). A display-only
     * `@FragmentScreen` whose route is cross-module (graph-less here) AND has NO compiled navigator must emit
     * NO nav wiring — the cross-module classpath probe returns false, so the FS5 no-nav path applies. The OLD
     * `?: true` optimism nav-wired this leaf → a call to a nonexistent `settingsNavigator()` factory
     * (unresolved reference), the exact FS5 bug relocated to the cross-module case.
     */
    @Test
    fun `cross-module display-only Fragment — no compiled navigator emits no nav wiring (probe fix)`() {
        val result = compileGezgin(
            SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB),
            SourceFile.kotlin("FragmentDisplayOnlySource.kt", FRAGMENT_DISPLAY_ONLY_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(
            !result.messages.contains("[FS") && !result.messages.contains("[SC"),
            "unexpected KSP [FS|SC] error: ${result.messages}",
        )
        val gen = result.generatedSourceFor("GezginFragmentEntries.kt")
        assertNotNull(gen, "GezginFragmentEntries.kt not generated: ${result.messages}")
        val text = gen.readText()

        assertContains(text, "public fun GezginEntryScope.provideSettingsEntry()")
        assertContains(text, "register<SettingsRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->")
        assertContains(text, "AndroidFragment<SettingsFragment>(")
        // NO navigator — the cross-module probe found no compiled SettingsNavigator → 2-arg no-nav bindGezgin.
        assertContains(text, "onUpdate = { fragment -> bindGezgin(fragment, route) },")
        assertFalse(
            text.contains("settingsNavigator") || text.contains("val nav ="),
            "display-only cross-module leaf must NOT wire a navigator (no `val nav`, no settingsNavigator): $text",
        )
        // `raw` is still bound (route.toBundle needs it) even when nav wiring is suppressed.
        assertContains(text, "arguments = remember(route) { route.toBundle(raw) },")
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
