package dev.gezgin.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Standard helper that collects an effect flow SAFELY, only while STARTED (§10.1). A route-explicit
 * effect handler calls it: `ObserveEffects(vm.effects) { effect -> ... }`.
 *
 * **Collection window (honest contract):** the [repeatOnLifecycle]`(STARTED)` collect is open ONLY while
 * STARTED — when the entry is covered (Nav3 removes a covered entry from composition entirely) or the app
 * goes to the background (STOPPED), the collector is CLOSED. Whether an effect emitted in that window
 * survives depends ENTIRELY on the `effects` BACKING; this function alone can't guarantee it: a buffered
 * source (RECOMMENDED [GezginEffects] = `Channel(UNLIMITED).receiveAsFlow()`) holds values and delivers
 * them IN ORDER once STARTED again, whereas `MutableSharedFlow(replay = 0) + tryEmit` SILENTLY DROPS an
 * effect when there is no observer (this helper cannot bring it back). So the "no lost toast" guarantee is
 * a property of a [GezginEffects]-backed `effects`, not of this function.
 *
 * `Main.immediate` = on recompose/collect resume (returning to STARTED) the buffered effect is processed
 * without being deferred to the next frame or skipped.
 *
 * **Stale-lambda fix (MJ-A):** the [LaunchedEffect] keys are `(effects, lifecycleOwner)` only — both stable,
 * so the running `collect` is NOT restarted on recomposition. If `onEffect` were passed straight to
 * `collect(onEffect)` the collector would bind the FIRST composition's lambda FOREVER; an `onEffect` that
 * closes over a value that changes across recomposition (a callback re-created by the parent, or a captured
 * `currentUser`/state read by value) would keep delivering to the STALE target, silently. So `onEffect` is
 * wrapped in [rememberUpdatedState] and the collector invokes the MOST RECENT instance on every emission —
 * the same pattern `rememberNavigator` applies to `onRootBack`.
 */
@Composable
public fun <E> ObserveEffects(effects: Flow<E>, onEffect: (E) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                effects.collect { latestOnEffect(it) }
            }
        }
    }
}
