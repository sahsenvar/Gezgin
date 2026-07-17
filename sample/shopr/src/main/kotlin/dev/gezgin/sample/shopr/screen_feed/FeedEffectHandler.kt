package dev.gezgin.sample.shopr.screen_feed

import androidx.compose.runtime.Composable
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.shopr.nav.FeedNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import kotlinx.coroutines.flow.Flow

@EffectHandler(HomeGraph.Feed::class)
@Composable
fun FeedEffectHandler(effects: Flow<FeedEffect>, nav: FeedNavigator) {
    ObserveEffects(effects) { effect -> handleFeedEffect(effect, nav) }
}

internal fun handleFeedEffect(effect: FeedEffect, nav: FeedNavigator) {
    when (effect) {
        FeedEffect.NavigateToCatalog -> nav.goToCatalog()
    }
}
