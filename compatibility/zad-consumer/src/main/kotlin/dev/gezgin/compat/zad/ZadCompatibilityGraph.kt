package dev.gezgin.compat.zad

import dev.gezgin.core.Route
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph
import kotlinx.serialization.Serializable

@NavGraph
sealed interface ZadCompatibilityGraph : Route

@Serializable
@GoTo(FeaturedCompatibilityRoute::class)
data object ZadCompatibilityRoute : ZadCompatibilityGraph

@Serializable
@GoTo(ZadCompatibilityRoute::class)
data object FeaturedCompatibilityRoute : ZadCompatibilityGraph
