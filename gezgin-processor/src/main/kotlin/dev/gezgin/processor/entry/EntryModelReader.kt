package dev.gezgin.processor.entry

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import com.squareup.kotlinpoet.TypeName
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.codegen.NavigatorProbe
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel

private const val SCREEN_FQ = "dev.gezgin.core.annotation.Screen"
private const val DIALOG_FQ = "dev.gezgin.core.annotation.Dialog"
private const val BOTTOM_SHEET_FQ = "dev.gezgin.core.annotation.BottomSheet"
private const val FULLSCREEN_MODAL_FQ = "dev.gezgin.core.annotation.FullscreenModal"
private const val ROUTE_FQ = "dev.gezgin.core.Route"
private const val NO_BACK_FQ = "dev.gezgin.core.annotation.NoBack"

// Read modal contracts as string FQs; kind and no-back validation uses route supertypes.
private const val DIALOG_CONTRACT_FQ = "dev.gezgin.core.DialogContract"
private const val FULLSCREEN_MODAL_CONTRACT_FQ = "dev.gezgin.core.FullscreenModalContract"
private const val BOTTOM_SHEET_CONTRACT_FQ = "dev.gezgin.core.BottomSheetContract"

/**
 * The single presentation contract each kind reads at runtime (null for SCREEN — carries no
 * contract).
 */
private val CONTRACT_BY_KIND =
  mapOf(
    EntryKindModel.DIALOG to DIALOG_CONTRACT_FQ,
    EntryKindModel.FULLSCREEN_MODAL to FULLSCREEN_MODAL_CONTRACT_FQ,
    EntryKindModel.BOTTOM_SHEET to BOTTOM_SHEET_CONTRACT_FQ,
  )
private val ALL_KIND_CONTRACT_FQS = CONTRACT_BY_KIND.values.toSet()

// Read MVI types as strings to avoid a processor dependency on gezgin-mvi. Bottom sheets use
// GezginSheetController rather than Material3 SheetState.
private const val SHEET_CONTROLLER_FQ = "dev.gezgin.core.compose.GezginSheetController"
private const val TOP_BAR_FQ = "dev.gezgin.mvi.annotation.TopBar"
private const val BOTTOM_BAR_FQ = "dev.gezgin.mvi.annotation.BottomBar"
private const val FLOW_FQ = "kotlinx.coroutines.flow.Flow"
private const val FUNCTION1_FQ = "kotlin.Function1"
private const val UNIT_FQ = "kotlin.Unit"

// Reserve resolver and register-body names introduced by MviEntryCodegen. A colliding extra would
// produce a reference to the wrong generated local; state and onIntent are filtered beforehand.
private val RESERVED_EXTRA_NAMES = setOf("viewModel", "nav", "route", "vm")

private val KIND_BY_ANNOTATION_FQ =
  mapOf(
    SCREEN_FQ to EntryKindModel.SCREEN,
    DIALOG_FQ to EntryKindModel.DIALOG,
    BOTTOM_SHEET_FQ to EntryKindModel.BOTTOM_SHEET,
    FULLSCREEN_MODAL_FQ to EntryKindModel.FULLSCREEN_MODAL,
  )

