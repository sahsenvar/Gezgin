package dev.gezgin.processor.entry

/** Core-mode kind (§3.2/§10.1) — mirrors `dev.gezgin.core.compose.EntryKind` 1:1. */
enum class EntryKindModel { SCREEN, DIALOG, BOTTOM_SHEET, FULLSCREEN_MODAL }

/**
 * One `@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal`-annotated composable function, resolved
 * and validated (Task 3.4, spec §10.1/§12/§14 core-mode slice) into everything [EntryCodegen] needs
 * to emit a `provideXEntry()` — no further KSP lookups happen at codegen time.
 */
data class EntryFunctionModel(
    /** The composable function's own package — `provideXEntry` is emitted INTO this same package. */
    val packageName: String,
    /** The composable function's simple name (e.g. `OrderChainScreen`). */
    val functionSimpleName: String,
    val kind: EntryKindModel,
    /** Resolved route fqName — either the annotation's explicit `route=` or the `route:` param's type. */
    val routeFq: String,
    val hasRouteParam: Boolean,
    val hasNavParam: Boolean,
    /** `true` only when [routeFq] resolved to a route the model actually knows about (same module). */
    val routeInModel: Boolean,
    /**
     * The package the resolved route DECLARATION lives in — i.e. where [dev.gezgin.processor.codegen.NavigatorCodegen]
     * emits the `RawNavigator.xNavigator()` factory (the nav-topology target package). Read per-entry
     * from the route declaration itself (NOT from this module's [dev.gezgin.processor.codegen.TopologyCodegen.targetPackage]),
     * so a cross-module feature — whose own [dev.gezgin.processor.model.GraphModel] has NO graphs and
     * hence an empty target package — still qualifies the factory import against the nav module's package.
     */
    val routePackageName: String,
    val noBack: Boolean,
    /** `X` derivation for both the entry function name (`provideXEntry`) and the navigator factory. */
    val x: String,
)
