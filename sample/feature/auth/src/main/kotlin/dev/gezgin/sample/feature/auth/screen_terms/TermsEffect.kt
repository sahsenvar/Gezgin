package dev.gezgin.sample.feature.auth.screen_terms

sealed interface TermsEffect {
    data class ShowMessage(val text: String) : TermsEffect
}
