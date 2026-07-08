package dev.gezgin.sample.navigation

import dev.gezgin.core.BottomSheetContract
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

    /** APP START (arg-less / G1). Cross-feature @GoTo to ProfileGraph (B1). Named flow-less result. */
    @GoTo(ItemDetailScreenRoute::class)
    @GoTo(ProfileGraph.ProfileScreenRoute::class)
    @GoForResult(FilterBottomSheetRoute::class, name = "pickSort")       // named screen-mode (SortOrder)
    @Serializable
    data object DashboardScreenRoute : HomeGraph

    @GoTo(ItemDetailScreenRoute::class, singleTop = false, name = "goToRelated")  // 2nd named edge to same target (N9/R2)
    @BackTo(DashboardScreenRoute::class)
    @Serializable
    data class ItemDetailScreenRoute(val id: String) : HomeGraph

    /**
     * `@BottomSheet`-kind result producer — real `ModalBottomSheet` overlay (Faz 4:
     * `GezginBottomSheetSceneStrategy`, arka `DashboardScreenRoute` görünür kalır).
     * `BottomSheetContract.skipPartiallyExpanded = true` — kısa, tek-sütun sıralama listesi ara
     * (yarı-açık) durağı gerektirmiyor; doğrudan tam-açık/gizli. `dismissOnBackPress`/
     * `dismissOnClickOutside` varsayılan (`true`) — swipe-down/scrim-tap/geri-tuşu üçü de
     * `onDismissRequest` → `back()` → (bekleyen sonuç varsa) `Canceled`.
     */
    @Serializable
    data class FilterBottomSheetRoute(val current: String) :
        HomeGraph, ResultRoute<SortOrder>, BottomSheetContract {
        override val skipPartiallyExpanded = true
    }

    /**
     * @NoBack + a still-declared @ReplaceTo — its @Screen composable lives in :feature:home, so this
     * is the CROSS-MODULE @NoBack proof (noBack flows from the route declaration into the feature's
     * generated entry).
     */
    @NoBack
    @ReplaceTo(DashboardScreenRoute::class, name = "continueToDashboard")
    @Serializable
    data class WelcomeScreenRoute(val name: String? = null) : HomeGraph
}
