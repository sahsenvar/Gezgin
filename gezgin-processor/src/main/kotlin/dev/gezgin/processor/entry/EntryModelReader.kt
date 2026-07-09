package dev.gezgin.processor.entry

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel
import dev.gezgin.processor.mvi.SCREEN_EFFECT_FQ
import dev.gezgin.processor.mvi.ViewModelModel
import dev.gezgin.processor.mvi.VmDiClassifier

private const val SCREEN_FQ = "dev.gezgin.core.annotation.Screen"
private const val DIALOG_FQ = "dev.gezgin.core.annotation.Dialog"
private const val BOTTOM_SHEET_FQ = "dev.gezgin.core.annotation.BottomSheet"
private const val FULLSCREEN_MODAL_FQ = "dev.gezgin.core.annotation.FullscreenModal"
private const val ROUTE_FQ = "dev.gezgin.core.Route"
private const val NO_BACK_FQ = "dev.gezgin.core.annotation.NoBack"

// MVI-mode (§10.1) FQ constants — read as strings, no compile dep on gezgin-mvi.
private const val SHEET_STATE_FQ = "androidx.compose.material3.SheetState"
private const val FLOW_FQ = "kotlinx.coroutines.flow.Flow"
private const val FUNCTION1_FQ = "kotlin.Function1"
private const val UNIT_FQ = "kotlin.Unit"

// Problem-2 (§10.1) content-extra names that collide with the identifiers `MviEntryCodegen`'s emitted
// `provideXEntry` introduces: the resolver param `viewModel`, and the register-body locals `route`
// (lambda param), `nav` (nav-wired factory), `vm` (the resolved VM). An extra colliding with one of
// these would emit broken generated code (e.g. an extra `nav: XNavigator` → `nav = nav()`, calling the
// navigator instance as if it were the resolver lambda) — MV10 rejects it at model-read time. (`state`/
// `onIntent` are the MVI signature params, filtered out BEFORE the extras loop, so they never reach it.)
private val RESERVED_EXTRA_NAMES = setOf("viewModel", "nav", "route", "vm")

private val KIND_BY_ANNOTATION_FQ = mapOf(
    SCREEN_FQ to EntryKindModel.SCREEN,
    DIALOG_FQ to EntryKindModel.DIALOG,
    BOTTOM_SHEET_FQ to EntryKindModel.BOTTOM_SHEET,
    FULLSCREEN_MODAL_FQ to EntryKindModel.FULLSCREEN_MODAL,
)

