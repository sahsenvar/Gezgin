package dev.gezgin.compat.zad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.gezgin.core.annotation.Screen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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

@ViewModelOf(ZadCompatibilityRoute::class)
@Composable
fun zadCompatibilityViewModel(route: ZadCompatibilityRoute): ZadCompatibilityViewModel =
  koinViewModel {
    parametersOf(route)
  }

@ViewModelOf(FeaturedCompatibilityRoute::class)
@Composable
fun featuredCompatibilityViewModel(
  route: FeaturedCompatibilityRoute
): FeaturedCompatibilityViewModel = koinViewModel { parametersOf(route) }

@Effects(ZadCompatibilityRoute::class)
fun handleZadCompatibilityEffect(effect: ZadCompatibilityEffect, nav: ZadCompatibilityNavigator) {
  when (effect) {
    ZadCompatibilityEffect.NavigateToFeatured -> nav.goToFeaturedCompatibility()
  }
}

@Effects(FeaturedCompatibilityRoute::class)
fun handleFeaturedCompatibilityEffect(
  effect: FeaturedCompatibilityEffect,
  nav: FeaturedCompatibilityNavigator,
) {
  when (effect) {
    FeaturedCompatibilityEffect.NavigateToHome -> nav.goToZadCompatibility()
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
