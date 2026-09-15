package dev.gezgin.sample.hello.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

interface UiState

interface UiIntent

interface UiEvent

/**
 * The application's own MVI base. Gezgin knows none of these types: the wrapper below is what binds
 * them to a screen, and swapping this base for Orbit, Molecule or a plain StateFlow changes nothing
 * in the library.
 */
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEvent> : ViewModel() {
  abstract val uiState: StateFlow<S>
  abstract val effects: Flow<E>

  abstract fun onIntent(intent: I)
}

/**
 * One-shot, lossless effect sink. A `Channel(UNLIMITED)` HOLDS effects while nothing is collecting;
 * a `MutableSharedFlow` with `replay = 0` would drop them, and that window is real — a covered
 * Navigation 3 entry leaves composition entirely, and a backgrounded app stops collecting.
 */
class EffectSink<E> {
  private val channel = Channel<E>(Channel.UNLIMITED)

  /** Stable instance, for a SINGLE collector: an effect is delivered exactly once. */
  val flow: Flow<E> = channel.receiveAsFlow()

  fun send(effect: E) {
    channel.trySend(effect)
  }
}
