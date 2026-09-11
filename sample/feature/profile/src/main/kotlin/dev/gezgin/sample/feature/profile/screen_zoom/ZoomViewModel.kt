package dev.gezgin.sample.feature.profile.screen_zoom

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.navigation.AvatarFlow.ZoomFlow.ZoomScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ZoomViewModel : BaseViewModel<ZoomUiState, ZoomIntent, ZoomEffect>() {

  private val _uiState = MutableStateFlow(ZoomUiState)
  override val uiState: StateFlow<ZoomUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<ZoomEffect>()
  override val effects: Flow<ZoomEffect> = _effects.flow

  // Giriş ipucu entry yaratılırken gönderilir (quitWith'ten önce DEĞİL); lossless kanal STARTED'da
  // toplar.
  init {
    _effects.send(ZoomEffect.ShowMessage("Yakınlaştırıp kareyi seçin"))
  }

  override fun onIntent(intent: ZoomIntent) {
    when (intent) {
      ZoomIntent.UseFrame ->
        _effects.send(ZoomEffect.Complete(AvatarChoice(uri = "zoomed://frame")))
      ZoomIntent.Back -> _effects.send(ZoomEffect.Back)
    }
  }
}

@ViewModelOf(ZoomScreenRoute::class)
@Composable
fun zoomViewModel(): ZoomViewModel = viewModel { ZoomViewModel() }
