package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.FRAGMENT_STUB
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Fix-round gate for the UNIFIED cross-module navigator probe ([dev.gezgin.processor.codegen.NavigatorProbe])
 * — the `@GezginNavigatorFor` identity marker (Faz-6 M1) + the SC2/MV7 backport of the fragment classpath
 * probe (Faz-5 MN2 / Integ M1). The probe replaces two defects at once:
 *
 * - **M1 (false positive):** the old cross-module probe matched a navigator BY NAME only. `stripSuffix`
 *   collides (`HelpRoute`/`HelpScreenRoute` → `x=Help` → both name-match `HelpNavigator`), so a display-only
 *   route was silently wired to a FOREIGN route's navigator. The marker makes the probe verify IDENTITY.
 * - **MN2/Integ M1 (false negative):** core-mode `SC2` and MVI-mode `MV7` assumed `?: true` for cross-module
 *   routes → a nav-wanting entry/VM on a navigator-less cross-module route emitted `raw.xNavigator()`
 *   (unresolved reference in GENERATED code) instead of a clean `[SC2]`/`[MV7]`.
 *
 * **kctfork caveat (same as [EntryCodegenTest]/[FragmentEntryCodegenTest]):** the emitted bodies call
 * compose-runtime inline accessors / `AndroidFragment` / an unresolved cross-module factory the single-round
 * stubs don't define — so the WITH-navigator tests assert the golden TEXT + absence of `[SC*]`/`[MV*]` (KSP
 * ran clean), NOT `exitCode == OK`. The REJECT tests assert `COMPILATION_ERROR` + the bracketed KSP code.
 */
@OptIn(ExperimentalCompilerApi::class)
class NavigatorProbeTest {

    // region Fragment (FS5) — M1 identity, plus the two-module COMPILED-classpath proof

