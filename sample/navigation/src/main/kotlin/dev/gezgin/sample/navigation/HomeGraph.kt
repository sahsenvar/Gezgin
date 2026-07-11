package dev.gezgin.sample.navigation

import dev.gezgin.core.BottomSheetContract
import dev.gezgin.core.FullscreenModalContract
import dev.gezgin.core.ResultRoute
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.BackTo
import dev.gezgin.core.annotation.GoForResult
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph
import dev.gezgin.core.annotation.NoBack
import dev.gezgin.core.annotation.ReplaceTo
import kotlinx.serialization.Serializable

@Serializable
enum class SortOrder { RELEVANCE, PRICE_ASC, PRICE_DESC }

@NavGraph
@Serializable
sealed interface HomeGraph : Route {

    @GoTo(ItemDetailScreenRoute::class, ProfileGraph.ProfileScreenRoute::class, HelpScreenRoute::class)
    @GoForResult(FilterBottomSheetRoute::class, name = "pickSort")
    @Serializable
    data object DashboardScreenRoute : HomeGraph

    @GoTo(ItemDetailScreenRoute::class, singleTop = false, name = "goToRelated")
    @GoTo(ItemImageViewerRoute::class)
    @BackTo(DashboardScreenRoute::class)
    @Serializable
    data class ItemDetailScreenRoute(
        val id: String
    ) : HomeGraph


    @BackTo(ItemDetailScreenRoute::class)
    @Serializable
    data class ItemImageViewerRoute(
        val id: String
    ) : HomeGraph, FullscreenModalContract {
        override val dismissOnClickOutside: Boolean get() = false
    }

    @Serializable
    data class FilterBottomSheetRoute(
        val current: String
    ) : HomeGraph, ResultRoute<SortOrder>, BottomSheetContract {
        override val skipPartiallyExpanded: Boolean get() = true
    }

    @NoBack
    @ReplaceTo(DashboardScreenRoute::class, name = "continueToDashboard")
    @Serializable
    data class WelcomeScreenRoute(
        val name: String? = null
    ) : HomeGraph

    @BackTo(DashboardScreenRoute::class)
    @Serializable
    data class HelpScreenRoute(
        val topic: String
    ) : HomeGraph
}
