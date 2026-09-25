package dev.gezgin.processor.entry

/** Core-mode kind that mirrors `dev.gezgin.core.compose.EntryKind` one-to-one. */
internal enum class EntryKindModel {
  SCREEN,
  DIALOG,
  BOTTOM_SHEET,
  FULLSCREEN_MODAL,
}

/**
 * One `@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal`-annotated composable function, resolved
 * and validated in core mode into everything `EntryCodegen` needs to emit a `provideXEntry()` — no
 * further KSP lookups happen at codegen time.
 */
internal data class EntryFunctionModel(
  /** The composable function's own package — `provideXEntry` is emitted INTO this same package. */
  val packageName: String,
  /** The composable function's simple name (e.g. `OrderChainScreen`). */
  val functionSimpleName: String,
  val kind: EntryKindModel,
  /**
   * Resolved route fqName — either the annotation's explicit `route=` or the `route:` param's type.
   */
  val routeFq: String,
  val hasRouteParam: Boolean,
  val hasNavParam: Boolean,
  /**
   * `true` only when [routeFq] resolved to a route the model actually knows about (same module).
   */
  val routeInModel: Boolean,
  /**
   * The package the resolved route DECLARATION lives in — i.e. where
   * [dev.gezgin.processor.codegen.NavigatorCodegen] emits the `RawNavigator.xNavigator()` factory
   * (the nav-topology target package). Read per-entry from the route declaration itself (NOT from
   * this module's [dev.gezgin.processor.codegen.TopologyCodegen.targetPackage]), so a cross-module
   * feature — whose own [dev.gezgin.processor.model.GraphModel] has NO graphs and hence an empty
   * target package — still qualifies the factory import against the nav module's package.
   */
  val routePackageName: String,
  val noBack: Boolean,
  /**
   * `X` derivation for both the entry function name (`provideXEntry`) and the navigator factory.
   */
  val x: String,
  /**
   * The `@ScreenWrapper` bound to this entry's route, or `null` when no wrapper is in scope. A
   * bound entry is emitted by [dev.gezgin.processor.codegen.WrapperEntryCodegen] and is excluded
   * from the no-wrapper codegen, so a route is never registered twice.
   */
  val wrapper: dev.gezgin.processor.wrapper.WrapperBindingModel? = null,
  /**
   * Non-null when the route is a callback route: the composable's parameters in declaration order,
   * each bound to `route`, `nav`, a route field or a guarded route callback.
   */
  val callbackArgs: List<CallbackEntryArg>? = null,
  /** The route's `@OnDismiss` field, wired as the container-dismissal hook. */
  val onDismissField: String? = null,
)

/** One composable argument of a callback-route entry. */
internal data class CallbackEntryArg(val name: String, val source: CallbackArgSource)

internal sealed interface CallbackArgSource {
  data object RouteInstance : CallbackArgSource

  data object Nav : CallbackArgSource

  /** A plain route field, forwarded as `route.<name>`. */
  data object Field : CallbackArgSource

  /** A route callback, forwarded behind the `isOnStack` guard. */
  data class Callback(val arity: Int) : CallbackArgSource
}
