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
    val noBack: Boolean,
    /** `X` derivation for both the entry function name (`provideXEntry`) and the navigator factory. */
    val x: String,
)
