package dev.gezgin.sample.feature.profile.screen_crop

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.navigation.AvatarFlow.CropScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CropViewModel(route: CropScreenRoute) : BaseViewModel<CropUiState, CropIntent, CropEffect>() {

  private val _uiState = MutableStateFlow(CropUiState(route.source))
  override val uiState: StateFlow<CropUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<CropEffect>()
  override val effects: Flow<CropEffect> = _effects.flow

  // Giriş ipucu entry yaratılırken gönderilir (goToZoom'dan önce DEĞİL); lossless kanal STARTED'da
  // toplar.
  init {
    _effects.send(CropEffect.ShowMessage("Kaynak: ${_uiState.value.source}"))
  }

  override fun onIntent(intent: CropIntent) {
    when (intent) {
      CropIntent.Zoom -> _effects.send(CropEffect.OpenZoom)
      CropIntent.Use ->
        _effects.send(CropEffect.Complete(AvatarChoice(uri = "avatar://${_uiState.value.source}")))
      CropIntent.Cancel -> _effects.send(CropEffect.Back)
    }
  }
}

@ViewModelOf(CropScreenRoute::class)
@Composable
fun cropViewModel(route: CropScreenRoute): CropViewModel = viewModel { CropViewModel(route) }
