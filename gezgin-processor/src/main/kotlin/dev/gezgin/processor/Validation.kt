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
 * rule list (`E1`-`E5`, `G1`, `N9`, `R1`, `NB1`, `SD1`, `FX1`, `FX2`) as a KSP error. Each message is
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
                        "@${edge.kind.annotationName()} hedefi ${simple(target)} bir ResultFlow — girişe " +
                            "yalnız @GoForResult izin verir (kaynak: ${route.simpleName})",
                    )
                } else if (entersResultFlowViaStart) {
                    // Target is a plain ROUTE, not the flow-graph type itself — it's a ResultFlow only
                    // by being that flow's @StartDestination, so the message must name it as such
                    // rather than (incorrectly) calling the route itself "a ResultFlow".
                    error(
                        "E1",
                        "@${edge.kind.annotationName()} hedefi ${simple(target)}, ${simple(parent!!.fqName)} " +
                            "ResultFlow'un START'ı — girişe yalnız @GoForResult izin verir (kaynak: " +
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
                    "@GoForResult hedefi ${simple(target)} ne bir ResultRoute ne de bir ResultFlow — " +
                        "sonuç alınamaz (kaynak: ${route.simpleName})",
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
                    "${simple(target)}, ${simple(violated)} flow'unun bir iç üyesi ve kaynağın " +
                        "(${route.simpleName}) flow-chain'i dışında — doğrudan hedeflenemez, yalnız " +
                        "${simple(violated)}'un kendisi (start'ı) hedeflenebilir",
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
                        "@ReplaceTo.clearUpTo=${simple(clearUpTo)} kaynağın (${route.simpleName}) en " +
                            "içteki flow'u ${simple(innermostFlowFq)}'un üyesi değil",
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
                "${route.simpleName}, nested olduğu ${simple(route.graphFq)} dışında ikinci bir " +
                    "graph-arayüzü (${simple(second)}) implement ediyor — bir route yalnız kendi " +
                    "graph'ını implement edebilir",
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
                    "@${edge.kind.annotationName()} hedefi çözülemedi/geçersiz: $target " +
                        "(kaynak: ${route.simpleName}) — hedef bir route ya da @FlowGraph olmalı",
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
                        "@BackTo hedefi çözülemedi/geçersiz: ${target ?: "?"} " +
                            "(kaynak: ${route.simpleName}) — hedef model'de bir route olmalı",
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
                    "${x}Navigator birden çok kaynaktan üretiliyor (aynı pakete çıkacak sınıf adı " +
                        "çakışması): ${routes.joinToString { it.fqName }}",
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
                "${route.simpleName} içinde iki ayrı edge aynı üye adını ($member) üretiyor — " +
                    "çakışan hedefler: ${targets.joinToString()}",
            )
        }

        byMember.keys.filter { it in RESERVED_MEMBER_NAMES }.forEach { member ->
            error(
                "N10",
                "${route.simpleName} içinde bir name= override üretilen üye adı ($member) " +
                    "navigator'ın rezerve üyeleriyle (${RESERVED_MEMBER_NAMES.joinToString()}) çakışıyor",
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
                "${simple(graph.fqName)} bir FlowGraph ama start'ı ${start.simpleName} argümansız " +
                    "kurulamıyor (zorunlu ctor parametresi var: ${requiredParams.joinToString { it.name }})",
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
                        "${route.simpleName} içinde aynı hedefe (${simple(target)}, $kind) isimsiz iki " +
                            "edge var — birbirinden ayırt edilemez, en az birine name= ver",
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
                "${simple(graph.fqName)} bir ResultFlow<T> implement ediyor ama @FlowGraph değil — " +
                    "yalnız @FlowGraph bir ResultFlow olabilir",
            )
        }
    }

    // endregion

    // region NB1 — @NoBack and @StartDestination are mutually exclusive

    private fun checkNB1(route: RouteModel) {
        if (route.noBack && route.isStart) {
            error("NB1", "${route.simpleName} hem @NoBack hem @StartDestination olamaz")
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
                    "${simple(graph.fqName)} bir FlowGraph ama ${starts.size} @StartDestination var " +
                        "(tam olarak 1 olmalı)",
                )
            }
        } else if (starts.isNotEmpty()) {
            error(
                "SD1",
                "@StartDestination yalnız @FlowGraph üyesinde olabilir — ${simple(graph.fqName)} bir " +
                    "@NavGraph (üye: ${starts.joinToString { it.simpleName }})",
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
                "${route.simpleName} bir flow üyesi değilken @BackToStart/@Quit kullanıyor — dönülecek " +
                    "veya çıkılacak bir flow yok",
            )
        }
    }

    /** `@QuitAndGoTo` inside a ResultFlow silently drops the expected result — use a result-returning edge instead. */
    private fun checkFX2(route: RouteModel) {
        val insideResultFlow = route.flowChainFq.any { graphsByFq[it]?.isResultFlow == true }
        if (insideResultFlow && route.edges.any { it.kind == EdgeKind.QUIT_AND_GO_TO }) {
            error(
                "FX2",
                "${route.simpleName} bir ResultFlow üyesiyken @QuitAndGoTo kullanıyor — beklenen sonuç " +
                    "kaybolur, uygun bir result-dönen back-edge kullan",
            )
        }
    }

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
