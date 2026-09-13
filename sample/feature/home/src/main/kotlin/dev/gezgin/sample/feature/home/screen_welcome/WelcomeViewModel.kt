package dev.gezgin.sample.feature.home.screen_welcome

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.navigation.HomeGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WelcomeViewModel(route: HomeGraph.WelcomeScreenRoute) :
  BaseViewModel<WelcomeUiState, WelcomeIntent, WelcomeEffect>() {

  private val _uiState = MutableStateFlow(WelcomeUiState(name = route.name))
  override val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<WelcomeEffect>()
  override val effects: Flow<WelcomeEffect> = _effects.flow

  override fun onIntent(intent: WelcomeIntent) {
    when (intent) {
      // Efekt Continue'da DEĞİL OnAppear'da: @ReplaceTo Welcome entry'sini kaldırır → Continue'da
      // gönderilen efekti hiçbir observer toplayamadan ekran yok olur (kayıp toast).
      WelcomeIntent.OnAppear ->
        _effects.send(
          WelcomeEffect.ShowMessage(_uiState.value.name?.let { "Merhaba $it" } ?: "Merhaba")
        )
      WelcomeIntent.Continue -> _effects.send(WelcomeEffect.ContinueToDashboard)
    }
  }
}

@ViewModelOf(HomeGraph.WelcomeScreenRoute::class)
@Composable
fun welcomeViewModel(route: HomeGraph.WelcomeScreenRoute): WelcomeViewModel = viewModel {
  WelcomeViewModel(route)
}
