package dev.gezgin.compat.zad

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import dev.gezgin.core.annotation.Screen
import dev.gezgin.mvi.ObserveEffects
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
    BasicText(
        text = state.routeName,
        modifier = Modifier.clickable { onIntent(ZadCompatibilityIntent.Navigate) },
    )
}

@EffectHandler(ZadCompatibilityRoute::class)
@Composable
fun ZadCompatibilityEffectHandler(
    effects: Flow<ZadCompatibilityEffect>,
    nav: ZadCompatibilityNavigator,
) {
    ObserveEffects(effects) { effect ->
        when (effect) {
            ZadCompatibilityEffect.NavigateToFeatured -> nav.goToFeaturedCompatibility()
        }
    }
}

@EffectHandler(FeaturedCompatibilityRoute::class)
@Composable
fun FeaturedCompatibilityEffectHandler(
    nav: FeaturedCompatibilityNavigator,
    effects: Flow<FeaturedCompatibilityEffect>,
) {
    ObserveEffects(effects) { effect ->
        when (effect) {
            FeaturedCompatibilityEffect.NavigateToHome -> nav.goToZadCompatibility()
        }
    }
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
