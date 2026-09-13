package dev.gezgin.sample.shopr.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Shopr's own MVI base. Unlike `sample/hello` it puts no marker interfaces on S/I/E — a wrapper's
 * type parameters need no bounds at all, and leaving them off keeps existing state, intent and
 * effect types untouched.
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
