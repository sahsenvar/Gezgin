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
 * Neden [repeatOnLifecycle] + [Dispatchers.Main]`.immediate`: (1) STOPPED iken (arka plan/başka ekran)
 * efekt toplanmaz — kaybolan snackbar/toast yok, gereksiz iş yok; STARTED'a dönünce yeniden bağlanır.
 * (2) `Main.immediate` = re-compose/collect resume anında efekt DÜŞÜRÜLMEZ (kuyruk atlanır). Bu, tek-
 * seferlik efektler (nav-olmayan yan etkiler) için doğru "collect while STARTED" desenidir.
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