/**
 * In MVI mode, reads every `@Screen`-, `@Dialog`-, `@BottomSheet`-, or `@FullscreenModal`-annotated
 * composable FUNCTION and validates it into an [EntryFunctionModel] list, reporting every violation
 * as a bracketed-code KSP error via [logger]. [read] never throws — like the graph validator, it
 * collects every violation in one pass and returns whether the read was clean alongside whatever
 * models DID resolve.
 *
 * **Two modes, selected by the composable's parameter shape — the annotation is unchanged:**
 * - **core-mode** `(route, nav)` — self-bind boilerplate (see [buildCoreEntry]); `SC2`-`SC10`
 *   below. A composable with `route`/`nav` only (or neither) stays here.
 * - **MVI-mode** `(state, onIntent[, extras])` — a composable whose params include BOTH a `state`
 *   and an `onIntent` (by name) is MVI-mode (see [buildMviEntry]); it pairs with a same-route,
 *   same-module `@MviViewModel` (`MV2`-`MV6`). A composable with ONLY `state` or ONLY `onIntent` is
 *   malformed and deliberately NOT special-cased — it falls through to core-mode's `SC3`
 *   unknown-param rejection (a half-MVI shape is a user error, not a third mode).
 *
 * **Route resolution (core-mode):** the annotation's `route=` is MANDATORY and names the
 * destination route directly (the inference-from-`route:`-param sentinel was removed — see
 * [resolveMandatoryRoute]). `Route::class` (the old sentinel) or a missing arg is `SC9`. A `route:`
 * param is still allowed — it carries route DATA into the composable — but its type MUST equal the
 * annotation's route (`SC10` on mismatch).
 *
 * **Route resolution (MVI-mode):** an MVI-mode content has NO `route:` param (the `state` param's
 * TYPE is NOT the route), so the route comes solely from the mandatory `@Screen(Route)` arg;
 * `Route::class`/ missing → `SC9`. The matched `@MviViewModel(Route)` binds the same route → the
 * pairing is explicit, not inferred by S/I type-match.
 *
 * **Nav wiring (`SC2`, core-mode):** a `nav:` param requires the resolved route to actually earn a
 * navigator ([NavigatorCodegen.hasNavigator]).
 *
 * **Unknown params (`SC3`, core-mode only):** V1 core-mode supports only `route:`/`nav:` params —
 * any other parameter is rejected. MVI-mode does NOT use `SC3`: its non-`{state,onIntent}` params
 * are recorded as extras, never rejected (`gezgin-mvi` scope).
 *
 * **Route type sanity (`SC5`):** the resolved type must implement `dev.gezgin.core.Route`.
 *
 * **Duplicate registration (`SC4`) / provide-name clash (`SC6`):** shared by both modes — two
 * kind-annotated functions resolving to the same route (`SC4`), or to the same `provideXEntry` name
 * in one package (`SC6`), is rejected.
 *
 * **Annotated `@Screen` overloads (`SC11`):** repeated `@Screen` annotations belong on one
 * composable declaration. Multiple annotated declarations with the same package and function name
 * are rejected, because MVI entry codegen calls content by simple name and Kotlin cannot select an
 * overload from the generated `(state, onIntent[, extras])` call.
 *
 * **MVI guardrails:**
 * - `MV2` — an MVI-mode content whose route has no `@MviViewModel` in THIS module (route-linked,
 *   not state/onIntent-type-matched).
 * - `MV3` — a `@MviViewModel` with no matching MVI-mode content in this module (symmetric to
 *   `MV2`).
 * - `MV5` — a matched content's `state`/`onIntent` types don't satisfy the VM's `GezginMvi<S,I,E>`
 *   contract: `state` ≠ `S` (compared by [TypeName], generics-preserving), or `onIntent` is not a
 *   `(I) -> Unit` function type.
 * - `MV6` — an `@EffectHandler` does not take a `Flow<E>` parameter.
 * - `MV7` — MVI-mode `SC2` parity: nav is wired (the matched VM's ctor wants `nav`, or the matched
 *   `@EffectHandler` takes a `nav` param) but an `@NoBack` route earns no navigator
 *   ([NavigatorCodegen.hasNavigator] false) — otherwise codegen would emit an unresolved
 *   `<x>Navigator()` factory call.
 *
 * **MVI guardrails:**
 * - `MV8` — a `controller: GezginSheetController` extra on a non-`BOTTOM_SHEET`-kind MVI content:
 *   role-injected `LocalGezginSheetController.current` `error()`s outside a `@BottomSheet`, so
 *   codegen would emit compile-clean code that crashes at first render. Classified as a role extra
 *   ONLY on `BOTTOM_SHEET`.
 * - `MV10` — a Problem-2 content extra whose name collides with an emitted identifier
 *   (`viewModel`/`nav`/`route`/`vm`), which would produce broken generated code.
 *
 * **MVI guardrails:**
 * - `MV11` — an `@EffectHandler` whose signature isn't a subset of `{Flow<E>, nav: XNavigator}`: an
 *   EXTRA param (e.g. `SnackbarHostState` — no wiring path, unlike content's extra resolvers) or a
 *   `nav` param whose RESOLVED type isn't the matched route's `${x}Navigator`. Both would otherwise
 *   emit compile-broken code inside `GezginMviEntries.kt`; rejected up front with an actionable
 *   message.
 * - `MV12` — a plain `@HiltViewModel` (no assisted factory) bound to a route that CARRIES DATA
 *   (parameterized ctor). Nav3 has no path that writes the route into `SavedStateHandle`, so such a
 *   VM silently reads null route data; rejected with a "use HILT_ASSISTED / parameterless route"
 *   message.
 *
 * (`MV1`/`MV4` — `@MviViewModel` must implement `GezginMvi`, and no two `@MviViewModel`s per route
 * — live in [dev.gezgin.processor.mvi.ViewModelModelReader], whose output [vmModels] this reader
 * consumes.)
 */
