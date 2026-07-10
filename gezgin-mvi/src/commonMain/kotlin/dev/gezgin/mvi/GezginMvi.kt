package dev.gezgin.mvi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * MVI add-on sözleşmesi (§10.1) — opt-in. Gezgin state-holder dünyasına GİRMEZ (store/reducer dayatmaz);
 * bu yalnız codegen'in okuyacağı **minimal yüzey**: state akışı, opsiyonel efekt akışı, intent girişi.
 *
 * `@ViewModel(Route::class)` işaretli VM bunu implement ETMELİDİR (İKİSİ DE ZORUNLU — guardrail, §10.1):
 * codegen VM'in somut tipini `@ViewModel`'den, **S/I/E'yi bu supertype'ın tip arg'larından** okur
 * (content'ten türetme yok → spec'in E-kaynağı problemi çözülür). `@ViewModel` var ama `GezginMvi`
 * yoksa → derleme hatası (5.1).
 *
 * Variance (`out S, in I, out E`) = polish (S/E üretilir/çıkar, I tüketilir); tip çıkarımını
 * kolaylaştırır, artık yük taşımaz — S/I/E doğrudan supertype arg'larından okunduğu için codegen'e
 * ekstra kısıt getirmez.
 */
public interface GezginMvi<out S, in I, out E> {
    /** UI state akışı — codegen `collectAsStateWithLifecycle()` ile gözler, stateless content'e `state` verir. */
    public val uiState: StateFlow<S>

    /**
     * Opsiyonel tek-seferlik efekt akışı (nav-olmayan yan etkiler; snackbar/toast/haptic). Yoksa
     * `emptyFlow()`. `@ScreenEffect` bunu [ObserveAsEvents] ile **yalnız STARTED iken** tüketir → örtülen
     * (covered) veya STOPPED entry'de gözlemci YOKTUR.
     *
     * **Backing sözleşmesi (kayıpsızlık, Faz 5 recheck / MJ2):** gözlemci yokken emit edilen efektin
     * KAYBOLMAMASI için buffer'lı bir kaynak ŞART. ÖNERİLEN: [GezginEffects] (`Channel(UNLIMITED)
     * .receiveAsFlow()`). `MutableSharedFlow(replay = 0) + tryEmit` deseni gözlemci yokken efekti SESSİZCE
     * düşürür (bkz. [GezginEffects] KDoc'undaki gerekçe). Ayrıca döndürülen `Flow` **stabil bir instance**
     * olmalı (tek `val` — her erişimde `receiveAsFlow()` çağıran `get()` DEĞİL), aksi halde
     * [ObserveAsEvents]'in `LaunchedEffect` key'i her recomposition'da değişip collector'ı restart eder.
     */
    public val effects: Flow<E> get() = emptyFlow()

    /** Intent girişi — stateless content `onIntent(...)` ile çağırır. */
    public fun onIntent(intent: I)
}
