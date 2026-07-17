package dev.gezgin.sample.feature.auth.screen_terms

sealed interface TermsEffect {
    data class ShowMessage(val text: String) : TermsEffect
    data object BackToStart : TermsEffect
    data object Quit : TermsEffect
    data class Complete(val name: String) : TermsEffect
}
