package dev.gezgin.processor.routemeta

import com.squareup.kotlinpoet.CodeBlock

internal data class RouteMetaModel(
  val routeName: String,
  val annotations: List<CodeBlock>,
  /** `null` when the route is not inside any annotated graph. */
  val graph: GraphMetaModel?,
)

internal data class GraphMetaModel(
  val fq: String,
  val name: String,
  val isFlow: Boolean,
  val annotations: List<CodeBlock>,
  val parent: GraphMetaModel?,
)
