package dev.gezgin.core.compose

import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.DialogDefault
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.FullModal
import dev.gezgin.core.fixtures.SheetDefault
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Faz4 final-review Important 1 (§7) — modal-kind-at-root DINAMIK guard'ının saf-JVM adapter-level pini
 * (Compose runtime GEREKMEZ). [toNavEntry] `isRoot=true` iken kind SCREEN DIŞINDAysa (DIALOG/BOTTOM_SHEET/
 * FULLSCREEN_MODAL) `require` fırlatır — bu ANA guard TÜM dinamik yolları (replaceTo/quitAndGoTo ile
 * modal'ı köke koyma) kapatır. SCREEN root geçer. Nav3 OverlayScene invariant'ı: modal genuinely kökte
 * tek başına var olamaz (altında ≥1 SCREEN entry şart).
 */
class GezginModalRootGuardTest {
    private val navigator = RawNavigator(start = Feed, topology = testTopology)

    private fun scope() = GezginEntryScope().apply {
        register<Feed> { }
        register<DialogDefault>(kind = EntryKind.DIALOG) { }
        register<SheetDefault>(kind = EntryKind.BOTTOM_SHEET) { }
        register<FullModal>(kind = EntryKind.FULLSCREEN_MODAL) { }
    }

    private fun assertRootModalRejected(route: dev.gezgin.core.Route, id: Long) {
        val ex = assertFailsWith<IllegalArgumentException> {
            scope().toNavEntry(GezginKey(route = route, id = id), navigator, navTransitions {}, isRoot = true)
        }
        assertTrue(ex.message?.contains("only/root entry") == true, "guard message: ${ex.message}")
    }

    @Test
    fun `DIALOG kok entry require firlatir`() = assertRootModalRejected(DialogDefault("x"), 1L)

    @Test
    fun `BOTTOM_SHEET kok entry require firlatir`() = assertRootModalRejected(SheetDefault("x"), 2L)

    @Test
    fun `FULLSCREEN_MODAL kok entry require firlatir`() = assertRootModalRejected(FullModal, 3L)

    @Test
    fun `SCREEN kok entry GECER`() {
        // Fırlatmamalı — SCREEN kök meşru.
        scope().toNavEntry(GezginKey(route = Feed, id = 4L), navigator, navTransitions {}, isRoot = true)
    }

    @Test
    fun `modal entry kok DEGILSE (isRoot=false) GECER`() {
        // Aynı DIALOG route kök değilken (bir SCREEN üstünde) meşru — guard yalnız isRoot'a bakar.
        scope().toNavEntry(GezginKey(route = DialogDefault("x"), id = 5L), navigator, navTransitions {}, isRoot = false)
    }
}
