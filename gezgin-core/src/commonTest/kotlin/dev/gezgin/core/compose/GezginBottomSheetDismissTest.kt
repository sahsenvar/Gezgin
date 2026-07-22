@file:OptIn(dev.gezgin.core.GezginInternalApi::class)

package dev.gezgin.core.compose

import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.SheetDefault
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

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
    val scope =
      GezginEntryScope().apply {
        register<Feed> {}
        register<SheetDefault>(
          kind = EntryKind.BOTTOM_SHEET
        ) {} // ResultRoute-benzeri: pending target
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

  // C-MJ-1 — GezginBottomSheetScene'in FİİLİ dismiss çağrısı entry-pinli: onDismissRequest = {
  // back(sheetId) }.
  // Sheet HÂLÂ top iken pop + Canceled.
  @Test
  fun `pinned sheet dismiss - back(entryId) top iken Canceled teslim eder`() = runTest {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    val callerId = nav.currentEntryId
    nav.launchForResult(callerId, edgeId = "Feed→Sheet", route = SheetDefault("s"))
    val sheetId = nav.currentEntryId
    nav.back(sheetId)
    assertEquals(Feed, nav.current, "pinli dismiss sheet'i pop etmeli")
    assertEquals(NavResult.Canceled, nav.results<String>(callerId, "Feed→Sheet").first())
  }

  // C-MJ-1 (asıl bug) — hide-animasyon penceresi / geç async: sheet artık top DEĞİLKEN pinli
  // dismiss
  // NO-OP → alttaki SCREEN poplanmaz.
  @Test
  fun `pinned sheet dismiss - top degilken NO-OP`() = runTest {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    nav.navigate(Catalog) // [Feed, Catalog]
    nav.navigate(SheetDefault("s")) // [Feed, Catalog, Sheet]
    val sheetId = nav.currentEntryId
    nav.back(sheetId) // ilk dismiss → [Feed, Catalog]
    assertEquals(Catalog, nav.current)
    nav.back(sheetId) // bayat/geç dismiss → NO-OP
    assertEquals(Catalog, nav.current, "bayat sheet dismiss alttaki SCREEN'i poplamamalı")
    assertEquals(listOf<Route>(Feed, Catalog), nav.backStack.value)
  }
}
