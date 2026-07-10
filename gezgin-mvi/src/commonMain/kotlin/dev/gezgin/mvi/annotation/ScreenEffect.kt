package dev.gezgin.mvi.annotation

/**
 * Opsiyonel efekt binder'ı (§10.1) — `@Screen` gibi bir **composable fonksiyon** üzerinde durur
 * (FUNCTION target). İmza: `@ScreenEffect fun XEffects(effects: Flow<E>[, nav])` — kendi
 * [dev.gezgin.mvi.ObserveEffects]'ini yapar (Google state-first önerisi → effect dayatılmaz).
 *
 * Codegen (5.2) `@MviViewModel`/`@Screen` ile aynı route'a eşler (aynı-modül kuralı) ve `provideXEntry`
 * içinde VM'in `effects: Flow<E>` akışıyla besler; `Flow<E>`'nin E'si VM'in `GezginMvi<S,I,E>`
 * supertype'ındaki E'ye karşı doğrulanır. Route bağı eşleşen `@Screen`/`@MviViewModel`'den gelir —
 * annotation'ın kendisi parametresizdir.
 */
@Target(AnnotationTarget.FUNCTION)
public annotation class ScreenEffect
