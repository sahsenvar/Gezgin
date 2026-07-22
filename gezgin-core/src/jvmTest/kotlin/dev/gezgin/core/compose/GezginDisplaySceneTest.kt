package dev.gezgin.core.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.DialogCustom
import dev.gezgin.core.fixtures.FullModal
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Faz 4.0 spike — desktop uiTest: bir `EntryKind.DIALOG` entry'nin
 * [GezginNavDisplay]/DialogSceneStrategy wiring'i üzerinden çökmeden overlay olarak render olduğu
 * smoke kanıtı. **En güçlü/en güvenilir assertion = "arka görünür":** dialog entry top iken alttaki
 * (overlaid) SCREEN entry HÂLÂ görünür. Bu, Faz 3 davranışından (dialog kind = SinglePaneScene =
 * üstteki TEK entry render, alt REPLACE) net ayrımdır → scene overlay'in gerçekten devrede olduğunu
 * kanıtlar (wiring olmasa "home" pop-out olurdu).
 *
 * Faz 3 regresyon kontrolü: SCREEN entry hâlâ tek-pane render (fallback SinglePaneSceneStrategy).
 */
@OptIn(ExperimentalTestApi::class)
class GezginDisplaySceneTest {

  @Test
  fun `DIALOG kind entry overlay render olur - alttaki SCREEN gorunur kalir`() = runComposeUiTest {
    val nav = RawNavigator(start = Catalog, topology = testTopology)
    setContent {
      GezginDisplay(navigator = nav) {
        register<Catalog> { BasicText("home-screen") }
        register<Product>(kind = EntryKind.DIALOG) { BasicText("dialog-body") }
      }
    }
    // Başlangıç: yalnız SCREEN (Catalog) — plain tek-pane.
    onNodeWithText("home-screen").assertIsDisplayed()

    // DIALOG kind entry push → overlay. Çökmemeli; alttaki SCREEN görünür kalmalı (arka görünür).
    nav.navigate(Product("x"))
    waitForIdle()

    onNodeWithText("home-screen").assertIsDisplayed() // overlaid entry hâlâ compose ediliyor
    onNodeWithText("dialog-body").assertIsDisplayed() // dialog içeriği render oldu
  }

  @Test
  fun `SCREEN kind - Faz 3 tek-pane davranisi bozulmaz`() = runComposeUiTest {
    val nav = RawNavigator(start = Catalog, topology = testTopology)
    setContent {
      GezginDisplay(navigator = nav) {
        register<Catalog> { BasicText("catalog") }
        register<Product> { BasicText("product") } // kind = SCREEN (default)
      }
    }
    onNodeWithText("catalog").assertIsDisplayed()
    nav.navigate(Product("x"))
    waitForIdle()
    // SCREEN push = tek-pane → yalnız top görünür (overlay DEĞİL).
    onNodeWithText("product").assertIsDisplayed()
  }

  @Test
  fun `DialogContract'li DIALOG entry overlay render olur - arka SCREEN gorunur (4_1)`() =
    runComposeUiTest {
      val nav = RawNavigator(start = Catalog, topology = testTopology)
      setContent {
        GezginDisplay(navigator = nav) {
          register<Catalog> { BasicText("home-screen") }
          register<DialogCustom>(kind = EntryKind.DIALOG) { BasicText("contract-dialog") }
        }
      }
      onNodeWithText("home-screen").assertIsDisplayed()

      nav.navigate(DialogCustom("x")) // contract'lı dialog → overlay (props contract'tan iner)
      waitForIdle()

      onNodeWithText("home-screen").assertIsDisplayed() // overlaid SCREEN hâlâ görünür
      onNodeWithText("contract-dialog").assertIsDisplayed() // dialog içeriği render oldu
    }

  @Test
  fun `dialog dismiss (back) - overlay kapanir, arka SCREEN kalir (4_1)`() = runComposeUiTest {
    val nav = RawNavigator(start = Catalog, topology = testTopology)
    setContent {
      GezginDisplay(navigator = nav) {
        register<Catalog> { BasicText("home-screen") }
        register<DialogCustom>(kind = EntryKind.DIALOG) { BasicText("contract-dialog") }
      }
    }
    nav.navigate(DialogCustom("x"))
    waitForIdle()
    onNodeWithText("contract-dialog").assertIsDisplayed()

    // Dismiss = pop (dialog scene onDismissRequest → NavDisplay.onBack yolu). Programatik pop ile
    // aynı state değişimini sürüyoruz → overlay kaybolur, arka SCREEN top olur.
    nav.back()
    waitForIdle()

    onNodeWithText("contract-dialog").assertDoesNotExist()
    onNodeWithText("home-screen").assertIsDisplayed()
    assertEquals(Catalog, nav.current)
  }

  @Test
  fun `FULLSCREEN_MODAL entry overlay olarak render olur - DialogScene yolu (4_3)`() =
    runComposeUiTest {
      val nav = RawNavigator(start = Catalog, topology = testTopology)
      setContent {
        GezginDisplay(navigator = nav) {
          register<Catalog> { BasicText("home-screen") }
          register<FullModal>(kind = EntryKind.FULLSCREEN_MODAL) { BasicText("fullscreen-body") }
        }
      }
      onNodeWithText("home-screen").assertIsDisplayed()

      // FULLSCREEN_MODAL = DialogSceneStrategy + DialogProperties(usePlatformDefaultWidth=false) —
      // dialog ile AYNI overlay yolu, yalnız tam-ekran (adapter resolveDialogProperties, §7).
      nav.navigate(FullModal)
      waitForIdle()

      // Tam-ekran modal içeriği render oldu. Arka SCREEN görsel olarak KAPANIR (tam ekran) ama
      // scene
      // overlay entry'si olarak compose'da HÂLÂ var (overlaidEntries soyulmaz) → single-pane
      // replace
      // DEĞİL, overlay. `assertExists` bu overlay semantiğini pinler (görsel örtülme uiTest'te
      // değil,
      // on-device: §7 fullscreen tanımı usePlatformDefaultWidth=false).
      onNodeWithText("fullscreen-body").assertIsDisplayed()
      onNodeWithText("home-screen").assertExists()
    }

  @Test
  fun `FULLSCREEN_MODAL dismiss (back) - overlay kapanir, arka SCREEN geri gelir (4_3)`() =
    runComposeUiTest {
      val nav = RawNavigator(start = Catalog, topology = testTopology)
      setContent {
        GezginDisplay(navigator = nav) {
          register<Catalog> { BasicText("home-screen") }
          register<FullModal>(kind = EntryKind.FULLSCREEN_MODAL) { BasicText("fullscreen-body") }
        }
      }
      nav.navigate(FullModal)
      waitForIdle()
      onNodeWithText("fullscreen-body").assertIsDisplayed()

      nav
        .back() // dismiss = DialogScene.onDismissRequest → NavDisplay.onBack yolu (dialog ile aynı)
      waitForIdle()

      onNodeWithText("fullscreen-body").assertDoesNotExist()
      onNodeWithText("home-screen").assertIsDisplayed()
      assertEquals(Catalog, nav.current)
    }
}
