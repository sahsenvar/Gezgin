package dev.gezgin.sample.feature.profile.screen_pick_source

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gezgin.sample.designsystem.BaseViewModel
import dev.gezgin.sample.designsystem.EffectSink
import dev.gezgin.sample.designsystem.ViewModelOf
import dev.gezgin.sample.navigation.AvatarFlow.PickSourceScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PickSourceViewModel : BaseViewModel<PickSourceUiState, PickSourceIntent, PickSourceEffect>() {

  private val _uiState = MutableStateFlow(PickSourceUiState)
  override val uiState: StateFlow<PickSourceUiState> = _uiState.asStateFlow()

  private val _effects = EffectSink<PickSourceEffect>()
  override val effects: Flow<PickSourceEffect> = _effects.flow

  // Giriş ipucu entry yaratılırken gönderilir (goToCrop'tan önce DEĞİL); lossless kanal STARTED'da
  // toplar.
  init {
    _effects.send(PickSourceEffect.ShowMessage("Avatar kaynağı seçin"))
  }

  override fun onIntent(intent: PickSourceIntent) {
    when (intent) {
      PickSourceIntent.PickGallery -> _effects.send(PickSourceEffect.OpenCrop("gallery"))
      PickSourceIntent.PickCamera -> _effects.send(PickSourceEffect.OpenCrop("camera"))
    }
  }
}

@ViewModelOf(PickSourceScreenRoute::class)
@Composable
fun pickSourceViewModel(): PickSourceViewModel = viewModel { PickSourceViewModel() }
