package dev.gezgin.sample.feature.auth.screen_profile_info

import androidx.lifecycle.ViewModel
import dev.gezgin.mvi.GezginEffects
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.annotation.MviViewModel
import dev.gezgin.sample.navigation.ProfileInfoNavigator
import dev.gezgin.sample.navigation.SignUpFlow.ProfileInfoScreenRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@MviViewModel(ProfileInfoScreenRoute::class)
class ProfileInfoViewModel(
    route: ProfileInfoScreenRoute,
    private val nav: ProfileInfoNavigator,
) : ViewModel(), GezginMvi<ProfileInfoUiState, ProfileInfoIntent, ProfileInfoEffect> {

    private val _uiState = MutableStateFlow(ProfileInfoUiState(route.email))
    override val uiState: StateFlow<ProfileInfoUiState> = _uiState.asStateFlow()

    private val _effects = GezginEffects<ProfileInfoEffect>()
    override val effects: Flow<ProfileInfoEffect> = _effects.flow

    // Giriş ipucu entry yaratılırken gönderilir (nav'dan önce DEĞİL); lossless kanal STARTED'da toplar.
    init {
        _effects.send(ProfileInfoEffect.ShowMessage("Hesap: ${_uiState.value.email}"))
    }

    override fun onIntent(intent: ProfileInfoIntent) {
        when (intent) {
            ProfileInfoIntent.Continue -> nav.goToTerms()
        }
    }
}
