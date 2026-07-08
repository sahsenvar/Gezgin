package dev.gezgin.core.compose

import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.SheetDefault
import dev.gezgin.core.fixtures.testTopology
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 4.2 (§7) — sheet swipe-dismiss→Canceled saf-JVM pini (Compose runtime GEREKMEZ). material3
 * `ModalBottomSheet`'te swipe-down / scrim-tap / geri-tuşu ÜÇÜ de tek `onDismissRequest`'e düşer
 * (jar-doğrulandı); [GezginBottomSheetScene] bunu `onBack` = Gezgin [gezginOnBack]'e bağlar. Bir
 * ResultRoute sheet (pending-target) için `back()` = pop + caller'a `Canceled` (dialog'daki 4.1
 * mekanizmasının aynısı). Bu test dismiss'in bu zinciri uçtan uca `Canceled` ürettiğini kanıtlar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GezginBottomSheetDismissTest {

    @Test
    fun `ResultRoute sheet dismiss (gezginOnBack) - caller Canceled alir`() = runTest {
        val nav = RawNavigator(start = Feed, topology = testTopology)
        val scope = GezginEntryScope().apply {
            register<Feed> { }
            register<SheetDefault>(kind = EntryKind.BOTTOM_SHEET) { }   // ResultRoute-benzeri: pending target
        }
        val callerId = nav.currentEntryId

        nav.launchForResult(callerId, edgeId = "Feed→Sheet", route = SheetDefault("s"))
        assertEquals(SheetDefault("s"), nav.current)

        // DISMISS = sheet onDismissRequest (swipe/scrim/back) → NavDisplay.onBack = gezginOnBack.
        gezginOnBack(nav, scope).invoke()

        assertEquals(Feed, nav.current, "dismiss sheet'i pop etmeli")
        val result = nav.results<String>(callerId, "Feed→Sheet").first()
        assertEquals(NavResult.Canceled, result, "swipe-dismiss → Canceled teslim edilmeli")
    }
}
