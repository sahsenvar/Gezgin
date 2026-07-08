package dev.gezgin.processor.model

import com.squareup.kotlinpoet.TypeName

/**
 * A single constructor parameter of a route's primary constructor.
 *
 * [typeFq] is the parameter type's declaration fqName only (no type arguments) — kept for the
 * `gezgin.dumpModel` text dump and back-compat. [typeName] is the FULL KotlinPoet type including
 * generics/nullability (e.g. `List<String>`, `String?`), which navigator codegen forwards verbatim
 * so a generic param compiles instead of degrading to its raw erasure. Carrying a KotlinPoet type
 * makes this model processor-internal (no longer pure data) — an accepted trade for correctness.
 */
data class ParamModel(
    val name: String,
    val typeFq: String,
    val typeName: TypeName,
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
    /**
     * TRANSITIVELY a `ResultFlow<T>` — true for a nested sub-flow that merely inherits an enclosing
     * flow's result contract too (e.g. `ZoomFlow : AvatarFlow` where `AvatarFlow : ResultFlow<…>`).
     * Use for "does `quitWith` apply / what type" style questions. For the E1 entry-boundary check,
     * use [declaresResultFlowDirectly] instead — a nested result-LESS sub-flow is transitively a
     * ResultFlow but establishes no new result contract, so entering it from within the enclosing
     * flow is legal via `@GoTo`.
     */
    val isResultFlow: Boolean,
    /**
     * Declares `ResultFlow<T>` on its OWN supertype list (direct, non-transitive). This is the flow
     * that actually OWNS a result contract — the only kind whose entry from outside requires an
     * awaiting caller (`@GoForResult`, E1). A sub-flow that inherits the marker from a parent has
     * [isResultFlow] `true` but [declaresResultFlowDirectly] `false`.
     */
    val declaresResultFlowDirectly: Boolean,
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
