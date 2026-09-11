package dev.gezgin.sample.feature.home.screen_dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.gezgin.sample.designsystem.Effects
import dev.gezgin.sample.designsystem.ResultCollector
import dev.gezgin.sample.navigation.DashboardNavigator
import dev.gezgin.sample.navigation.HomeGraph.DashboardScreenRoute

@Effects(DashboardScreenRoute::class)
fun handleDashboardEffect(
  effect: DashboardEffect,
  show: (String) -> Unit,
  nav: DashboardNavigator,
) {
  when (effect) {
    is DashboardEffect.ShowMessage -> show(effect.text)
    is DashboardEffect.OpenItem -> nav.goToItemDetail(effect.id)
    DashboardEffect.OpenProfile -> nav.goToProfile()
    is DashboardEffect.OpenHelp -> nav.goToHelp(effect.topic)
    is DashboardEffect.OpenSortPicker -> nav.launchPickSort(effect.current)
  }
}

@ResultCollector(DashboardScreenRoute::class)
@Composable
fun DashboardResultCollector(onIntent: (DashboardIntent) -> Unit, nav: DashboardNavigator) {
  LaunchedEffect(nav) {
    nav.pickSortResults.collect { result -> onIntent(DashboardIntent.SortResult(result)) }
  }
}
