package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.gezgin.processor.model.BackEdgeKind
import dev.gezgin.processor.model.BackEdgeModel
import dev.gezgin.processor.model.EdgeKind
import dev.gezgin.processor.model.EdgeModel
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel

private const val CORE_PKG = "dev.gezgin.core"
private const val FLOW_PKG = "kotlinx.coroutines.flow"

private val RAW_NAVIGATOR = ClassName(CORE_PKG, "RawNavigator")
private val NAV_RESULT = ClassName(CORE_PKG, "NavResult")
private val FLOW = ClassName(FLOW_PKG, "Flow")

/**
 * Task 2.5: emits a typed per-source `<X>Navigator` for every route that declares at least one
 * forward edge (`@GoTo`/`@ReplaceTo`/`@GoForResult`/`@QuitAndGoTo`), back-navigation annotation
 * (`@BackTo`/`@BackToStart`/`@Quit`), result contract (`ResultRoute<T>`), or membership in a
 * `ResultFlow`'s chain (which earns a `quitWith`) — this is the "çekirdek değer önermesi": an
 * undeclared edge simply has no corresponding method, so calling it is an unresolved reference
 * (a compile error), not a runtime failure.
 *
 * A bare route with NONE of the above (e.g. `Catalog` in the shop fixture — no edges, no back
 * annotations, no result contract, not itself inside a `ResultFlow`) gets no navigator at all:
 * generating a class whose only possible member would be the unconditional `back()` isn't useful
 * on its own, and would just be dead API surface every source pays for.
 *
 * `X` is the route's simple name with a trailing `Route`/`Screen`/`Flow` suffix stripped (applied
 * uniformly — including graph names for `@GoForResult` flow-mode member naming, e.g.
 * `CheckoutFlow` → `Checkout`). An edge's `name=` override replaces the derived method name
 * wholesale for `@GoTo`/`@ReplaceTo`/`@QuitAndGoTo` (single method); for the `@GoForResult` triple
 * (`launchX`/`xResults`/`goToXForResult`) it substitutes for `X` itself so all three members stay
 * consistently named. `@BackTo` has no `name=` param at all (see `Annotations.kt`) — its method
 * name is always derived (`backTo` + target's `X`), never overridable.
 */
object NavigatorCodegen {

    private val SUFFIXES = listOf("Route", "Screen", "Flow")

    fun generate(model: GraphModel, packageName: String): List<FileSpec> {
        val graphsByFq = model.graphs.associateBy(GraphModelNode::fqName)
        val routesByFq = model.routes.associateBy(RouteModel::fqName)
        return model.routes.mapNotNull { route -> buildNavigatorFile(route, graphsByFq, routesByFq, packageName) }
    }

    /**
     * Task 2.6 hook (`TestApiCodegen`): the exact same "does this route earn a navigator at all"
     * predicate [buildNavigatorFile] uses for its early-return, exposed so a SEPARATE codegen pass
     * can decide whether a `fromX()` test accessor is even meaningful — a bare route (no navigator
     * class) has nothing for `fromX()` to return.
     */
    internal fun hasNavigator(route: RouteModel, graphsByFq: Map<String, GraphModelNode>): Boolean =
        route.edges.isNotEmpty() ||
            route.backEdges.isNotEmpty() ||
            innermostResultFlowResultTypeFq(route, graphsByFq) != null ||
            route.resultTypeFq != null

    /** `X` derivation (Task 2.6 hook) — see [buildNavigatorFile]'s use for the class-name rule. */
    internal fun navigatorX(simpleName: String): String = stripSuffix(simpleName)

    /** `RawNavigator.xNavigator(entryId)` factory name (Task 2.6 hook) — mirrors [buildNavigatorFile]. */
    internal fun rawFactoryFunName(x: String): String = lowerFirst(x) + "Navigator"