    /**
     * M1 false-positive killer. `HelpRoute` (edged → earns `HelpNavigator`) and `HelpScreenRoute`
     * (display-only) both derive `x=Help`; the only compiled `HelpNavigator` is stamped for `HelpRoute`.
     * The Fragment targets the DIFFERENT `HelpScreenRoute`. A name-only probe (old code) wires HelpRoute's
     * navigator to a foreign route (silent type-safety breach); the identity check rejects the decoy.
     */
    @Test
    fun `FS5-M1 — cross-module fragment probe rejects a name-matching decoy navigator (identity)`() {
        val decoyNav = """
            package dev.gezgin.decoynav

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.GezginNavigatorFor

            data class HelpRoute(val id: String = "x") : Route
            data class HelpScreenRoute(val topic: String = "x") : Route

            // Models NavigatorCodegen's real stamp: this compiled navigator belongs to HelpRoute.
            @GezginNavigatorFor(HelpRoute::class)
            class HelpNavigator
        """.trimIndent()
        val fragSrc = """
            package dev.gezgin.decoyfrag

            import androidx.fragment.app.Fragment
            import dev.gezgin.core.annotation.FragmentScreen
            import dev.gezgin.decoynav.HelpScreenRoute

            @FragmentScreen(HelpScreenRoute::class)
            class HelpFragment : Fragment()
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB),
            SourceFile.kotlin("DecoyNav.kt", decoyNav),
            SourceFile.kotlin("DecoyFrag.kt", fragSrc),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        // A display-only Fragment is legitimate — no FS/SC reject.
        assertTrue(
            !result.messages.contains("[FS") && !result.messages.contains("[SC"),
            "unexpected KSP [FS|SC] error: ${result.messages}",
        )
        val gen = result.generatedSourceFor("GezginFragmentEntries.kt")
        assertNotNull(gen, "GezginFragmentEntries.kt not generated: ${result.messages}")
        val text = gen.readText()

        assertContains(text, "public fun GezginEntryScope.provideHelpEntry()")
        // Identity: HelpNavigator is stamped for HelpRoute ≠ HelpScreenRoute → NO nav wiring (2-arg bind).
        assertContains(text, "onUpdate = { fragment -> bindGezgin(fragment, route) },")
        assertFalse(
            text.contains("helpNavigator") || text.contains("val nav ="),
            "a decoy navigator (marked for a foreign route) must NOT be wired: $text",
        )
    }

    /**
     * The COMPILED-classpath proof the task requires: module 1 (nav) owns a graph whose `HelpScreenRoute`
     * earns a REAL `HelpNavigator` that `NavigatorCodegen` STAMPS with `@GezginNavigatorFor`; it compiles to
     * bytecode. Module 2 (fragment feature) probes that route cross-module — so the probe must read the marker
     * (and its `KClass` arg) off a genuinely COMPILED declaration on the classpath (KSP2), match identity, and
     * wire the navigator. Exercises the full real pipeline: stamp → compile → classpath read → identity match.
     */
    @Test
    fun `FS5 — probe reads @GezginNavigatorFor off a COMPILED classpath navigator (two-module)`() {
        val navSrc = """
            package dev.gezgin.twonav

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.BackTo
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.NavGraph

            @NavGraph
            sealed interface HomeGraph : Route {
                // @GoTo → earns a DashboardNavigator; @BackTo below → HelpScreenRoute earns HelpNavigator.
                @GoTo(HelpScreenRoute::class)
                data object DashboardScreenRoute : HomeGraph

                @BackTo(DashboardScreenRoute::class)
                data class HelpScreenRoute(val topic: String = "x") : HomeGraph
            }
        """.trimIndent()
        val navModule = CompileHarness.compileGezginModule(
            SourceFile.kotlin("Nav.kt", navSrc),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        // No @Screen → no compose call sites → the nav module must compile cleanly so its stamped
        // HelpNavigator lands on the feature module's classpath as real bytecode.
        assertEquals(KotlinCompilation.ExitCode.OK, navModule.exitCode, navModule.messages)

        val fragSrc = """
            package dev.gezgin.twofrag

            import androidx.fragment.app.Fragment
            import dev.gezgin.core.annotation.FragmentScreen
            import dev.gezgin.twonav.HomeGraph.HelpScreenRoute

            @FragmentScreen(HelpScreenRoute::class)
            class HelpFragment : Fragment()
        """.trimIndent()
        val feature = CompileHarness.compileGezginModule(
            SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB),
            SourceFile.kotlin("Frag.kt", fragSrc),
            extraClasspath = listOf(navModule.outputDirectory),
        )
        assertTrue(
            !feature.messages.contains("[FS") && !feature.messages.contains("[SC"),
            "unexpected KSP [FS|SC] error in feature module: ${feature.messages}",
        )
        val gen = feature.generatedSourceFor("GezginFragmentEntries.kt")
        assertNotNull(gen, "feature emitted no GezginFragmentEntries.kt: ${feature.messages}")
        val text = gen.readText()

        // Probe read the marker off the COMPILED HelpNavigator → identity matched → nav wired.
        assertContains(text, "public fun GezginEntryScope.provideHelpEntry()")
        assertContains(text, "import dev.gezgin.twonav.helpNavigator")
        assertContains(text, "val nav = raw.helpNavigator(LocalGezginEntryId.current)")
        assertContains(text, "onUpdate = { fragment -> bindGezgin(fragment, route, nav) },")
    }

    // endregion

    // region Core-mode (SC2) cross-module × (exists / doesn't / decoy)

    @Test
    fun `SC2 cross-module — nav wired when a compiled navigator's marker matches the route`() {
        // WidgetRoute is graph-less here (cross-module branch) with a compiled, correctly-stamped navigator.
        val src = """
            package dev.gezgin.scx

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.GezginNavigatorFor
            import dev.gezgin.core.annotation.Screen

            data class WidgetRoute(val id: String = "x") : Route

            @GezginNavigatorFor(WidgetRoute::class)
            class WidgetNavigator

            @Screen
            @Composable
            fun WidgetScreen(route: WidgetRoute, nav: WidgetNavigator) {
            }
        """.trimIndent()
        val result = compileGezgin(
            SourceFile.kotlin("Sc.kt", src),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(!result.messages.contains("[SC"), "unexpected KSP [SC] error: ${result.messages}")
        val gen = result.generatedSourceFor("GezginEntries.kt")
        assertNotNull(gen, "GezginEntries.kt not generated: ${result.messages}")
        assertContains(
            gen.readText(),
            "val nav = LocalGezginRawNavigator.current.widgetNavigator(LocalGezginEntryId.current)",
        )
    }

    @Test
    fun `SC2 cross-module — no compiled navigator now rejects (old blind pass emitted unresolved code)`() {
        // BareRoute graph-less, NO compiled BareNavigator (the nav param type stays an error type). Old
        // `?: true` sailed through and emitted `raw.bareNavigator()`; the probe now yields a clean [SC2].
        val src = """
            package dev.gezgin.scbare

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen

            data class BareRoute(val id: String = "x") : Route

            @Screen
            @Composable
            fun BareScreen(route: BareRoute, nav: BareNavigator) {
            }
        """.trimIndent()
        val result = compileGezgin(SourceFile.kotlin("Sc.kt", src))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("[SC2]"), result.messages)
    }

    @Test
    fun `SC2 cross-module — name-matching decoy navigator (foreign marker) is rejected (identity)`() {
        // FooScreenRoute → x=Foo; the compiled FooNavigator (name matches, so m4's type check passes) is
        // stamped for a DIFFERENT FooRoute → the probe's identity check rejects it with [SC2].
        val src = """
            package dev.gezgin.scdecoy

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.GezginNavigatorFor
            import dev.gezgin.core.annotation.Screen

            data class FooRoute(val id: String = "x") : Route
            data class FooScreenRoute(val id: String = "x") : Route

            @GezginNavigatorFor(FooRoute::class)
            class FooNavigator

            @Screen
            @Composable
            fun FooScreen(route: FooScreenRoute, nav: FooNavigator) {
            }
        """.trimIndent()
        val result = compileGezgin(
            SourceFile.kotlin("Sc.kt", src),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("[SC2]"), result.messages)
    }

    // endregion

    // region MVI-mode (MV7) cross-module × (exists / doesn't / decoy)

    private fun mviSource(
        pkg: String,
        routeDecls: String,
        vmCtor: String,
        route: String = "WidgetRoute",
    ): String = """
        package $pkg

        import androidx.compose.runtime.Composable
        import dev.gezgin.core.Route
        import dev.gezgin.core.annotation.Screen
        import dev.gezgin.mvi.GezginMvi
        import dev.gezgin.mvi.annotation.ViewModel
        import kotlinx.coroutines.flow.Flow
        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.flow.StateFlow
        import kotlinx.coroutines.flow.emptyFlow

        $routeDecls

        data class WState(val n: Int)
        sealed interface WIntent { data object Tick : WIntent }
        sealed interface WEffect { data class Toast(val t: String) : WEffect }

        @ViewModel($route::class)
        class WidgetVm($vmCtor) : GezginMvi<WState, WIntent, WEffect> {
            override val uiState: StateFlow<WState> = MutableStateFlow(WState(0))
            override val effects: Flow<WEffect> = emptyFlow()
            override fun onIntent(intent: WIntent) {}
        }

        @Screen($route::class)
        @Composable
        fun WidgetContent(state: WState, onIntent: (WIntent) -> Unit) {
        }
    """.trimIndent()

    @Test
    fun `MV7 cross-module — nav wired when a compiled navigator's marker matches the route`() {
        val src = mviSource(
            pkg = "dev.gezgin.mvx",
            routeDecls = """
                data class WidgetRoute(val id: String = "x") : Route

                @dev.gezgin.core.annotation.GezginNavigatorFor(WidgetRoute::class)
                class WidgetNavigator
            """.trimIndent(),
            vmCtor = "nav: WidgetNavigator",
        )
        val result = compileGezgin(
            SourceFile.kotlin("Mv.kt", src),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(
            !result.messages.contains("[MV") && !result.messages.contains("[SC"),
            "unexpected KSP [MV|SC] error: ${result.messages}",
        )
        val gen = result.generatedSourceFor("GezginMviEntries.kt")
        assertNotNull(gen, "GezginMviEntries.kt not generated: ${result.messages}")
        assertContains(gen.readText(), "widgetNavigator(")
    }

    @Test
    fun `MV7 cross-module — no compiled navigator now rejects (old blind pass emitted unresolved code)`() {
        // nav: BareNavigator is unresolved → classified NAV by name (isError) → MV7 fires; the probe finds
        // no compiled BareNavigator → clean [MV7] instead of the old `?: true` unresolved `raw.bareNavigator()`.
        val src = mviSource(
            pkg = "dev.gezgin.mvbare",
            routeDecls = "data class WidgetRoute(val id: String = \"x\") : Route",
            vmCtor = "nav: BareNavigator",
        )
        val result = compileGezgin(SourceFile.kotlin("Mv.kt", src))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("[MV7]"), result.messages)
    }

    @Test
    fun `MV7 cross-module — name-matching decoy navigator (foreign marker) is rejected (identity)`() {
        // WidgetScreenRoute → x=Widget; the compiled WidgetNavigator (type matches → classified NAV) is
        // stamped for a DIFFERENT WidgetRoute → identity check rejects with [MV7].
        val src = mviSource(
            pkg = "dev.gezgin.mvdecoy",
            route = "WidgetScreenRoute",
            routeDecls = """
                data class WidgetRoute(val id: String = "x") : Route
                data class WidgetScreenRoute(val id: String = "x") : Route

                @dev.gezgin.core.annotation.GezginNavigatorFor(WidgetRoute::class)
                class WidgetNavigator
            """.trimIndent(),
            vmCtor = "nav: WidgetNavigator",
        )
        val result = compileGezgin(
            SourceFile.kotlin("Mv.kt", src),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("[MV7]"), result.messages)
    }

    // endregion
}
