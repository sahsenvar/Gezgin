package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * Kind annotation'ları (§3.2) — composable üzerinde durur, üç iş yapar: destination = binding,
 * sunum kind'ı, `route:` param tipinden route'a bağ. `route = Route::class` sentinel'i "route'u
 * composable'ın `route:` param tipinden türet" anlamına gelir (argsız route'ta açıkça verilmeli).
 * Processor okuması Faz 3.4'te; bu görevde yalnız tanım.
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class Screen(val route: KClass<out Route> = Route::class)

/**
 * Kind: route'u modal bir **dialog** overlay'i olarak render eder (§3.2/§7) — sunum property'leri için
 * route opsiyonel [dev.gezgin.core.DialogContract] implement edebilir. `route = Route::class` sentinel'i
 * için [Screen]'e bakınız.
 *
 * Ad çakışması notu: `androidx.compose.ui.window.Dialog` (Compose'un kendi dialog composable'ı) ile
 * AYNI basit ad — aynı dosyada/paket-import'unda ikisi birden kullanılacaksa import alias'ı önerilir
 * (örn. `import androidx.compose.ui.window.Dialog as ComposeDialog`), aksi halde derleyici ambiguity
 * hatası verir ya da (import sırasına göre) yanlış olan sessizce çözülebilir.
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class Dialog(val route: KClass<out Route> = Route::class)

/**
 * Kind: route'u alttan açılan modal bir **bottom sheet** olarak render eder (§3.2/§7) — sunum property'leri
 * için route opsiyonel [dev.gezgin.core.BottomSheetContract] implement edebilir; içerik
 * `LocalGezginSheetState` ile sheet'i programatik kapatabilir.
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class BottomSheet(val route: KClass<out Route> = Route::class)

/**
 * Kind: route'u **tam-ekran** modal overlay olarak render eder (§3.2/§7; scrim'siz, `usePlatformDefaultWidth`
 * kavramı yok) — sunum property'leri için route opsiyonel [dev.gezgin.core.FullscreenModalContract]
 * implement edebilir.
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class FullscreenModal(val route: KClass<out Route> = Route::class)
