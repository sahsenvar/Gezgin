package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.DAGGER_ASSISTED_STUBS
import dev.gezgin.processor.fixtures.DUP_ROUTE_MVI_SOURCE
import dev.gezgin.processor.fixtures.EFFECT_NAV_MVI_SOURCE
import dev.gezgin.processor.fixtures.HILT_ASSISTED_MVI_SOURCE
import dev.gezgin.processor.fixtures.HILT_PLAIN_MVI_SOURCE
import dev.gezgin.processor.fixtures.HILT_STUBS
import dev.gezgin.processor.fixtures.KOIN_MVI_SOURCE
import dev.gezgin.processor.fixtures.KOIN_PROBLEM1_MVI_SOURCE
import dev.gezgin.processor.fixtures.KOIN_STUBS
import dev.gezgin.processor.fixtures.MVI_NAV_SOURCE
import dev.gezgin.processor.fixtures.MVI_SOURCE
import dev.gezgin.processor.fixtures.SHEET_MVI_SOURCE
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Faz 5.2 gate for [dev.gezgin.processor.codegen.MviEntryCodegen] — MVI-mode `provideXEntry` codegen
 * (spec §10.1): DI-detected default resolver, conditional nav wiring, `@ScreenEffect` wiring, and
 * Problem-2 resolver params.
 *
 * **kctfork caveat (same as [EntryCodegenTest]):** the emitted entry bodies call compose-runtime inline
 * accessors (`collectAsStateWithLifecycle`, `CompositionLocal.current`) that the plugin-less kctfork
 * BACKEND can't inline (ICE); the Koin/Hilt fixtures additionally reference symbols absent from the
 * classpath. So these tests do NOT assert `exitCode == OK` — they assert no `[SC*]`/`[MV*]` KSP error
 * (codegen ran clean) plus the generated `GezginMviEntries.kt` golden TEXT, which the successful KSP
 * round populates regardless of the later backend/frontend failure.
 */
@OptIn(ExperimentalCompilerApi::class)
class MviEntryCodegenTest {

    /** Compiles the MVI fixture(s), asserts a clean KSP round, and returns the generated entry text. */
    private fun generateMvi(vararg sources: SourceFile): String {
        val result = compileGezgin(*sources, kspArgs = mapOf("gezgin.emitSerializers" to "false"))
        assertTrue(
            !result.messages.contains("[SC") && !result.messages.contains("[MV"),
            "unexpected KSP/[SC|MV] error: ${result.messages}",
        )
        val gen = result.generatedSourceFor("GezginMviEntries.kt")
        assertNotNull(gen, "GezginMviEntries.kt not generated: ${result.messages}")
        return gen.readText()
    }

    @Test
    fun `androidx-fallback default resolver — route-only ctor, effect wired, no nav`() {
        val text = generateMvi(SourceFile.kotlin("MviSource.kt", MVI_SOURCE))

        // DI-detected default = androidx `viewModel(factory = viewModelFactory { initializer { VM(args) } })`.
        assertContains(text, "import androidx.lifecycle.viewmodel.compose.viewModel")
        assertContains(
            text,
            "provideCounterEntry(viewModel: @Composable (args: CounterRoute) -> CounterViewModel = " +
                "{ args -> viewModel(factory = viewModelFactory { initializer { CounterViewModel(route = args) } }) })",
        )
        assertContains(text, "register<CounterRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->")
        // No navigator: no `val nav = …`, and the resolver is invoked with `route` only.
        assertFalse(text.contains("LocalGezginRawNavigator"), text)
        assertContains(text, "val vm = viewModel(route)")
        assertContains(text, "val state by vm.uiState.collectAsStateWithLifecycle()")
        // @ScreenEffect matched by effect type → wired (no nav param on the binder), NAMED arg (MN1).
        assertContains(text, "CounterEffects(effects = vm.effects)")
        assertContains(text, "CounterContent(state = state, onIntent = vm::onIntent)")
    }

