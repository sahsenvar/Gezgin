package dev.gezgin.processor.fixtures

/**
 * Faz 5.2 codegen fixtures for [dev.gezgin.processor.MviEntryCodegenTest] — one MVI triple per
 * DI-detection path plus the nav-wiring and Problem-2 paths (§10.1).
 *
 * **DI stub strategy (documented decision):** `gezgin-processor` has NO compile dependency on Hilt or
 * Koin (their annotations are read as string FQNs). So the Hilt/Koin fixtures declare MINIMAL local
 * `annotation class` stubs under the EXACT real FQN packages ([HILT_STUBS], [DAGGER_ASSISTED_STUBS],
 * [KOIN_STUBS]) — the same string-FQN-reading contract the processor uses in production, exercised
 * against a compile-time-present stub. The stub `@HiltViewModel.assistedFactory` defaults to the
 * self-referential `HiltViewModel::class` (matching real Hilt exactly), which the reader's sentinel
 * guard treats as "no assisted factory" (plain Hilt) — the same disjunct that fires in production.
 *
 * The generated Koin/Hilt entries reference `koinViewModel`/`hiltViewModel` etc. which are NOT on the
 * kctfork classpath, so those compilations don't reach a clean exit — but the KSP round still emits
 * `GezginMviEntries.kt`, whose golden TEXT is what these tests assert (same contract as
 * [dev.gezgin.processor.EntryCodegenTest]'s backend-ICE caveat).
 */

// region DI annotation stubs (one package each — a .kt file has a single package)

val HILT_STUBS = """
    package dagger.hilt.android.lifecycle

    import kotlin.reflect.KClass

    // Self-referential `HiltViewModel::class` default — matches REAL Hilt exactly, so the plain-Hilt
    // test exercises the production-relevant `factoryFq == HILT_VIEW_MODEL_FQ` sentinel disjunct (not a
    // synthetic `Unit::class` that never fires in production). See ViewModelModelReader.detectDi.
    annotation class HiltViewModel(val assistedFactory: KClass<*> = HiltViewModel::class)
""".trimIndent()

val DAGGER_ASSISTED_STUBS = """
    package dagger.assisted

    annotation class AssistedInject
    annotation class Assisted
    annotation class AssistedFactory
""".trimIndent()

val KOIN_STUBS = """
    package org.koin.core.annotation

    annotation class KoinViewModel
    annotation class InjectedParam
""".trimIndent()

// endregion

/**
 * androidx-fallback WITH nav wiring: `Detail` earns a `DetailNavigator` (via `@GoTo(Home)`), and the VM
 * ctor takes both `route` and `nav`. Pins the conditional nav wiring (`val nav = …`, `viewModel(nav,
 * route)`, resolver `nav:` param) + the androidx ctor order `DetailViewModel(args, nav)`. The VM's
 * `nav: DetailNavigator` type is not yet generated in the KSP round that reads it (matched by the `nav`
 * name convention).
 */
val MVI_NAV_SOURCE = """
    package dev.gezgin.navmvi

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.GoTo
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow

    @NavGraph
    sealed interface G : Route {
        // `Detail` earns a `DetailNavigator` via its forward edge; `Other` is bare (no navigator).
        @GoTo(Other::class)
        data class Detail(val id: String) : G

        data object Other : G
    }

    data class DetailState(val n: Int)
    sealed interface DetailIntent { data object Go : DetailIntent }
    data class DetailEffect(val m: String)

    @ViewModel(G.Detail::class)
    class DetailViewModel(route: G.Detail, nav: DetailNavigator) :
        GezginMvi<DetailState, DetailIntent, DetailEffect> {
        override val uiState: StateFlow<DetailState> = MutableStateFlow(DetailState(route.id.length))
        override fun onIntent(intent: DetailIntent) { nav.goToOther() }
    }

    @Screen(G.Detail::class)
    @Composable
    fun DetailContent(state: DetailState, onIntent: (DetailIntent) -> Unit) {
    }
""".trimIndent()

/** Koin, route-only ctor → `koinViewModel { parametersOf(args) }`. */
val KOIN_MVI_SOURCE = """
    package dev.gezgin.koinmvi

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import org.koin.core.annotation.InjectedParam
    import org.koin.core.annotation.KoinViewModel

    data class KoinRoute(val id: String = "x") : Route
    data class KoinState(val n: Int)
    sealed interface KoinIntent { data object Go : KoinIntent }
    data class KoinEffect(val m: String)

    @ViewModel(KoinRoute::class)
    @KoinViewModel
    class KoinVm(@InjectedParam route: KoinRoute) : GezginMvi<KoinState, KoinIntent, KoinEffect> {
        override val uiState: StateFlow<KoinState> = MutableStateFlow(KoinState(route.id.length))
        override fun onIntent(intent: KoinIntent) {}
    }

    @Screen(KoinRoute::class)
    @Composable
    fun KoinContent(state: KoinState, onIntent: (KoinIntent) -> Unit) {
    }
""".trimIndent()

