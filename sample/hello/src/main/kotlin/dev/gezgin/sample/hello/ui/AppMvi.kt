package dev.gezgin.sample.hello.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