    @Test
    fun `androidx-fallback with nav — nav wired, resolver nav param, ctor order preserved`() {
        val text = generateMvi(SourceFile.kotlin("Nav.kt", MVI_NAV_SOURCE))

        // Resolver signature carries `nav: XNavigator` (VM ctor declares nav); ctor call keeps declared
        // order `DetailViewModel(args, nav)` even though the resolver takes `(nav, args)`.
        assertContains(
            text,
            "provideDetailEntry(viewModel: @Composable (nav: DetailNavigator, args: G.Detail) -> " +
                "DetailViewModel = { nav, args -> viewModel(factory = viewModelFactory " +
                "{ initializer { DetailViewModel(route = args, nav = nav) } }) })",
        )
        // DetailNavigator is same-package as the generated file → referenced bare, no import needed
        // (the resolver signature `nav: DetailNavigator` above already pins the type is wired).
        // Nav wiring identical to core-mode: factory qualified against the route's package.
        assertContains(
            text,
            "val nav = LocalGezginRawNavigator.current.detailNavigator(LocalGezginEntryId.current)",
        )
        assertContains(text, "val vm = viewModel(nav, route)")
        assertContains(text, "DetailContent(state = state, onIntent = vm::onIntent)")
    }

    @Test
    fun `koin default resolver — koinViewModel with parametersOf`() {
        val text = generateMvi(
            SourceFile.kotlin("Koin.kt", KOIN_MVI_SOURCE),
            SourceFile.kotlin("KoinStubs.kt", KOIN_STUBS),
        )
        assertContains(text, "import org.koin.compose.viewmodel.koinViewModel")
        assertContains(text, "import org.koin.core.parameter.parametersOf")
        assertContains(
            text,
            "provideKoinEntry(viewModel: @Composable (args: KoinRoute) -> KoinVm = " +
                "{ args -> koinViewModel { parametersOf(args) } })",
        )
    }

    @Test
    fun `Problem 1 — VM with a non-route-nav injected param gets NO default resolver (required)`() {
        val text = generateMvi(
            SourceFile.kotlin("KoinP1.kt", KOIN_PROBLEM1_MVI_SOURCE),
            SourceFile.kotlin("KoinStubs.kt", KOIN_STUBS),
        )
        // The `viewModel` param is REQUIRED — no `= { … }` default — because `userId: String` can't be
        // supplied by Gezgin. The user provides the resolver themselves.
        assertContains(text, "provideP1Entry(viewModel: @Composable (args: P1Route) -> P1Vm) {")
        assertFalse(text.contains("koinViewModel"), "no default should be emitted for Problem-1: $text")
    }

    @Test
    fun `hilt assisted default resolver — hiltViewModel with assisted factory creationCallback`() {
        val text = generateMvi(
            SourceFile.kotlin("Hilt.kt", HILT_ASSISTED_MVI_SOURCE),
            SourceFile.kotlin("HiltStubs.kt", HILT_STUBS),
            SourceFile.kotlin("AssistedStubs.kt", DAGGER_ASSISTED_STUBS),
        )
        assertContains(text, "import androidx.hilt.navigation.compose.hiltViewModel")
        assertContains(
            text,
            "provideHiltEntry(viewModel: @Composable (args: HiltRoute) -> HiltVm = " +
                "{ args -> hiltViewModel<HiltVm, HiltVm.Factory>(creationCallback = " +
                "{ factory -> factory.create(args) }) })",
        )
    }

    @Test
    fun `hilt plain default resolver — hiltViewModel with no assisted factory, no nav`() {
        val text = generateMvi(
            SourceFile.kotlin("HiltPlain.kt", HILT_PLAIN_MVI_SOURCE),
            SourceFile.kotlin("HiltStubs.kt", HILT_STUBS),
        )
        assertContains(text, "import androidx.hilt.navigation.compose.hiltViewModel")
        assertContains(
            text,
            "providePlainEntry(viewModel: @Composable (args: PlainRoute) -> PlainVm = " +
                "{ hiltViewModel<PlainVm>() })",
        )
    }

