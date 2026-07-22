package dev.gezgin.sample.shopr.screen_catalog

import kotlinx.coroutines.flow.Flow

internal interface CatalogResultIntentSink {
  fun send(intent: CatalogIntent)
}

private class CatalogResultIntentEffectFlow(
  delegate: Flow<CatalogEffect>,
  private val onIntent: (CatalogIntent) -> Unit,
) : Flow<CatalogEffect> by delegate, CatalogResultIntentSink {
  override fun send(intent: CatalogIntent) = onIntent(intent)
}

internal fun catalogResultIntentEffectFlow(
  effects: Flow<CatalogEffect>,
  onIntent: (CatalogIntent) -> Unit,
): Flow<CatalogEffect> = CatalogResultIntentEffectFlow(effects, onIntent)

internal fun Flow<CatalogEffect>.catalogResultIntentSink(): CatalogResultIntentSink =
  this as? CatalogResultIntentSink
    ?: error("Catalog checkout results require a CatalogViewModel-backed effects stream")
