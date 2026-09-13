package dev.gezgin.sample.designsystem

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The showcase application's own MVI base, living in a module every feature depends on. Gezgin
 * knows none of these types.
 */
abstract class BaseViewModel<S, I, E> : ViewModel() {
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
