package dev.gezgin.sample.feature.home

import kotlinx.coroutines.flow.Flow

internal interface ResultIntentSink<in I> {
  fun sendResultIntent(intent: I)
}

private class ResultIntentEffectFlow<E, I>(delegate: Flow<E>, private val onIntent: (I) -> Unit) :
  Flow<E> by delegate, ResultIntentSink<I> {
  override fun sendResultIntent(intent: I) = onIntent(intent)
}

internal fun <E, I> resultIntentEffectFlow(effects: Flow<E>, onIntent: (I) -> Unit): Flow<E> =
  ResultIntentEffectFlow(effects, onIntent)

@Suppress("UNCHECKED_CAST")
internal fun <I> Flow<*>.resultIntentSink(): ResultIntentSink<I> =
  this as? ResultIntentSink<I>
    ?: error("Strict-MVI result collection requires a resultIntentEffectFlow-backed effects stream")
