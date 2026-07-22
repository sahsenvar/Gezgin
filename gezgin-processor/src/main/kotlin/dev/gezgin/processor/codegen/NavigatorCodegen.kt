package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.AnnotationSpec
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

// Generated navigators carry their source route identity. NavigatorProbe verifies that identity
// when resolving a class across module boundaries, rejecting same-name decoys.
private val GEZGIN_NAVIGATOR_FOR = ClassName("$CORE_PKG.annotation", "GezginNavigatorFor")

/**
 * Emits a typed per-source `<X>Navigator` for every route that permits implicit back navigation, or
 * declares at least one forward edge (`@GoTo`/`@ReplaceTo`/`@GoForResult`/`@QuitAndGoTo`),
 * back-navigation annotation (`@BackTo`/`@BackToStart`/`@Quit`), result contract
 * (`ResultRoute<T>`), or membership in a `ResultFlow`'s chain (which earns a `quitWith`) — this is
 * the core guarantee: an undeclared edge simply has no corresponding method, so calling it is an
 * unresolved reference (a compile error), not a runtime failure.
 *
 * A bare route still gets a navigator whose single operation is `back()`. This is the uniform
 * one-step dismissal API used by screens and modal routes. `@NoBack` is the explicit opt-out; an
 * `@NoBack` route with no other declared operation gets no navigator at all.
 *
 * `X` is the route's simple name with a trailing `Route` stripped first, then a trailing
 * `Screen`/`Flow` kind token stripped; `Dialog`/`BottomSheet` tokens are retained (applied
 * uniformly — including graph names for `@GoForResult` flow-mode member naming, e.g. `CheckoutFlow`
 * → `Checkout`). An edge's `name=` override replaces the derived method name wholesale for
 * `@GoTo`/`@ReplaceTo`/`@QuitAndGoTo` (single method); for the `@GoForResult` triple
 * (`launchX`/`xResults`/`goToXForResult`) it substitutes for `X` itself so all three members stay
 * consistently named. `@BackTo` has no `name=` param at all (see `Annotations.kt`) — its method
 * name is always derived (`backTo` + target's `X`), never overridable.
 */
internal object NavigatorCodegen {

  // Strip Screen and Flow from derived names while retaining Dialog and BottomSheet so modal kind
  // remains visible in generated APIs.
  private val KIND_SUFFIXES = listOf("Screen", "Flow")

  fun generate(model: GraphModel, packageName: String): List<FileSpec> {
    val graphsByFq = model.graphs.associateBy(GraphModelNode::fqName)
    val routesByFq = model.routes.associateBy(RouteModel::fqName)
    return model.routes.mapNotNull { route ->
      buildNavigatorFile(route, graphsByFq, routesByFq, packageName)
    }
  }

  /**
   * Hook (`TestApiCodegen`): the exact same "does this route earn a navigator at all" predicate
   * [buildNavigatorFile] uses for its early-return, exposed so a SEPARATE codegen pass can decide
   * whether a `fromX()` test accessor is meaningful.
   */
  internal fun hasNavigator(route: RouteModel, graphsByFq: Map<String, GraphModelNode>): Boolean =
    !route.noBack ||
      route.edges.isNotEmpty() ||
      route.backEdges.isNotEmpty() ||
      innermostResultFlowResultTypeFq(route, graphsByFq) != null ||
      route.resultTypeFq != null

  /** Derives `X`; see [buildNavigatorFile]'s use for the class-name rule. */
  internal fun navigatorX(simpleName: String): String = stripSuffix(simpleName)

  /** Derives the `RawNavigator.xNavigator(entryId)` factory name used by [buildNavigatorFile]. */
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

    route.resultTypeFq?.let { resultTypeFq -> members += backWithResultFun(resultTypeFq) }

    if (!route.noBack) members += backFun()

    // @NoBack is the only opt-out from the implicit one-step back operation. Such a route still
    // earns a navigator when it declares another typed navigation or result operation.
    if (members.isEmpty() && properties.isEmpty()) return null

    val x = stripSuffix(route.simpleName)
    val navigatorClassName = "${x}Navigator"
    val navigatorClass = ClassName(packageName, navigatorClassName)

