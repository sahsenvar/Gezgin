@file:OptIn(dev.gezgin.core.GezginInternalApi::class)

package dev.gezgin.core.compose

import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.DialogDefault
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.FullModal
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Task 4.1 (§7) — dismiss→Canceled saf-JVM pini (Compose runtime GEREKMEZ). Dialog scene'in
 * `onDismissRequest`'i (tap-outside/Esc/geri-izin-varken) NavDisplay.onBack'e = Gezgin
 * [gezginOnBack]'e bağlıdır (4.0 raporu §2/§6); [gezginOnBack] top `@NoBack` DEĞİLSE
 * `navigator.back()` çağırır. Bir ResultRoute dialog (pending-target) için `back()` = pop +
 * caller'a `Canceled` (mevcut `settleRemoved` yolu). Bu test dismiss'in bu zinciri uçtan uca
 * `Canceled` ürettiğini kanıtlar — dialog scene'in pop'unun result muhasebesini atlamadığının
 * garantisi.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GezginDialogDismissTest {

  @Test
  fun `ResultRoute dialog dismiss (gezginOnBack) - caller Canceled alir`() = runTest {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    val scope =
      GezginEntryScope().apply {
        register<Feed> {}
        register<DialogDefault>(kind = EntryKind.DIALOG) {} // ResultRoute-benzeri: pending target
      }
    val callerId = nav.currentEntryId

    // Dialog'u result isteğiyle push et (caller pending → dialog = pending target).
    nav.launchForResult(callerId, edgeId = "Feed→Dialog", route = DialogDefault("d"))
    assertEquals(DialogDefault("d"), nav.current)

    // DISMISS = dialog scene onDismissRequest → NavDisplay.onBack = gezginOnBack.
    gezginOnBack(nav, scope).invoke()

    // Pop olmuş + caller Canceled almış olmalı.
    assertEquals(Feed, nav.current, "dismiss dialog'u pop etmeli")
    val result = nav.results<String>(callerId, "Feed→Dialog").first()
    assertEquals(NavResult.Canceled, result, "dismiss → Canceled teslim edilmeli")
  }

  @Test
  fun `dogrudan back() de ayni Canceled'i uretir (dismiss = back esdegerligi)`() = runTest {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    val callerId = nav.currentEntryId
    nav.launchForResult(callerId, edgeId = "Feed→Dialog", route = DialogDefault("d"))

    nav.back() // dismiss'in düştüğü raw yol

    assertEquals(Feed, nav.current)
    assertEquals(NavResult.Canceled, nav.results<String>(callerId, "Feed→Dialog").first())
  }

  @Test
  fun `FULLSCREEN_MODAL dismiss (gezginOnBack) - caller Canceled alir (4_3)`() = runTest {
    // FULLSCREEN_MODAL, DIALOG ile AYNI dismiss yolundan geçer: DialogScene.onDismissRequest =
    // NavDisplay.onBack = gezginOnBack → back() → ResultRoute-benzeri pending target'a Canceled.
    // 4.1 guard'ı FULLSCREEN_MODAL'ı kapsıyordu; bu test dismiss→Canceled'ın da kapsandığını
    // pinler.
    val nav = RawNavigator(start = Feed, topology = testTopology)
    val scope =
      GezginEntryScope().apply {
        register<Feed> {}
        register<FullModal>(kind = EntryKind.FULLSCREEN_MODAL) {}
      }
    val callerId = nav.currentEntryId

    nav.launchForResult(callerId, edgeId = "Feed→Modal", route = FullModal)
    assertEquals(FullModal, nav.current)

    gezginOnBack(nav, scope).invoke() // dismiss simülasyonu

    assertEquals(Feed, nav.current, "dismiss fullscreen modal'ı pop etmeli")
    assertEquals(
      NavResult.Canceled,
      nav.results<String>(callerId, "Feed→Modal").first(),
      "fullscreen modal dismiss → Canceled teslim edilmeli",
    )
  }

  // C-MJ-1 — GezginDialogScene'in FİİLİ dismiss çağrısı entry-pinli: onDismissRequest = {
  // back(dialogId) }.
  // Dialog HÂLÂ top iken pop + Canceled (eski davranışla aynı sonuç, artık sahibe-pinli kapı
  // üzerinden).
  @Test
  fun `pinned dialog dismiss - back(entryId) top iken Canceled teslim eder`() = runTest {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    val callerId = nav.currentEntryId
    nav.launchForResult(callerId, edgeId = "Feed→Dialog", route = DialogDefault("d"))
    val dialogId = nav.currentEntryId
    nav.back(dialogId) // GezginDialogScene.onDismissRequest'in yaptığı
    assertEquals(Feed, nav.current, "pinli dismiss dialog'u pop etmeli")
    assertEquals(NavResult.Canceled, nav.results<String>(callerId, "Feed→Dialog").first())
  }

  // C-MJ-1 (asıl bug) — dialog artık top DEĞİLKEN pinli dismiss NO-OP: çifte-dismiss / geç-dismiss
  // ALTTAKİ SCREEN'i poplamaz. Eski (pinsiz) `back()` yolu ikinci dismiss'te Catalog'u poplardı.
  @Test
  fun `pinned dialog dismiss - top degilken NO-OP (cifte-dismiss altindaki ekrani korur)`() =
    runTest {
      val nav = RawNavigator(start = Feed, topology = testTopology)
      nav.navigate(Catalog) // [Feed, Catalog]
      nav.navigate(DialogDefault("d")) // [Feed, Catalog, Dialog]
      val dialogId = nav.currentEntryId
      nav.back(dialogId) // ilk dismiss → [Feed, Catalog]
      assertEquals(Catalog, nav.current)
      nav.back(dialogId) // bayat ikinci dismiss → NO-OP
      assertEquals(Catalog, nav.current, "ikinci (bayat) dismiss alttaki SCREEN'i poplamamalı")
      assertEquals(listOf<Route>(Feed, Catalog), nav.backStack.value)
    }
}