/**
 * Problem 1 (§10.1 rule 1): a Koin VM with an extra `@InjectedParam userId: String` beyond route/nav →
 * NO default resolver; the `viewModel` param becomes REQUIRED (user must supply it).
 */
val KOIN_PROBLEM1_MVI_SOURCE = """
    package dev.gezgin.koinp1

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import org.koin.core.annotation.InjectedParam
    import org.koin.core.annotation.KoinViewModel

    data class P1Route(val id: String = "x") : Route
    data class P1State(val n: Int)
    sealed interface P1Intent { data object Go : P1Intent }
    data class P1Effect(val m: String)

    @ViewModel(P1Route::class)
    @KoinViewModel
    class P1Vm(@InjectedParam route: P1Route, @InjectedParam userId: String) :
        GezginMvi<P1State, P1Intent, P1Effect> {
        override val uiState: StateFlow<P1State> = MutableStateFlow(P1State(userId.length))
        override fun onIntent(intent: P1Intent) {}
    }

    @Screen(P1Route::class)
    @Composable
    fun P1Content(state: P1State, onIntent: (P1Intent) -> Unit) {
    }
""".trimIndent()

/** Hilt assisted, route-only → `hiltViewModel<HiltVm, HiltVm.Factory>(creationCallback = { factory -> factory.create(args) })`. */
val HILT_ASSISTED_MVI_SOURCE = """
    package dev.gezgin.hiltmvi

    import androidx.compose.runtime.Composable
    import dagger.assisted.Assisted
    import dagger.assisted.AssistedFactory
    import dagger.assisted.AssistedInject
    import dagger.hilt.android.lifecycle.HiltViewModel
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow

    data class HiltRoute(val id: String = "x") : Route
    data class HiltState(val n: Int)
    sealed interface HiltIntent { data object Go : HiltIntent }
    data class HiltEffect(val m: String)

    @ViewModel(HiltRoute::class)
    @HiltViewModel(assistedFactory = HiltVm.Factory::class)
    class HiltVm @AssistedInject constructor(@Assisted route: HiltRoute) :
        GezginMvi<HiltState, HiltIntent, HiltEffect> {
        override val uiState: StateFlow<HiltState> = MutableStateFlow(HiltState(route.id.length))
        override fun onIntent(intent: HiltIntent) {}

        @AssistedFactory
        interface Factory { fun create(route: HiltRoute): HiltVm }
    }

    @Screen(HiltRoute::class)
    @Composable
    fun HiltContent(state: HiltState, onIntent: (HiltIntent) -> Unit) {
    }
""".trimIndent()

/**
 * Plain Hilt (no assisted factory) → `hiltViewModel<PlainVm>()`, no nav. The route is PARAMETERLESS: a
 * plain-Hilt VM receives NO route data in Nav3 (no SavedStateHandle path), so a route-data-carrying route
 * is an `MV12` reject (see `MviModelReaderTest`); a data-object route is the only valid plain-Hilt shape.
 */
val HILT_PLAIN_MVI_SOURCE = """
    package dev.gezgin.hiltplain

    import androidx.compose.runtime.Composable
    import dagger.hilt.android.lifecycle.HiltViewModel
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow

    data object PlainRoute : Route
    data class PlainState(val n: Int)
    sealed interface PlainIntent { data object Go : PlainIntent }
    data class PlainEffect(val m: String)

    @ViewModel(PlainRoute::class)
    @HiltViewModel
    class PlainVm : GezginMvi<PlainState, PlainIntent, PlainEffect> {
        override val uiState: StateFlow<PlainState> = MutableStateFlow(PlainState(0))
        override fun onIntent(intent: PlainIntent) {}
    }

    @Screen(PlainRoute::class)
    @Composable
    fun PlainContent(state: PlainState, onIntent: (PlainIntent) -> Unit) {
    }
""".trimIndent()

/**
 * `@BottomSheet` MVI content with a role extra (`sheetState`) and a Problem-2 resolver extra
 * (`imageLoader`). Pins: `EntryKind.BOTTOM_SHEET`, the required `imageLoader: @Composable () ->
 * ImageLoader` param, `LocalGezginSheetState.current` for the role, and `imageLoader()` for the
 * resolver — all passed as NAMED content args.
 */
val SHEET_MVI_SOURCE = """
    @file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

    package dev.gezgin.sheetmvi2

    import androidx.compose.material3.SheetState
    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.BottomSheet
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow

    data class SheetRoute(val x: Int = 0) : Route
    data class SheetStateData(val n: Int)
    sealed interface SheetIntent { data object Go : SheetIntent }
    data class SheetEffect(val m: String)
    class ImageLoader

    @ViewModel(SheetRoute::class)
    class SheetVm(route: SheetRoute) : GezginMvi<SheetStateData, SheetIntent, SheetEffect> {
        override val uiState: StateFlow<SheetStateData> = MutableStateFlow(SheetStateData(route.x))
        override fun onIntent(intent: SheetIntent) {}
    }

    @BottomSheet(SheetRoute::class)
    @Composable
    fun SheetContent(
        state: SheetStateData,
        onIntent: (SheetIntent) -> Unit,
        sheetState: SheetState,
        imageLoader: ImageLoader,
    ) {
    }
""".trimIndent()

