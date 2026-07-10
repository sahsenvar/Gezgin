package dev.gezgin.sample.navigation

import dev.gezgin.core.annotation.BackToStart
import dev.gezgin.core.annotation.FlowGraph
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.Quit
import dev.gezgin.core.annotation.QuitAndGoTo
import dev.gezgin.core.annotation.StartDestination
import kotlinx.serialization.Serializable

@FlowGraph
@Serializable
sealed interface SignUpFlow : AuthGraph {

    @StartDestination
    @GoTo(ProfileInfoScreenRoute::class)
    @Serializable
    data object CredentialsScreenRoute : SignUpFlow

    @GoTo(TermsScreenRoute::class)
    @Serializable
    data class ProfileInfoScreenRoute(val email: String) : SignUpFlow

    @BackToStart
    @Quit
    @QuitAndGoTo(HomeGraph.WelcomeScreenRoute::class)
    @Serializable
    data object TermsScreenRoute : SignUpFlow
}
