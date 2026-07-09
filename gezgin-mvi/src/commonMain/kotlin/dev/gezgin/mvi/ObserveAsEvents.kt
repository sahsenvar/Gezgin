package dev.gezgin.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Efekt akışını **yalnız STARTED iken** güvenle toplayan standart yardımcı (§10.1). `@ScreenEffect`
 * composable'ı bunu çağırır: `ObserveAsEvents(vm.effects) { event -> ... }`.
 *
 * **Toplama penceresi (dürüst sözleşme, Faz 5 recheck / MJ2):** [repeatOnLifecycle]`(STARTED)` collect'i
 * YALNIZ STARTED iken açıktır — entry örtülünce (Nav3 covered entry'yi composition'dan tamamen çıkarır)
 * ya da uygulama arka plana gidince (STOPPED) collector KAPANIR. Bu pencerede emit edilen efektlerin
 * kaybolmaması TAMAMEN `effects`'in **backing'ine** bağlıdır; bu fonksiyon tek başına garanti veremez:
 * buffer'lı bir kaynak (ÖNERİLEN [GezginEffects] = `Channel(UNLIMITED).receiveAsFlow()`) değerleri tutar
 * ve STARTED'a dönünce SIRAYLA teslim eder; `MutableSharedFlow(replay = 0) + tryEmit` ise gözlemci yokken
 * efekti SESSİZCE DÜŞÜRÜR (bu yardımcı onu geri getiremez). Yani "kaybolan toast yok" garantisi bu
 * fonksiyonun değil, [GezginEffects]-backed bir `effects`'in özelliğidir.
 *
 * `Main.immediate` = re-compose/collect resume anında (STARTED'a dönüş) buffer'daki efekt bir sonraki
 * frame'e ertelenmeden, atlanmadan işlenir.
 *
 * Kaynak artefaktlar (spike task-5.0, 2026-07-08): `LocalLifecycleOwner`/[repeatOnLifecycle]
 * `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`(+`-runtime`) commonMain'de (KMP, jvm+android).
 */
@Composable
fun <E> ObserveAsEvents(effects: Flow<E>, onEvent: (E) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                effects.collect(onEvent)
            }
        }
    }
}