/**
 * Task 3.4 (+ Faz 5.1 MVI-mode) — reads every `@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal`-
 * annotated composable FUNCTION (spec §10.1) and validates it into an [EntryFunctionModel] list,
 * reporting every violation as a bracketed-code KSP error via [logger]. [read] never throws — like the
 * graph validator, it collects every violation in one pass and returns whether the read was clean
 * alongside whatever models DID resolve.
 *
 * **Two modes, selected by the composable's parameter shape (§10.1) — the annotation is unchanged:**
 * - **core-mode** `(route, nav)` — the original Task 3.4 behavior, byte-for-byte UNCHANGED (see
 *   [buildCoreEntry]); `SC1`-`SC6` below. A composable with `route`/`nav` only (or neither) stays here.
 * - **MVI-mode** `(state, onIntent[, extras])` — a composable whose params include BOTH a `state` and
 *   an `onIntent` (by name) is MVI-mode (see [buildMviEntry]); it pairs with a same-route,
 *   same-module `@ViewModel` (`MV2`-`MV6`). A composable with ONLY `state` or ONLY `onIntent` is
 *   malformed and deliberately NOT special-cased — it falls through to core-mode's `SC3`
 *   unknown-param rejection (a half-MVI shape is a user error, not a third mode).
 *
 * **Route resolution (`SC1`, core-mode):** the annotation's `route=` is a sentinel default
 * (`Route::class`) meaning "derive from the composable's `route:` parameter type instead". If the
 * annotation gives an explicit (non-sentinel) type AND a `route:` param exists with a DIFFERENT type,
 * or if neither source resolves a type, that's `SC1`.
 *
 * **Route resolution (MVI-mode):** an MVI-mode content has NO `route:` param, so it MUST carry an
 * explicit `@Screen(Route::class)` — its route link is the annotation arg itself (the spec §10.1
 * example and the Faz-5.0 `CounterMvi` fixture both do exactly this; the `state` param's TYPE is NOT
 * the route). The matched `@ViewModel(Route::class)` binds the same route → the pairing is explicit,
 * not inferred by S/I type-match. A sentinel-only MVI content can't derive a route → `SC1`.
 *
 * **Nav wiring (`SC2`, core-mode):** a `nav:` param requires the resolved route to actually earn a
 * navigator ([NavigatorCodegen.hasNavigator]).
 *
 * **Unknown params (`SC3`, core-mode only):** V1 core-mode supports only `route:`/`nav:` params — any
 * other parameter is rejected. MVI-mode does NOT use `SC3`: its non-`{state,onIntent}` params are
 * recorded as extras (Problem 2), never rejected (`gezgin-mvi` scope, §10.1).
 *
 * **Route type sanity (`SC5`):** the resolved type must implement `dev.gezgin.core.Route`.
 *
 * **Duplicate registration (`SC4`) / provide-name clash (`SC6`):** shared by both modes — two
 * kind-annotated functions resolving to the same route (`SC4`), or to the same `provideXEntry` name in
 * one package (`SC6`), is rejected.
 *
 * **MVI guardrails (Faz 5.1):**
 * - `MV2` — an MVI-mode content whose route has no `@ViewModel` in THIS module (route-linked, not
 *   state/onIntent-type-matched).
 * - `MV3` — a `@ViewModel` with no matching MVI-mode content in this module (symmetric to `MV2`).
 * - `MV5` — a matched content's `state`/`onIntent` types don't satisfy the VM's `GezginMvi<S,I,E>`
 *   contract: `state` ≠ `S` (compared by [TypeName], generics-preserving), or `onIntent` is not a
 *   `(I) -> Unit` function type.
 * - `MV6` — a `@ScreenEffect`'s `Flow<E>` `E` (by [TypeName]) matches no `@ViewModel`'s effect type (`E`).
 * - `MV7` — MVI-mode `SC2` parity: nav is wired (the matched VM's ctor wants `nav`, or the matched
 *   `@ScreenEffect` takes a `nav` param) but the route earns no navigator ([NavigatorCodegen.hasNavigator]
 *   false) — otherwise codegen would emit an unresolved `<x>Navigator()` factory call.
 *
 * **MVI guardrails (Faz 5 final review):**
 * - `MV8` — a `sheetState: SheetState` extra on a non-`BOTTOM_SHEET`-kind MVI content: role-injected
 *   `LocalGezginSheetState.current` `error()`s outside a `@BottomSheet`, so codegen would emit
 *   compile-clean code that crashes at first render. Classified as a role extra ONLY on `BOTTOM_SHEET`.
 * - `MV9` — two `@ScreenEffect` binders resolving to the same effect type `E` (symmetric to `MV4`):
 *   only one wires to any VM, the other silently dangles.
 * - `MV10` — a Problem-2 content extra whose name collides with an emitted identifier
 *   (`viewModel`/`nav`/`route`/`vm`), which would produce broken generated code.
 *
 * (`MV1`/`MV4` — `@ViewModel` must implement `GezginMvi`, and no two `@ViewModel`s per route — live in
 * [dev.gezgin.processor.mvi.ViewModelModelReader], whose output [vmModels] this reader consumes.)
 */