internal class EntryModelReader(
  private val resolver: Resolver,
  private val logger: KSPLogger,
  private val model: GraphModel,
  /** Routes a `@ScreenWrapper` was bound to; their content parameters are the wrapper's job. */
  private val wrappedRoutes: Set<String> = emptySet(),
) {

  private val graphsByFq: Map<String, GraphModelNode> = model.graphs.associateBy { it.fqName }
  private val routesByFq: Map<String, RouteModel> = model.routes.associateBy { it.fqName }

  private var ok = true
  private val seenRouteFqs =
    mutableMapOf<String, String>() // routeFq -> first function's simple name
  private val seenProvideNames =
    mutableMapOf<Pair<String, String>, String>() // (package, x) -> first function's simple name

  fun read(): Pair<List<EntryFunctionModel>, Boolean> {
    val entries =
      KIND_BY_ANNOTATION_FQ.flatMap { (annotationFq, kind) ->
        val functions =
          resolver
            .getSymbolsWithAnnotation(annotationFq)
            .filterIsInstance<KSFunctionDeclaration>()
            .distinctBy { it.declarationIdentity() }
            .toList()
        val overloadedScreenNames =
          if (annotationFq == SCREEN_FQ) {
            functions
              .groupBy { it.packageName.asString() to it.simpleName.asString() }
              .filterValues { it.size > 1 }
              .also { overloaded ->
                overloaded.forEach { (key, declarations) ->
                  val (packageName, functionName) = key
                  val routes =
                    declarations
                      .flatMap { fn ->
                        fn.annotations
                          .filter { it.fqName() == SCREEN_FQ }
                          .map { annotation ->
                            annotation.classArg("route")?.declaration?.qualifiedName?.asString()
                              ?: "<unresolved>"
                          }
                      }
                      .distinct()
                  error(
                    "SC11",
                    "@Screen function overloads are unsupported: $packageName.$functionName is annotated " +
                      "for route(s) ${routes.joinToString()}; declare one @Screen composable and repeat " +
                      "@Screen on that declaration instead",
                  )
                }
              }
              .keys
          } else {
            emptySet()
          }
        functions
          .filterNot {
            it.packageName.asString() to it.simpleName.asString() in overloadedScreenNames
          }
          .flatMap { fn ->
            fn.annotations
              .filter { it.fqName() == annotationFq }
              .mapNotNull { annotation -> buildEntry(fn, annotation, kind) }
          }
      }

    return entries to ok
  }

  /** A wrapped route's content parameters belong to the wrapper binder, not to this reader. */
  private fun buildEntry(
    fn: KSFunctionDeclaration,
    annotation: KSAnnotation,
    kind: EntryKindModel,
  ): EntryFunctionModel? {
    val routeFq = annotation.classArg("route")?.fqOf()
    val wrapped = routeFq != null && routeFq in wrappedRoutes
    return buildCoreEntry(fn, annotation, kind, wrapped = wrapped)
  }

  // region Core-mode (UNCHANGED)

  private fun buildCoreEntry(
    fn: KSFunctionDeclaration,
    annotation: KSAnnotation,
    kind: EntryKindModel,
    wrapped: Boolean,
  ): EntryFunctionModel? {
    val fnName = fn.simpleName.asString()
    val params = fn.parameters

    val routeParam = params.firstOrNull { it.name?.asString() == "route" }
    val navParam = params.firstOrNull { it.name?.asString() == "nav" }
    val unknownParams = params.filter { it.name?.asString() !in setOf("route", "nav") }

    if (unknownParams.isNotEmpty() && !wrapped) {
      error(
        "SC3",
        "$fnName has unsupported parameter(s): " +
          unknownParams.joinToString { it.name?.asString().orEmpty() } +
          "; core-mode only supports route:/nav: (resolver mechanism is MVI-mode only)",
      )
      return null
    }

    val resolvedRouteType = resolveMandatoryRoute(annotation, fnName) ?: return null

    // A `route:` param stays legal — it carries route DATA into the composable — but its type MUST
    // equal the mandatory annotation route; a mismatch would bind the wrong route (copy-paste bug).
    val routeParamType = routeParam?.type?.resolve()
    if (routeParamType != null && !routeParamType.isError) {
      val routeFq = resolvedRouteType.declaration.qualifiedName?.asString()
      val paramFq = routeParamType.declaration.qualifiedName?.asString()
      if (routeFq != paramFq) {
        error(
          "SC10",
          "$fnName: route: parameter type (${routeParamType.declaration.simpleName.asString()}) must equal the " +
            "annotation route (${resolvedRouteType.declaration.simpleName.asString()}); the route: param carries " +
            "route DATA, so a mismatch would bind the wrong route (fix the annotation route or the route: param type)",
        )
        return null
      }
    }

    val routeDecl = resolvedRouteType.declaration as? KSClassDeclaration
    val implementsRoute =
      routeDecl != null &&
        (routeDecl.qualifiedName?.asString() == ROUTE_FQ ||
          routeDecl.getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == ROUTE_FQ })
    if (!implementsRoute) {
      error(
        "SC5",
        "$fnName: route type (${resolvedRouteType.declaration.qualifiedName?.asString()}) " +
          "does not implement dev.gezgin.core.Route",
      )
      return null
    }

    val routeFq = requireNotNull(routeDecl.qualifiedName?.asString())
    val routeModel = routesByFq[routeFq]

    val previousOwner = seenRouteFqs[routeFq]
    if (previousOwner != null) {
      error(
        "SC4",
        "route ${routeFq.substringAfterLast('.')} is registered by multiple functions: $previousOwner, $fnName",
      )
      return null
    }
    seenRouteFqs[routeFq] = fnName

    val packageName = fn.packageName.asString()
    val x = NavigatorCodegen.navigatorX(routeDecl.simpleName.asString())

    // The shared helper performs the statically decidable `SC8` kind-contract and `SC7`
    // `@NoBack`-modal checks.
    if (!checkKindContractAndNoBack(fnName, routeDecl, kind)) return null

    if (navParam != null) {
      // Use the shared identity-verified classpath probe for cross-module routes. A missing
      // navigator then produces `SC2` instead of an unresolved generated reference.
      val hasNavigator =
        NavigatorProbe.routeEarnsNavigator(
          resolver,
          routeModel,
          graphsByFq,
          routeDecl.packageName.asString(),
          x,
          routeFq,
        )
      if (!hasNavigator) {
        error(
          "SC2",
          "$fnName: nav: parameter was requested, but target route (${routeFq.substringAfterLast('.')}) " +
            "has no navigator (@NoBack and no declared navigation/result operation)",
        )
        return null
      }
      // The `nav:` parameter type must be the route's own `${x}Navigator`; otherwise the
      // generated `XScreen(route, nav)` call site would type-mismatch inside GezginEntries.kt
      // (a confusing generated-code error instead of a clean [`SC2`]). Same technique as
      // A same-module navigator type isn't generated yet in this KSP round (its FQ resolves to
      // an error type), so an unresolved type is accepted by the NAME `nav` and only a RESOLVED,
      // wrong-typed param is rejected.
      val navParamType = navParam.type.resolve()
      val navParamFq = navParamType.declaration.qualifiedName?.asString()
      val expectedNavigatorFq = "${routeDecl.packageName.asString()}.${x}Navigator"
      if (!navParamType.isError && navParamFq != expectedNavigatorFq) {
        error(
          "SC2",
          "$fnName: nav: parameter type ($navParamFq) is not the expected navigator type " +
            "($expectedNavigatorFq); the generated ${x}Screen(route, nav) call would fail with " +
            "a type mismatch in GezginEntries.kt. Use `nav: ${x}Navigator` (§10.1)",
        )
        return null
      }
    }

    // Reject two entry functions that resolve to the same `provideXEntry` name in one package,
    // before KotlinPoet emits conflicting overloads.
    val provideKey = packageName to x
    val previousProvideOwner = seenProvideNames[provideKey]
    if (previousProvideOwner != null) {
      error(
        "SC6",
        "$packageName generates provide${x}Entry() from multiple functions: " +
          "$previousProvideOwner, $fnName; route names resolve to the same derived 'X' (${x})",
      )
      return null
    }
    seenProvideNames[provideKey] = fnName

    return EntryFunctionModel(
      packageName = packageName,
      functionSimpleName = fnName,
      kind = kind,
      routeFq = routeFq,
      hasRouteParam = routeParam != null,
      hasNavParam = navParam != null,
      routeInModel = routeModel != null,
      // The factory `RawNavigator.xNavigator()` lives in the route DECLARATION's package
      // (that's where NavigatorCodegen emits it). Reading it off `routeDecl` — always
      // resolvable via KSP regardless of which module the route was compiled in — is what
      // lets EntryCodegen qualify the factory import cross-module (a feature module's own
      // model has no graphs, so its `targetPackage` is empty and useless here).
      routePackageName = routeDecl.packageName.asString(),
      // Read @NoBack directly from the declaration because the local model omits cross-module
      // routes while KSP still resolves their annotations.
      noBack = routeDecl.hasAnnotation(NO_BACK_FQ),
      x = x,
    )
  }

  // endregion

  /**
   * `SC8` (kind↔contract mismatch) + `SC7` (@NoBack × modal) — both STATICALLY decidable and shared
   * by core-mode and MVI-mode. Everything they need — the `kind` (annotation arg), the route's
   * supertypes, and its `@NoBack` — is visible in one compilation unit regardless of which module
   * the route was compiled in ([getAllSuperTypes] + `hasAnnotation` are cross-module-safe, exactly
   * like `noBack`). Returns `true` if clean; on the first violation it reports the bracketed error
   * and returns `false` (the caller emits no model). `SC8` is checked BEFORE `SC7` so a
   * wrong-contract modal reports the more specific mismatch rather than the (also-true)
   * missing-matching-contract `SC7`.
   *
   * **`SC8`** — the modal presentation contract a route implements MUST match its kind annotation.
   * An `@FullscreenModal` route implementing `DialogContract` (or a `@Screen` implementing ANY kind
   * contract) is read at runtime via `route as? XContract` for the KIND's contract only → the wrong
   * contract casts to `null` → the route's overrides (e.g. A deliberately non-dismissable modal)
   * are SILENTLY dropped to type-defaults, with no diagnostic. A route may also implement TWO kind
   * contracts (only the kind's is ever read); every non-matching one is a mismatch.
   *
   * **`SC7`** — a `@NoBack` modal without its matching contract is guaranteed to fail the runtime
   * guard: dialog kinds default `dismissOnBackPress=true`; bottom sheets additionally default
   * `sheetGesturesEnabled=true`. This statically known missing-contract case is rejected by KSP. A
   * route that implements its matching contract is accepted structurally because getter results are
   * runtime route-instance values; `EntryAdapter` validates the resolved values when the entry is
   * built.
   */
  private fun checkKindContractAndNoBack(
    fnName: String,
    routeDecl: KSClassDeclaration,
    kind: EntryKindModel,
  ): Boolean {
    val routeSimple = routeDecl.simpleName.asString()
    val implementedContracts =
      routeDecl
        .getAllSuperTypes()
        .mapNotNull { it.declaration.qualifiedName?.asString() }
        .filter { it in ALL_KIND_CONTRACT_FQS }
        .toSet()
    val expectedContract = CONTRACT_BY_KIND[kind] // SCREEN has no presentation contract.

    // `SC8`: an implemented presentation contract that does not match this kind would be ignored.
    val mismatched = implementedContracts.filter { it != expectedContract }
    if (mismatched.isNotEmpty()) {
      val mismatch = mismatched.first().substringAfterLast('.')
      error(
        "SC8",
        "$fnName: route $routeSimple is @${kind.name}-kind but implements $mismatch; kind and " +
          "presentation contract must match (@Dialog↔DialogContract, @FullscreenModal↔" +
          "FullscreenModalContract, @BottomSheet↔BottomSheetContract). The adapter reads only the " +
          "contract for the kind, so the wrong contract becomes null through `route as? …` and overrides " +
          "(for example dismissOnClickOutside=false) are silently dropped. Fix the kind or remove the contract (§7)",
      )
      return false
    }

    // `SC7` — @NoBack × modal (route's own @NoBack, cross-module-safe like `noBack`).
    if (routeDecl.hasAnnotation(NO_BACK_FQ)) {
      when (kind) {
        EntryKindModel.BOTTOM_SHEET -> {
          if (expectedContract !in implementedContracts) {
            error(
              "SC7",
              "$fnName: @NoBack + @BottomSheet, but route $routeSimple BottomSheetContract is not " +
                "implemented; dismissOnBackPress and sheetGesturesEnabled both default to TRUE " +
                "(statically known), so the first navigation would definitely fail the runtime " +
                "guard. Implement BottomSheetContract with getter-only overrides for both values, " +
                "or remove @NoBack (§7)",
            )
            return false
          }
        }
        EntryKindModel.DIALOG,
        EntryKindModel.FULLSCREEN_MODAL -> {
          if (expectedContract !in implementedContracts) {
            error(
              "SC7",
              "$fnName: @NoBack + @${kind.name}, but route $routeSimple " +
                "${expectedContract!!.substringAfterLast('.')} is not implemented; " +
                "dismissOnBackPress defaults to TRUE (statically known), which conflicts with @NoBack, " +
                "so the first navigation would definitely crash at runtime. Add `$routeSimple : …, " +
                "${expectedContract.substringAfterLast('.')} { override val " +
                "dismissOnBackPress get() = false }` to the route, or remove @NoBack (§7)",
            )
            return false
          }
        }
        EntryKindModel.SCREEN -> Unit // `@NoBack` is valid on a terminal screen.
      }
    }
    return true
  }

  private fun KSType.fqOf(): String = declaration.qualifiedName?.asString() ?: toString()

  /** Deduplicates repeated resolver emissions without collapsing distinct overload declarations. */
  private fun KSFunctionDeclaration.declarationIdentity(): String {
    val signature = buildString {
      append(qualifiedName?.asString() ?: simpleName.asString())
      append(parameters.joinToString(prefix = "(", postfix = ")") { it.type.toString() })
    }
    val fileLocation = location as? FileLocation
    return if (fileLocation != null) {
      "${fileLocation.filePath}:${fileLocation.lineNumber}:$signature"
    } else {
      signature
    }
  }

  private fun KSAnnotated.hasAnnotation(fq: String): Boolean = annotations.any { it.fqName() == fq }

  private fun KSAnnotation.fqName(): String? =
    annotationType.resolve().declaration.qualifiedName?.asString()

  private fun KSAnnotation.arg(name: String): KSValueArgument? =
    arguments.firstOrNull { it.name?.asString() == name }
      ?: defaultArguments.firstOrNull { it.name?.asString() == name }

  private fun KSAnnotation.classArg(name: String): KSType? = arg(name)?.value as? KSType

  /**
   * Reads the kind annotation's now-MANDATORY `route` arg (shared by both modes). The
   * `Route::class` inference sentinel was removed — a bare `Route::class` (or a missing arg,
   * defensively) names no concrete destination and is rejected as `SC9`. Returns the resolved route
   * KSType, or null (after reporting `SC9`) when it is sentinel/absent.
   */
  private fun resolveMandatoryRoute(annotation: KSAnnotation, fnName: String): KSType? {
    val routeType = annotation.classArg("route")
    val isSentinel =
      routeType == null || routeType.declaration.qualifiedName?.asString() == ROUTE_FQ
    if (isSentinel) {
      error(
        "SC9",
        "$fnName: route must be given explicitly — the `Route::class` sentinel was removed; name the target " +
          "route (e.g. `@Screen(FeedScreenRoute::class)`)",
      )
      return null
    }
    return routeType
  }

  private fun error(code: String, message: String) {
    logger.error("[$code] $message")
    ok = false
  }
}
