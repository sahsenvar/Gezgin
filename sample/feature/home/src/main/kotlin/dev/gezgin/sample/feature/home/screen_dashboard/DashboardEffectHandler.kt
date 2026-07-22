package dev.gezgin.sample.feature.home.screen_dashboard

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.feature.home.resultIntentSink
import dev.gezgin.sample.navigation.DashboardNavigator
import dev.gezgin.sample.navigation.HomeGraph.DashboardScreenRoute
import kotlinx.coroutines.flow.Flow

@EffectHandler(DashboardScreenRoute::class)
@Composable
fun DashboardEffectHandler(effects: Flow<DashboardEffect>, nav: DashboardNavigator) {
  val context = LocalContext.current
  val resultIntents = effects.resultIntentSink<DashboardIntent>()
  LaunchedEffect(effects) {
    nav.pickSortResults.collect { result ->
      resultIntents.sendResultIntent(DashboardIntent.SortResult(result))
    }
  }
  ObserveEffects(effects) { effect ->
    when (effect) {
      is DashboardEffect.ShowMessage ->
        Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
      else -> handleDashboardEffect(effect, nav)
    }
  }
}

internal fun handleDashboardEffect(effect: DashboardEffect, nav: DashboardNavigator) {
  when (effect) {
    is DashboardEffect.ShowMessage -> Unit
    is DashboardEffect.OpenItem -> nav.goToItemDetail(effect.id)
    DashboardEffect.OpenProfile -> nav.goToProfile()
    is DashboardEffect.OpenHelp -> nav.goToHelp(effect.topic)
    is DashboardEffect.OpenSortPicker -> nav.launchPickSort(effect.current)
  }
}