    @Test
    fun `Problem 2 — bottom sheet role extra (sheetState) and resolver extra (imageLoader)`() {
        val text = generateMvi(SourceFile.kotlin("Sheet.kt", SHEET_MVI_SOURCE))

        // The resolver extra is a REQUIRED `@Composable () -> T` param (no default); kind is BOTTOM_SHEET.
        assertContains(
            text,
            "= { args -> viewModel(factory = viewModelFactory { initializer { SheetVm(route = args) } }) }, " +
                "imageLoader: @Composable () -> ImageLoader)",
        )
        assertContains(text, "register<SheetRoute>(kind = EntryKind.BOTTOM_SHEET, noBack = false) { route ->")
        // The generated register injects `LocalGezginSheetState.current` (a material3 `SheetState`), so
        // the generated file — not the user's content — must opt in to the ERROR-level experimental API;
        // otherwise the consumer module fails to compile.
        assertContains(text, "@OptIn(ExperimentalMaterial3Api::class)")
        // Role extra reads its Local; resolver extra is invoked — all content args NAMED (MN1).
        assertContains(
            text,
            "SheetContent(state = state, onIntent = vm::onIntent, sheetState = LocalGezginSheetState.current, " +
                "imageLoader = imageLoader())",
        )
    }

    @Test
    fun `Important 2 — @ScreenEffect with nav wires navigator solely for the effect (VM ctor has no nav)`() {
        val text = generateMvi(SourceFile.kotlin("EffNav.kt", EFFECT_NAV_MVI_SOURCE))

        // (a) The effect binder is invoked WITH nav, NAMED args (MN1).
        assertContains(text, "HomeEffects(effects = vm.effects, nav = nav)")
        // The navigator IS wired (the effect needs it) even though the VM ctor takes no nav.
        assertContains(
            text,
            "val nav = LocalGezginRawNavigator.current.homeNavigator(LocalGezginEntryId.current)",
        )
        // (b) nav wired SOLELY by the effect: the resolver lambda signature has NO `nav` param, and the
        // androidx default + call site pass only `route` (the VM ctor is route-only).
        assertContains(
            text,
            "provideHomeEntry(viewModel: @Composable (args: F.Home) -> HomeViewModel = " +
                "{ args -> viewModel(factory = viewModelFactory { initializer { HomeViewModel(route = args) } }) })",
        )
        assertContains(text, "val vm = viewModel(route)")
        assertFalse(
            text.contains("viewModel(nav, route)"),
            "VM ctor takes no nav — resolver must be invoked with route only: $text",
        )
    }

    @Test
    fun `Minor 4 — VM with two route-typed ctor params gets NO default (no silent VM(args, args))`() {
        val text = generateMvi(SourceFile.kotlin("DupRoute.kt", DUP_ROUTE_MVI_SOURCE))

        // Two route-typed params can't be positionally disambiguated → `viewModel` becomes REQUIRED.
        assertContains(text, "provideDupEntry(viewModel: @Composable (args: DupRoute) -> DupVm) {")
        assertFalse(text.contains("DupVm(args, args)"), "must not silently emit VM(args, args): $text")
        assertFalse(text.contains("viewModelFactory"), "no default resolver should be emitted: $text")
    }

