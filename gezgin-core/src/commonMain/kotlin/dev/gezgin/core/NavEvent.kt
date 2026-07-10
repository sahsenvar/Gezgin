package dev.gezgin.core

/**
 * Gözlem-amaçlı (observe-only) navigasyon olayı (§10). `navigator.events: Flow<NavEvent>` bu tipleri
 * yayar — log/analytics/devtools içindir; akışı ETKİLEMEZ (yalnız gözler).
 */
public sealed interface NavEvent {
    /** Yeni bir hedef stack'e itildi. */
    public data class Pushed(val route: Route) : NavEvent

    /** Tepedeki hedef pop edildi. */
    public data class Popped(val route: Route) : NavEvent

    /** `replaceTo`: `removed` hedefler `pushed` ile değiştirildi. */
    public data class Replaced(val removed: List<Route>, val pushed: Route) : NavEvent

    /** `backTo`/`backToStart`: `target`'a kadar pop edildi, `removed` çıkarılanlar. */
    public data class PoppedTo(val target: String, val removed: List<Route>) : NavEvent

    /** Bir flow-unit kapandı (`flowInstanceId`); `canceled` = sonuçsuz (quit) mı. */
    public data class FlowQuit(val flowInstanceId: Long, val canceled: Boolean) : NavEvent

    /** Bir result kenarının (`edgeId`) sonucu, bekleyen caller olmadığı için düştü. */
    public data class ResultDropped(val edgeId: String) : NavEvent

    /** `@BackTo`/`backTo` hedefi (`target`) stack'te bulunamadı — pop yapılmadı. */
    public data class BackToTargetMissing(val target: String) : NavEvent

    /** Kök stack'te geri denendi → `onRootBack` tetiklendi (uygulamadan çıkış noktası). */
    public data object RootBack : NavEvent
}
