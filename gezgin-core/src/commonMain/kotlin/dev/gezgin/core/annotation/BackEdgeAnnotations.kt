package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import kotlin.annotation.Repeatable
import kotlin.reflect.KClass

/**
 * Geri kenarı (§4.2): stack'te `target`'a kadar pop eder (`inclusive=true` ise target da çıkar). Codegen
 * tipli `backToX()` üretir; target stack'te yoksa `NavEvent.BackToTargetMissing` yayılır, pop olmaz.
 */
@Target(AnnotationTarget.CLASS)
@Repeatable
public annotation class BackTo(val target: KClass<out Route>, val inclusive: Boolean = false)

/** Geri kenarı (§4.2): stack'i start destination'a kadar boşaltır. Codegen tipli `backToStart()` üretir. */
@Target(AnnotationTarget.CLASS)
public annotation class BackToStart

/** Flow-çıkış kenarı (§8.1): mevcut flow'u Canceled ile kapatır (root'ta `onRootBack`). Codegen tipli `quit()` üretir. */
@Target(AnnotationTarget.CLASS)
public annotation class Quit

/**
 * Terminal işaret (§4.2, M5′): bu route top iken geri (sistem-back/predictive dahil) YUTULUR — Gezgin-sahipli
 * handler dispatcher LIFO'sunda kazanır. Kök muaftır (dipteyken back = `onRootBack`; kullanıcı app'e hapsolmaz).
 */
@Target(AnnotationTarget.CLASS)
public annotation class NoBack
