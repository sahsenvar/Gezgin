package dev.gezgin.sample.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import dev.gezgin.core.BottomSheetContract
import dev.gezgin.core.DialogContract
import dev.gezgin.core.ResultRoute
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.GoForResult
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph
import dev.gezgin.core.annotation.ReplaceTo
import dev.gezgin.core.compose.GezginTransition
import dev.gezgin.core.compose.transition
import dev.gezgin.sample.domain.model.NotificationLevel
import kotlinx.serialization.Serializable

@NavGraph
@Serializable
sealed interface ProfileGraph : Route {

    override val transition: GezginTransition?
        get() = transition { forward { fadeIn() togetherWith fadeOut() } }

    @GoForResult(EditNameDialogRoute::class)
    @GoTo(SettingsScreenRoute::class)
    @GoForResult(AvatarFlow::class, name = "pickAvatar")
    @GoForResult(NotificationsSheetRoute::class, name = "pickNotifications")
    @Serializable
    data object ProfileScreenRoute : ProfileGraph

    @ReplaceTo(
        AuthGraph.LoginScreenRoute::class,
        clearUpTo = HomeGraph.DashboardScreenRoute::class,
        inclusive = true,
        name = "logout",
    )
    @Serializable
    data object SettingsScreenRoute : ProfileGraph {
        override val transition: GezginTransition
            get() = transition {
                forward { slideInHorizontally() togetherWith slideOutHorizontally() }
            }
    }

    @Serializable
    data class EditNameDialogRoute(val current: String) : ProfileGraph, ResultRoute<String>,
        DialogContract {
        override val dismissOnClickOutside: Boolean get() = current.isNotBlank()
    }

    // @MviViewModel/@BottomSheet-content/@ScreenEffect üçlüsü :feature:profile'da olmalı (per-module KSP, §10.1).
    @Serializable
    data class NotificationsSheetRoute(
        val current: NotificationLevel
    ) : ProfileGraph, ResultRoute<NotificationLevel>, BottomSheetContract {
        override val skipPartiallyExpanded: Boolean get() = true
    }
}
