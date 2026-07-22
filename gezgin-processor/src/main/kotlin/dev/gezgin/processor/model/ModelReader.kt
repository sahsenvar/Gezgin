package dev.gezgin.processor.model

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.KSValueArgument
import com.squareup.kotlinpoet.ksp.toTypeName

private const val NAV_GRAPH_FQ = "dev.gezgin.core.annotation.NavGraph"
private const val FLOW_GRAPH_FQ = "dev.gezgin.core.annotation.FlowGraph"
private const val START_DESTINATION_FQ = "dev.gezgin.core.annotation.StartDestination"
private const val NO_BACK_FQ = "dev.gezgin.core.annotation.NoBack"
private const val GO_TO_FQ = "dev.gezgin.core.annotation.GoTo"
private const val REPLACE_TO_FQ = "dev.gezgin.core.annotation.ReplaceTo"
private const val GO_FOR_RESULT_FQ = "dev.gezgin.core.annotation.GoForResult"
private const val QUIT_AND_GO_TO_FQ = "dev.gezgin.core.annotation.QuitAndGoTo"
private const val BACK_TO_FQ = "dev.gezgin.core.annotation.BackTo"
private const val BACK_TO_START_FQ = "dev.gezgin.core.annotation.BackToStart"
private const val QUIT_FQ = "dev.gezgin.core.annotation.Quit"
private const val SELF_FQ = "dev.gezgin.core.Self"
private const val RESULT_ROUTE_FQ = "dev.gezgin.core.ResultRoute"
private const val RESULT_FLOW_FQ = "dev.gezgin.core.ResultFlow"

/**
 * Reads every `@NavGraph`/`@FlowGraph`-annotated interface reachable from [resolver], plus the routes
 * (classes/objects) that belong to them, into a semantic [GraphModel].
 *
 * MEMBERSHIP (Task 8.1): a route/sub-graph belongs to the annotated graph/flow it DIRECTLY implements
 * (`: ParentGraph`) — "subtyping = nesting" (design-notes §3), so a member declared in a SEPARATE file
 * is attributed correctly. Member enumeration uses [KSClassDeclaration.getSealedSubclasses] (proven
 * cross-file in the Task 8.0 spike), unioned with lexical children so a member written `: Route`
 * (declaring no annotated supertype but nested inside an annotated graph — e.g. an intervening
 * `@NavGraph`) keeps its pre-8.0 nesting membership byte-for-byte. See [membershipParent].
 *
 * Flow-chain and result-type rules are documented on the individual model types in `GraphModel.kt`.
 */
