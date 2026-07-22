package dev.gezgin.core

import kotlinx.serialization.Serializable

/**
 * A single stack entry: route instance + unique id + enclosing flow-instance chain. `internal`:
 * part of the process-death snapshot ([SavedState]), which is not exposed on the public ABI.
 */
@Serializable
internal data class GezginKey(
  val route: Route, // polymorphic (via the app SerializersModule)
  val id: Long, // Instance identity used as the Nav3 content key.
  val flowPath: List<Long> = emptyList(), // Enclosing flow-instance chain, outer to inner.
)
