package dev.gezgin.core

/**
 * Gözlem-amaçlı (observe-only) navigasyon olayı (§10). `navigator.events: Flow<NavEvent>` bu tipleri
 * yayar — log/analytics/devtools içindir; akışı ETKİLEMEZ (yalnız gözler).
 */
sealed interface NavEvent {
    /** Yeni bir hedef stack'e itildi. */
    data class Pushed(val route: Route) : NavEvent

    /** Tepedeki hedef pop edildi. */
    data class Popped(val route: Route) : NavEvent

    /** `replaceTo`: `removed` hedefler `pushed` ile değiştirildi. */
    data class Replaced(val removed: List<Route>, val pushed: Route) : NavEvent

    /** `backTo`/`backToStart`: `target`'a kadar pop edildi, `removed` çıkarılanlar. */
    data class PoppedTo(val target: String, val removed: List<Route>) : NavEvent

    /** Bir flow-unit kapandı (`flowInstanceId`); `canceled` = sonuçsuz (quit) mı. */
    data class FlowQuit(val flowInstanceId: Long, val canceled: Boolean) : NavEvent

    /** Bir result kenarının (`edgeId`) sonucu, bekleyen caller olmadığı için düştü. */
    data class ResultDropped(val edgeId: String) : NavEvent

    /** `@BackTo`/`backTo` hedefi (`target`) stack'te bulunamadı — pop yapılmadı. */
    data class BackToTargetMissing(val target: String) : NavEvent

    /** Kök stack'te geri denendi → `onRootBack` tetiklendi (uygulamadan çıkış noktası). */
    data object RootBack : NavEvent
}
