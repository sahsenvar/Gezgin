package dev.gezgin.processor.model

/**
 * Deterministic, line-based textual dump of a [GraphModel], written by the processor under the
 * `gezgin.dumpModel=true` KSP option for test assertions (see Task 2.2 brief for the exact grammar).
 *
 * Sections are emitted in a fixed order (graphs, routes, edges, back-edges); within each section
 * entries are sorted by the owning declaration's fully-qualified name so the output never depends on
 * KSP's traversal order.
 */
fun GraphModel.dumpText(): String {
    val lines = mutableListOf<String>()

    graphs.sortedBy { it.fqName }.forEach { graph ->
        lines += "graph ${graph.fqName} flow=${graph.isFlow} resultFlow=${graph.isResultFlow} " +
            "resultType=${graph.resultTypeFq.orDash()} start=${graph.startFq.orDash()} " +
            "parentFlow=${graph.parentFlowFq.orDash()} members=${graph.memberFq.joinOrDash()}"
    }

    val sortedRoutes = routes.sortedBy { it.fqName }
    sortedRoutes.forEach { route ->
        lines += "route ${route.fqName} graph=${route.graphFq} chain=${route.flowChainFq.joinOrDash()} " +
            "start=${route.isStart} noBack=${route.noBack} resultType=${route.resultTypeFq.orDash()} " +
            "params=${route.ctorParams.joinToString(",") { it.dump() }.ifEmpty { "-" }}"
    }

    sortedRoutes.forEach { route ->
        route.edges.forEach { edge ->
            lines += "edge ${route.fqName} ${edge.kind} ${edge.targetFq} singleTop=${edge.singleTop} " +
                "clearUpTo=${edge.clearUpToFq.orDash()} inclusive=${edge.inclusive} " +
                "name=${edge.name.ifEmpty { "-" }}"
        }
    }

    sortedRoutes.forEach { route ->
        route.backEdges.forEach { backEdge ->
            lines += "backedge ${route.fqName} ${backEdge.kind} target=${backEdge.targetFq.orDash()} " +
                "inclusive=${backEdge.inclusive}"
        }
    }

    return lines.joinToString("\n")
}

private fun ParamModel.dump(): String {
    val nullableMark = if (isNullable) "?" else ""
    val defaultMark = if (hasDefault) "=" else ""
    return "$name:$typeFq$nullableMark$defaultMark"
}

private fun String?.orDash(): String = this ?: "-"

private fun List<String>.joinOrDash(): String = if (isEmpty()) "-" else joinToString(",")
