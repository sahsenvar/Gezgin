package dev.gezgin.sample.shopr.screen_featured_feed

import androidx.compose.runtime.Composable
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.shopr.nav.FeaturedFeedNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import kotlinx.coroutines.flow.Flow

@EffectHandler(HomeGraph.FeaturedFeed::class)
@Composable
fun FeaturedFeedEffectHandler(
    effects: Flow<FeaturedFeedEffect>,
    nav: FeaturedFeedNavigator,
) {
    ObserveEffects(effects) { effect -> handleFeaturedFeedEffect(effect, nav) }
}

internal fun handleFeaturedFeedEffect(effect: FeaturedFeedEffect, nav: FeaturedFeedNavigator) {
    when (effect) {
        is FeaturedFeedEffect.NavigateToFeaturedProduct -> nav.goToProduct(effect.productId)
    }
}
