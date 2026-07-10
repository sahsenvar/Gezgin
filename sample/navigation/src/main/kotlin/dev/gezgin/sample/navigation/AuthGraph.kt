package dev.gezgin.sample.navigation

import dev.gezgin.core.DialogContract
import dev.gezgin.core.ResultRoute
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.BackToStart
import dev.gezgin.core.annotation.FlowGraph
import dev.gezgin.core.annotation.GoForResult
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph
import dev.gezgin.core.annotation.Quit
import dev.gezgin.core.annotation.QuitAndGoTo
import dev.gezgin.core.annotation.ReplaceTo
import dev.gezgin.core.annotation.StartDestination
import kotlinx.serialization.Serializable

@NavGraph
@Serializable
sealed interface AuthGraph : Route {

    @GoForResult(ForgotPasswordDialogRoute::class)
    @GoTo(SignUpFlow::class)
    @ReplaceTo(HomeGraph.DashboardScreenRoute::class, name = "loginSuccess")
    @Serializable
    data object LoginScreenRoute : AuthGraph

    @Serializable
    data class ForgotPasswordDialogRoute(val email: String? = null) :
        AuthGraph, ResultRoute<Boolean>, DialogContract {
        override val dismissOnClickOutside: Boolean get() = false
    }

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
}
