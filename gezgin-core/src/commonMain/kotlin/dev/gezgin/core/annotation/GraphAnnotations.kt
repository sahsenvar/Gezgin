package dev.gezgin.core.annotation

/**
 * Şeffaf graph konteyneri (§3/§8.1): route'ları gruplayan bir sealed interface'i işaretler; üyeleri sıradan
 * stack entry'leridir (flow-unit sınırı YOK). Her route tam olarak bir graph'a aittir.
 */
@Target(AnnotationTarget.CLASS)
annotation class NavGraph

/**
 * Opak flow konteyneri (§8.1): üyeleri tek bir flow-unit oluşturan bir sealed interface'i işaretler;
 * `quit`/`quitWith` birimin TAMAMINI yıkar. `ResultFlow<T>` ile birleşince sonuç-sahipliği taşır.
 */
@Target(AnnotationTarget.CLASS)
annotation class FlowGraph

/** Bir `@NavGraph`/`@FlowGraph` içindeki başlangıç hedefini işaretler (§8.1) — konteyner'a girişte ilk açılan route. */
@Target(AnnotationTarget.CLASS)
annotation class StartDestination
