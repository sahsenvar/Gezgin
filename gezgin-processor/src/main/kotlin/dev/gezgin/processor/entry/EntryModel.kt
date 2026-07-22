package dev.gezgin.processor.entry

import com.squareup.kotlinpoet.TypeName
import dev.gezgin.processor.mvi.ViewModelModel

/** Core-mode kind that mirrors `dev.gezgin.core.compose.EntryKind` one-to-one. */
internal enum class EntryKindModel {
  SCREEN,
  DIALOG,
  BOTTOM_SHEET,
  FULLSCREEN_MODAL,
}

/**
 * One content-composable parameter that is NEITHER `state` NOR `onIntent` in an MVI-mode `@Screen`
 * (extra-parameter support). Carries both the flattened [typeFq] (for classification/dump) and the
 * KotlinPoet [typeName] (for codegen), same FQ+TypeName rationale as [ViewModelModel]'s S/I/E.
 */
internal data class MviExtraParam(val name: String, val typeFq: String, val typeName: TypeName)

/** One migration-only route-bound chrome provider resolved before code generation. */
internal data class MviChromeProviderModel(val functionSimpleName: String, val packageName: String)

/**
 * The MVI-mode descriptor attached to an [EntryFunctionModel] whose content composable is shaped
 * `(state, onIntent[, extras])` (as opposed to core-mode's `(route, nav)`). Present only on
 * MVI-mode entries; a core-mode entry carries `mvi = null` and is emitted by
 * [dev.gezgin.processor.codegen.EntryCodegen] exactly as before (zero behavior change). The MVI
 * codegen branches on this being non-null.
 *
 * [vm] is the matched `@MviViewModel` (linked by shared route — see `EntryModelReader`); its
 * `S/I/E` types drove the content/effect validation (`MV5`/`MV6`) that produced this descriptor.
 *
 * **Extra-parameter split:** [roleExtraParams] are Gezgin-role-provided (currently only a
 * `dev.gezgin.core.compose.GezginSheetController`, Local-injected via
 * `LocalGezginSheetController`); [resolverExtraParams] are truly-unknown content params that 5.2
 * turns into `@Composable -> T` resolver params on `provideXEntry`.
 */
internal data class MviEntryModel(
  val vm: ViewModelModel,
  /** Matched route-bound effect composable's simple name, or null if none. */
  val effectFunSimpleName: String?,
  /**
   * The effect function's own package (may differ from the content's), if [effectFunSimpleName] !=
   * null.
   */
  val effectFunPackageName: String?,
  /**
   * The matched effect function's `Flow<E>` parameter NAME (e.g. `effects`), so 5.2 can emit the
   * effect call with NAMED args (`XEffects(<flowName> = vm.effects[, nav = nav])`) — kills the
   * positional-order hazard when a user declares `fun XEffects(nav: …, effects: Flow<E>)`. Null if
   * no effect.
   */
  val effectFlowParamName: String?,
  /**
   * The matched effect function declares an optional `nav`-named param (5.2 wires the navigator
   * into it).
   */
  val effectHasNavParam: Boolean,
  /** The route-explicit handler declares `onIntent: (I) -> Unit`; codegen wires `vm::onIntent`. */
  val effectHasIntentParam: Boolean,
  val topBar: MviChromeProviderModel?,
  val bottomBar: MviChromeProviderModel?,
  val roleExtraParams: List<MviExtraParam>,
  val resolverExtraParams: List<MviExtraParam>,
)

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
   * MVI-mode descriptor when this entry's content is `(state, onIntent[, extras])`; `null` for a
   * core-mode `(route, nav)` entry. Core-mode codegen ignores it entirely — the MVI codegen keys
   * off `mvi != null` to emit the VM-driven `provideXEntry` instead.
   */
  val mvi: MviEntryModel? = null,
)
