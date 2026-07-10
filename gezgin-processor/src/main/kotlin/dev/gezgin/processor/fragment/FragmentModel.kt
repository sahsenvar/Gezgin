package dev.gezgin.processor.fragment

/**
 * One `@FragmentScreen(Route::class)`-annotated legacy `androidx.fragment.app.Fragment` (spec
 * §11/§11.1/§11.2), resolved and validated by [FragmentModelReader] into everything Task 6.2's
 * `FragmentEntryCodegen` needs to emit a `provideXEntry()` that hosts the Fragment via
 * `androidx.fragment.compose.AndroidFragment` (Task 6.0 report §"Binding decision for 6.1 / 6.2").
 *
 * This is a THIRD kind of entry model — distinct from core-mode's `EntryFunctionModel` (a
 * `(route, nav)`/`(state, onIntent)` composable FUNCTION) and Faz-5's `ViewModelModel` (a `@MviViewModel`
 * VM class). A `@FragmentScreen` binds a Fragment CLASS to a route with no composable content at all;
 * the route/navigator reach it through the `gezginArgs`/`gezginNav` delegates (Task 6.2), not a ctor.
 *
 * All `androidx.fragment.*` symbols are read as **string FQNs** — `gezgin-processor` has (and per §11.2
 * will keep) NO compile dependency on `androidx.fragment`, exactly like the `dev.gezgin.mvi.*` reads.
 */
internal data class FragmentEntryModel(
    /** The annotated Fragment class's fully-qualified name (e.g. `com.app.OrderChainFragment`). */
    val fragmentFq: String,
    /** The annotated Fragment class's simple name (e.g. `OrderChainFragment`). */
    val fragmentSimpleName: String,
    /**
     * The Fragment class's OWN package — where Task 6.2's `FragmentEntryCodegen` emits `provideXEntry`
     * (exactly like core-mode's [dev.gezgin.processor.entry.EntryFunctionModel.packageName]).
     */
    val packageName: String,
    /** `@FragmentScreen(route = …)` — the leaf route this Fragment is registered under. */
    val routeFq: String,
    /**
     * The package the resolved route DECLARATION lives in — where
     * [dev.gezgin.processor.codegen.NavigatorCodegen] emits the `RawNavigator.xNavigator()` factory.
     * Read per-entry from the route declaration itself (NOT this module's target package), so a
     * cross-module Fragment whose route was compiled in the central nav module still qualifies the
     * navigator-factory import correctly — same reasoning as
     * [dev.gezgin.processor.entry.EntryFunctionModel.routePackageName].
     */
    val routePackageName: String,
    /**
     * `true` iff the route DECLARATION itself carries `@NoBack`. Read directly off the route decl (not
     * a same-module model lookup), the cross-module-safe pattern from
     * [dev.gezgin.processor.entry.EntryModelReader] (`noBack = routeDecl.hasAnnotation(NO_BACK_FQ)`) —
     * a cross-module route's `@NoBack` is always visible on its KSP declaration regardless of module.
     */
    val noBack: Boolean,
    /**
     * `X` derivation (via [dev.gezgin.processor.codegen.NavigatorCodegen.navigatorX] on the route's
     * simple name) for both the entry function name (`provideXEntry`) and the navigator factory
     * (`RawNavigator.xNavigator()`) — identical derivation to core-mode / MVI-mode entries.
     */
    val x: String,
)
