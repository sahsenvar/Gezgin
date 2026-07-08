package dev.gezgin.sample.navigation

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

/**
 * Sample showcase — the WHOLE sealed graph tree lives in this `:sample:navigation` (kotlin-jvm)
 * module across `AuthGraph.kt`, `HomeGraph.kt`, and `ProfileGraph.kt` (spec §3.3: sealed subtypes
 * must co-reside). Feature modules and `:app` depend on it and see every route/navigator/topology
 * transitively.
 *
 * Coverage is deliberate (see `sample/README.md`): @NavGraph×3 · result-less @FlowGraph · nested
 * @FlowGraph · ResultFlow<T> · every forward/back/exit annotation · screen- and flow-mode results ·
 * a 3-level transition cascade (app navTransitions [S2] > ProfileGraph interface > SettingsScreenRoute
 * getter) · cross-module @Screen entries (auth/home/profile features) including a cross-module
 * @NoBack (WelcomeScreenRoute, rendered in `:feature:home`).
 */

@NavGraph
@Serializable
sealed interface AuthGraph : Route {

    /** App START candidate for the auth flow; three distinct forward operations. */
    @GoForResult(ForgotPasswordDialogRoute::class)                      // screen-mode result (Boolean)
    @GoTo(SignUpFlow::class)                                            // enter a result-less flow container
    @ReplaceTo(HomeGraph.DashboardScreenRoute::class, name = "loginSuccess")  // Self-default clearUpTo (back != login)
    @Serializable
    data object LoginScreenRoute : AuthGraph

    /** Screen-mode result producer — rendered as a @Dialog composable in :feature:auth. */
    @Serializable
    data class ForgotPasswordDialogRoute(val email: String? = null) : AuthGraph, ResultRoute<Boolean>

    /** Result-less opaque flow (@QuitAndGoTo allowed — no awaiting caller). */
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

        /** Three exit kinds on one member: back-to-start, quit (Canceled), quit-and-go-to. */
        @BackToStart
        @Quit
        @QuitAndGoTo(HomeGraph.WelcomeScreenRoute::class)
        @Serializable
        data object TermsScreenRoute : SignUpFlow
    }
}
