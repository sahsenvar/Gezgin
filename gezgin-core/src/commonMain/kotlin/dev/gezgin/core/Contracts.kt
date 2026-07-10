package dev.gezgin.core

/**
 * Modal sunum property'lerinin **opsiyonel** tipli evi (§7) — bir `@Dialog` route'u bu interface'i
 * implement ederek dialog penceresinin dismiss/layout davranışını route-instance'ından **runtime
 * değer** olarak taşır (KSP'den DEĞİL — §2.4; adapter `route as? DialogContract` ile okur). Route
 * implement etmezse adapter varsayılan [androidx.compose.ui.window.DialogProperties] kullanır
 * (`DialogContract`'ın default'larıyla birebir aynı).
 *
 * `ResultRoute<T>` gibi bir **marker** DEĞİL: PROPERTY taşır. İki besleme yolu — **HER İKİSİNDE DE
 * `get() =` biçimini kullan** (aşağıdaki m5 uyarısı):
 * - **SABİT** (route'a özgü, argsız): `override val dismissOnClickOutside get() = false` — interface
 *   property override'ı (default'lu → yalnız değiştirmek istediğini yaz).
 * - **KOŞULLU** (çağrı-anına bağlı): route ctor param'ından besle —
 *   `data class Confirm(val cancelable: Boolean) : ..., DialogContract {
 *        override val dismissOnClickOutside get() = cancelable }`. Route zaten `@Serializable` →
 *   ekstra iş yok, param serialize edilir (PD-güvenli).
 *
 * **m5 — `get() =` ZORUNLU (initializer'lı `val` YAZMA):** `override val dismissOnClickOutside = false`
 * (backing-field'lı initializer) yazılırsa kotlinx.serialization bu property'yi `@Serializable` route'un
 * SERİLEŞEN ŞEMASINA dahil eder → sunum-prop'u saved-state (PD) formatına sızar. `encodeDefaults=false`
 * ile pratik veri zararsız olabilir ama şema kirlenir ve gereksizdir; `get() =` biçimi backing field
 * üretmez (getter-only), şemaya HİÇ girmez. Bu yüzden SABİT durumda bile getter-form kullan.
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
public interface DialogContract {
    public val dismissOnClickOutside: Boolean get() = true
    public val dismissOnBackPress: Boolean get() = true
    public val usePlatformDefaultWidth: Boolean get() = true
}

/**
 * Tam-ekran modal sunum property'lerinin opsiyonel tipli evi (§7) — [DialogContract]'ın paraleli,
 * ama `usePlatformDefaultWidth` YOK: tam-ekran modal TANIMI gereği içerik genişliğini kendi belirler
 * (`DialogProperties(usePlatformDefaultWidth = false)` — adapter'da SABİT). Bir `@FullscreenModal`
 * route'u yalnız dismiss davranışını taşır. Route implement etmezse adapter default dismiss'lerle
 * (ikisi de `true`) tam-ekran `DialogProperties` kurar. **Override'lar `get() =` biçiminde yazılmalı**
 * ([DialogContract]'ın m5 uyarısı — initializer'lı `val` serileşen şemaya sızar).
 *
 * NOT: §7 tam-ekran modal render'ının uçtan-uca on-device doğrulaması (scrim, predictive) Task 4.3'te.
 * 4.1 contract okumayı + metadata wiring'i + guard'ı kurar; DialogSceneStrategy tam-ekran dialog
 * olarak render eder (usePlatformDefaultWidth=false; 4.0 raporu §6).
 */
public interface FullscreenModalContract {
    public val dismissOnClickOutside: Boolean get() = true
    public val dismissOnBackPress: Boolean get() = true
}

/**
 * BottomSheet sunum property'lerinin **opsiyonel** tipli evi (§7) — bir `@BottomSheet` route'u bu
 * interface'i implement ederek modal sheet'in davranışını route-instance'ından **runtime değer** olarak
 * taşır ([DialogContract] ile aynı desen; adapter `route as? BottomSheetContract` ile okur). Route
 * implement etmezse adapter tip-varsayılanları kullanır (aşağıdaki default'larla birebir). Override'lar
 * `get() =` biçiminde yazılmalı ([DialogContract]'ın m5 uyarısı — initializer'lı `val` serileşen şemaya sızar).
 *
 * **Prop seti — material3 `ModalBottomSheet`'in GERÇEK knob'larına maplenen üç alan** ([DialogContract]
 * ile simetrik dismiss ikilisi + sheet'e özgü layout knob'u):
 * - [skipPartiallyExpanded] → `rememberModalBottomSheetState(skipPartiallyExpanded = ...)`. `true` iken
 *   sheet ara (yarı-açık) durağı atlar; doğrudan tam-açık ya da gizli olur (kısa içerik sheet'leri için).
 * - [dismissOnBackPress] → `ModalBottomSheetProperties(shouldDismissOnBackPress = ...)`. `false` iken
 *   geri tuşu sheet'i kapatmaz. [DialogContract.dismissOnBackPress] paraleli; **aynı `@NoBack` guard'ına**
 *   tabidir (adapter'da `require(!(noBack && dismissOnBackPress))` — kuruluş-zamanı runtime, §7).
 * - [dismissOnClickOutside] → `ModalBottomSheetProperties(shouldDismissOnClickOutside = ...)`. scrim-tap
 *   (dışarı tık) sheet'i kapatır mı; default `true`. `false` → tap-outside kapatmaz, ama swipe-down ve
 *   geri-tuşu (izin varsa) HÂLÂ çalışır. [DialogContract.dismissOnClickOutside] paraleli.
 *
 * dismiss (swipe-down / scrim-tap / geri-izin-varken) → sheet `onDismissRequest = onBack` →
 * `navigator.back()` → pop; route `ResultRoute` ise caller `Canceled` alır (mevcut `back()` yolu —
 * material3'te swipe+scrim+back ÜÇÜ de tek `onDismissRequest`'e düşer, jar-doğrulandı).
 */
public interface BottomSheetContract {
    public val skipPartiallyExpanded: Boolean get() = false
    public val dismissOnBackPress: Boolean get() = true
    public val dismissOnClickOutside: Boolean get() = true
}
