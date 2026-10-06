package dev.gezgin.processor.routemeta

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.gezgin.processor.model.FLOW_GRAPH_FQ
import dev.gezgin.processor.model.GraphMembership
import dev.gezgin.processor.model.hasGezginAnnotation

/**
 * Reads route metadata from the declaration itself rather than from `GraphModel`, so a route
 * compiled into another module is handled like a local one.
 */
internal class RouteMetaReader(logger: KSPLogger) {
  private val membership = GraphMembership()
  private val renderer = AnnotationRenderer(logger)
  private val graphs = HashMap<String, GraphMetaModel>()
  private val inProgress = HashSet<String>()

  fun read(route: KSClassDeclaration): RouteMetaModel {
    val fq = route.qualifiedName?.asString() ?: route.simpleName.asString()
    return RouteMetaModel(
      routeName = route.simpleName.asString(),
      annotations = renderer.renderAll(fq, route.annotations),
      graph = membership.membershipParent(route)?.let(::graphOf),
    )
  }

  private fun graphOf(graph: KSClassDeclaration): GraphMetaModel? {
    val fq = graph.qualifiedName?.asString() ?: graph.simpleName.asString()
    graphs[fq]?.let {
      return it
    }
    // Membership mixes supertypes with lexical nesting, so guard against a malformed cycle.
    if (!inProgress.add(fq)) return null
    val parent = membership.membershipParent(graph)?.let(::graphOf)
    inProgress.remove(fq)
    return GraphMetaModel(
        fq = fq,
        name = graph.simpleName.asString(),
        isFlow = graph.hasGezginAnnotation(FLOW_GRAPH_FQ),
        annotations = renderer.renderAll(fq, graph.annotations),
        parent = parent,
      )
      .also { graphs[fq] = it }
  }
}
