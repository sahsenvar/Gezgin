package dev.gezgin.processor.model

/** A single constructor parameter of a route's primary constructor. */
data class ParamModel(
    val name: String,
    val typeFq: String,
    val isNullable: Boolean,
    val hasDefault: Boolean,
)

/** The kind of a forward navigation edge declared on a route. */
enum class EdgeKind { GO_TO, REPLACE_TO, GO_FOR_RESULT, QUIT_AND_GO_TO }

/** The kind of a backward navigation edge declared on a route. */
enum class BackEdgeKind { BACK_TO, BACK_TO_START, QUIT }

/** A forward navigation edge (`@GoTo`/`@ReplaceTo`/`@GoForResult`/`@QuitAndGoTo`) declared on a route. */
data class EdgeModel(
    val kind: EdgeKind,
    val targetFq: String,
    val singleTop: Boolean,
    val clearUpToFq: String?,
    val inclusive: Boolean,
    val name: String,
)

/** A backward navigation edge (`@BackTo`/`@BackToStart`/`@Quit`) declared on a route. */
data class BackEdgeModel(
    val kind: BackEdgeKind,
    val targetFq: String?,
    val inclusive: Boolean = false,
)

/** A single navigable destination: a class/object nested directly inside a graph interface. */
data class RouteModel(
    val fqName: String,
    val simpleName: String,
    val graphFq: String,
    val flowChainFq: List<String>,
    val ctorParams: List<ParamModel>,
    val isStart: Boolean,
    val noBack: Boolean,
    val resultTypeFq: String?,
    val edges: List<EdgeModel>,
    val backEdges: List<BackEdgeModel>,
    /**
     * Every `@NavGraph`/`@FlowGraph`-annotated interface this route implements DIRECTLY (declared
     * supertypes only — deliberately non-transitive so `OrderGraph : AppGraph`-style graph
     * inheritance doesn't leak into routes), including [graphFq] itself. Task 2.3 (E5) needs this
     * beyond the nesting relationship captured by [graphFq]: a route may additionally implement a
     * *second*, non-enclosing graph interface, which is itself the violation.
     */
    val implementedGraphFqs: List<String>,
)

/** A `@NavGraph`/`@FlowGraph`-annotated interface that groups routes (and possibly nested graphs). */
data class GraphModelNode(
    val fqName: String,
    val isFlow: Boolean,
    val isResultFlow: Boolean,
    val resultTypeFq: String?,
    val startFq: String?,
    val memberFq: List<String>,
    val parentFlowFq: String?,
)

/** The full semantic model read from `@NavGraph`/`@FlowGraph`-annotated sources. */
data class GraphModel(
    val graphs: List<GraphModelNode>,
    val routes: List<RouteModel>,
)
