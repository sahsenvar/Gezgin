package dev.gezgin.core

import kotlinx.serialization.Serializable

/**
 * A single stack entry: route instance + unique id + enclosing flow-instance chain. `internal`
 * (K1): part of the process-death snapshot ([SavedState]), which is not exposed on the public ABI.
 */
@Serializable
internal data class GezginKey(
  val route: Route, // polymorphic (via the app SerializersModule)
  val id: Long, // instance identity → Nav3 contentKey (§2.1)
  val flowPath: List<Long> = emptyList(), // enclosing flow-instance chain, outer → inner (§8.1)
)
