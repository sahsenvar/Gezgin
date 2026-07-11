package dev.gezgin.sample.feature.profile.screen_profile

sealed interface ProfileEffect {
    data class ShowMessage(val text: String) : ProfileEffect
}
