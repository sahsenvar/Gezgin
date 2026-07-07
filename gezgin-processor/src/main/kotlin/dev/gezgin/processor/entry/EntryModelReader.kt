package dev.gezgin.processor.entry

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel

private const val SCREEN_FQ = "dev.gezgin.core.annotation.Screen"
private const val DIALOG_FQ = "dev.gezgin.core.annotation.Dialog"
private const val BOTTOM_SHEET_FQ = "dev.gezgin.core.annotation.BottomSheet"
private const val FULLSCREEN_MODAL_FQ = "dev.gezgin.core.annotation.FullscreenModal"
private const val ROUTE_FQ = "dev.gezgin.core.Route"

private val KIND_BY_ANNOTATION_FQ = mapOf(
    SCREEN_FQ to EntryKindModel.SCREEN,
    DIALOG_FQ to EntryKindModel.DIALOG,
    BOTTOM_SHEET_FQ to EntryKindModel.BOTTOM_SHEET,
    FULLSCREEN_MODAL_FQ to EntryKindModel.FULLSCREEN_MODAL,
)

/**
 * Task 3.4 — reads every `@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal`-annotated composable
 * FUNCTION (spec §10.1 core-mode) and validates it into an [EntryFunctionModel] list, reporting
 * every violation as a bracketed-code KSP error (`SC1`-`SC5`, mirroring [dev.gezgin.processor.GezginValidator]'s
 * style) via [logger]. [read] never throws — like the graph validator, it collects every violation
 * in one pass and returns whether the read was clean alongside whatever models DID resolve.
 *
 * **Route resolution (`SC1`):** the annotation's `route=` is a sentinel default (`Route::class`,
 * see `Annotations.kt`) meaning "derive from the composable's `route:` parameter type instead". If
 * the annotation gives an explicit (non-sentinel) type AND a `route:` param exists with a DIFFERENT
 * type, or if neither source resolves a type at all, that's `SC1`.
 *
 * **Nav wiring (`SC2`):** a `nav:` param requires the resolved route to actually earn a navigator
 * ([NavigatorCodegen.hasNavigator]) — a bare route has no `XNavigator` to construct. This check is
 * only possible when the route is in THIS module's [GraphModel] ([EntryFunctionModel.routeInModel]);
 * a cross-module route (composable compiled separately from its nav-module routes) can't be
 * verified here and is optimistically allowed through — see the Task 3.4 brief's SC5 adjudication.
 *
 * **Unknown params (`SC3`):** V1 core-mode supports only `route:`/`nav:` params (a resolver
 * mechanism for arbitrary params is gezgin-mvi Faz 5, out of scope here) — any other function
 * parameter is rejected.
 *
 * **Route type sanity (`SC5`):** the resolved type must actually implement `dev.gezgin.core.Route`
 * — a plain non-Route class can never be a valid registry key.
 *
 * **Duplicate registration (`SC4`):** two kind-annotated functions resolving to the SAME route is
 * rejected — [dev.gezgin.core.compose.GezginEntryScope.register] itself would blow up at RUNTIME
 * ("Route için entry zaten kayıtlı"), catching it here fails the BUILD instead.
 */
class EntryModelReader(
    private val resolver: Resolver,
    private val logger: KSPLogger,
    private val model: GraphModel,
) {

    private val graphsByFq: Map<String, GraphModelNode> = model.graphs.associateBy { it.fqName }
    private val routesByFq: Map<String, RouteModel> = model.routes.associateBy { it.fqName }

    private var ok = true
    private val seenRouteFqs = mutableMapOf<String, String>() // routeFq -> first function's simple name

    fun read(): Pair<List<EntryFunctionModel>, Boolean> {
        val entries = KIND_BY_ANNOTATION_FQ.flatMap { (annotationFq, kind) ->
            resolver.getSymbolsWithAnnotation(annotationFq)
                .filterIsInstance<KSFunctionDeclaration>()
                .mapNotNull { fn -> buildEntry(fn, annotationFq, kind) }
                .toList()
        }
        return entries to ok
    }

    private fun buildEntry(fn: KSFunctionDeclaration, annotationFq: String, kind: EntryKindModel): EntryFunctionModel? {
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

        return EntryFunctionModel(
            packageName = fn.packageName.asString(),
            functionSimpleName = fnName,
            kind = kind,
            routeFq = routeFq,
            hasRouteParam = routeParam != null,
            hasNavParam = navParam != null,
            routeInModel = routeModel != null,
            noBack = routeModel?.noBack ?: false,
            x = NavigatorCodegen.navigatorX(routeDecl!!.simpleName.asString()),
        )
    }

    private fun KSAnnotation.fqName(): String? = annotationType.resolve().declaration.qualifiedName?.asString()

    private fun KSAnnotation.arg(name: String): KSValueArgument? =
        arguments.firstOrNull { it.name?.asString() == name } ?: defaultArguments.firstOrNull { it.name?.asString() == name }

    private fun KSAnnotation.classArg(name: String): KSType? = arg(name)?.value as? KSType

    private fun error(code: String, message: String) {
        logger.error("[$code] $message")
        ok = false
    }
}
