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

    /** APP START (arg-less / G1). Cross-feature @GoTo to ProfileGraph (B1). Named flow-less result. */
    @GoTo(ItemDetailScreenRoute::class)
    @GoTo(ProfileGraph.ProfileScreenRoute::class)
    @GoTo(HelpScreenRoute::class)                                        // Faz 6.4 — legacy Fragment yaprağına giriş kenarı
    @GoForResult(FilterBottomSheetRoute::class, name = "pickSort")       // named screen-mode (SortOrder)
    @Serializable
    data object DashboardScreenRoute : HomeGraph

    @GoTo(ItemDetailScreenRoute::class, singleTop = false, name = "goToRelated")  // 2nd named edge to same target (N9/R2)
    @GoTo(ItemImageViewerRoute::class)                                            // Faz 7.2 — @FullscreenModal-kind hedefe giriş kenarı (goToItemImageViewer(id))
    @BackTo(DashboardScreenRoute::class)
    @Serializable
    data class ItemDetailScreenRoute(val id: String) : HomeGraph

    /**
     * Faz 7.2 (GAP-1) — `@FullscreenModal`-kind route: tam-ekran ürün görseli önizleyici. `@Screen`/
     * `@Dialog`/`@BottomSheet` gibi `@FullscreenModal` de composable'da (`:feature:home`); route yalnız
     * kind'ı ve modal davranışını taşır. `FullscreenModalContract`'ın `usePlatformDefaultWidth` YOK
     * (tam-ekran tanımı gereği adapter'da SABİT `false`) → `DialogContract`'tan AYRI bir render kontratı.
     *
     * `dismissOnClickOutside = false`: yanlış bir dış-tık tam-ekran önizleyiciyi kapatmamalı;
     * `dismissOnBackPress` VARSAYILAN (`true`) kalır → geri tuşu/predictive-gesture kapatır. Modal'ın
     * explicit "Kapat" düğmesi `backToItemDetail()` ile pop'lar → açan `ItemDetailScreenRoute`'a döner
     * (görsel doğrudan onun altındadır; bu tipli çıkış, navigator'ı kazandıran `@BackTo` kenarının
     * kendisidir — bkz. aşağıdaki `@BackTo` notu). Result taşımaz (düz `@GoTo` girişi — result deseni
     * zaten `FilterBottomSheet`/dialog'lar/`AvatarFlow`'da kanıtlı).
     */
    @BackTo(ItemDetailScreenRoute::class)
    @Serializable
    data class ItemImageViewerRoute(val id: String) : HomeGraph, FullscreenModalContract {
        override val dismissOnClickOutside: Boolean get() = false
    }

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
        override val skipPartiallyExpanded: Boolean get() = true
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

    /**
     * Faz 6.4 — brownfield Fragment interop örneği (§11.1). Bu route "henüz Compose'a TAŞINMAMIŞ"
     * legacy bir yardım ekranını temsil eder: karşılığı bir `@Screen` composable DEĞİL, `:feature:home`'daki
     * View-tabanlı `@FragmentScreen HelpFragment` (XML layout inflate eden gerçek bir `Fragment`). Route'un
     * DECLARATION'ı burada (`:sample:navigation`), Fragment'ı ayrı bir modülde → codegen route paketini
     * Fragment'ın kendi paketinden DEĞİL, route declaration'ından okur (cross-module).
     *
     * `@BackTo(DashboardScreenRoute)` bir gerçek navigasyon kenarıdır → route bir `HelpNavigator` KAZANIR
     * (`gezginNav<HelpNavigator>()` bunun canlı örneğini registry'den okur; `nav.backToDashboard()` + `back()`).
     * Böylece `gezginNav`'ın navigator'LI yolu canlı host altında GERÇEKTEN çalıştırılır (edge'siz-yaprak/FS5
     * yolu DEĞİL — o Task 6.2 codegen testlerinde kapsanır). `topic` ctor param'ı `gezginArgs<HelpScreenRoute>()`
     * ile Fragment'ın `TextView`'ına yansır (route Bundle'dan → PD-safe).
     */
    @BackTo(DashboardScreenRoute::class)
    @Serializable
    data class HelpScreenRoute(val topic: String) : HomeGraph
}
