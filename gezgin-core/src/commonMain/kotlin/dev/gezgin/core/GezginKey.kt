package dev.gezgin.core

import kotlinx.serialization.Serializable

/**
 * A single stack entry: route instance + unique id + enclosing flow-instance chain + tab segment.
 * `internal`: part of the process-death snapshot ([SavedState]), which is not exposed on the public
 * ABI.
 *
 * [segment] names the tab segment the entry lives in (the id of the segment's root entry). An app
 * without tabs has exactly one segment. The default `0` is also what a snapshot written before tab
 * support decodes to, so every old key lands in one shared segment.
 */
@Serializable
internal data class GezginKey(
  val route: Route, // polymorphic (via the app SerializersModule)
  val id: Long, // Instance identity used as the Nav3 content key.
  val flowPath: List<Long> = emptyList(), // Enclosing flow-instance chain, outer to inner.
  val segment: Long = 0, // Tab segment id; entries of one tab stack share it.
)