    val classSpec =
      TypeSpec.classBuilder(navigatorClassName)
        // Stamp the source route so classpath probes can verify more than the generated name.
        .addAnnotation(
          AnnotationSpec.builder(GEZGIN_NAVIGATOR_FOR)
            .addMember("route = %T::class", ClassName.bestGuess(route.fqName))
            .build()
        )
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .addModifiers(KModifier.INTERNAL)
            .addParameter("raw", RAW_NAVIGATOR)
            .addParameter("entryId", LONG)
            .build()
        )
        // Public escape hatch required by generated callers; deliberately not `private`.
        .addProperty(PropertySpec.builder("raw", RAW_NAVIGATOR).initializer("raw").build())
        .addProperty(
          PropertySpec.builder("entryId", LONG)
            .addModifiers(KModifier.PRIVATE)
            .initializer("entryId")
            .build()
        )
        .addFunctions(members)
        .addProperties(properties)
        .build()

    val extensionFun =
      FunSpec.builder(lowerFirst(x) + "Navigator")
        .receiver(RAW_NAVIGATOR)
        .addParameter("entryId", LONG)
        .returns(navigatorClass)
        .addStatement("return %T(this, entryId)", navigatorClass)
        .build()

    return FileSpec.builder(packageName, navigatorClassName)
      // Every generated navigator applies @GezginNavigatorFor and may call the entry-id
      // RawNavigator overloads, all gated behind @GezginInternalApi.
      .optInGezginInternalApi()
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
    return NavigationTarget(
      graph.fqName.substringAfterLast('.'),
      constructStartCall(startRoute),
      emptyList(),
    )
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
    val clearUpToBlock =
      edge.clearUpToFq?.let { CodeBlock.of("%T::class", ClassName.bestGuess(it)) }
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

    val shape =
      if (targetGraph != null) {
        // Flow-mode: target is a @FlowGraph/ResultFlow<T> — push its @StartDestination, which
        // G1 guarantees is constructible with no REQUIRED args.
        val resultTypeFq =
          requireNotNull(targetGraph.resultTypeFq) {
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
        val resultTypeFq =
          requireNotNull(targetRoute.resultTypeFq) {
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

    val launchFun =
      FunSpec.builder("launch$x")
        .addParameters(launchParams)
        .addStatement("raw.launchForResult(entryId, %S, %L)", id, pushCall)
        .build()

    val resultsProperty =
      PropertySpec.builder(lowerFirst(x) + "Results", FLOW.parameterizedBy(navResultOfT))
        .addKdoc(
          "PD-safe re-attach result stream. Collect this in your ViewModel init{} — on VM recreation " +
            "(config-change AND real process death) the collector re-subscribes and receives the " +
            "persisted pending slot's result. Use this (not the suspend goTo${x}ForResult sugar) when " +
            "the result must survive process death."
        )
        .getter(FunSpec.getterBuilder().addStatement("return raw.results(entryId, %S)", id).build())
        .build()

    val goToForResultFun =
      FunSpec.builder("goTo${x}ForResult")
        .addKdoc(
          "SUSPEND sugar: launch + await in one coroutine. WITHIN-PROCESS-LIFETIME ONLY — the await is " +
            "bound to the calling coroutine; on a REAL process death that coroutine dies and the result " +
            "is DROPPED (nothing re-drives the await). For a PD-safe result use launch$x() + collect " +
            "${lowerFirst(x)}Results in your ViewModel init{}. Device-verified (maestro run-18)."
        )
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

  private fun backFun(): FunSpec = FunSpec.builder("back").addStatement("raw.back()").build()

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

  /** Targets the start of the source route's innermost enclosing flow. */
  private fun backToStartFun(route: RouteModel, graphsByFq: Map<String, GraphModelNode>): FunSpec {
    val innermostFlowFq = route.flowChainFq.last()
    val startFq = requireNotNull(graphsByFq.getValue(innermostFlowFq).startFq)
    return FunSpec.builder("backToStart")
      .addStatement("raw.backTo(%T::class, inclusive = false)", ClassName.bestGuess(startFq))
      .build()
  }

  private fun quitFun(): FunSpec = FunSpec.builder("quit").addStatement("raw.quit()").build()

  private fun quitWithFun(resultTypeFq: String): FunSpec {
    val resultType = ClassName.bestGuess(resultTypeFq)
    return FunSpec.builder("quitWith")
      .addParameter("result", resultType)
      .addStatement("raw.quitWith(result)")
      .build()
  }

  private fun backWithResultFun(resultTypeFq: String): FunSpec {
    val resultType = ClassName.bestGuess(resultTypeFq)
    // The constructor's `entryId` pins the entry that owns this typed navigator.
    // raw.backWithResult delivers only while the owning entry remains top, preventing a late modal
    // result from reaching or popping the unrelated entry underneath it.
    return FunSpec.builder("backWithResult")
      .addParameter("result", resultType)
      .addStatement("raw.backWithResult(entryId, result)")
      .build()
  }

  /**
   * The result type of the innermost result-OWNING (`ResultFlow<T>` DIRECTLY declaring, the current
   * contract ownership) flow in [route]'s chain — mirrors `RawNavigator.quitWith`'s runtime
   * resolution (`chain.indexOfLast { it.isResultFlow }` over the generated topology, whose
   * `isResultFlow` flag is emitted from [GraphModelNode.declaresResultFlowDirectly] — see
   * `TopologyCodegen`), so the statically generated `quitWith(result: T)` targets the exact same
   * flow instance the raw call resolves to. A nested result-less sub-flow (transitive
   * [GraphModelNode.isResultFlow]) owns no contract and must never capture the resolution.
   */
  private fun innermostResultFlowResultTypeFq(
    route: RouteModel,
    graphsByFq: Map<String, GraphModelNode>,
  ): String? =
    route.flowChainFq
      .mapNotNull { graphsByFq[it] }
      .lastOrNull { it.declaresResultFlowDirectly }
      ?.resultTypeFq

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
   * G1 guarantees a `@FlowGraph` start has no REQUIRED (non-default, non-nullable) ctor params. The
   * navigator method itself exposes none of these params (the caller never chose the flow's start),
   * so named args are used to skip every defaulted param and pass `null` explicitly for the
   * (guaranteed-nullable) rest.
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

  // Strip one trailing Route and then one Screen or Flow token without ever emptying the name.
  // Dialog and BottomSheet are intentionally retained.
  private fun stripSuffix(simpleName: String): String {
    var name = simpleName
    if (name.length > "Route".length && name.endsWith("Route")) {
      name = name.removeSuffix("Route")
    }
    val kind = KIND_SUFFIXES.firstOrNull { name.length > it.length && name.endsWith(it) }
    if (kind != null) name = name.removeSuffix(kind)
    return name
  }

  private fun lowerFirst(s: String): String = s.replaceFirstChar { it.lowercase() }

  // endregion
}
