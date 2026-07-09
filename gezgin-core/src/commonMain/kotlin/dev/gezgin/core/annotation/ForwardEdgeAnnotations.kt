package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import dev.gezgin.core.Self
import kotlin.annotation.Repeatable
import kotlin.reflect.KClass

/**
 * İleri navigasyon kenarı (§3.1/§4.1): kaynak graph interface'inden `target`'a push. Codegen tipli
 * `goToX()` üretir; `singleTop=true` (varsayılan) aynı-değerli top'u dedup eder, `name` aynı hedefe giden
 * birden çok kenarı ayırır (`@Repeatable`).
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class GoTo(val target: KClass<out Route>, val singleTop: Boolean = true, val name: String = "")

/**
 * Replace kenarı (§4.1): mevcut hedefi `target` ile değiştirir — `clearUpTo` (varsayılan `Self`) + `inclusive`
 * ile ne kadarının temizleneceği belirlenir; auth-success/onboarding gibi geri-dönülmez geçişler için.
 * Codegen tipli `replaceToX()` üretir.
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class ReplaceTo(val target: KClass<out Route>, val clearUpTo: KClass<out Route> = Self::class, val inclusive: Boolean = true, val name: String = "")

/**
 * Sonuç-döndüren ileri kenar (§6): `target` bir `ResultRoute<T>` ya da `ResultFlow<T>` olmalı. Codegen
 * PD-güvenli üçlüyü üretir — `launchX()` (tetik) + `xResults` (stream) + suspend `goToXForResult()` (sugar).
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class GoForResult(val target: KClass<out Route>, val name: String = "")

/**
 * Flow-çıkış + ileri kenar (§8.1): mevcut flow'u Canceled ile kapatıp `target`'a gider. Codegen tipli
 * `quitAndGoToX()` üretir.
 */
@Target(AnnotationTarget.CLASS)
annotation class QuitAndGoTo(val target: KClass<out Route>)
