package dev.gezgin.core.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.DialogCustom
import dev.gezgin.core.fixtures.DialogDefault
import dev.gezgin.core.fixtures.SheetDefault
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 4.3 (§7 N8 — modal-üstü-modal / stacked overlay) desktop uiTest. Nav3 `OverlayScene`'in
 * scene-peel döngüsü (4.0 raporu: `SceneState` do/while) N-derin overlay'i doğal stack'ler:
 * `[Screen, DialogB, DialogC]` → dıştaki scene DialogScene(C) `overlaidEntries=[Screen, DialogB]`,
 * o alt-liste yeniden DialogScene(B) `overlaidEntries=[Screen]`, en dip SinglePane(Screen). Her modal
 * KENDİ overlay window'unda compose edilir → hepsi aynı anda ağaçta; en üstteki (C) aktif. `back()`
 * tek tek soyar (C → B → Screen). Karışık stack (dialog + sheet) da aynı peel döngüsünden geçer —
 * DialogSceneStrategy ve GezginBottomSheetSceneStrategy top-entry metadata'sına göre ayrık seçilir.
 *
 * **uiTest'te pinlenen:** her katmanın compose'da var olması (`assertExists` — overlay semantiği:
 * alttaki soyulmaz), en üstteki aktifin görünürlüğü, `back()`'in tam bir katmanı kapatıp bir alttakini
 * ortaya çıkarması, stack sırasının `nav.current`'a yansıması. **on-device'a bırakılan:** N-derin scrim
 * katmanının görsel z-order'ı/opaklığı ve predictive-back animasyonu (window-katmanı görsel doğrulaması
 * headless uiTest'te güvenilir okunamaz — §7 "on-device doğrulanacak" + 4.4 checklist).
 */
@OptIn(ExperimentalTestApi::class)
class GezginStackedOverlaySceneTest {

    @Test
    fun `iki dialog ust uste - hepsi render, back tek tek kapatir (N8)`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("screen-base") }
                register<DialogDefault>(kind = EntryKind.DIALOG) { BasicText("dialog-B") }
                register<DialogCustom>(kind = EntryKind.DIALOG) { BasicText("dialog-C") }
            }
        }
        // [Catalog] → [Catalog, DialogB] → [Catalog, DialogB, DialogC]
        nav.navigate(DialogDefault("b"))
        nav.navigate(DialogCustom("c"))
        waitForIdle()

        // Üç katman da compose edilmiş (overlay: alt katmanlar soyulmaz), en üstteki aktif.
        onNodeWithText("screen-base").assertExists()
        onNodeWithText("dialog-B").assertExists()
        onNodeWithText("dialog-C").assertIsDisplayed()   // en üstteki (aktif) overlay

        // back → yalnız C kapanır; B ve Screen kalır.
        nav.back()
        waitForIdle()
        onNodeWithText("dialog-C").assertDoesNotExist()
        onNodeWithText("dialog-B").assertIsDisplayed()   // artık en üstteki
        onNodeWithText("screen-base").assertExists()
        assertEquals(DialogDefault("b"), nav.current)

        // back → B de kapanır; yalnız Screen kalır.
        nav.back()
        waitForIdle()
        onNodeWithText("dialog-B").assertDoesNotExist()
        onNodeWithText("screen-base").assertIsDisplayed()
        assertEquals(Catalog, nav.current)
    }

    @Test
    fun `karisik stack - dialog uzerinde sheet, back sheet'i kapatir dialog kalir (N8)`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("screen-base") }
                register<DialogDefault>(kind = EntryKind.DIALOG) { BasicText("dialog-mid") }
                register<SheetDefault>(kind = EntryKind.BOTTOM_SHEET) { BasicText("sheet-top") }
            }
        }
        // [Catalog, Dialog, Sheet] — farklı iki SceneStrategy aynı peel döngüsünde.
        nav.navigate(DialogDefault("d"))
        nav.navigate(SheetDefault("s"))
        waitForIdle()

        onNodeWithText("screen-base").assertExists()
        onNodeWithText("dialog-mid").assertExists()
        onNodeWithText("sheet-top").assertIsDisplayed()   // en üstteki sheet overlay

        // back → sheet kapanır, dialog (ve Screen) kalır.
        nav.back()
        waitForIdle()
        onNodeWithText("sheet-top").assertDoesNotExist()
        onNodeWithText("dialog-mid").assertIsDisplayed()
        assertEquals(DialogDefault("d"), nav.current)
    }
}