    @Test
    fun `core-mode and MVI-mode coexist in one package — separate files, no collision`() {
        val source = """
            package dev.gezgin.mixed

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            // core-mode entry
            data class PlainScreenRoute(val a: Int = 0) : Route
            @Screen
            @Composable
            fun PlainScreen(route: PlainScreenRoute) {
            }

            // MVI triple (same package)
            data class MRoute(val b: Int = 0) : Route
            data class MState(val n: Int)
            sealed interface MIntent { data object Go : MIntent }
            data class MEffect(val m: String)

            @ViewModel(MRoute::class)
            class MVm(route: MRoute) : GezginMvi<MState, MIntent, MEffect> {
                override val uiState: StateFlow<MState> = MutableStateFlow(MState(route.b))
                override fun onIntent(intent: MIntent) {}
            }

            @Screen(MRoute::class)
            @Composable
            fun MContent(state: MState, onIntent: (MIntent) -> Unit) {
            }
        """.trimIndent()

        val result = compileGezgin(
            SourceFile.kotlin("Mixed.kt", source),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(
            !result.messages.contains("[SC") && !result.messages.contains("[MV"),
            "unexpected KSP error: ${result.messages}",
        )
        val core = result.generatedSourceFor("GezginEntries.kt")
        val mvi = result.generatedSourceFor("GezginMviEntries.kt")
        assertNotNull(core, "core-mode GezginEntries.kt missing: ${result.messages}")
        assertNotNull(mvi, "MVI-mode GezginMviEntries.kt missing: ${result.messages}")
        assertContains(core.readText(), "providePlainEntry")
        assertContains(mvi.readText(), "provideMEntry")
    }

    @Test
    fun `MJ1 — nav-named non-navigator @InjectedParam is OTHER not NAV (no spurious MV7, no default)`() {
        // `About` (SHOP_SOURCE) is in-model but earns NO navigator. Under the OLD name-over-type rule a
        // `nav`-named param classified NAV → vmHasNav=true → a SPURIOUS [MV7] (and, in Koin, a default
        // that crashes at runtime). Now the RESOLVED `AnalyticsTracker` type wins → OTHER → no MV7, and no
        // default resolver (the `viewModel` param becomes required). generateMvi throws if any [MV*] fires.
        val text = generateMvi(
            SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
            SourceFile.kotlin("KoinStubs.kt", KOIN_STUBS),
            SourceFile.kotlin(
                "Mj1.kt",
                """
                package dev.gezgin.mj1

                import androidx.compose.runtime.Composable
                import dev.gezgin.core.annotation.Screen
                import dev.gezgin.mvi.GezginMvi
                import dev.gezgin.mvi.annotation.ViewModel
                import dev.gezgin.shop.HomeGraph.About
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import org.koin.core.annotation.InjectedParam
                import org.koin.core.annotation.KoinViewModel

                data class AboutState(val n: Int)
                sealed interface AboutIntent { data object Go : AboutIntent }
                data class AboutEffect(val m: String)
                class AnalyticsTracker

                // NAMED `nav` but TYPED AnalyticsTracker (resolvable, NOT the navigator) → OTHER.
                @ViewModel(About::class)
                @KoinViewModel
                class AboutVm(@InjectedParam nav: AnalyticsTracker) :
                    GezginMvi<AboutState, AboutIntent, AboutEffect> {
                    override val uiState: StateFlow<AboutState> = MutableStateFlow(AboutState(0))
                    override fun onIntent(intent: AboutIntent) {}
                }

                @Screen(About::class)
                @Composable
                fun AboutContent(state: AboutState, onIntent: (AboutIntent) -> Unit) {
                }
                """.trimIndent(),
            ),
        )
        // No default resolver (OTHER param can't be Gezgin-supplied → `viewModel` required, no `= {`).
        assertFalse(text.contains("koinViewModel"), "no default should be emitted (nav is OTHER): $text")
        assertFalse(text.contains("parametersOf"), "no default should be emitted (nav is OTHER): $text")
        // No nav wiring — an OTHER param must not force a navigator factory call.
        assertFalse(text.contains("Navigator"), "OTHER param must not trigger nav wiring: $text")
    }

    @Test
    fun `MN4a — a defaulted OTHER ctor param is honored (default resolver still emitted, param omitted)`() {
        // `retries: Int = 3` is an OTHER param but HAS a default → it must NOT force the `viewModel` param
        // to become required. A default resolver is still emitted; the ctor call omits `retries` (named args).
        val text = generateMvi(
            SourceFile.kotlin(
                "Mn4a.kt",
                """
                package dev.gezgin.mn4a

                import androidx.compose.runtime.Composable
                import dev.gezgin.core.Route
                import dev.gezgin.core.annotation.Screen
                import dev.gezgin.mvi.GezginMvi
                import dev.gezgin.mvi.annotation.ViewModel
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow

                data class ConfigRoute(val id: String = "x") : Route
                data class ConfigState(val n: Int)
                sealed interface ConfigIntent { data object Go : ConfigIntent }
                data class ConfigEffect(val m: String)

                @ViewModel(ConfigRoute::class)
                class ConfigVm(route: ConfigRoute, retries: Int = 3) :
                    GezginMvi<ConfigState, ConfigIntent, ConfigEffect> {
                    override val uiState: StateFlow<ConfigState> = MutableStateFlow(ConfigState(retries))
                    override fun onIntent(intent: ConfigIntent) {}
                }

                @Screen(ConfigRoute::class)
                @Composable
                fun ConfigContent(state: ConfigState, onIntent: (ConfigIntent) -> Unit) {
                }
                """.trimIndent(),
            ),
        )
        assertContains(
            text,
            "provideConfigEntry(viewModel: @Composable (args: ConfigRoute) -> ConfigVm = " +
                "{ args -> viewModel(factory = viewModelFactory { initializer { ConfigVm(route = args) } }) })",
        )
        assertFalse(text.contains("retries"), "defaulted OTHER param must be omitted from the ctor call: $text")
    }

    @Test
    fun `MN4b — a defaulted content extra needs no resolver param (uses the composable's own default)`() {
        // `showTitle: Boolean = true` is a resolver-type extra but HAS a default → it must NOT become a
        // required `@Composable () -> Boolean` param. The content call (named args) simply omits it.
        val text = generateMvi(
            SourceFile.kotlin(
                "Mn4b.kt",
                """
                package dev.gezgin.mn4b

                import androidx.compose.runtime.Composable
                import dev.gezgin.core.Route
                import dev.gezgin.core.annotation.Screen
                import dev.gezgin.mvi.GezginMvi
                import dev.gezgin.mvi.annotation.ViewModel
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow

                data class PageRoute(val id: String = "x") : Route
                data class PageState(val n: Int)
                sealed interface PageIntent { data object Go : PageIntent }
                data class PageEffect(val m: String)

                @ViewModel(PageRoute::class)
                class PageVm(route: PageRoute) : GezginMvi<PageState, PageIntent, PageEffect> {
                    override val uiState: StateFlow<PageState> = MutableStateFlow(PageState(0))
                    override fun onIntent(intent: PageIntent) {}
                }

                @Screen(PageRoute::class)
                @Composable
                fun PageContent(state: PageState, onIntent: (PageIntent) -> Unit, showTitle: Boolean = true) {
                }
                """.trimIndent(),
            ),
        )
        // No `showTitle` resolver param on provideXEntry, and the content call omits it.
        assertFalse(text.contains("showTitle"), "defaulted content extra must not force a resolver: $text")
        assertContains(text, "PageContent(state = state, onIntent = vm::onIntent)")
    }

    @Test
    fun `gezgin_emitEntries=false suppresses GezginMviEntries generation`() {
        val result = compileGezgin(
            SourceFile.kotlin("MviSource.kt", MVI_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitEntries" to "false"),
        )
        // Nothing calls compose-runtime → no backend ICE → full-OK exit expected.
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(
            result.generatedSourceFor("GezginMviEntries.kt") == null,
            "GezginMviEntries.kt emitted despite opt-out",
        )
    }
}