/**
 * `@ScreenEffect`-with-`nav` path (Important 2): `Home` earns a `HomeNavigator` (via `@GoTo(Other)`),
 * the VM ctor takes ONLY `route` (no nav), and the `@ScreenEffect` binder takes `nav: HomeNavigator`.
 * Pins the branch where nav is wired SOLELY because the effect wants it: `val nav = …` IS emitted and
 * the binder is called `HomeEffects(vm.effects, nav)`, yet the resolver signature carries NO `nav` param
 * and the `viewModel(route)` call site (androidx, route-only ctor) passes only `route`.
 */
val EFFECT_NAV_MVI_SOURCE = """
    package dev.gezgin.effnav

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.GoTo
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ScreenEffect
    import dev.gezgin.mvi.annotation.ViewModel
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow

    @NavGraph
    sealed interface F : Route {
        // `Home` earns a `HomeNavigator` via its forward edge; `Other` is bare.
        @GoTo(Other::class)
        data class Home(val id: String) : F

        data object Other : F
    }

    data class HomeState(val n: Int)
    sealed interface HomeIntent { data object Go : HomeIntent }
    data class HomeEffect(val m: String)

    // VM ctor takes ONLY route — nav is wired SOLELY because the @ScreenEffect below wants it.
    @ViewModel(F.Home::class)
    class HomeViewModel(route: F.Home) : GezginMvi<HomeState, HomeIntent, HomeEffect> {
        override val uiState: StateFlow<HomeState> = MutableStateFlow(HomeState(route.id.length))
        override fun onIntent(intent: HomeIntent) {}
    }

    @Screen(F.Home::class)
    @Composable
    fun HomeContent(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    }

    // Effect binder wants nav — its Flow<E> E matches HomeViewModel's effect type (MV6-clean).
    @ScreenEffect
    @Composable
    fun HomeEffects(effects: Flow<HomeEffect>, nav: HomeNavigator) {
    }
""".trimIndent()

/**
 * Dup-role guard (Minor 4): an androidx VM whose ctor has TWO route-typed params. Neither can be
 * positionally disambiguated by a default resolver, so `emitDefault` must fall back to false (the
 * `viewModel` param becomes REQUIRED) rather than silently emit `DupVm(args, args)`.
 */
val DUP_ROUTE_MVI_SOURCE = """
    package dev.gezgin.duproute

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow

    data class DupRoute(val id: String = "x") : Route
    data class DupState(val n: Int)
    sealed interface DupIntent { data object Go : DupIntent }
    data class DupEffect(val m: String)

    @ViewModel(DupRoute::class)
    class DupVm(route: DupRoute, other: DupRoute) : GezginMvi<DupState, DupIntent, DupEffect> {
        override val uiState: StateFlow<DupState> = MutableStateFlow(DupState(route.id.length + other.id.length))
        override fun onIntent(intent: DupIntent) {}
    }

    @Screen(DupRoute::class)
    @Composable
    fun DupContent(state: DupState, onIntent: (DupIntent) -> Unit) {
    }
""".trimIndent()

/**
 * MV7 (Important 1) — MVI-mode SC2 parity. Mirrors the core-mode `SC2` test: reuse [SHOP_SOURCE]'s bare
 * `HomeGraph.About` (a @NavGraph member that IS in the model but earns NO navigator — no edges/back-
 * edges/result-contract). The `@ViewModel`'s ctor wants a `nav` (by name convention), so codegen would
 * otherwise emit an unresolved `aboutNavigator()` factory call. Must be rejected with `[MV7]` instead.
 * Compile alongside `SHOP_SOURCE` (like the SC2 test).
 */
val MV7_NO_NAV_SOURCE = """
    package dev.gezgin.mv7

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.mvi.GezginMvi
    import dev.gezgin.mvi.annotation.ViewModel
    import dev.gezgin.shop.AboutNavigator
    import dev.gezgin.shop.HomeGraph.About
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow

    data class AboutState(val n: Int)
    sealed interface AboutIntent { data object Go : AboutIntent }
    data class AboutEffect(val m: String)

    // `About` is a bare @NavGraph member — no navigator is generated for it. The VM ctor wants a `nav`
    // (matched by name), so MV7 must fire (else an unresolved `aboutNavigator()` call is emitted).
    @ViewModel(About::class)
    class AboutViewModel(route: About, nav: AboutNavigator) :
        GezginMvi<AboutState, AboutIntent, AboutEffect> {
        override val uiState: StateFlow<AboutState> = MutableStateFlow(AboutState(0))
        override fun onIntent(intent: AboutIntent) {}
    }

    @Screen(About::class)
    @Composable
    fun AboutMviContent(state: AboutState, onIntent: (AboutIntent) -> Unit) {
    }
""".trimIndent()
