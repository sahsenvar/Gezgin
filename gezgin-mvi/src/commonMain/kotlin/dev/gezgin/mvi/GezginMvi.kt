package dev.gezgin.mvi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The MVI add-on contract (§10.1) — opt-in. It does NOT enter Gezgin's state-holder world (it
 * imposes no store/reducer); it is just the **minimal surface** codegen reads: a state stream, an
 * optional effect stream, an intent entry point.
 *
 * A VM marked `@MviViewModel(Route::class)` MUST implement this (BOTH ARE MANDATORY — guardrail,
 * §10.1): codegen reads the VM's concrete type from `@MviViewModel`, and **S/I/E from this
 * supertype's type args** (no derivation from the content → the spec's E-source problem is solved).
 * If `@MviViewModel` is present but `GezginMvi` is not → compile error (5.1).
 *
 * The variance (`out S, in I, out E`) = polish (S/E are produced/come out, I is consumed); it eases
 * type inference and no longer carries weight — since S/I/E are read directly from the supertype
 * args, it adds no extra constraint on codegen.
 *
 * @author @sahsenvar
 */
public interface GezginMvi<out S, in I, out E> {
  /**
   * The UI state stream — codegen observes it with `collectAsStateWithLifecycle()` and gives
   * `state` to the stateless content.
   */
  public val uiState: StateFlow<S>

  /**
   * The optional one-shot effect stream (non-nav side effects; snackbar/toast/haptic). If absent,
   * `emptyFlow()`. A route-explicit effect handler consumes it via [ObserveEffects] **only while
   * STARTED** → there is NO observer on a covered or STOPPED entry.
   *
   * **Backing contract (losslessness):** a buffered source is required so that an effect emitted
   * while there is no observer is NOT LOST. RECOMMENDED: [GezginEffects] (`Channel(UNLIMITED)
   * .receiveAsFlow()`). The `MutableSharedFlow(replay = 0) + tryEmit` pattern SILENTLY drops an
   * effect while there is no observer (see the rationale in the [GezginEffects] KDoc). The returned
   * `Flow` must also be a **stable instance** (a single `val` — NOT a `get()` that calls
   * `receiveAsFlow()` on every access), otherwise [ObserveEffects]'s `LaunchedEffect` key changes
   * on every recomposition and restarts the collector.
   */
  public val effects: Flow<E>
    get() = emptyFlow()

  /** The intent entry point — the stateless content calls it via `onIntent(...)`. */
  public fun onIntent(intent: I)
}