internal class ModelReader(
    private val resolver: Resolver,
    private val logger: KSPLogger,
) {

    /** Per-read memoization of [membershipParent] — the chain walks would otherwise re-resolve supertypes. */
    private val parentCache = HashMap<String, KSClassDeclaration?>()

    fun read(): GraphModel {
        val graphDecls = collectGraphDeclarations()

        // Candidate members: cross-file sealed subtypes (flat-file, Task 8.0 spike) UNION lexical
        // children (pre-8.0 nesting for `: Route` members). Deduped by fqName — a member reachable
        // both ways is attributed once via [membershipParent].
        val memberDecls = graphDecls
            .flatMap { graph -> graph.getSealedSubclasses() + graph.declarations.filterIsInstance<KSClassDeclaration>() }
            .distinctBy { it.qualifiedName?.asString() }
            .toList()

        val graphs = graphDecls.map { buildGraphNode(it, memberDecls) }

        val routes = memberDecls
            .filter { isRouteDeclaration(it) }
            .filter { membershipParent(it) != null }
            .map { buildRouteModel(it) }

        return GraphModel(
            graphs = graphs.sortedBy { it.fqName },
            routes = routes.sortedBy { it.fqName },
        )
    }

    // region Graph discovery

    private fun collectGraphDeclarations(): List<KSClassDeclaration> {
        val navGraphs = resolver.getSymbolsWithAnnotation(NAV_GRAPH_FQ).filterIsInstance<KSClassDeclaration>()
        val flowGraphs = resolver.getSymbolsWithAnnotation(FLOW_GRAPH_FQ).filterIsInstance<KSClassDeclaration>()
        return (navGraphs + flowGraphs).distinctBy { it.qualifiedName?.asString() }.toList()
    }

    /**
     * Whether `decl` is an instantiable navigable destination (a route), not a subgraph or an
     * intermediate type. `object`s qualify; classes only if NON-abstract and NON-sealed — an
     * abstract/sealed class is a shared base, never a destination, and must not leak into the route
     * list (nor into the polymorphic serializers module as a `subclass()`). Annotated graphs are
     * subgraphs, not routes.
     */
    private fun isRouteDeclaration(decl: KSClassDeclaration): Boolean {
        val instantiable = decl.classKind == ClassKind.OBJECT ||
            (
                decl.classKind == ClassKind.CLASS &&
                    Modifier.ABSTRACT !in decl.modifiers &&
                    Modifier.SEALED !in decl.modifiers
                )
        return instantiable && !decl.isAnnotatedGraph()
    }

    // endregion

    // region Membership derivation (Task 8.1 — supertype-primary, lexical-fallback)

    /**
     * The single annotated graph/flow `decl` is a member of. Primary source is the DIRECT annotated
     * supertype (`: ParentGraph`) so a member declared in a separate file resolves correctly
     * (design-notes §3: "subtyping = nesting"). Fallback is the lexically-enclosing annotated graph,
     * preserving pre-8.0 nesting for a member that declares no annotated supertype (e.g. an
     * intervening `@NavGraph : Route`). When both agree — the lexical parent is itself a declared
     * supertype (the normal nested route) — the lexical parent is chosen, so an E5-style route
     * implementing a SECOND graph still reports its nesting graph as `graphFq`. `null` = no parent by
     * either mechanism (a top-level root graph/flow, or an orphan).
     */
    private fun membershipParent(decl: KSClassDeclaration): KSClassDeclaration? {
        val key = decl.qualifiedName?.asString() ?: return computeMembershipParent(decl)
        if (parentCache.containsKey(key)) return parentCache[key]
        return computeMembershipParent(decl).also { parentCache[key] = it }
    }

    private fun computeMembershipParent(decl: KSClassDeclaration): KSClassDeclaration? {
        val directAnnotated = directAnnotatedGraphSupertypes(decl)
        val lexParent = (decl.parentDeclaration as? KSClassDeclaration)?.takeIf { it.isAnnotatedGraph() }
        val lexFq = lexParent?.qualifiedName?.asString()
        return when {
            lexParent != null && directAnnotated.any { it.qualifiedName?.asString() == lexFq } -> lexParent
            directAnnotated.isNotEmpty() -> directAnnotated.first()
            lexParent != null -> lexParent
            else -> null
        }
    }

    /** Enclosing annotated graphs from outermost to innermost, excluding `decl` itself. */
    private fun enclosingGraphChain(decl: KSClassDeclaration): List<KSClassDeclaration> {
        val chain = mutableListOf<KSClassDeclaration>()
        val seen = mutableSetOf<String>()
        var cur = membershipParent(decl)
        while (cur != null) {
            val fq = cur.qualifiedName?.asString()
            if (fq != null && !seen.add(fq)) break // defensive: never loop on a malformed supertype cycle
            chain.add(0, cur)
            cur = membershipParent(cur)
        }
        return chain
    }

    private fun directAnnotatedGraphSupertypes(decl: KSClassDeclaration): List<KSClassDeclaration> =
        decl.superTypes
            .map { it.resolve().declaration }
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.isAnnotatedGraph() }
            .distinctBy { it.qualifiedName?.asString() }
            .toList()

    private fun KSClassDeclaration.isAnnotatedGraph(): Boolean =
        hasAnnotation(NAV_GRAPH_FQ) || hasAnnotation(FLOW_GRAPH_FQ)

    // endregion

    // region Graph model

    private fun buildGraphNode(graphDecl: KSClassDeclaration, memberDecls: List<KSClassDeclaration>): GraphModelNode {
        val fqName = graphDecl.requireQualifiedName()
        val isFlow = graphDecl.hasAnnotation(FLOW_GRAPH_FQ)
        val resultTypeFq = resultTypeArgOf(graphDecl, RESULT_FLOW_FQ)
        val members = memberDecls.filter { membershipParent(it)?.qualifiedName?.asString() == fqName }
        val startFq = members.firstOrNull { it.hasAnnotation(START_DESTINATION_FQ) }?.requireQualifiedName()
        val parentFlowFq = enclosingGraphChain(graphDecl).lastOrNull { it.hasAnnotation(FLOW_GRAPH_FQ) }
            ?.requireQualifiedName()

        return GraphModelNode(
            fqName = fqName,
            isFlow = isFlow,
            isResultFlow = resultTypeFq != null,
            declaresResultFlowDirectly = graphDecl.directlyImplements(RESULT_FLOW_FQ),
            resultTypeFq = resultTypeFq,
            startFq = startFq,
            memberFq = members.map { it.requireQualifiedName() }.sorted(),
            parentFlowFq = parentFlowFq,
            directParentFqs = implementedGraphFqsOf(graphDecl),
            membershipParentFq = membershipParent(graphDecl)?.qualifiedName?.asString(),
            isNested = graphDecl.parentDeclaration is KSClassDeclaration,
            isSealedInterface = graphDecl.classKind == ClassKind.INTERFACE &&
                Modifier.SEALED in graphDecl.modifiers,
        )
    }

    // endregion

    // region Route model

    private fun buildRouteModel(routeDecl: KSClassDeclaration): RouteModel {
        val chain = enclosingGraphChain(routeDecl)
        val graphFq = chain.last().requireQualifiedName()
        val flowChainFq = chain.filter { it.hasAnnotation(FLOW_GRAPH_FQ) }
            .map { it.requireQualifiedName() }

        return RouteModel(
            fqName = routeDecl.requireQualifiedName(),
            simpleName = routeDecl.simpleName.asString(),
            graphFq = graphFq,
            flowChainFq = flowChainFq,
            ctorParams = ctorParamsOf(routeDecl),
            isStart = routeDecl.hasAnnotation(START_DESTINATION_FQ),
            noBack = routeDecl.hasAnnotation(NO_BACK_FQ),
            resultTypeFq = resultTypeArgOf(routeDecl, RESULT_ROUTE_FQ),
            edges = edgesOf(routeDecl),
            backEdges = backEdgesOf(routeDecl),
            implementedGraphFqs = implementedGraphFqsOf(routeDecl),
        )
    }

    /**
     * Every `@NavGraph`/`@FlowGraph`-annotated interface `decl` implements DIRECTLY (declared
     * supertypes only, no transitive walk). Deliberately non-transitive for E5/N11: a graph interface
     * extending another annotated graph (`OrderGraph : AppGraph`, spec §3.1) makes each of its routes
     * transitively implement the parent graph too — that inheritance must not read as the route (or
     * graph) "implementing a second graph".
     */
    private fun implementedGraphFqsOf(decl: KSClassDeclaration): List<String> =
        directAnnotatedGraphSupertypes(decl).mapNotNull { it.qualifiedName?.asString() }

    private fun ctorParamsOf(decl: KSClassDeclaration): List<ParamModel> =
        decl.primaryConstructor?.parameters.orEmpty().map { param ->
            val resolved = param.type.resolve()
            ParamModel(
                name = param.name?.asString().orEmpty(),
                typeFq = resolved.declaration.qualifiedName?.asString() ?: resolved.toString(),
                typeName = resolved.toTypeName(),
                isNullable = resolved.isMarkedNullable,
                hasDefault = param.hasDefault,
            )
        }

    // endregion

    // region Edges

    private fun edgesOf(decl: KSClassDeclaration): List<EdgeModel> =
        decl.annotations.flatMap { annotation ->
            val kind = when (annotation.fqName()) {
                GO_TO_FQ -> EdgeKind.GO_TO
                REPLACE_TO_FQ -> EdgeKind.REPLACE_TO
                GO_FOR_RESULT_FQ -> EdgeKind.GO_FOR_RESULT
                QUIT_AND_GO_TO_FQ -> EdgeKind.QUIT_AND_GO_TO
                else -> null
            } ?: return@flatMap emptyList()

            val targetTypes = when (kind) {
                EdgeKind.GO_TO -> annotation.classArgs("target")
                EdgeKind.REPLACE_TO,
                EdgeKind.GO_FOR_RESULT,
                EdgeKind.QUIT_AND_GO_TO -> listOfNotNull(annotation.classArg("target"))
            }
            if (targetTypes.isEmpty()) {
                error("@${annotation.shortName.asString()} on ${decl.requireQualifiedName()} has no resolvable target")
            }
            val clearUpTo = annotation.classArg("clearUpTo")?.declaration?.qualifiedName?.asString()
            val clearUpToFq = clearUpTo?.takeUnless { it == SELF_FQ }

            targetTypes.map { targetType ->
                val targetFq = targetType.declaration.qualifiedName?.asString()
                    ?: error(
                        "@${annotation.shortName.asString()} on ${decl.requireQualifiedName()} " +
                            "has no resolvable target",
                    )
                EdgeModel(
                    kind = kind,
                    targetFq = targetFq,
                    // `?: false` fallback'leri savunma amaçlı — gerçek default'lar arguments+defaultArguments
                    // merge'ünden gelir; buraya sadece annotation'da o parametre hiç yoksa düşülür
                    // (ör. @GoForResult'ta singleTop/inclusive olmaması).
                    singleTop = annotation.boolArg("singleTop") ?: false,
                    clearUpToFq = clearUpToFq,
                    inclusive = annotation.boolArg("inclusive") ?: false,
                    name = annotation.stringArg("name").orEmpty(),
                )
            }
        }.toList()

    private fun backEdgesOf(decl: KSClassDeclaration): List<BackEdgeModel> =
        decl.annotations.mapNotNull { annotation ->
            when (annotation.fqName()) {
                BACK_TO_FQ -> BackEdgeModel(
                    kind = BackEdgeKind.BACK_TO,
                    targetFq = annotation.classArg("target")?.declaration?.qualifiedName?.asString(),
                    // Savunma fallback'i — gerçek default arguments+defaultArguments merge'ünden gelir.
                    inclusive = annotation.boolArg("inclusive") ?: false,
                )

                BACK_TO_START_FQ -> BackEdgeModel(kind = BackEdgeKind.BACK_TO_START, targetFq = null)
                QUIT_FQ -> BackEdgeModel(kind = BackEdgeKind.QUIT, targetFq = null)
                else -> null
            }
        }.toList()

    // endregion

    // region Helpers

    /** Whether `decl`'s OWN (declared) supertype list names [fq] — non-transitive (cf. [resultTypeArgOf]). */
    private fun KSClassDeclaration.directlyImplements(fq: String): Boolean =
        superTypes.any { it.resolve().declaration.qualifiedName?.asString() == fq }

    /** `T` from `markerFq<T>` if `decl` transitively implements `markerFq<T>` (substituted), else null. */
    private fun resultTypeArgOf(decl: KSClassDeclaration, markerFq: String): String? {
        val markerType = decl.getAllSuperTypes().firstOrNull { it.declaration.qualifiedName?.asString() == markerFq }
            ?: return null
        val argType = markerType.arguments.firstOrNull()?.type?.resolve() ?: return null
        return argType.declaration.qualifiedName?.asString() ?: argType.toString()
    }

    private fun KSClassDeclaration.requireQualifiedName(): String =
        qualifiedName?.asString() ?: error("Declaration without a qualified name: $this")

    private fun KSAnnotated.hasAnnotation(fq: String): Boolean = annotations.any { it.fqName() == fq }

    private fun KSAnnotation.fqName(): String? = annotationType.resolve().declaration.qualifiedName?.asString()

    private fun KSAnnotation.arg(name: String): KSValueArgument? =
        arguments.firstOrNull { it.name?.asString() == name }
            ?: defaultArguments.firstOrNull { it.name?.asString() == name }

    private fun KSAnnotation.classArg(name: String): KSType? = arg(name)?.value as? KSType

    private fun KSAnnotation.classArgs(name: String): List<KSType> {
        val value = arg(name)?.value ?: return emptyList()
        return when (value) {
            is KSType -> listOf(value)
            is List<*> -> value.filterIsInstance<KSType>()
            is Array<*> -> value.filterIsInstance<KSType>()
            else -> emptyList()
        }
    }

    private fun KSAnnotation.boolArg(name: String): Boolean? = arg(name)?.value as? Boolean

    private fun KSAnnotation.stringArg(name: String): String? = arg(name)?.value as? String

    // endregion
}