    private fun buildNavigatorFile(
        route: RouteModel,
        graphsByFq: Map<String, GraphModelNode>,
        routesByFq: Map<String, RouteModel>,
        packageName: String,
    ): FileSpec? {
        val members = mutableListOf<FunSpec>()
        val properties = mutableListOf<PropertySpec>()

        route.edges.forEach { edge ->
            when (edge.kind) {
                EdgeKind.GO_TO -> members += goToFun(edge, graphsByFq, routesByFq)
                EdgeKind.REPLACE_TO -> members += replaceToFun(edge, graphsByFq, routesByFq)
                EdgeKind.QUIT_AND_GO_TO -> members += quitAndGoToFun(edge, graphsByFq, routesByFq)
                EdgeKind.GO_FOR_RESULT -> {
                    val (funs, props) = goForResultMembers(route, edge, graphsByFq, routesByFq)
                    members += funs
                    properties += props
                }
            }
        }

        route.backEdges.forEach { backEdge ->
            when (backEdge.kind) {
                BackEdgeKind.BACK_TO -> members += backToFun(backEdge, routesByFq)
                BackEdgeKind.BACK_TO_START -> members += backToStartFun(route, graphsByFq)
                BackEdgeKind.QUIT -> members += quitFun()
            }
        }

        innermostResultFlowResultTypeFq(route, graphsByFq)?.let { resultTypeFq ->
            members += quitWithFun(resultTypeFq)
        }

        route.resultTypeFq?.let { resultTypeFq ->
            members += backWithResultFun(resultTypeFq)
        }

        // A route generates a navigator only if it NEEDS one (see [hasNavigator]) — a bare route
        // with no declared edge, back-annotation, or result-contract would otherwise get an empty
        // (or back()-only) class that's pure dead API surface. `back()` itself is added below as a
        // bonus member on TOP of an already-justified generation, never as the sole justification.
        if (members.isEmpty() && properties.isEmpty()) return null

        if (!route.noBack) members += backFun()

        val x = stripSuffix(route.simpleName)
        val navigatorClassName = "${x}Navigator"
        val navigatorClass = ClassName(packageName, navigatorClassName)

        val classSpec = TypeSpec.classBuilder(navigatorClassName)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("raw", RAW_NAVIGATOR)
                    .addParameter("entryId", LONG)
                    .build(),
            )
            // Public escape hatch (Task 2.5 requirement) — deliberately not `private`.
            .addProperty(PropertySpec.builder("raw", RAW_NAVIGATOR).initializer("raw").build())
            .addProperty(
                PropertySpec.builder("entryId", LONG).addModifiers(KModifier.PRIVATE).initializer("entryId").build(),
            )
            .addFunctions(members)
            .addProperties(properties)
            .build()

        val extensionFun = FunSpec.builder(lowerFirst(x) + "Navigator")
            .receiver(RAW_NAVIGATOR)
            .addParameter("entryId", LONG)
            .returns(navigatorClass)
            .addStatement("return %T(this, entryId)", navigatorClass)
            .build()

