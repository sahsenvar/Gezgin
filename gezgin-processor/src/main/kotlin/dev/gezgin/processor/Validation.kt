package dev.gezgin.processor

import com.google.devtools.ksp.processing.KSPLogger
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

    fun validate(): Boolean {
        model.routes.forEach { route ->
            checkE1(route)
            checkE2(route)
            checkE3(route)
            checkE4(route)
            checkE5(route)
            checkN9(route)
            checkNB1(route)
            checkFX1(route)
            checkFX2(route)
        }
        model.graphs.forEach { graph ->
            checkG1(graph)
            checkR1(graph)
            checkSD1(graph)
        }
        return ok
    }

    // region E1/E2 — ResultFlow entry must be via @GoForResult

    /** @GoTo/@ReplaceTo/@QuitAndGoTo may never enter a ResultFlow — only @GoForResult may. */
    private fun checkE1(route: RouteModel) {
        route.edges
            .filter { it.kind == EdgeKind.GO_TO || it.kind == EdgeKind.REPLACE_TO || it.kind == EdgeKind.QUIT_AND_GO_TO }
            .forEach { edge ->
                val target = edge.targetFq
                val targetAsGraph = graphsByFq[target]
                val parent = parentGraphOf(target)
                val entersResultFlow = (targetAsGraph != null && targetAsGraph.isResultFlow) ||
                    (parent != null && parent.isResultFlow && parent.startFq == target)
                if (entersResultFlow) {
                    error(
                        "E1",
                        "@${edge.kind.annotationName()} hedefi ${simple(target)} bir ResultFlow — girişe " +
                            "yalnız @GoForResult izin verir (kaynak: ${route.simpleName})",
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
     * Any edge target that is a direct (non-start) member of a flow the source isn't itself part of
     * is unreachable except through that flow's own entry point.
     */
    private fun checkE3(route: RouteModel) {
        (route.edges.map { it.targetFq } + route.backEdges.mapNotNull { it.targetFq }).forEach { target ->
            val parent = parentGraphOf(target) ?: return@forEach
            val isDirectNonStartMember = target != parent.startFq
            if (parent.isFlow && isDirectNonStartMember && parent.fqName !in route.flowChainFq) {
                error(
                    "E3",
                    "${simple(target)}, ${simple(parent.fqName)} flow'unun bir iç üyesi ve kaynağın " +
                        "(${route.simpleName}) flow-chain'i dışında — doğrudan hedeflenemez, yalnız " +
                        "${simple(parent.fqName)}'un kendisi (start'ı) hedeflenebilir",
                )
            }
        }
    }

    /** `@ReplaceTo.clearUpTo` on a flow member must clear up to something within that same innermost flow. */
    private fun checkE4(route: RouteModel) {
        val innermostFlowFq = route.flowChainFq.lastOrNull() ?: return
        val innermostFlow = graphsByFq[innermostFlowFq] ?: return

        route.edges
            .filter { it.kind == EdgeKind.REPLACE_TO && it.clearUpToFq != null }
            .forEach { edge ->
                val clearUpTo = edge.clearUpToFq!!
                if (clearUpTo !in innermostFlow.memberFq) {
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

    // region G1 — FlowGraph start must be parameterless-constructible

    /** A `@FlowGraph`'s start must be constructible with no arguments (object, or all-defaulted ctor params). */
    private fun checkG1(graph: GraphModelNode) {
        if (!graph.isFlow) return
        val start = graph.startFq?.let { routesByFq[it] } ?: return
        val requiredParams = start.ctorParams.filter { !it.hasDefault }
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
