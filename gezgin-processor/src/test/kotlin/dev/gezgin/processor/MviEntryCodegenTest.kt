package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.DAGGER_ASSISTED_STUBS
import dev.gezgin.processor.fixtures.HILT_ASSISTED_MVI_SOURCE
import dev.gezgin.processor.fixtures.HILT_PLAIN_MVI_SOURCE
import dev.gezgin.processor.fixtures.HILT_STUBS
import dev.gezgin.processor.fixtures.KOIN_MVI_SOURCE
import dev.gezgin.processor.fixtures.KOIN_PROBLEM1_MVI_SOURCE
import dev.gezgin.processor.fixtures.KOIN_STUBS
import dev.gezgin.processor.fixtures.MVI_NAV_SOURCE
import dev.gezgin.processor.fixtures.MVI_SOURCE
import dev.gezgin.processor.fixtures.SHEET_MVI_SOURCE
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
                "{ args -> viewModel(factory = viewModelFactory { initializer { CounterViewModel(args) } }) })",
        )
        assertContains(text, "register<CounterRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->")
        // No navigator: no `val nav = …`, and the resolver is invoked with `route` only.
        assertFalse(text.contains("LocalGezginRawNavigator"), text)
        assertContains(text, "val vm = viewModel(route)")
        assertContains(text, "val state by vm.uiState.collectAsStateWithLifecycle()")
        // @ScreenEffect matched by effect type → wired (no nav param on the binder).
        assertContains(text, "CounterEffects(vm.effects)")
        assertContains(text, "CounterContent(state, vm::onIntent)")
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
                "{ initializer { DetailViewModel(args, nav) } }) })",
        )
        // DetailNavigator is same-package as the generated file → referenced bare, no import needed
        // (the resolver signature `nav: DetailNavigator` above already pins the type is wired).
        // Nav wiring identical to core-mode: factory qualified against the route's package.
        assertContains(
            text,
            "val nav = LocalGezginRawNavigator.current.detailNavigator(LocalGezginEntryId.current)",
        )
        assertContains(text, "val vm = viewModel(nav, route)")
        assertContains(text, "DetailContent(state, vm::onIntent)")
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
            "= { args -> viewModel(factory = viewModelFactory { initializer { SheetVm(args) } }) }, " +
                "imageLoader: @Composable () -> ImageLoader)",
        )
        assertContains(text, "register<SheetRoute>(kind = EntryKind.BOTTOM_SHEET, noBack = false) { route ->")
        // Role extra reads its Local; resolver extra is invoked — both passed as NAMED content args.
        assertContains(
            text,
            "SheetContent(state, vm::onIntent, sheetState = LocalGezginSheetState.current, " +
                "imageLoader = imageLoader())",
        )
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
