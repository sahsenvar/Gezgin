package dev.gezgin.core.compose

import dev.gezgin.core.fixtures.ScreenInheritsGraphTransition
import dev.gezgin.core.fixtures.ScreenNoTransitionAnywhere
import dev.gezgin.core.fixtures.ScreenOwnTransition
import dev.gezgin.core.fixtures.appTransitionFixture
import dev.gezgin.core.fixtures.graphTransitionFixture
import dev.gezgin.core.fixtures.screenTransitionFixture
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Task 3.5 gate — pure-Kotlin (Compose kurulumu yok) cascade çözümü testleri (§9: "Cascade en içteki
 * (screen) > graph > app"). Screen>graph basamağı [dev.gezgin.core.Route.transition]'ın interface
 * override zincirinden geldiği için burada AYRICA sınanmıyor gibi görünebilir — ama [resolveTransition]
 * tam olarak `route.transition` okuyarak bu zinciri tetikliyor, dolayısıyla ilk iki senaryo o zincirin
 * DOĞRU çalıştığının kanıtı. §9 per-call override (N1) API'de hiç yok (yalnız `raw.navigate` escape-hatch'i
 * — Task 3.5 kapsamında negatif test gerekmiyor, ayrı bir "transition" parametresi/override noktası
 * tanımlanmadı; bkz. task-3.5-report.md).
 */
class GezginTransitionCascadeTest {

    @Test
    fun `route kendi transition'ini override ederse o kazanir (screen greater than graph)`() {
        val resolved = resolveTransition(ScreenOwnTransition, navTransitions {})
        assertSame(screenTransitionFixture, resolved)
    }

    @Test
    fun `route override etmezse graph'in transition'i miras alinir (graph greater than app)`() {
        val resolved = resolveTransition(ScreenInheritsGraphTransition, navTransitions {})
        assertSame(graphTransitionFixture, resolved)
    }

    @Test
    fun `ne route ne graph soylerse app-seviyesi default'a duser`() {
        val appTransitions = GezginTransitions(default = appTransitionFixture)
        val resolved = resolveTransition(ScreenNoTransitionAnywhere, appTransitions)
        assertSame(appTransitionFixture, resolved)
    }

    @Test
    fun `hicbir seviye (route, graph, app) bir sey soylemezse null doner`() {
        val resolved = resolveTransition(ScreenNoTransitionAnywhere, navTransitions {})
        assertNull(resolved)
    }
}
