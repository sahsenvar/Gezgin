package dev.gezgin.compat.zad

import dev.gezgin.core.Route
import dev.gezgin.core.annotation.NavGraph
import kotlinx.serialization.Serializable

@NavGraph
sealed interface ZadCompatibilityGraph : Route

@Serializable
data object ZadCompatibilityRoute : ZadCompatibilityGraph
