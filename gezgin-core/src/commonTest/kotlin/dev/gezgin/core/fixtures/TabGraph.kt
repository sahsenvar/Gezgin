@file:OptIn(dev.gezgin.core.GezginInternalApi::class)

package dev.gezgin.core.fixtures

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.Route
import dev.gezgin.core.compose.GezginTransition
import dev.gezgin.core.compose.transition
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface TabGraph : Route

@Serializable data object HomeTab : TabGraph

@Serializable data object SearchTab : TabGraph

@Serializable data object ProfileTab : TabGraph

@Serializable data class TabDetail(val id: String) : TabGraph

@Serializable data object TabLogin : TabGraph

val routeSwitchTransitionFixture: GezginTransition = transition {
  switch { fadeIn() togetherWith fadeOut() }
}

@Serializable
data object TabOwnSwitch : TabGraph {
  override val transition: GezginTransition?
    get() = routeSwitchTransitionFixture
}

val tabSerializersModule = SerializersModule {
  polymorphic(Route::class) {
    subclass(HomeTab::class)
    subclass(SearchTab::class)
    subclass(ProfileTab::class)
    subclass(TabDetail::class)
    subclass(TabLogin::class)
  }
}

val tabTopology =
  GezginTopology(
    flowChains = emptyMap(),
    flowStarts = emptyMap(),
    edges = emptyMap(),
    tabRoots = setOf(HomeTab::class, SearchTab::class, ProfileTab::class),
  )
