package dev.gezgin.mvi

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Kayıpsız tek-seferlik efekt primitifi (§10.1, Faz 5 recheck / MJ2) — [GezginMvi.effects] için
 * ÖNERİLEN backing.
 *
 * **Neden `MutableSharedFlow` + `tryEmit` DEĞİL:** `MutableSharedFlow(replay = 0, extraBufferCapacity = n)`
 * SICAK bir yayındır ve `replay = 0` olduğundan **abone yokken** emit edilen değeri hiçbir yerde tutmaz.
 * Nav3'te örtülen (covered) entry composition'dan TAMAMEN çıkar → collector yok; ya da uygulama arka
 * plana gidince (`STOPPED`) [ObserveEffects]'in `repeatOnLifecycle` collect'i kesilir. Bu pencerede VM
 * `_effects.tryEmit(...)` derse: (a) buffer boşsa değer yok olur (abone yok, replay yok); (b) buffer
 * doluysa (`extraBufferCapacity` aşımı — bir frame'de art arda efekt) `tryEmit` `false` döner ve efekt
 * yine düşer — ki yaygın fixture'lar dönüş değerini yok sayar. Sonuç: kullanıcı geri döndüğünde
 * snackbar/toast OYNATILMAZ. [ObserveEffects]'in "STARTED'a dönünce kaybolan toast yok" vaadi YALNIZ
 * buffer'lı bir `Channel` backing'iyle doğrudur.
 *
 * **Neden `Channel(UNLIMITED)`:** `Channel` SICAK'tır ama tüketilene kadar değeri TUTAR. Abone olmasa
 * bile [send] edilen efektler kuyrukta bekler (`UNLIMITED` → [send] asla suspend/blok etmez, asla düşmez)
 * ve [flow] ([receiveAsFlow]) ile tek collector yeniden bağlanınca (STARTED'a dönüş / entry re-compose)
 * SIRAYLA teslim edilir. Efekt = kesin-bir-kez teslim; bu yüzden [flow] **tek** collector içindir
 * (`receiveAsFlow` fan-out yapmaz — ikinci bir gözlemci değeri paylaşır, çoğaltmaz).
 *
 * **Stabil instance:** [flow] tek seferlik kurulur (`val`) → her erişimde AYNI `Flow`. `@ScreenEffect`
 * içindeki `ObserveEffects(vm.effects) { ... }` çağrısında `LaunchedEffect(effects, ...)` key'i
 * recomposition'da değişmez, collector gereksiz yere restart olmaz. (Her erişimde `receiveAsFlow()`
 * çağıran bir `get()` property'si bu yüzden YANLIŞTIR — key her recomposition'da değişir.)
 *
 * Kullanım:
 * ```
 * private val _effects = GezginEffects<CounterEffect>()
 * override val effects: Flow<CounterEffect> = _effects.flow
 * // ...
 * _effects.send(CounterEffect.Toast("kaydedildi"))
 * ```
 */
public class GezginEffects<E> {
    private val channel = Channel<E>(Channel.UNLIMITED)

    /**
     * Kayıpsız efekt akışı — [GezginMvi.effects]'e doğrudan atanır. Stabil (`val`) instance; **tek**
     * collector içindir (efekt = kesin-tek-teslim, fan-out yok).
     */
    public val flow: Flow<E> = channel.receiveAsFlow()

    /**
     * Bir efekt gönder. `UNLIMITED` buffer → asla suspend etmez, asla düşürmez: gözlemci yokken bile
     * kuyruğa alınır ve re-observe'de teslim edilir. Herhangi bir thread'den çağrılabilir.
     */
    public fun send(effect: E) {
        channel.trySend(effect)
    }
}
