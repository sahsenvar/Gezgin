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
import com.squareup.kotlinpoet.ksp.toTypeName
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.codegen.NavigatorProbe
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel
import dev.gezgin.processor.mvi.EFFECT_HANDLER_FQ
import dev.gezgin.processor.mvi.ViewModelModel
import dev.gezgin.processor.mvi.VmDiClassifier
import dev.gezgin.processor.mvi.VmDiKind

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
  private val vmModels: List<ViewModelModel> = emptyList(),
) {

  private val graphsByFq: Map<String, GraphModelNode> = model.graphs.associateBy { it.fqName }
  private val routesByFq: Map<String, RouteModel> = model.routes.associateBy { it.fqName }
  private val vmByRouteFq: Map<String, ViewModelModel> = vmModels.associateBy { it.routeFq }

  private var ok = true
  private val seenRouteFqs =
    mutableMapOf<String, String>() // routeFq -> first function's simple name
  private val seenProvideNames =
    mutableMapOf<Pair<String, String>, String>() // (package, x) -> first function's simple name
  // Routes whose `@MviViewModel` has matching content; used by the `MV3` validation.
  private val matchedVmRoutes = mutableSetOf<String>()
  private val declaredMviScreenRoutes = mutableSetOf<String>()

  fun read(): Pair<List<EntryFunctionModel>, Boolean> {
    val effectFuns = readEffectFuns()
    val chromeFuns = readChromeFuns()

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
              .mapNotNull { annotation -> buildEntry(fn, annotation, kind, effectFuns, chromeFuns) }
          }
      }

    effectFuns
      .filter { it.routeFq !in declaredMviScreenRoutes }
      .forEach { effect ->
        error(
          "MV15",
          "@EffectHandler ${effect.simpleName} targets route ${effect.routeFq}, but that route has no " +
            "@Screen(state, onIntent) declaration in this module",
        )
      }

    chromeFuns
      .filter { it.routeFq !in declaredMviScreenRoutes }
      .forEach { chrome ->
        error(
          "MV21",
          "@${chrome.kind.annotationName} ${chrome.simpleName} targets route ${chrome.routeFq}, but that " +
            "route has no @Screen(state, onIntent) declaration in this module",
        )
      }

    // `MV3` — every @MviViewModel must have matching content in this module (same-module
    // triple).
    vmModels.forEach { vm ->
      if (vm.routeFq !in matchedVmRoutes) {
        error(
          "MV3",
          "@MviViewModel ${vm.vmSimpleName}(${vm.routeFq.substringAfterLast('.')}) exists, but this module " +
            "has no matching @Screen(state, onIntent) content (§10.1 same-module triple)",
        )
      }
    }

    return entries to ok
  }

  /** Dispatches on parameter shape: MVI-mode iff BOTH `state` and `onIntent` params are present. */
  private fun buildEntry(
    fn: KSFunctionDeclaration,
    annotation: KSAnnotation,
    kind: EntryKindModel,
    effectFuns: List<EffectFun>,
    chromeFuns: List<ChromeFun>,
  ): EntryFunctionModel? {
    val paramNames = fn.parameters.mapNotNull { it.name?.asString() }.toSet()
    val isMvi = "state" in paramNames && "onIntent" in paramNames
    return if (isMvi) {
      buildMviEntry(fn, annotation, kind, effectFuns, chromeFuns)
    } else {
      buildCoreEntry(fn, annotation, kind)
    }
  }

  // region Core-mode (UNCHANGED)

  private fun buildCoreEntry(
    fn: KSFunctionDeclaration,
    annotation: KSAnnotation,
    kind: EntryKindModel,
  ): EntryFunctionModel? {
    val fnName = fn.simpleName.asString()
    val params = fn.parameters

    val routeParam = params.firstOrNull { it.name?.asString() == "route" }
    val navParam = params.firstOrNull { it.name?.asString() == "nav" }
    val unknownParams = params.filter { it.name?.asString() !in setOf("route", "nav") }

    if (unknownParams.isNotEmpty()) {
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

    // `SC8` (kind↔contract) + `SC7` (@NoBack × modal) — shared, statically decidable (see the
    // helper).
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
      // VmDiClassifier:
      // a same-module navigator type isn't generated yet in this KSP round (its FQ resolves to an
      // error type), so we accept an unresolved type by NAME `nav` and only reject a RESOLVED,
      // wrong-typed param.
      val navParamType = navParam.type.resolve()
      val navParamFq = navParamType.declaration.qualifiedName?.asString()
      val expectedNavigatorFq = VmDiClassifier.navigatorTypeFq(routeDecl.packageName.asString(), x)
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

  // region MVI-mode

  private fun buildMviEntry(
    fn: KSFunctionDeclaration,
    annotation: KSAnnotation,
    kind: EntryKindModel,
    effectFuns: List<EffectFun>,
    chromeFuns: List<ChromeFun>,
  ): EntryFunctionModel? {
    val fnName = fn.simpleName.asString()
    val params = fn.parameters
    val stateParam = params.first { it.name?.asString() == "state" }
    val onIntentParam = params.first { it.name?.asString() == "onIntent" }

    // MVI content has no route parameter, so the mandatory annotation is its only route source.
    val resolvedRouteType = resolveMandatoryRoute(annotation, fnName) ?: return null

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
    declaredMviScreenRoutes += routeFq

    // `SC4` — shared with core-mode: a route may have only ONE content registration.
    val previousOwner = seenRouteFqs[routeFq]
    if (previousOwner != null) {
      error(
        "SC4",
        "route ${routeFq.substringAfterLast('.')} is registered by multiple functions: $previousOwner, $fnName",
      )
      return null
    }
    seenRouteFqs[routeFq] = fnName

    // `SC8` (kind↔contract) + `SC7` (@NoBack × modal) — shared with core-mode, statically
    // decidable.
    if (!checkKindContractAndNoBack(fnName, routeDecl, kind)) return null

    // `MV2` — the content's route must have a @MviViewModel in THIS module (same-module
    // triple).
    val vm = vmByRouteFq[routeFq]
    if (vm == null) {
      error(
        "MV2",
        "$fnName: MVI-mode content has no matching @MviViewModel for route ${routeFq.substringAfterLast('.')} " +
          "in this module; content is linked to the VM by ROUTE, not by state/onIntent types. " +
          "Add @MviViewModel(${routeFq.substringAfterLast('.')}::class) for the same route (§10.1)",
      )
      return null
    }
    // Mark matched as soon as a VM pairs with a content (even if `MV5` fails below) so `MV3`
    // doesn't
    // also fire for the same VM — `MV3` is strictly "no content at all", not "invalid content".
    matchedVmRoutes += routeFq

    // Plain Hilt receives no route data through SavedStateHandle in Navigation 3. Parameterized
    // routes therefore require an assisted factory or an explicit resolver.
    if (
      vm.di == VmDiKind.HILT_PLAIN &&
        routeDecl.primaryConstructor?.parameters.orEmpty().isNotEmpty()
    ) {
      error(
        "MV12",
        "$fnName: route ${routeFq.substringAfterLast('.')} has parameters (carries route data), but " +
          "@MviViewModel ${vm.vmSimpleName} is plain @HiltViewModel (no assistedFactory). In Nav3, a plain-Hilt " +
          "VM cannot access route arguments: nothing writes the route into SavedStateHandle, so " +
          "`SavedStateHandle.get(...)` always returns null. For a screen with route data, use " +
          "@HiltViewModel(assistedFactory = …) (HILT_ASSISTED), or make the route parameterless (§10.1)",
      )
      return null
    }

    // Compare generics-preserving TypeNames so Wrapper<Int> cannot match Wrapper<String> merely by
    // flattened FQ name.
    val stateTypeName = stateParam.type.resolve().toTypeName()
    if (stateTypeName != vm.stateTypeName) {
      error(
        "MV5",
        "$fnName for route $routeFq: state parameter type ($stateTypeName) does not match VM " +
          "${vm.vmSimpleName}'s state type (${vm.stateTypeName})",
      )
      return null
    }
    val onIntentType = onIntentParam.type.resolve()
    val onIntentDeclFq = onIntentType.declaration.qualifiedName?.asString()
    val returnsUnit = onIntentType.arguments.getOrNull(1)?.type?.resolve()?.fqOf() == UNIT_FQ
    if (onIntentDeclFq != FUNCTION1_FQ || !returnsUnit) {
      error(
        "MV5",
        "$fnName for route $routeFq: onIntent parameter is not a " +
          "(${vm.intentTypeFq.substringAfterLast('.')}) -> Unit function " +
          "(type: ${onIntentType.fqOf()})",
      )
      return null
    }
    val intentArgTypeName = onIntentType.arguments.getOrNull(0)?.type?.resolve()?.toTypeName()
    if (intentArgTypeName != vm.intentTypeName) {
      error(
        "MV5",
        "$fnName for route $routeFq: onIntent intent type ($intentArgTypeName) does not match VM " +
          "${vm.vmSimpleName}'s intent type (${vm.intentTypeName})",
      )
      return null
    }

    // Record content parameters beyond {state, onIntent}. A GezginSheetController (by TYPE) is
    // role-provided (Local-injected via LocalGezginSheetController); everything else becomes a
    // resolver param. Deliberately NOT `SC3`-rejected here — that hard-reject is core-mode only —
    // but `MV10` (reserved name) and `MV8` (controller off a @BottomSheet) ARE rejected: both would
    // otherwise reach codegen and emit compile-clean-but-broken/crashing code (compile-safe
    // philosophy).
    val roleExtras = mutableListOf<MviExtraParam>()
    val resolverExtras = mutableListOf<MviExtraParam>()
    var extrasInvalid = false
    params
      .filter { it.name?.asString() != "state" && it.name?.asString() != "onIntent" }
      .forEach { p ->
        val pName = p.name?.asString().orEmpty()
        // Reject extras that collide with generated register-body identifiers.
        if (pName in RESERVED_EXTRA_NAMES) {
          error(
            "MV10",
            "$fnName: '$pName' cannot be a content-extra parameter name; it is reserved in the " +
              "generated register body (viewModel/nav/route/vm). Use a different name " +
              "(in MVI, nav access belongs in the VM constructor, not content)",
          )
          extrasInvalid = true
          return@forEach
        }
        val t = p.type.resolve()
        val extra = MviExtraParam(pName, t.fqOf(), t.toTypeName())
        if (t.declaration.qualifiedName?.asString() == SHEET_CONTROLLER_FQ) {
          // LocalGezginSheetController is valid only inside bottom-sheet content; elsewhere its
          // default fails at first render.
          if (kind == EntryKindModel.BOTTOM_SHEET) {
            roleExtras += extra
          } else {
            error(
              "MV8",
              "$fnName: GezginSheetController parameter is only valid in @BottomSheet content (role-extra, " +
                "provided from LocalGezginSheetController); this content is $kind, not @BottomSheet",
            )
            extrasInvalid = true
          }
        } else if (p.hasDefault) {
          // A resolver extra with a Kotlin default need not be supplied by Gezgin:
          // the generated content call passes named args, so an omitted defaulted param falls
          // back to the composable's own default. Don't force a mandatory `@Composable -> T`
          // resolver for it ("minimal ceremony") — drop it from both lists.
          Unit
        } else {
          resolverExtras += extra
        }
      }
    if (extrasInvalid) return null

    val effect = effectFuns.firstOrNull { it.routeFq == routeFq }
    if (effect != null && effect.effectTypeName != vm.effectTypeName) {
      error(
        "MV16",
        "@EffectHandler ${effect.simpleName} for route $routeFq takes Flow<${effect.effectTypeName}>, " +
          "but ${vm.vmSimpleName} declares effect ${vm.effectTypeName}",
      )
      return null
    }
    if (effect?.hasIntentParam == true) {
      if (effect.intentTypeName != vm.intentTypeName) {
        error(
          "MV23",
          "@EffectHandler ${effect.simpleName} for route $routeFq has onIntent type " +
            "${effect.intentTypeName}, but ${vm.vmSimpleName} declares intent ${vm.intentTypeName}",
        )
        return null
      }
    }

    val topChrome = chromeFuns.firstOrNull { it.routeFq == routeFq && it.kind == ChromeKind.TOP }
    val topBar = validateChromeProvider(topChrome, routeFq, vm)
    if (topChrome != null && topBar == null) return null
    val bottomChrome =
      chromeFuns.firstOrNull { it.routeFq == routeFq && it.kind == ChromeKind.BOTTOM }
    val bottomBar = validateChromeProvider(bottomChrome, routeFq, vm)
    if (bottomChrome != null && bottomBar == null) return null

    val packageName = fn.packageName.asString()
    val x = NavigatorCodegen.navigatorX(routeDecl.simpleName.asString())

    // Nav wiring requires a generated navigator whether requested by the VM or effect handler.
    val navigatorTypeFq = VmDiClassifier.navigatorTypeFq(routeDecl.packageName.asString(), x)
    val vmWantsNav = VmDiClassifier.classify(vm, routeFq, navigatorTypeFq).vmHasNav
    val effectWantsNav = effect != null && effect.hasNavParam
    if (vmWantsNav || effectWantsNav) {
      // Reuse the identity-verified probe so a missing cross-module navigator produces `MV7`.
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
          "MV7",
          "$fnName: nav is being wired (VM constructor or @EffectHandler requests nav), but target route " +
            "(${routeFq.substringAfterLast('.')}) has no navigator " +
            "(@NoBack and no declared navigation/result operation)",
        )
        return null
      }
    }

    // An effect handler's navigator must belong to this route. Same-round navigator types remain
    // error types, so compare their declared simple name while retaining exact FQ checks otherwise.
    val effectNavigatorMatches =
      effect?.navParamTypeFq?.let { actual ->
        if (effect.navParamIsError) {
          actual.substringAfterLast('.') == navigatorTypeFq.substringAfterLast('.')
        } else {
          actual == navigatorTypeFq
        }
      } ?: false
    if (effectWantsNav && !effectNavigatorMatches) {
      error(
        "MV11",
        "@EffectHandler ${effect.simpleName} for route $routeFq has nav parameter type " +
          "(${effect.navParamTypeFq}), which is not this route's " +
          "navigator ($navigatorTypeFq); the generated ${effect.simpleName}(effects = …, nav = nav) " +
          "call would fail with a type mismatch in GezginMviEntries.kt. Use `nav: ${x}Navigator`",
      )
      return null
    }

    // As in core mode, reject duplicate provideXEntry names within one package.
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
      hasRouteParam = false,
      hasNavParam = false,
      routeInModel = routeModel != null,
      routePackageName = routeDecl.packageName.asString(),
      noBack = routeDecl.hasAnnotation(NO_BACK_FQ),
      x = x,
      mvi =
        MviEntryModel(
          vm = vm,
          effectFunSimpleName = effect?.simpleName,
          effectFunPackageName = effect?.packageName,
          effectFlowParamName = effect?.flowParamName,
          effectHasNavParam = effect?.hasNavParam ?: false,
          effectHasIntentParam = effect?.hasIntentParam ?: false,
          topBar = topBar,
          bottomBar = bottomBar,
          roleExtraParams = roleExtras,
          resolverExtraParams = resolverExtras,
        ),
    )
  }

  /** One route-explicit effect binder, resolved before MVI-entry wiring. */
  private data class EffectFun(
    val simpleName: String,
    val packageName: String,
    /** Generics-preserving effect type used to join the binder with its ViewModel. */
    val effectTypeName: TypeName?,
    /** Name of the `Flow<E>` parameter, or null when the binder has none. */
    val flowParamName: String?,
    val hasNavParam: Boolean,
    val hasIntentParam: Boolean,
    val intentTypeName: TypeName?,
    /** Resolved nav FQ, or the declared simple name for a same-round error type. */
    val navParamTypeFq: String?,
    /** `true` when [navParamTypeFq] is a same-module declared name rather than a resolved FQ. */
    val navParamIsError: Boolean,
    val routeFq: String,
  )

  private enum class ChromeKind(
    val annotationFq: String,
    val annotationName: String,
    val duplicateCode: String,
  ) {
    TOP(TOP_BAR_FQ, "TopBar", "MV19"),
    BOTTOM(BOTTOM_BAR_FQ, "BottomBar", "MV20"),
  }

  private data class ChromeFun(
    val simpleName: String,
    val packageName: String,
    val routeFq: String,
    val kind: ChromeKind,
    val stateTypeName: TypeName,
    val intentTypeName: TypeName,
  )

  /** Reads temporary chrome strictly by explicit route; no State/Intent inference is permitted. */
  private fun readChromeFuns(): List<ChromeFun> {
    val chrome =
      ChromeKind.entries.flatMap { kind ->
        resolver
          .getSymbolsWithAnnotation(kind.annotationFq)
          .filterIsInstance<KSFunctionDeclaration>()
          .distinctBy { it.declarationIdentity() }
          .flatMap { fn ->
            fn.annotations
              .filter { it.fqName() == kind.annotationFq }
              .mapNotNull { annotation -> readChromeFun(fn, annotation, kind) }
          }
          .toList()
      }

    chrome
      .groupBy { it.kind to it.routeFq }
      .forEach { (_, providers) ->
        if (providers.size > 1) {
          val first = providers.first()
          error(
            first.kind.duplicateCode,
            "route ${first.routeFq} has multiple @${first.kind.annotationName} providers: " +
              providers.joinToString { it.simpleName },
          )
        }
      }
    return chrome
  }

  private fun readChromeFun(
    fn: KSFunctionDeclaration,
    annotation: KSAnnotation,
    kind: ChromeKind,
  ): ChromeFun? {
    val simpleName = fn.simpleName.asString()
    val routeFq = annotation.classArg("route")?.declaration?.qualifiedName?.asString()
    if (routeFq == null) {
      error("MV21", "@${kind.annotationName} $simpleName has an unresolved route declaration")
      return null
    }

    val stateParam = fn.parameters.firstOrNull { it.name?.asString() == "state" }
    val onIntentParam = fn.parameters.firstOrNull { it.name?.asString() == "onIntent" }
    val unsupported = fn.parameters.filter { it != stateParam && it != onIntentParam }
    if (stateParam == null || onIntentParam == null || unsupported.isNotEmpty()) {
      error(
        "MV22",
        "@${kind.annotationName} $simpleName for route $routeFq must declare exactly " +
          "(state, onIntent); unsupported parameters: " +
          unsupported.joinToString { it.name?.asString().orEmpty() }.ifEmpty { "none" },
      )
      return null
    }

    val onIntentType = onIntentParam.type.resolve()
    val returnsUnit = onIntentType.arguments.getOrNull(1)?.type?.resolve()?.fqOf() == UNIT_FQ
    val intentType = onIntentType.arguments.getOrNull(0)?.type?.resolve()
    if (
      onIntentType.declaration.qualifiedName?.asString() != FUNCTION1_FQ ||
        !returnsUnit ||
        intentType == null
    ) {
      error(
        "MV22",
        "@${kind.annotationName} $simpleName for route $routeFq has invalid onIntent type " +
          "(${onIntentType.fqOf()}); expected (Intent) -> Unit",
      )
      return null
    }

    return ChromeFun(
      simpleName = simpleName,
      packageName = fn.packageName.asString(),
      routeFq = routeFq,
      kind = kind,
      stateTypeName = stateParam.type.resolve().toTypeName(),
      intentTypeName = intentType.toTypeName(),
    )
  }

  private fun validateChromeProvider(
    chrome: ChromeFun?,
    routeFq: String,
    vm: ViewModelModel,
  ): MviChromeProviderModel? {
    if (chrome == null) return null
    if (chrome.stateTypeName != vm.stateTypeName) {
      error(
        "MV22",
        "@${chrome.kind.annotationName} ${chrome.simpleName} for route $routeFq has state type " +
          "${chrome.stateTypeName}, but ${vm.vmSimpleName} declares ${vm.stateTypeName}",
      )
      return null
    }
    if (chrome.intentTypeName != vm.intentTypeName) {
      error(
        "MV22",
        "@${chrome.kind.annotationName} ${chrome.simpleName} for route $routeFq has onIntent type " +
          "${chrome.intentTypeName}, but ${vm.vmSimpleName} declares ${vm.intentTypeName}",
      )
      return null
    }
    return MviChromeProviderModel(chrome.simpleName, chrome.packageName)
  }

  /** Reads route-explicit handlers and rejects duplicate bindings for one route. */
  private fun readEffectFuns(): List<EffectFun> {
    val explicit =
      resolver
        .getSymbolsWithAnnotation(EFFECT_HANDLER_FQ)
        .filterIsInstance<KSFunctionDeclaration>()
        .distinctBy { it.declarationIdentity() }
        .flatMap { fn ->
          fn.annotations
            .filter { it.fqName() == EFFECT_HANDLER_FQ }
            .mapNotNull { annotation ->
              val routeFq = annotation.classArg("route")?.declaration?.qualifiedName?.asString()
              if (routeFq == null) {
                error(
                  "MV15",
                  "@EffectHandler ${fn.simpleName.asString()} has an unresolved route declaration",
                )
                null
              } else {
                readEffectFun(fn, routeFq)
              }
            }
        }
        .toList()

    explicit
      .groupBy { it.routeFq }
      .forEach { (routeFq, handlers) ->
        if (handlers.size > 1) {
          error(
            "MV14",
            "route $routeFq has multiple @EffectHandler functions: ${handlers.joinToString { it.simpleName }}",
          )
        }
      }

    return explicit
  }

  private fun readEffectFun(fn: KSFunctionDeclaration, routeFq: String): EffectFun {
    val simpleName = fn.simpleName.asString()
    val flowParam =
      fn.parameters.firstOrNull { p ->
        p.type.resolve().declaration.qualifiedName?.asString() == FLOW_FQ
      }
    val effectArgType = flowParam?.type?.resolve()?.arguments?.firstOrNull()?.type?.resolve()
    val navParam = fn.parameters.firstOrNull { it.name?.asString() == "nav" }
    val navParamType = navParam?.type?.resolve()
    val intentParam = fn.parameters.firstOrNull { it.name?.asString() == "onIntent" }
    val intentParamType = intentParam?.type?.resolve()
    val intentReturnsUnit =
      intentParamType?.arguments?.getOrNull(1)?.type?.resolve()?.fqOf() == UNIT_FQ
    val intentArgType = intentParamType?.arguments?.getOrNull(0)?.type?.resolve()
    val validIntentParam =
      intentParam == null ||
        (intentParamType?.declaration?.qualifiedName?.asString() == FUNCTION1_FQ &&
          intentReturnsUnit &&
          intentArgType != null)
    if (!validIntentParam) {
      error(
        "MV23",
        "@EffectHandler $simpleName for route $routeFq has invalid onIntent " +
          "parameter; expected (Intent) -> Unit",
      )
    }
    val extraParams =
      fn.parameters.filter { it != flowParam && it != navParam && it != intentParam }
    if (extraParams.isNotEmpty()) {
      error(
        "MV11",
        "@EffectHandler $simpleName for route $routeFq has unsupported " +
          "extra parameter(s): ${extraParams.joinToString { it.name?.asString().orEmpty() }}",
      )
    }
    if (effectArgType == null) {
      error(
        "MV6",
        "@EffectHandler $simpleName for route $routeFq does not take a Flow<E> parameter",
      )
    }
    return EffectFun(
      simpleName = simpleName,
      packageName = fn.packageName.asString(),
      effectTypeName = effectArgType?.toTypeName(),
      flowParamName = flowParam?.name?.asString(),
      hasNavParam = navParam != null,
      hasIntentParam = intentParam != null,
      intentTypeName = intentArgType?.toTypeName(),
      navParamTypeFq =
        if (navParamType?.isError == true) {
          navParamType.toString().removeSurrounding("<ERROR TYPE: ", ">")
        } else {
          navParamType?.fqOf()
        },
      navParamIsError = navParamType?.isError ?: false,
      routeFq = routeFq,
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
    val expectedContract = CONTRACT_BY_KIND[kind] // null for SCREEN

    // `SC8` — every implemented kind-contract that isn't THIS kind's contract is a silent-drop
    // mismatch.
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
        EntryKindModel.SCREEN -> Unit // @NoBack + @Screen legal (terminal ekran)
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
