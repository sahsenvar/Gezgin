package dev.gezgin.processor

import com.google.devtools.ksp.processing.KSPLogger
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.model.BackEdgeKind
import dev.gezgin.processor.model.EdgeKind
import dev.gezgin.processor.model.EdgeModel
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel

/**
 * Compile-time strictness gate for Task 2.3: walks the [GraphModel] produced by
 * [dev.gezgin.processor.model.ModelReader] and reports every violation of the Global Constraints
 * rule list (`E1`-`E6`, `G1`, `N9`, `N10`, `N11`, `N12`, `N13`, `R1`, `NB1`, `SD1`, `FX1`, `FX2`) as a
 * KSP error. Each message is
 * prefixed with its bracketed rule code (`"[E1] ..."`) so tests and IDE tooling can key off it; KSP
 * errors fail the enclosing compilation, which is the enforcement mechanism.
 *
 * [validate] never throws — it logs every violation it finds in one pass (rather than stopping at
 * the first) and returns whether the model was clean, purely for caller-side bookkeeping.
 */
class GezginValidator(
    private val model: GraphModel,
    private val logger: KSPLogger,
) {

    private val graphsByFq: Map<String, GraphModelNode> = model.graphs.associateBy { it.fqName }
    private val routesByFq: Map<String, RouteModel> = model.routes.associateBy { it.fqName }

    private var ok = true

    /** [NavigatorCodegen]'in HER navigator sınıfında koşulsuz taşıdığı üye adları — bkz. [checkN10Members]. */
    private val RESERVED_MEMBER_NAMES = setOf("back", "quit", "quitWith", "backWithResult", "raw")

    fun validate(): Boolean {
        model.routes.forEach { route ->
            checkE1(route)
            checkE2(route)
            checkE3(route)
            checkE4(route)
            checkE5(route)
            checkE6(route)
            checkN9(route)
            checkN10Members(route)
            checkNB1(route)
            checkFX1(route)
            checkFX2(route)
        }
        model.graphs.forEach { graph ->
            checkG1(graph)
            checkR1(graph)
            checkSD1(graph)
            checkN11(graph)
            checkN12(graph)
            checkN13(graph)
        }
        checkN10ClassNames()
        return ok
    }

    // region E1/E2 — ResultFlow entry must be via @GoForResult

    /**
     * @GoTo/@ReplaceTo/@QuitAndGoTo may never *enter* a ResultFlow from outside — only @GoForResult
     * may. A source already inside the target flow is exempt (spec §8.1 "İçeride: serbest @GoTo" —
     * entry rules constrain crossing the boundary, not movement within it).
     *
     * The boundary is keyed on [GraphModelNode.declaresResultFlowDirectly], NOT the transitive
     * [GraphModelNode.isResultFlow]: a result-LESS nested sub-flow (`ZoomFlow : AvatarFlow` where
     * only `AvatarFlow : ResultFlow<…>`) is transitively a ResultFlow but owns no result contract of
     * its own — `@GoTo`-ing into it from within its enclosing result flow crosses no result boundary
     * (§6 "nested ResultFlow" / the sample's AvatarFlow→ZoomFlow). Entering such a sub-flow from
     * *outside* the enclosing flow is still rejected — by E3 (jumping into a flow's interior).
     */
    private fun checkE1(route: RouteModel) {
        route.edges
            .filter { it.kind == EdgeKind.GO_TO || it.kind == EdgeKind.REPLACE_TO || it.kind == EdgeKind.QUIT_AND_GO_TO }
            .forEach { edge ->
                val target = edge.targetFq
                val targetAsGraph = graphsByFq[target]
                val parent = parentGraphOf(target)
                val entersResultFlowDirectly = targetAsGraph != null && targetAsGraph.declaresResultFlowDirectly &&
                    targetAsGraph.fqName !in route.flowChainFq
                val entersResultFlowViaStart = parent != null && parent.declaresResultFlowDirectly &&
                    parent.startFq == target && parent.fqName !in route.flowChainFq
                if (entersResultFlowDirectly) {
                    error(
                        "E1",
                        "@${edge.kind.annotationName()} target ${simple(target)} is a ResultFlow; only " +
                            "@GoForResult may enter it (source: ${route.simpleName})",
                    )
                } else if (entersResultFlowViaStart) {
                    // Target is a plain ROUTE, not the flow-graph type itself — it's a ResultFlow only
                    // by being that flow's @StartDestination, so the message must name it as such
                    // rather than (incorrectly) calling the route itself "a ResultFlow".
                    error(
                        "E1",
                        "@${edge.kind.annotationName()} target ${simple(target)} is the start route of " +
                            "ResultFlow ${simple(parent!!.fqName)}; only @GoForResult may enter it (source: " +
                            "${route.simpleName})",
                    )
                }
            }
    }

    /** @GoForResult must target either a ResultRoute or a ResultFlow (graph-typed) entry. */
    private fun checkE2(route: RouteModel) {
        route.edges.filter { it.kind == EdgeKind.GO_FOR_RESULT }.forEach { edge ->
            val target = edge.targetFq
            val targetAsGraph = graphsByFq[target]
            val targetAsRoute = routesByFq[target]
            val isValid = (targetAsGraph != null && targetAsGraph.isResultFlow) ||
                (targetAsRoute != null && targetAsRoute.resultTypeFq != null)
            if (!isValid) {
                error(
                    "E2",
                    "@GoForResult target ${simple(target)} is neither a ResultRoute nor a ResultFlow; " +
                        "no result can be produced (source: ${route.simpleName})",
                )
            }
        }
    }

    // endregion

    // region E3/E4 — flow-membership boundaries

    /**
     * A forward-edge target reachable only by crossing a flow boundary the source doesn't share is
     * rejected: EVERY flow enclosing the target must be in the source's own flow chain, with exactly
     * ONE exemption — the legal container-entry level. A route target exempts its INNERMOST
     * enclosing flow when it IS that flow's `@StartDestination` (entering a flow via its start is
     * precisely what a `@GoTo(SomeFlow::class)` container edge pushes); a flow-container target
     * exempts itself (its own ancestors still count). Everything ABOVE the exempted level must
     * already be in the source's chain — the walk covers the whole ancestor chain, closing the
     * grandchild-start hole: from outside AvatarFlow, `@GoTo(ZoomRoute)` (start of the nested
     * ZoomFlow) exempts ZoomFlow but still crosses AvatarFlow's boundary → E3 (the old single-level
     * parent check let it slip past both E3 and — post the direct-declaration E1 fix — E1 too,
     * silently bypassing AvatarFlow's result contract). From INSIDE AvatarFlow the same edge stays
     * legal (AvatarFlow is in the source's chain — §8.1 "içeride serbest @GoTo").
     *
     * Back-edges (`@BackTo`) are out of scope per spec §4.2: forward edges define topology, back
     * edges walk existing history (a `@BackTo` whose target isn't on the back stack is a runtime
     * no-op, not a topology error).
     */
    private fun checkE3(route: RouteModel) {
        route.edges.map { it.targetFq }.forEach { target ->
            val targetRoute = routesByFq[target]
            val crossedFlows: List<String> = when {
                targetRoute != null -> {
                    val chain = targetRoute.flowChainFq
                    val innermost = chain.lastOrNull()
                    // Start-route exemption applies ONLY to the target's own (innermost) container.
                    if (innermost != null && graphsByFq[innermost]?.startFq == target) chain.dropLast(1) else chain
                }
                target in graphsByFq -> ancestorFlowChainOf(target)
                else -> emptyList() // unresolved target — E6's problem, not a boundary question
            }
            val violated = crossedFlows.firstOrNull { it !in route.flowChainFq }
            if (violated != null) {
                error(
                    "E3",
                    "${simple(target)} is an inner member of flow ${simple(violated)}, which is outside " +
                        "the source (${route.simpleName}) flow chain; target the flow container " +
                        "${simple(violated)} itself instead of its inner member",
                )
            }
        }
    }

    /** Flows lexically enclosing graph [graphFq] (outermost→innermost), NOT including [graphFq] itself. */
    private fun ancestorFlowChainOf(graphFq: String): List<String> {
        val chain = mutableListOf<String>()
        var cur = graphsByFq[graphFq]?.parentFlowFq
        while (cur != null) {
            chain.add(0, cur)
            cur = graphsByFq[cur]?.parentFlowFq
        }
        return chain
    }

    /**
     * `@ReplaceTo.clearUpTo` on a flow member must clear up to something within that same innermost
     * flow — TRANSITIVELY: a route nested in a `@NavGraph` (or sub-flow) inside the flow counts as a
     * member too (its `flowChainFq` contains the source's innermost flow). Non-route `clearUpTo`
     * targets (e.g. a graph interface) fall back to the direct `memberFq` check.
     */
    private fun checkE4(route: RouteModel) {
        val innermostFlowFq = route.flowChainFq.lastOrNull() ?: return
        val innermostFlow = graphsByFq[innermostFlowFq] ?: return

        route.edges
            .filter { it.kind == EdgeKind.REPLACE_TO && it.clearUpToFq != null }
            .forEach { edge ->
                val clearUpTo = edge.clearUpToFq!!
                val clearUpToRoute = routesByFq[clearUpTo]
                val isMember = if (clearUpToRoute != null) {
                    innermostFlowFq in clearUpToRoute.flowChainFq
                } else {
                    clearUpTo in innermostFlow.memberFq
                }
                if (!isMember) {
                    error(
                        "E4",
                        "@ReplaceTo.clearUpTo=${simple(clearUpTo)} is not a member of the source " +
                            "(${route.simpleName}) innermost flow ${simple(innermostFlowFq)}",
                    )
                }
            }
    }

    // endregion

    // region E5 — at most one graph-interface per route

    /** A route may only implement the graph interface it is lexically nested inside. */
    private fun checkE5(route: RouteModel) {
        val extra = route.implementedGraphFqs.filter { it != route.graphFq }
        extra.forEach { second ->
            error(
                "E5",
                "${route.simpleName} implements a second graph interface (${simple(second)}) in addition " +
                    "to its owning graph ${simple(route.graphFq)}; a route may implement only its own graph",
            )
        }
    }

    // endregion

    // region N11/N12 — graph/flow parent structure (Task 8.1 flat-file membership)

    /**
     * A graph/flow may declare AT MOST ONE annotated graph/flow supertype — the membership walk
     * (which derives `graphFq`/`flowChainFq`/`parentFlow`) needs a single unambiguous parent.
     * `OrderGraph : AppGraph` (one parent, spec §3.1) is fine; declaring two annotated parents is not.
     * The graph-level parallel of the route-level E5.
     */
    private fun checkN11(graph: GraphModelNode) {
        if (graph.directParentFqs.size >= 2) {
            error(
                "N11",
                "${simple(graph.fqName)} directly implements more than one annotated graph/flow " +
                    "(${graph.directParentFqs.joinToString { simple(it) }}); a graph/flow must have one parent",
            )
        }
    }

    /**
     * An öksüz (orphan) `@FlowGraph`: lexically NESTED inside a non-graph declaration yet declaring no
     * annotated graph/flow supertype, so neither subtyping (`: ParentGraph`) nor nesting connects it
     * to a graph and its members float free. A TOP-LEVEL `@FlowGraph` is a legitimate root/independent
     * flow (entered by an edge, e.g. `@GoForResult`) — [GraphModelNode.isNested] is false there, so it
     * is exempt.
     */
    private fun checkN12(graph: GraphModelNode) {
        if (graph.isFlow && graph.membershipParentFq == null && graph.isNested) {
            error(
                "N12",
                "${simple(graph.fqName)} is a @FlowGraph but is not attached to any annotated graph/flow: " +
                    "it declares no annotated supertype (`: ParentGraph`) and is not enclosed by an " +
                    "annotated graph. Move it to top level for an edge-entered root flow, or attach it " +
                    "with an annotated parent (`: ParentGraph`) / enclosing annotated graph",
            )
        }
    }

    /**
     * Every `@NavGraph`/`@FlowGraph` must be a `sealed interface` (design.md §3.1). Cross-file member
     * discovery ([dev.gezgin.processor.model.ModelReader]) uses `getSealedSubclasses`, which per the KSP
     * contract returns EMPTY for a non-sealed declaration — so a route/flow declaring `: G` in ANOTHER
     * file silently drops out of the model (no navigator/serializer), surfacing later as a misleading
     * SD1/E6 or pure silence. A lexically-nested member survives via the fallback, hiding the gap;
     * enforcing `sealed interface` universally makes the derivation sound. A non-interface graph is
     * rejected too (an `object`/`class` can't be the `: G` supertype its routes need) — not covered by
     * any E-rule.
     */
    private fun checkN13(graph: GraphModelNode) {
        if (!graph.isSealedInterface) {
            val kind = if (graph.isFlow) "@FlowGraph" else "@NavGraph"
            error(
                "N13",
                "${simple(graph.fqName)} is a $kind but is not a `sealed interface`; members declared " +
                    "in separate files would be silently dropped because getSealedSubclasses only finds " +
                    "members for sealed types. Use `sealed interface`",
            )
        }
    }

    // endregion

    // region E6 — every forward-edge/@BackTo target must resolve to a real navigable

    /**
     * Every `@GoTo`/`@ReplaceTo`/`@GoForResult`/`@QuitAndGoTo` target must be either a route in the
     * model or a `@FlowGraph` (whose `@StartDestination` SD1 guarantees exists, so codegen can push
     * it). A `@NavGraph` target has no start to navigate to, and a model-external/unresolved class
     * is not navigable at all — both would make codegen crash (`getValue`/`requireNotNull`) with an
     * ICE + half-written output, so they're rejected here first. `@BackTo`'s target must likewise be
     * a real route in the model.
     */
    private fun checkE6(route: RouteModel) {
        route.edges.forEach { edge ->
            val target = edge.targetFq
            val resolvable = target in routesByFq || graphsByFq[target]?.isFlow == true
            if (!resolvable) {
                error(
                    "E6",
                    "@${edge.kind.annotationName()} target is unresolved or invalid: $target " +
                        "(source: ${route.simpleName}); target must be a route or @FlowGraph",
                )
            }
        }
        route.backEdges
            .filter { it.kind == BackEdgeKind.BACK_TO }
            .forEach { backEdge ->
                val target = backEdge.targetFq
                if (target == null || target !in routesByFq) {
                    error(
                        "E6",
                        "@BackTo target is unresolved or invalid: ${target ?: "?"} " +
                            "(source: ${route.simpleName}); target must be a route in the model",
                    )
                }
            }
    }

    // endregion

    // region N10 — generated navigator name collisions (class-level + member-level)

    /**
     * Two sources whose stripped simple name collapses to the same `X` both emit an `XNavigator`
     * into the single generated package — the second silently overwrites the first. Only routes that
     * actually earn a navigator (same predicate codegen uses) participate.
     */
    private fun checkN10ClassNames() {
        model.routes
            .filter { NavigatorCodegen.hasNavigator(it, graphsByFq) }
            .groupBy { NavigatorCodegen.navigatorX(it.simpleName) }
            .filterValues { it.size >= 2 }
            .forEach { (x, routes) ->
                error(
                    "N10",
                    "${x}Navigator would be generated from multiple sources into the same package " +
                        "(class-name collision): ${routes.joinToString { it.fqName }}",
                )
            }
    }

    /**
     * Within a single source, two edges whose derived method names coincide (e.g. `@GoTo(Detail)`
     * and `@GoTo(DetailRoute)` both → `goToDetail`, or two `@GoForResult`s sharing a `name=` → the
     * same `launchX`/`xResults`/`goToXForResult` triple) would emit duplicate members. Each
     * `@GoForResult` records all three triple members (not just `launchX`) so a collision against
     * either sibling is caught too.
     *
     * Task 3.4 devir: `name=` overrides are also checked against the navigator class's FIXED
     * members ([RESERVED_MEMBER_NAMES] — `back`/`quit`/`quitWith`/`backWithResult`/the public `raw`
     * property) — these exist independent of any single edge, so an override that happens to spell
     * one out (e.g. `@GoTo(X::class, name = "back")`) would silently shadow/duplicate a real member.
     */
    private fun checkN10Members(route: RouteModel) {
        val byMember = linkedMapOf<String, MutableList<String>>()
        fun record(member: String, targetFq: String) {
            byMember.getOrPut(member) { mutableListOf() }.add(simple(targetFq))
        }
        route.edges.forEach { edge ->
            val derived = strip(targetSimpleName(edge.targetFq))
            when (edge.kind) {
                EdgeKind.GO_TO -> record(edge.name.ifEmpty { "goTo$derived" }, edge.targetFq)
                EdgeKind.REPLACE_TO -> record(edge.name.ifEmpty { "replaceTo$derived" }, edge.targetFq)
                EdgeKind.QUIT_AND_GO_TO -> record(edge.name.ifEmpty { "quitAndGoTo$derived" }, edge.targetFq)
                EdgeKind.GO_FOR_RESULT -> {
                    val x = edge.name.ifEmpty { derived }.replaceFirstChar { it.uppercase() }
                    record("launch$x", edge.targetFq)
                    record(x.replaceFirstChar { it.lowercase() } + "Results", edge.targetFq)
                    record("goTo${x}ForResult", edge.targetFq)
                }
            }
        }
        route.backEdges
            .filter { it.kind == BackEdgeKind.BACK_TO }
            .forEach { backEdge -> backEdge.targetFq?.let { record("backTo" + strip(simple(it)), it) } }

        byMember.filterValues { it.size >= 2 }.forEach { (member, targets) ->
            error(
                "N10",
                "${route.simpleName} has multiple edges producing the same member name ($member); " +
                    "conflicting targets: ${targets.joinToString()}",
            )
        }

        byMember.keys.filter { it in RESERVED_MEMBER_NAMES }.forEach { member ->
            error(
                "N10",
                "${route.simpleName} has a name= override producing reserved navigator member " +
                    "($member); reserved members: ${RESERVED_MEMBER_NAMES.joinToString()}",
            )
        }
    }

    // endregion

    // region G1 — FlowGraph start must be parameterless-constructible

    /**
     * A `@FlowGraph`'s start must be constructible with no arguments: an object, or every ctor
     * param either defaulted or nullable (spec-literal — codegen, Task 2.5, passes `null` to
     * nullable params without defaults when constructing the start).
     */
    private fun checkG1(graph: GraphModelNode) {
        if (!graph.isFlow) return
        val start = graph.startFq?.let { routesByFq[it] } ?: return
        val requiredParams = start.ctorParams.filter { !it.hasDefault && !it.isNullable }
        if (requiredParams.isNotEmpty()) {
            error(
                "G1",
                "${simple(graph.fqName)} is a FlowGraph but its start route ${start.simpleName} cannot " +
                    "be constructed without arguments (required ctor params: ${requiredParams.joinToString { it.name }})",
            )
        }
    }

    // endregion

    // region N9 — same-kind duplicate edges to the same target need a disambiguating name

    /** Two edges of the same kind to the same target can't both rely on the empty default name. */
    private fun checkN9(route: RouteModel) {
        route.edges
            .groupBy { it.kind to it.targetFq }
            .forEach { (key, edges) ->
                val unnamed = edges.count { it.name.isEmpty() }
                if (edges.size >= 2 && unnamed >= 2) {
                    val (kind, target) = key
                    error(
                        "N9",
                        "${route.simpleName} has two unnamed edges of the same kind to the same target " +
                            "(${simple(target)}, $kind); add name= to at least one edge to disambiguate",
                    )
                }
            }
    }

    // endregion

    // region R1 — only @FlowGraph may implement ResultFlow<T>

    /** `ResultFlow<T>` (even transitively) is reserved for `@FlowGraph`-annotated interfaces. */
    private fun checkR1(graph: GraphModelNode) {
        if (graph.isResultFlow && !graph.isFlow) {
            error(
                "R1",
                "${simple(graph.fqName)} implements ResultFlow<T> but is not annotated @FlowGraph; " +
                    "only @FlowGraph may be a ResultFlow",
            )
        }
    }

    // endregion

    // region NB1 — @NoBack and @StartDestination are mutually exclusive

    private fun checkNB1(route: RouteModel) {
        if (route.noBack && route.isStart) {
            error("NB1", "${route.simpleName} cannot be both @NoBack and @StartDestination")
        }
    }

    // endregion

    // region SD1 — exactly one start per FlowGraph, no start in a NavGraph

    private fun checkSD1(graph: GraphModelNode) {
        val starts = model.routes.filter { it.graphFq == graph.fqName && it.isStart }
        if (graph.isFlow) {
            if (starts.size != 1) {
                error(
                    "SD1",
                    "${simple(graph.fqName)} is a FlowGraph but has ${starts.size} @StartDestination " +
                        "members (exactly 1 required)",
                )
            }
        } else if (starts.isNotEmpty()) {
            error(
                "SD1",
                "@StartDestination may only be used in a @FlowGraph; ${simple(graph.fqName)} is a " +
                    "@NavGraph (members: ${starts.joinToString { it.simpleName }})",
            )
        }
    }

    // endregion

    // region FX1/FX2 — flow-scoped back-navigation guardrails

    /** `@BackToStart`/`@Quit` need a flow to return to/quit out of. */
    private fun checkFX1(route: RouteModel) {
        val hasFlowScopedBackEdge = route.backEdges.any {
            it.kind == BackEdgeKind.BACK_TO_START || it.kind == BackEdgeKind.QUIT
        }
        if (hasFlowScopedBackEdge && route.flowChainFq.isEmpty()) {
            error(
                "FX1",
                "${route.simpleName} uses @BackToStart/@Quit but is not a flow member; there is no " +
                    "flow to return to or quit",
            )
        }
    }

    /** `@QuitAndGoTo` inside a ResultFlow silently drops the expected result — use a result-returning edge instead. */
    private fun checkFX2(route: RouteModel) {
        val insideResultFlow = route.flowChainFq.any { graphsByFq[it]?.isResultFlow == true }
        if (insideResultFlow && route.edges.any { it.kind == EdgeKind.QUIT_AND_GO_TO }) {
            error(
                "FX2",
                "${route.simpleName} uses @QuitAndGoTo while inside a ResultFlow; the expected result " +
                    "would be lost. Use a result-returning back edge instead",
            )
        }
    }

    // endregion

    // region Deliberately-omitted modal guards (Task 4.3 adjudication — see spec §7)
    //
    // `@QuitAndGoTo(modal)` is spec'd as a KSP *warning*, but it is NOT reliably implementable in
    // Gezgin's canonical cross-module architecture (§3.3) and is therefore DELIBERATELY NOT checked
    // here. The reason is a compilation-unit split: the `@QuitAndGoTo(X)` edge lives on a route
    // interface in the nav module (this `GraphModel`), whereas X's modal-*kind* lives on a
    // `@Dialog`/`@BottomSheet`/`@FullscreenModal` composable in a separate feature module
    // (`EntryFunctionModel`, read by EntryModelReader). This validator runs in the graph-owning nav
    // module, whose resolver cannot see the feature module's composable → the target's kind is
    // invisible. The feature module's run DOES see kind, but there `model.graphs.isEmpty()` so no
    // `@QuitAndGoTo` edges are validated. The join is only possible in a single-module (monolith)
    // build; shipping a monolith-only best-effort warning would give inconsistent signal for
    // identical code across build topologies, so it is intentionally not shipped. Moreover the case
    // does not crash at runtime — `@QuitAndGoTo(X)` yields `[.., Caller, X]` (caller stays below), so
    // OverlayScene's non-empty-overlaid invariant holds; it is an advisory design-smell, not a
    // correctness violation. Consequence: ALL §7 modal guards are runtime — modal props are
    // route-instance values (KSP-invisible, §2.4) and modal kind is feature-module-local.
    //
    // endregion

    // region Helpers

    /** The graph node [fq] is a direct (lexically nested) member of, if any. */
    private fun parentGraphOf(fq: String): GraphModelNode? = model.graphs.firstOrNull { fq in it.memberFq }

    private fun simple(fq: String): String = fq.substringAfterLast('.')

    /** A forward-edge target's simple name — the route's own name, or a graph's simple name. */
    private fun targetSimpleName(targetFq: String): String =
        routesByFq[targetFq]?.simpleName ?: simple(targetFq)

    /** Route/Screen/Flow-suffix strip, byte-identical to the navigator codegen's `X` derivation. */
    private fun strip(simpleName: String): String = NavigatorCodegen.navigatorX(simpleName)

    private fun EdgeKind.annotationName(): String = when (this) {
        EdgeKind.GO_TO -> "GoTo"
        EdgeKind.REPLACE_TO -> "ReplaceTo"
        EdgeKind.GO_FOR_RESULT -> "GoForResult"
        EdgeKind.QUIT_AND_GO_TO -> "QuitAndGoTo"
    }

    private fun error(code: String, message: String) {
        logger.error("[$code] $message")
        ok = false
    }

    // endregion
}