        return FileSpec.builder(packageName, navigatorClassName)
            .addType(classSpec)
            .addFunction(extensionFun)
            .build()
    }

    // region @GoTo / @ReplaceTo / @QuitAndGoTo

    /**
     * A forward-edge `target` may be either a leaf route OR a graph interface directly (e.g.
     * `@GoTo(OnboardingFlow::class)` targeting a non-result `@FlowGraph` container "as itself" —
     * validator's E3 only constrains targeting an INNER member of a flow the source isn't part of,
     * not the flow container itself). A graph target resolves to pushing/constructing its
     * `@StartDestination` (G1 guarantees no required ctor params), exactly like `@GoForResult`'s
     * flow-mode — so the generated method itself takes no parameters for a graph target.
     */
    private fun resolveTarget(
        targetFq: String,
        graphsByFq: Map<String, GraphModelNode>,
        routesByFq: Map<String, RouteModel>,
    ): NavigationTarget {
        routesByFq[targetFq]?.let { route ->
            return NavigationTarget(route.simpleName, constructTargetCall(route), paramSpecs(route))
        }
        val graph = graphsByFq.getValue(targetFq)
        val startRoute = routesByFq.getValue(requireNotNull(graph.startFq))
        return NavigationTarget(graph.fqName.substringAfterLast('.'), constructStartCall(startRoute), emptyList())
    }

    private data class NavigationTarget(
        val simpleName: String,
        val constructCall: CodeBlock,
        val params: List<ParameterSpec>,
    )

    private fun goToFun(
        edge: EdgeModel,
        graphsByFq: Map<String, GraphModelNode>,
        routesByFq: Map<String, RouteModel>,
    ): FunSpec {
        val target = resolveTarget(edge.targetFq, graphsByFq, routesByFq)
        val name = edge.name.ifEmpty { "goTo" + stripSuffix(target.simpleName) }
        return FunSpec.builder(name)
            .addParameters(target.params)
            .addStatement("raw.navigate(%L, singleTop = %L)", target.constructCall, edge.singleTop)
            .build()
    }

    private fun replaceToFun(
        edge: EdgeModel,
        graphsByFq: Map<String, GraphModelNode>,
        routesByFq: Map<String, RouteModel>,
    ): FunSpec {
        val target = resolveTarget(edge.targetFq, graphsByFq, routesByFq)
        val name = edge.name.ifEmpty { "replaceTo" + stripSuffix(target.simpleName) }
        val clearUpToBlock = edge.clearUpToFq?.let { CodeBlock.of("%T::class", ClassName.bestGuess(it)) }
            ?: CodeBlock.of("null")
        return FunSpec.builder(name)
            .addParameters(target.params)
            .addStatement(
                "raw.replaceTo(%L, clearUpTo = %L, inclusive = %L)",
                target.constructCall,
                clearUpToBlock,
                edge.inclusive,
            )
            .build()
    }

    private fun quitAndGoToFun(
        edge: EdgeModel,
        graphsByFq: Map<String, GraphModelNode>,
        routesByFq: Map<String, RouteModel>,
    ): FunSpec {
        val target = resolveTarget(edge.targetFq, graphsByFq, routesByFq)
        val name = edge.name.ifEmpty { "quitAndGoTo" + stripSuffix(target.simpleName) }
        return FunSpec.builder(name)
            .addParameters(target.params)
            .addStatement("raw.quitAndGoTo(%L)", target.constructCall)
            .build()
    }

    // endregion

    // region @GoForResult (flow-mode vs screen-mode)

    private fun goForResultMembers(
        route: RouteModel,
        edge: EdgeModel,
        graphsByFq: Map<String, GraphModelNode>,
        routesByFq: Map<String, RouteModel>,
    ): Pair<List<FunSpec>, List<PropertySpec>> {
        val targetGraph = graphsByFq[edge.targetFq]
        val id = edgeId(route.fqName, edge.targetFq, edge.name)

        val shape = if (targetGraph != null) {
            // Flow-mode: target is a @FlowGraph/ResultFlow<T> — push its @StartDestination, which
            // G1 (Task 2.3) guarantees is constructible with no REQUIRED args.
            val resultTypeFq = requireNotNull(targetGraph.resultTypeFq) {
                "@GoForResult target ${edge.targetFq} has no resultTypeFq — should have failed E2 validation"
            }
            val startRoute = routesByFq.getValue(requireNotNull(targetGraph.startFq))
            GoForResultShape(
                resultTypeFq = resultTypeFq,
                pushCall = constructStartCall(startRoute),
                launchParams = emptyList(),
                targetSimpleName = targetGraph.fqName.substringAfterLast('.'),
            )
        } else {
            // Screen-mode: target is a ResultRoute<T> route — its own ctor params become the
            // generated methods' parameters, forwarded straight through.
            val targetRoute = routesByFq.getValue(edge.targetFq)
            val resultTypeFq = requireNotNull(targetRoute.resultTypeFq) {
                "@GoForResult target ${edge.targetFq} has no resultTypeFq — should have failed E2 validation"
            }
            GoForResultShape(
                resultTypeFq = resultTypeFq,
                pushCall = constructTargetCall(targetRoute),
                launchParams = paramSpecs(targetRoute),
                targetSimpleName = targetRoute.simpleName,
            )
        }
        val (resultTypeFq, pushCall, launchParams, targetSimple) = shape

        // X-substitution: a lowerCamel `name=` (e.g. "pickAddress") must still compose into
        // idiomatic member names — UpperCamel where X sits mid-identifier (launchPickAddress /
        // goToPickAddressForResult), lowerCamel where it leads (pickAddressResults).
        val x = (edge.name.ifEmpty { stripSuffix(targetSimple) }).replaceFirstChar { it.uppercase() }
        val resultTypeName = ClassName.bestGuess(resultTypeFq)
        val navResultOfT = NAV_RESULT.parameterizedBy(resultTypeName)

        val launchFun = FunSpec.builder("launch$x")
            .addParameters(launchParams)
            .addStatement("raw.launchForResult(entryId, %S, %L)", id, pushCall)
            .build()

        val resultsProperty = PropertySpec.builder(lowerFirst(x) + "Results", FLOW.parameterizedBy(navResultOfT))
            .getter(
                FunSpec.getterBuilder()
                    .addStatement("return raw.results(entryId, %S)", id)
                    .build(),
            )
            .build()

        val goToForResultFun = FunSpec.builder("goTo${x}ForResult")
            .addModifiers(KModifier.SUSPEND)
            .addParameters(launchParams)
            .returns(navResultOfT)
            .addStatement("return raw.navigateForResult(entryId, %S, %L)", id, pushCall)
            .build()

        return listOf(launchFun, goToForResultFun) to listOf(resultsProperty)
    }

    private data class GoForResultShape(
        val resultTypeFq: String,
        val pushCall: CodeBlock,
        val launchParams: List<ParameterSpec>,
        val targetSimpleName: String,
    )

    // endregion

    // region Back surface

    private fun backFun(): FunSpec =
        FunSpec.builder("back").addStatement("raw.back()").build()

    private fun backToFun(backEdge: BackEdgeModel, routesByFq: Map<String, RouteModel>): FunSpec {
        val target = routesByFq.getValue(requireNotNull(backEdge.targetFq))
        val name = "backTo" + stripSuffix(target.simpleName)
        return FunSpec.builder(name)
            .addStatement(
                "raw.backTo(%T::class, inclusive = %L)",
                ClassName.bestGuess(target.fqName),
                backEdge.inclusive,
            )
            .build()
    }

    /** Target = the START of the source's EN İÇTEKİ (innermost) enclosing flow. */
    private fun backToStartFun(route: RouteModel, graphsByFq: Map<String, GraphModelNode>): FunSpec {
        val innermostFlowFq = route.flowChainFq.last()
        val startFq = requireNotNull(graphsByFq.getValue(innermostFlowFq).startFq)
        return FunSpec.builder("backToStart")
            .addStatement("raw.backTo(%T::class, inclusive = false)", ClassName.bestGuess(startFq))
            .build()
    }

    private fun quitFun(): FunSpec =
        FunSpec.builder("quit").addStatement("raw.quit()").build()

    private fun quitWithFun(resultTypeFq: String): FunSpec {
        val resultType = ClassName.bestGuess(resultTypeFq)
        return FunSpec.builder("quitWith")
            .addParameter("result", resultType)
            .addStatement("raw.quitWith(result)")
            .build()
    }

    private fun backWithResultFun(resultTypeFq: String): FunSpec {
        val resultType = ClassName.bestGuess(resultTypeFq)
        return FunSpec.builder("backWithResult")
            .addParameter("result", resultType)
            .addStatement("raw.backWithResult(result)")
            .build()
    }

    /**
     * The result type of the innermost `ResultFlow` in [route]'s chain — mirrors
     * `RawNavigator.quitWith`'s own runtime resolution (`chain.indexOfLast { it.isResultFlow }`),
     * so the statically generated `quitWith(result: T)` targets the exact same flow instance the
     * raw call would resolve to.
     */
    private fun innermostResultFlowResultTypeFq(route: RouteModel, graphsByFq: Map<String, GraphModelNode>): String? =
        route.flowChainFq.mapNotNull { graphsByFq[it] }.lastOrNull { it.isResultFlow }?.resultTypeFq

    // endregion

    // region Shared helpers

    private fun paramSpecs(route: RouteModel): List<ParameterSpec> =
        route.ctorParams.map { p -> ParameterSpec.builder(p.name, p.typeName).build() }

    /** Every ctor param becomes a required navigator-method param — positional forwarding is safe. */
    private fun constructTargetCall(target: RouteModel): CodeBlock {
        val cn = ClassName.bestGuess(target.fqName)
        if (target.ctorParams.isEmpty()) return CodeBlock.of("%T", cn)
        val block = CodeBlock.builder().add("%T(", cn)
        target.ctorParams.forEachIndexed { index, p ->
            if (index > 0) block.add(", ")
            block.add("%N", p.name)
        }
        return block.add(")").build()
    }

    /**
     * G1 (Task 2.3) guarantees a `@FlowGraph` start has no REQUIRED (non-default, non-nullable)
     * ctor params. The navigator method itself exposes none of these params (the caller never
     * chose the flow's start), so named args are used to skip every defaulted param and pass
     * `null` explicitly for the (guaranteed-nullable) rest.
     */
    private fun constructStartCall(start: RouteModel): CodeBlock {
        val cn = ClassName.bestGuess(start.fqName)
        if (start.ctorParams.isEmpty()) return CodeBlock.of("%T", cn)
        val toPass = start.ctorParams.filterNot { it.hasDefault }
        if (toPass.isEmpty()) return CodeBlock.of("%T()", cn)
        val block = CodeBlock.builder().add("%T(", cn)
        toPass.forEachIndexed { index, p ->
            if (index > 0) block.add(", ")
            block.add("%N = null", p.name)
        }
        return block.add(")").build()
    }

    private fun stripSuffix(simpleName: String): String =
        SUFFIXES.firstOrNull { simpleName.length > it.length && simpleName.endsWith(it) }
            ?.let { simpleName.removeSuffix(it) }
            ?: simpleName

    private fun lowerFirst(s: String): String = s.replaceFirstChar { it.lowercase() }

    // endregion
}