class EntryModelReader(
    private val resolver: Resolver,
    private val logger: KSPLogger,
    private val model: GraphModel,
    private val vmModels: List<ViewModelModel> = emptyList(),
) {

    private val graphsByFq: Map<String, GraphModelNode> = model.graphs.associateBy { it.fqName }
    private val routesByFq: Map<String, RouteModel> = model.routes.associateBy { it.fqName }
    private val vmByRouteFq: Map<String, ViewModelModel> = vmModels.associateBy { it.routeFq }

    private var ok = true
    private val seenRouteFqs = mutableMapOf<String, String>() // routeFq -> first function's simple name
    private val seenProvideNames = mutableMapOf<Pair<String, String>, String>() // (package, x) -> first function's simple name
    private val matchedVmRoutes = mutableSetOf<String>() // routes whose @ViewModel got a matching content (for MV3)

    fun read(): Pair<List<EntryFunctionModel>, Boolean> {
        // Scan @ScreenEffect binders once (also runs MV6); MVI entries wire them by effect type.
        val effectFuns = readScreenEffectFuns()

        val entries = KIND_BY_ANNOTATION_FQ.flatMap { (annotationFq, kind) ->
            resolver.getSymbolsWithAnnotation(annotationFq)
                .filterIsInstance<KSFunctionDeclaration>()
                .mapNotNull { fn -> buildEntry(fn, annotationFq, kind, effectFuns) }
                .toList()
        }

        // MV3 — every @ViewModel must have a matching content in this module (§10.1 same-module triple).
        vmModels.forEach { vm ->
            if (vm.routeFq !in matchedVmRoutes) {
                error(
                    "MV3",
                    "@ViewModel ${vm.vmSimpleName}(${vm.routeFq.substringAfterLast('.')}) var ama eşleşen " +
                        "@Screen(state, onIntent) content bu modülde yok (§10.1 aynı-modül üçlüsü)",
                )
            }
        }

        return entries to ok
    }

    /** Dispatches on parameter shape: MVI-mode iff BOTH `state` and `onIntent` params are present. */
    private fun buildEntry(
        fn: KSFunctionDeclaration,
        annotationFq: String,
        kind: EntryKindModel,
        effectFuns: List<EffectFun>,
    ): EntryFunctionModel? {
        val paramNames = fn.parameters.mapNotNull { it.name?.asString() }.toSet()
        val isMvi = "state" in paramNames && "onIntent" in paramNames
        return if (isMvi) buildMviEntry(fn, annotationFq, kind, effectFuns) else buildCoreEntry(fn, annotationFq, kind)
    }

    // region Core-mode (Task 3.4 — UNCHANGED)

    private fun buildCoreEntry(fn: KSFunctionDeclaration, annotationFq: String, kind: EntryKindModel): EntryFunctionModel? {
        val fnName = fn.simpleName.asString()
        val annotation = fn.annotations.first { it.fqName() == annotationFq }
        val params = fn.parameters

        val routeParam = params.firstOrNull { it.name?.asString() == "route" }
        val navParam = params.firstOrNull { it.name?.asString() == "nav" }
        val unknownParams = params.filter { it.name?.asString() !in setOf("route", "nav") }

        if (unknownParams.isNotEmpty()) {
            error(
                "SC3",
                "$fnName şu param(lar)ı destekliyor gibi görünmüyor: " +
                    unknownParams.joinToString { it.name?.asString().orEmpty() } +
                    " — core-mode yalnız route:/nav: destekler (resolver mekanizması Faz 5)",
            )
            return null
        }

        val explicitRouteType = annotation.classArg("route")
        val isSentinel = explicitRouteType == null || explicitRouteType.declaration.qualifiedName?.asString() == ROUTE_FQ
        val routeParamType = routeParam?.type?.resolve()

        val resolvedRouteType = when {
            !isSentinel && routeParamType != null -> {
                val explicitFq = explicitRouteType!!.declaration.qualifiedName?.asString()
                val paramFq = routeParamType.declaration.qualifiedName?.asString()
                if (explicitFq != paramFq) {
                    error(
                        "SC1",
                        "$fnName: annotation route=${explicitRouteType.declaration.simpleName.asString()} ile " +
                            "route: param tipi (${routeParamType.declaration.simpleName.asString()}) çelişiyor",
                    )
                    null
                } else {
                    explicitRouteType
                }
            }

            !isSentinel -> explicitRouteType
            routeParamType != null -> routeParamType
            else -> {
                error("SC1", "$fnName: route türetilemedi — ne annotation'da açık route= var ne de route: param'ı")
                null
            }
        } ?: return null

        val routeDecl = resolvedRouteType.declaration as? KSClassDeclaration
        val implementsRoute = routeDecl != null &&
            (routeDecl.qualifiedName?.asString() == ROUTE_FQ || routeDecl.getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == ROUTE_FQ })
        if (!implementsRoute) {
            error(
                "SC5",
                "$fnName: türetilen route tipi (${resolvedRouteType.declaration.qualifiedName?.asString()}) " +
                    "dev.gezgin.core.Route implement etmiyor",
            )
            return null
        }

        val routeFq = requireNotNull(routeDecl?.qualifiedName?.asString())
        val routeModel = routesByFq[routeFq]

        val previousOwner = seenRouteFqs[routeFq]
        if (previousOwner != null) {
            error("SC4", "route ${routeFq.substringAfterLast('.')} birden çok fonksiyon tarafından kaydediliyor: $previousOwner, $fnName")
            return null
        }
        seenRouteFqs[routeFq] = fnName

        if (navParam != null) {
            val hasNavigator = routeModel?.let { NavigatorCodegen.hasNavigator(it, graphsByFq) } ?: true
            if (!hasNavigator) {
                error(
                    "SC2",
                    "$fnName: nav: param'ı istendi ama hedef route'un (${routeFq.substringAfterLast('.')}) " +
                        "navigator'ı yok (hiç edge/back-edge/result-contract'ı yok)",
                )
                return null
            }
        }

        val packageName = fn.packageName.asString()
        val x = NavigatorCodegen.navigatorX(routeDecl!!.simpleName.asString())

        // SC6 (Minor 5) — iki entry fonksiyonu AYNI pakette AYNI `x`'e (dolayısıyla aynı
        // `provideXEntry` fonksiyon adına) çözülürse KotlinPoet aynı dosyaya iki eş-imzalı fonksiyon
        // yazar → derleme "conflicting overloads" ile patlar; burada erken ve açıklayıcı yakalanır.
        val provideKey = packageName to x
        val previousProvideOwner = seenProvideNames[provideKey]
        if (previousProvideOwner != null) {
            error(
                "SC6",
                "$packageName paketinde provide${x}Entry() birden çok fonksiyon tarafından üretiliyor: " +
                    "$previousProvideOwner, $fnName — route adlarının aynı 'X' türetimine (${x}) çözülmesi",
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
            // model has no graphs, so its `targetPackage` is empty and useless here). See §3.3.
            routePackageName = routeDecl!!.packageName.asString(),
            // Declaration-tabanlı okuma (Important 2, review): `routeModel` YALNIZ bu modülün
            // GraphModel'inde bilinen route'lar için var olur (cross-module route'larda null) — model
            // fallback'i bu yüzden cross-module @NoBack'i SESSİZCE düşürüyordu. `routeDecl` KSP'de
            // modülden bağımsız her zaman erişilebilir (Route implement eden herhangi bir sınıf, hangi
            // modülde derlenmiş olursa olsun), bu yüzden noBack HER ZAMAN doğrudan route declaration'ın
            // kendi annotation'larından okunur. Sınır: kctfork tek derleme birimi olduğu için gerçek
            // cross-module senaryo bu testlerle simüle edilemiyor — mevcut golden (Product, aynı modül)
            // bu okuma-yolunun declaration-tabanlı olduğunu pinler, cross-module davranışı manuel/on-device
            // doğrulamaya kalıyor (bkz. final-review raporu).
            noBack = routeDecl!!.hasAnnotation(NO_BACK_FQ),
            x = x,
        )
    }

    // endregion

    // region MVI-mode (Faz 5.1, §10.1)

    private fun buildMviEntry(
        fn: KSFunctionDeclaration,
        annotationFq: String,
        kind: EntryKindModel,
        effectFuns: List<EffectFun>,
    ): EntryFunctionModel? {
        val fnName = fn.simpleName.asString()
        val annotation = fn.annotations.first { it.fqName() == annotationFq }
        val params = fn.parameters
        val stateParam = params.first { it.name?.asString() == "state" }
        val onIntentParam = params.first { it.name?.asString() == "onIntent" }

        // Route resolution — MVI-mode has no route: param, so an explicit @Screen(Route::class) is
        // mandatory (the state param's TYPE is NOT the route). Sentinel-only → can't derive → SC1.
        val explicitRouteType = annotation.classArg("route")
        val isSentinel = explicitRouteType == null || explicitRouteType.declaration.qualifiedName?.asString() == ROUTE_FQ
        if (isSentinel) {
            error(
                "SC1",
                "$fnName: MVI-mode content (state, onIntent) route türetemez — açık @Screen(Route::class) şart " +
                    "(state param tipi route DEĞİLDİR)",
            )
            return null
        }
        val resolvedRouteType = explicitRouteType!!

        val routeDecl = resolvedRouteType.declaration as? KSClassDeclaration
        val implementsRoute = routeDecl != null &&
            (routeDecl.qualifiedName?.asString() == ROUTE_FQ || routeDecl.getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == ROUTE_FQ })
        if (!implementsRoute) {
            error(
                "SC5",
                "$fnName: route tipi (${resolvedRouteType.declaration.qualifiedName?.asString()}) " +
                    "dev.gezgin.core.Route implement etmiyor",
            )
            return null
        }

        val routeFq = requireNotNull(routeDecl?.qualifiedName?.asString())
        val routeModel = routesByFq[routeFq]

        // SC4 — shared with core-mode: a route may have only ONE content registration.
        val previousOwner = seenRouteFqs[routeFq]
        if (previousOwner != null) {
            error("SC4", "route ${routeFq.substringAfterLast('.')} birden çok fonksiyon tarafından kaydediliyor: $previousOwner, $fnName")
            return null
        }
        seenRouteFqs[routeFq] = fnName

        // MV2 — the content's route must have a @ViewModel in THIS module (§10.1 same-module triple).
        val vm = vmByRouteFq[routeFq]
        if (vm == null) {
            error(
                "MV2",
                "$fnName: MVI-mode content ama route ${routeFq.substringAfterLast('.')} için eşleşen @ViewModel " +
                    "bu modülde yok — content'i VM'e ROUTE üzerinden bağlar (state/onIntent TİPİYLE değil); " +
                    "aynı route'a @ViewModel(${routeFq.substringAfterLast('.')}::class) ekle (§10.1)",
            )
            return null
        }
        // Mark matched as soon as a VM pairs with a content (even if MV5 fails below) so MV3 doesn't
        // also fire for the same VM — MV3 is strictly "no content at all", not "invalid content".
        matchedVmRoutes += routeFq

        // MV5 — content (state, onIntent) must satisfy the VM's GezginMvi<S,I,E> contract. Compare by
        // KotlinPoet TypeName (structural, generics-preserving) NOT flattened FQ: an FQ compare collapses
        // `Wrapper<Int>` and `Wrapper<String>` to the same `…Wrapper` and would let a generic-arg mismatch
        // slip through to a downstream Kotlin error inside the generated GezginMviEntries.kt instead of a
        // clean [MV5]. The content's param types are ordinary already-compiled types here, so `toTypeName()`
        // resolves fully (unlike a same-module navigator type — cf. VmCtorParam's FQ-only note).
        val stateTypeName = stateParam.type.resolve().toTypeName()
        if (stateTypeName != vm.stateTypeName) {
            error("MV5", "$fnName: state param tipi ($stateTypeName) VM ${vm.vmSimpleName}'in state tipi (${vm.stateTypeName}) ile örtüşmüyor")
            return null
        }
        val onIntentType = onIntentParam.type.resolve()
        val onIntentDeclFq = onIntentType.declaration.qualifiedName?.asString()
        val returnsUnit = onIntentType.arguments.getOrNull(1)?.type?.resolve()?.fqOf() == UNIT_FQ
        if (onIntentDeclFq != FUNCTION1_FQ || !returnsUnit) {
            error(
                "MV5",
                "$fnName: onIntent param'ı bir (${vm.intentTypeFq.substringAfterLast('.')}) -> Unit fonksiyonu değil " +
                    "(tip: ${onIntentType.fqOf()})",
            )
            return null
        }
        val intentArgTypeName = onIntentType.arguments.getOrNull(0)?.type?.resolve()?.toTypeName()
        if (intentArgTypeName != vm.intentTypeName) {
            error("MV5", "$fnName: onIntent'in intent tipi ($intentArgTypeName) VM ${vm.vmSimpleName}'in intent tipi (${vm.intentTypeName}) ile örtüşmüyor")
            return null
        }

        // Problem 2 — record params beyond {state, onIntent}. sheetState (by TYPE) is role-provided
        // (Local-injected via Faz-4's LocalGezginSheetState); everything else becomes a 5.2 resolver
        // param. Deliberately NOT SC3-rejected here — that hard-reject is core-mode only (§10.1) — but
        // MV10 (reserved name) and MV8 (sheetState off a @BottomSheet) ARE rejected: both would otherwise
        // reach codegen and emit compile-clean-but-broken/crashing code, against the compile-safe philosophy.
        val roleExtras = mutableListOf<MviExtraParam>()
        val resolverExtras = mutableListOf<MviExtraParam>()
        var extrasInvalid = false
        params.filter { it.name?.asString() != "state" && it.name?.asString() != "onIntent" }.forEach { p ->
            val pName = p.name?.asString().orEmpty()
            // MV10 — an extra colliding with an emitted identifier (`viewModel`/`nav`/`route`/`vm`) would
            // emit broken generated code. Reject at read time rather than letting codegen produce it.
            if (pName in RESERVED_EXTRA_NAMES) {
                error(
                    "MV10",
                    "$fnName: '$pName' bir content-extra param adı olamaz — üretilen register gövdesinde " +
                        "ayrılmış bir isim (viewModel/nav/route/vm). Farklı bir ad kullan " +
                        "(MVI'da nav erişimi content'e değil VM ctor'una aittir)",
                )
                extrasInvalid = true
                return@forEach
            }
            val t = p.type.resolve()
            val extra = MviExtraParam(pName, t.fqOf(), t.toTypeName())
            if (t.declaration.qualifiedName?.asString() == SHEET_STATE_FQ) {
                // MV8 — sheetState is a @BottomSheet-ONLY role extra (Local-injected via Faz-4's
                // LocalGezginSheetState, whose default `error()`s outside a @BottomSheet content). On a
                // SCREEN/DIALOG/FULLSCREEN_MODAL kind, codegen would still emit
                // `sheetState = LocalGezginSheetState.current` → compiles clean, crashes at first render.
                // Classify as a valid role extra ONLY on BOTTOM_SHEET; else reject.
                if (kind == EntryKindModel.BOTTOM_SHEET) {
                    roleExtras += extra
                } else {
                    error(
                        "MV8",
                        "$fnName: sheetState param'ı yalnız @BottomSheet content'inde geçerli (rol-extra, " +
                            "LocalGezginSheetState'ten beslenir) — bu content $kind, @BottomSheet değil",
                    )
                    extrasInvalid = true
                }
            } else {
                resolverExtras += extra
            }
        }
        if (extrasInvalid) return null

        // @ScreenEffect wiring — the effect binder carries no route (§10.1), so it links by effect
        // type: its Flow<E> E must equal this VM's effect type. Match by TypeName (see the MV5/MV6 note)
        // so a generic-arg effect mismatch can't wire the wrong VM. (Its E→VM validity is MV6, and two
        // binders sharing an E are rejected as MV9 — both run once in readScreenEffectFuns.)
        val effect = effectFuns.firstOrNull { it.effectTypeName == vm.effectTypeName }

        val packageName = fn.packageName.asString()
        val x = NavigatorCodegen.navigatorX(routeDecl!!.simpleName.asString())

        // MV7 — MVI-mode SC2 parity. Nav is wired into the generated `provideXEntry` when the VM ctor
        // declares a `nav` (DI-relevant, via the SHARED classifier MviEntryCodegen also uses) OR the
        // matched @ScreenEffect takes a `nav` param. Either way, wiring emits a `<x>Navigator()` factory
        // call — but NavigatorCodegen only generates that factory when the route actually earns a
        // navigator ([NavigatorCodegen.hasNavigator]). Without this check a nav-wanting VM/effect on a
        // navigator-less route would sail through validation and emit an unresolved-reference call in
        // generated code (mirrors core-mode's SC2 at the top of [buildCoreEntry]).
        val navigatorTypeFq = VmDiClassifier.navigatorTypeFq(routeDecl!!.packageName.asString(), x)
        val vmWantsNav = VmDiClassifier.classify(vm, routeFq, navigatorTypeFq).vmHasNav
        val effectWantsNav = effect != null && effect.hasNavParam
        if (vmWantsNav || effectWantsNav) {
            val hasNavigator = routeModel?.let { NavigatorCodegen.hasNavigator(it, graphsByFq) } ?: true
            if (!hasNavigator) {
                error(
                    "MV7",
                    "$fnName: nav bağlanıyor (VM ctor'u ya da @ScreenEffect nav istiyor) ama hedef route'un " +
                        "(${routeFq.substringAfterLast('.')}) navigator'ı yok (hiç edge/back-edge/result-contract'ı yok)",
                )
                return null
            }
        }

        // SC6 — shared with core-mode: two entries → same provideXEntry name in one package.
        val provideKey = packageName to x
        val previousProvideOwner = seenProvideNames[provideKey]
        if (previousProvideOwner != null) {
            error(
                "SC6",
                "$packageName paketinde provide${x}Entry() birden çok fonksiyon tarafından üretiliyor: " +
                    "$previousProvideOwner, $fnName — route adlarının aynı 'X' türetimine (${x}) çözülmesi",
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
            routePackageName = routeDecl!!.packageName.asString(),
            noBack = routeDecl!!.hasAnnotation(NO_BACK_FQ),
            x = x,
            mvi = MviEntryModel(
                vm = vm,
                effectFunSimpleName = effect?.simpleName,
                effectFunPackageName = effect?.packageName,
                effectHasNavParam = effect?.hasNavParam ?: false,
                roleExtraParams = roleExtras,
                resolverExtraParams = resolverExtras,
            ),
        )
    }

    /** A `@ScreenEffect fun XEffects(effects: Flow<E>[, nav])` binder, resolved for MVI-entry wiring. */
    private data class EffectFun(
        val simpleName: String,
        val packageName: String,
        /** `E`'s KotlinPoet TypeName (generics-preserving) — the join key against `vm.effectTypeName`. */
        val effectTypeName: TypeName?,
        val hasNavParam: Boolean,
    )

    /**
     * Reads every `@ScreenEffect` composable and extracts its `Flow<E>` `E` type. Also runs `MV6` (an
     * effect binder whose `E` matches no `@ViewModel`'s effect type — compared by [TypeName], NOT
     * flattened FQ, so a generic-arg mismatch is caught here rather than downstream — or that has no
     * `Flow<E>` param at all → dangling/mis-typed) and `MV9` (two binders resolving to the SAME `E`,
     * symmetric to `MV4`: only one could ever wire to a given VM, the other silently dangles).
     */
    private fun readScreenEffectFuns(): List<EffectFun> {
        val vmEffectTypeNames = vmModels.map { it.effectTypeName }.toSet()
        val effectFuns = resolver.getSymbolsWithAnnotation(SCREEN_EFFECT_FQ)
            .filterIsInstance<KSFunctionDeclaration>()
            .map { fn ->
                val simpleName = fn.simpleName.asString()
                val flowParam = fn.parameters.firstOrNull { p ->
                    p.type.resolve().declaration.qualifiedName?.asString() == FLOW_FQ
                }
                val effectArgType = flowParam?.type?.resolve()?.arguments?.firstOrNull()?.type?.resolve()
                val effectTypeFq = effectArgType?.fqOf()
                val effectTypeName = effectArgType?.toTypeName()
                val hasNavParam = fn.parameters.any { it.name?.asString() == "nav" }

                when {
                    effectTypeName == null -> error(
                        "MV6",
                        "@ScreenEffect $simpleName bir Flow<E> parametresi almıyor — binder imzası " +
                            "fun XEffects(effects: Flow<E>[, nav]) olmalı",
                    )
                    effectTypeName !in vmEffectTypeNames -> error(
                        "MV6",
                        "@ScreenEffect $simpleName'in Flow<${effectTypeFq?.substringAfterLast('.')}> tipi hiçbir " +
                            "@ViewModel'in GezginMvi effect (E) tipiyle eşleşmiyor",
                    )
                }

                EffectFun(simpleName, fn.packageName.asString(), effectTypeName, hasNavParam)
            }
            .toList()

        // MV9 — two @ScreenEffect binders that resolve to the SAME effect type E both pass MV6, but only
        // ONE can wire to any given VM (buildMviEntry's firstOrNull, over KSP's non-guaranteed traversal
        // order) — the other silently dangles with no diagnostic. Symmetric to MV4 (two @ViewModel per
        // route): reject the ambiguity up front rather than let a binder be silently dropped.
        effectFuns
            .groupBy { it.effectTypeName }
            .forEach { (typeName, binders) ->
                if (typeName != null && binders.size > 1) {
                    error(
                        "MV9",
                        "birden çok @ScreenEffect binder'ı aynı effect (E) tipine ($typeName) çözülüyor: " +
                            "${binders.joinToString { it.simpleName }} — bir E'ye yalnız bir binder bağlanabilir " +
                            "(hangisinin VM'e bağlanacağı KSP sırasına kalır, biri sessizce boşta kalır)",
                    )
                }
            }

        return effectFuns
    }

    // endregion

    private fun KSType.fqOf(): String = declaration.qualifiedName?.asString() ?: toString()

    private fun KSAnnotated.hasAnnotation(fq: String): Boolean = annotations.any { it.fqName() == fq }

    private fun KSAnnotation.fqName(): String? = annotationType.resolve().declaration.qualifiedName?.asString()

    private fun KSAnnotation.arg(name: String): KSValueArgument? =
        arguments.firstOrNull { it.name?.asString() == name } ?: defaultArguments.firstOrNull { it.name?.asString() == name }

    private fun KSAnnotation.classArg(name: String): KSType? = arg(name)?.value as? KSType

    private fun error(code: String, message: String) {
        logger.error("[$code] $message")
        ok = false
    }
}
