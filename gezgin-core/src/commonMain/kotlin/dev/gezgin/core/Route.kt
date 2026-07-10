package dev.gezgin.core

import dev.gezgin.core.compose.GezginTransition

/**
 * Tüm graph interface'lerinin kökü. App'in kök sealed graph'ı bunu extend eder (AppGraph : Route).
 *
 * [transition] (§9) — runtime transition değeri, **getter zorunlu**: `override val transition
 * get() = transition { forward { .. } }` — initializer'lı hâli (`val transition = transition { .. }`)
 * `@Serializable` data class route'larda backing field yaratır, bu da kotlinx.serialization codegen'iyle
 * çakışır (non-serializable alan). Varsayılan `null` = "bu seviye bir şey söylemiyor" (§9 cascade'in
 * temeli): bir graph interface'i bu property'yi override edip kendi grup-geneli transition'ını
 * verebilir; onu implement eden route'lar kendi override'ı yoksa Kotlin'in interface property override
 * zinciriyle graph'ın değerini miras alır (screen>graph cascade BEDAVA gelir — ek kod gerekmez). Kalan
 * app-seviyesi basamak ([dev.gezgin.core.compose.navTransitions]) [dev.gezgin.core.compose.resolveTransition]'da.
 */
public interface Route {
    public val transition: GezginTransition? get() = null
}
