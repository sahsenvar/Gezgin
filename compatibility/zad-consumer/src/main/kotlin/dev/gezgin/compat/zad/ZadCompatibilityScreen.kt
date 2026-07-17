package dev.gezgin.compat.zad

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ColumnScope
import dev.gezgin.core.annotation.Screen
import dev.gezgin.mvi.annotation.BottomBar
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.mvi.annotation.TopBar
import kotlinx.coroutines.flow.Flow

@Screen(ZadCompatibilityRoute::class)
@Screen(FeaturedCompatibilityRoute::class)
@Composable
fun ColumnScope.ZadCompatibilityScreen(
    state: ZadCompatibilityState,
    onIntent: (ZadCompatibilityIntent) -> Unit,
) {
    state.hashCode()
    onIntent.hashCode()
}

@EffectHandler(ZadCompatibilityRoute::class)
@Composable
fun ZadCompatibilityEffectHandler(
    effects: Flow<ZadCompatibilityEffect>,
    nav: ZadCompatibilityNavigator,
) {
    effects.hashCode()
    nav.hashCode()
}

@EffectHandler(FeaturedCompatibilityRoute::class)
@Composable
fun FeaturedCompatibilityEffectHandler(
    nav: FeaturedCompatibilityNavigator,
    effects: Flow<FeaturedCompatibilityEffect>,
) {
    effects.hashCode()
    nav.hashCode()
}

@TopBar(ZadCompatibilityRoute::class)
@Composable
fun ZadCompatibilityTopBar(
    state: ZadCompatibilityState,
    onIntent: (ZadCompatibilityIntent) -> Unit,
) {
    state.hashCode()
    onIntent.hashCode()
}

@BottomBar(ZadCompatibilityRoute::class)
@Composable
fun ZadCompatibilityBottomBar(
    state: ZadCompatibilityState,
    onIntent: (ZadCompatibilityIntent) -> Unit,
) {
    state.hashCode()
    onIntent.hashCode()
}
