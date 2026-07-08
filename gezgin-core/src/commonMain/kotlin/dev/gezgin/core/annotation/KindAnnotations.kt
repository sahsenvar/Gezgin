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
annotation class Screen(val route: KClass<out Route> = Route::class)

/**
 * Ad çakışması notu: `androidx.compose.ui.window.Dialog` (Compose'un kendi dialog composable'ı) ile
 * AYNI basit ad — aynı dosyada/paket-import'unda ikisi birden kullanılacaksa import alias'ı önerilir
 * (örn. `import androidx.compose.ui.window.Dialog as ComposeDialog`), aksi halde derleyici ambiguity
 * hatası verir ya da (import sırasına göre) yanlış olan sessizce çözülebilir.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class Dialog(val route: KClass<out Route> = Route::class)

@Target(AnnotationTarget.FUNCTION)
annotation class BottomSheet(val route: KClass<out Route> = Route::class)

@Target(AnnotationTarget.FUNCTION)
annotation class FullscreenModal(val route: KClass<out Route> = Route::class)
