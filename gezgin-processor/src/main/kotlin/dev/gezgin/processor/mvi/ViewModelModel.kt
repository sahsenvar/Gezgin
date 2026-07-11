package dev.gezgin.processor.mvi

import com.squareup.kotlinpoet.TypeName

/**
 * The dependency-injection framework a `@MviViewModel` class opts into (§10.1 DI-detection, Faz-5.0 spike),
 * detected by [ViewModelModelReader] from the VM class's own annotations — read as **string FQNs**, so
 * `gezgin-processor` gains NO compile dependency on Hilt or Koin (mirrors the `dev.gezgin.mvi.*` reads).
 * Faz 5.2's `MviEntryCodegen` branches on this to emit the correct default `viewModel` resolver:
 *
 * - [HILT_ASSISTED] — `@HiltViewModel(assistedFactory = VM.Factory::class)` + `@AssistedInject` ctor
 *   with `@Assisted` params → `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(args, nav) })`
 *   (Android-only; the factory FQ is [ViewModelModel.assistedFactoryFq]).
 * - [HILT_PLAIN] — `@HiltViewModel` with NO assisted factory → `hiltViewModel<VM>()`; Gezgin supplies
 *   NOTHING and Nav3 has no path that writes the route into `SavedStateHandle`, so this VM receives NO
 *   route data (a parameterized route is rejected with `MV12`, [EntryModelReader]). It always has a
 *   default resolver and never wires nav; use [HILT_ASSISTED] for a route-data-carrying screen.
 * - [KOIN] — `@KoinViewModel`, `@InjectedParam` ctor params → `koinViewModel { parametersOf(args, nav) }`.
 * - [ANDROIDX] — no DI annotation at all → `viewModel(factory = viewModelFactory { initializer { VM(...) } })`
 *   (the shape proven to compile in `gezgin-mvi`'s `CounterMvi` fixture); every ctor param is
 *   Gezgin-supplied, so the default is emitted only when they are all `route`/`nav`-typed.
 */
internal enum class VmDiKind { HILT_ASSISTED, HILT_PLAIN, KOIN, ANDROIDX }

/**
 * One primary-constructor parameter of a `@MviViewModel` class, captured by [ViewModelModelReader] for
 * Faz-5.2 DI-detection (§10.1). [MviEntryCodegen][dev.gezgin.processor.codegen.MviEntryCodegen]
 * classifies each into `route`/`nav`/other by [name] and [typeFq] to decide whether a default resolver
 * can be emitted (only when every DI-relevant param is `route`- or `nav`-typed).
 *
 * **Why no [TypeName] here (unlike S/I/E and extras):** codegen never emits a ctor param's type. The
 * `route`/`nav` types are RECONSTRUCTED from the entry (`args: XRoute`, `nav: XNavigator`), and an
 * "other" param only ever forces the `viewModel` param to become required — its type is never printed.
 * Capturing only [typeFq] also sidesteps `toTypeName()` throwing on a same-module `nav: XNavigator` whose
 * generated navigator type is not yet resolvable in the KSP round that reads it (nav is matched by
 * [name] `"nav"` in that case — see `MviEntryCodegen`'s classification).
 */
internal data class VmCtorParam(
    val name: String,
    /** Best-effort flattened FQ of the param type; may be unresolved (an as-yet-ungenerated navigator). */
    val typeFq: String,
    /** `true` if annotated `@dagger.assisted.Assisted` (Hilt) or `@org.koin.core.annotation.InjectedParam` (Koin). */
    val diAnnotated: Boolean,
    /**
     * `KSType.isError` — the param TYPE failed to resolve (typically a same-module `nav: XNavigator` whose
     * navigator class isn't generated yet in this KSP round). Faz-5 recheck MJ1: the `nav` NAME is a
     * fallback classifier ONLY when the type is unresolvable — a RESOLVED non-navigator type (e.g. a Koin
     * `@InjectedParam nav: AnalyticsTracker`) must classify by TYPE (OTHER), not be hijacked by the name.
     */
    val isError: Boolean = false,
    /**
     * `KSValueParameter.hasDefault` — the param has a Kotlin default value. Faz-5 recheck MN4: a
     * defaulted OTHER param need NOT be supplied by Gezgin (the ctor call omits it), so it must not force
     * the `viewModel` resolver to become required.
     */
    val hasDefault: Boolean = false,
)

/**
 * One `@MviViewModel(Route::class)`-annotated class that additionally implements
 * `dev.gezgin.mvi.GezginMvi<S, I, E>` (Faz 5.1, spec §10/§10.1), resolved and validated by
 * [ViewModelModelReader] into everything Faz 5.2's MVI-mode `provideXEntry` codegen needs.
 *
 * **Route linking (§10.1):** the VM binds itself to a route via `@MviViewModel(Route::class)`; the
 * matching stateless `@Screen(Route::class)` content binds to the SAME route explicitly. [routeFq]
 * is the join key between this model and an [dev.gezgin.processor.entry.EntryFunctionModel] whose
 * `mvi` descriptor points back here (see the route-linking note on [ViewModelModelReader]).
 *
 * **S/I/E — FQ + [TypeName] pair per generic arg (load-bearing).** Each of state/intent/effect is
 * captured BOTH as a flattened fully-qualified string (`…FooState`, for validation/comparison/dump)
 * AND as a KotlinPoet [TypeName] (for 5.2 codegen — `StateFlow<S>`, VM references, etc.). The FQ
 * string alone flattens generics (`List<String>` → `kotlin.collections.List`); the [TypeName]
 * preserves the full parameterization. Capturing both avoids re-introducing the "generic-flattening"
 * bug the Sample Showcase phase fixed for ctor params (mirrors [dev.gezgin.processor.model.ParamModel]).
 */
internal data class ViewModelModel(
    val vmFq: String,
    val vmSimpleName: String,
    /** The VM class's own package — used for the same-module `@MviViewModel`/`@Screen` cross-check. */
    val packageName: String,
    /** `@MviViewModel(route = …)` — the route this VM (and its matching `@Screen` content) binds to. */
    val routeFq: String,
    val stateTypeFq: String,
    val stateTypeName: TypeName,
    val intentTypeFq: String,
    val intentTypeName: TypeName,
    val effectTypeFq: String,
    val effectTypeName: TypeName,
    /** DI framework the VM opts into (§10.1) — drives which default `viewModel` resolver 5.2 emits. */
    val di: VmDiKind,
    /** The `@HiltViewModel(assistedFactory = …)` type FQ for [VmDiKind.HILT_ASSISTED]; else null. */
    val assistedFactoryFq: String?,
    /** The VM's primary-constructor params (empty if none) — 5.2's route/nav/other DI classification. */
    val ctorParams: List<VmCtorParam>,
)
