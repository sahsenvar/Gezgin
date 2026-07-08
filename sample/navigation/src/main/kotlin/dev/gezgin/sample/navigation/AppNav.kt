package dev.gezgin.sample.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import dev.gezgin.core.ResultFlow
import dev.gezgin.core.ResultRoute
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.BackTo
import dev.gezgin.core.annotation.BackToStart
import dev.gezgin.core.annotation.FlowGraph
import dev.gezgin.core.annotation.GoForResult
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph
import dev.gezgin.core.annotation.NoBack
import dev.gezgin.core.annotation.Quit
import dev.gezgin.core.annotation.QuitAndGoTo
import dev.gezgin.core.annotation.ReplaceTo
import dev.gezgin.core.annotation.StartDestination
import dev.gezgin.core.compose.GezginTransition
import dev.gezgin.core.compose.transition
import kotlinx.serialization.Serializable

/**
 * Sample showcase — the WHOLE sealed graph tree lives in this single `:sample:navigation`
 * (kotlin-jvm) module (spec §3.3: sealed subtypes must co-reside). Feature modules and `:app`
 * depend on it and see every route/navigator/topology transitively.
 *
 * Coverage is deliberate (see `sample/README.md`): @NavGraph×3 · result-less @FlowGraph · nested
 * @FlowGraph · ResultFlow<T> · every forward/back/exit annotation · screen- and flow-mode results ·
 * a 3-level transition cascade (app navTransitions [S2] > ProfileGraph interface > SettingsRoute
 * getter) · cross-module @Screen entries (auth/home/profile features) including a cross-module
 * @NoBack (WelcomeRoute, rendered in `:feature:home`).
 */

// --- route-arg value types (@Serializable, live in :navigation) ------------------------------

@Serializable
enum class SortOrder { RELEVANCE, PRICE_ASC, PRICE_DESC }

@Serializable
data class AvatarChoice(val uri: String)

// --- root marker: NO graph annotation (E5 positive pattern — sub-graphs join via subtyping) ----

@Serializable
sealed interface AppRoot : Route

// --- AuthGraph ---------------------------------------------------------------------------------

@NavGraph
@Serializable
sealed interface AuthGraph : AppRoot {

    /** App START candidate for the auth flow; three distinct forward operations. */
    @GoForResult(ForgotPasswordDialog::class)                      // screen-mode result (Boolean)
    @GoTo(SignUpFlow::class)                                       // enter a result-less flow container
    @ReplaceTo(HomeGraph.DashboardRoute::class, name = "loginSuccess")  // Self-default clearUpTo (back != login)
    @Serializable
    data object LoginRoute : AuthGraph

    /** Screen-mode result producer — rendered as a @Dialog composable in :feature:auth. */
    @Serializable
    data class ForgotPasswordDialog(val email: String? = null) : AuthGraph, ResultRoute<Boolean>

    /** Result-less opaque flow (@QuitAndGoTo allowed — no awaiting caller). */
    @FlowGraph
    @Serializable
    sealed interface SignUpFlow : AuthGraph {

        @StartDestination
        @GoTo(ProfileInfoRoute::class)
        @Serializable
        data object CredentialsRoute : SignUpFlow

        @GoTo(TermsRoute::class)
        @Serializable
        data class ProfileInfoRoute(val email: String) : SignUpFlow

        /** Three exit kinds on one member: back-to-start, quit (Canceled), quit-and-go-to. */
        @BackToStart
        @Quit
        @QuitAndGoTo(HomeGraph.WelcomeRoute::class)
        @Serializable
        data object TermsRoute : SignUpFlow
    }
}

// --- HomeGraph ---------------------------------------------------------------------------------

@NavGraph
@Serializable
sealed interface HomeGraph : AppRoot {

    /** APP START (arg-less / G1). Cross-feature @GoTo to ProfileGraph (B1). Named flow-less result. */
    @GoTo(ItemDetailRoute::class)
    @GoTo(ProfileGraph.ProfileRoute::class)
    @GoForResult(FilterSheetRoute::class, name = "pickSort")       // named screen-mode (SortOrder)
    @Serializable
    data object DashboardRoute : HomeGraph

    @GoTo(ItemDetailRoute::class, singleTop = false, name = "goToRelated")  // 2nd named edge to same target (N9/R2)
    @BackTo(DashboardRoute::class)
    @Serializable
    data class ItemDetailRoute(val id: String) : HomeGraph

    /** @BottomSheet-kind result producer. */
    @Serializable
    data class FilterSheetRoute(val current: String) : HomeGraph, ResultRoute<SortOrder>

    /**
     * @NoBack + a still-declared @ReplaceTo — its @Screen composable lives in :feature:home, so this
     * is the CROSS-MODULE @NoBack proof (noBack flows from the route declaration into the feature's
     * generated entry).
     */
    @NoBack
    @ReplaceTo(DashboardRoute::class, name = "continueToDashboard")
    @Serializable
    data class WelcomeRoute(val name: String? = null) : HomeGraph
}

// --- ProfileGraph (graph-level transition override — cascade level 2) --------------------------

@NavGraph
@Serializable
sealed interface ProfileGraph : AppRoot {

    override val transition: GezginTransition?
        get() = transition { forward { fadeIn() togetherWith fadeOut() } }

    @GoForResult(EditNameDialog::class)                            // screen-mode (String)
    @GoTo(SettingsRoute::class)
    @GoForResult(AvatarFlow::class, name = "pickAvatar")           // FLOW-mode result (AvatarChoice)
    @Serializable
    data object ProfileRoute : ProfileGraph

    /** Route-level transition override (getter) — cascade level 3 vs. the graph default above. */
    @ReplaceTo(
        AuthGraph.LoginRoute::class,
        clearUpTo = HomeGraph.DashboardRoute::class,
        inclusive = true,
        name = "logout",
    )
    @Serializable
    data object SettingsRoute : ProfileGraph {
        override val transition: GezginTransition?
            get() = transition { forward { slideInHorizontally() togetherWith slideOutHorizontally() } }
    }

    @Serializable
    data class EditNameDialog(val current: String) : ProfileGraph, ResultRoute<String>

    /** Flow that RETURNS a result (ResultFlow<AvatarChoice>) — members get quitWith(AvatarChoice). */
    @FlowGraph
    @Serializable
    sealed interface AvatarFlow : ProfileGraph, ResultFlow<AvatarChoice> {

        @StartDestination
        @GoTo(CropRoute::class)
        @Serializable
        data object PickSourceRoute : AvatarFlow

        @GoTo(ZoomFlow::class)                                     // @GoTo into a result-less nested flow
        @Serializable
        data class CropRoute(val source: String) : AvatarFlow

        /** NESTED @FlowGraph — chain [AvatarFlow, ZoomFlow]; quit() exits from within. */
        @FlowGraph
        @Serializable
        sealed interface ZoomFlow : AvatarFlow {

            @StartDestination
            @Serializable
            data object ZoomRoute : ZoomFlow
        }
    }
}
