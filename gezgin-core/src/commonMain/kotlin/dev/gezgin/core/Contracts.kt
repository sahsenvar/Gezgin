package dev.gezgin.core

/**
 * Modal sunum property'lerinin **opsiyonel** tipli evi (§7) — bir `@Dialog` route'u bu interface'i
 * implement ederek dialog penceresinin dismiss/layout davranışını route-instance'ından **runtime
 * değer** olarak taşır (KSP'den DEĞİL — §2.4; adapter `route as? DialogContract` ile okur). Route
 * implement etmezse adapter varsayılan [androidx.compose.ui.window.DialogProperties] kullanır
 * (`DialogContract`'ın default'larıyla birebir aynı).
 *
 * `ResultRoute<T>` gibi bir **marker** DEĞİL: PROPERTY taşır. İki besleme yolu:
 * - **SABİT** (route'a özgü, argsız): `override val dismissOnClickOutside = false` — interface
 *   property override'ı (default'lu → yalnız değiştirmek istediğini yaz).
 * - **KOŞULLU** (çağrı-anına bağlı): route ctor param'ından besle —
 *   `data class Confirm(val cancelable: Boolean) : ..., DialogContract {
 *        override val dismissOnClickOutside get() = cancelable }`. Route zaten `@Serializable` →
 *   ekstra iş yok, param serialize edilir (PD-güvenli).
 *
 * Alanlar [androidx.compose.ui.window.DialogProperties]'in gerçek alanlarına **birebir** maplenir
 * (adapter'da, [dev.gezgin.core.compose.toNavEntry]):
 * - [dismissOnBackPress] → `DialogProperties.dismissOnBackPress` (geri tuşu/Esc dialog'u kapatır).
 * - [dismissOnClickOutside] → `DialogProperties.dismissOnClickOutside` (dışarı tık kapatır).
 * - [usePlatformDefaultWidth] → `DialogProperties.usePlatformDefaultWidth`. Spec §7'nin soyut
 *   `layout` property'sinin somut karşılığı: `true` = platform-varsayılan dialog genişliği (sarmalı),
 *   `false` = içerik kendi genişliğini belirler (geniş/tam-ekrana yakın). `layout` yerine bu adı
 *   kullanmak, hangi `DialogProperties` alanına indiğini saklamamak içindir (minimal magic).
 *
 * dismiss (tap-outside/Esc/geri-izin-varken) → dialog scene `onDismissRequest = onBack` →
 * `navigator.back()` → pop; route `ResultRoute` ise caller `Canceled` alır (mevcut `back()` yolu).
 */
interface DialogContract {
    val dismissOnClickOutside: Boolean get() = true
    val dismissOnBackPress: Boolean get() = true
    val usePlatformDefaultWidth: Boolean get() = true
}

/**
 * Tam-ekran modal sunum property'lerinin opsiyonel tipli evi (§7) — [DialogContract]'ın paraleli,
 * ama `usePlatformDefaultWidth` YOK: tam-ekran modal TANIMI gereği içerik genişliğini kendi belirler
 * (`DialogProperties(usePlatformDefaultWidth = false)` — adapter'da SABİT). Bir `@FullscreenModal`
 * route'u yalnız dismiss davranışını taşır. Route implement etmezse adapter default dismiss'lerle
 * (ikisi de `true`) tam-ekran `DialogProperties` kurar.
 *
 * NOT: §7 tam-ekran modal render'ının uçtan-uca on-device doğrulaması (scrim, predictive) Task 4.3'te.
 * 4.1 contract okumayı + metadata wiring'i + guard'ı kurar; DialogSceneStrategy tam-ekran dialog
 * olarak render eder (usePlatformDefaultWidth=false; 4.0 raporu §6).
 */
interface FullscreenModalContract {
    val dismissOnClickOutside: Boolean get() = true
    val dismissOnBackPress: Boolean get() = true
}
