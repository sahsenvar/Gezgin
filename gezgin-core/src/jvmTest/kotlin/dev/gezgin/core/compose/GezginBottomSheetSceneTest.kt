@file:OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)

package dev.gezgin.core.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.SheetCustom
import dev.gezgin.core.fixtures.SheetDefault
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 4.2 — desktop uiTest: `EntryKind.BOTTOM_SHEET` entry'nin el-yazımı [GezginBottomSheetScene]/
 * [GezginBottomSheetSceneStrategy] wiring'i üzerinden overlay olarak render olduğu + [LocalGezginSheetState]
 * enjeksiyonu + dismiss→pop kanıtı. Dialog scene testinin (GezginDisplaySceneTest) sheet paraleli.
 */
class GezginBottomSheetSceneTest {

    @Test
    fun `BOTTOM_SHEET kind entry overlay render olur - alttaki SCREEN gorunur kalir`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("home-screen") }
                register<SheetDefault>(kind = EntryKind.BOTTOM_SHEET) { BasicText("sheet-body") }
            }
        }
        onNodeWithText("home-screen").assertIsDisplayed()

        nav.navigate(SheetDefault("x"))
        waitForIdle()

        onNodeWithText("home-screen").assertIsDisplayed()  // overlaid SCREEN hâlâ compose ediliyor (arka görünür)
        onNodeWithText("sheet-body").assertIsDisplayed()   // sheet içeriği overlay olarak render oldu
    }

    @Test
    fun `sheetState LocalGezginSheetState uzerinden content'e enjekte edilir`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("home-screen") }
                register<SheetCustom>(kind = EntryKind.BOTTOM_SHEET) {
                    // Local'i oku: sağlanmamışsa staticCompositionLocalOf'un error() default'u fırlar →
                    // test çöker. Render olabiliyorsa scene sheetState'i content'e enjekte etmiş demektir.
                    // (Prop akışı — skipPartiallyExpanded — ayrıca adapter-level GezginBottomSheetContractTest'te
                    // pinli; SheetState.skipPartiallyExpanded material3 1.9.0'da internal, buradan okunamaz.)
                    val sheetState = LocalGezginSheetState.current
                    BasicText("sheet-state-${sheetState.currentValue}")
                }
            }
        }
        nav.navigate(SheetCustom("x"))
        waitForIdle()

        // currentValue (Hidden→Expanded) animasyonla değişebilir → substring match; asıl kanıt: Local'den
        // okuyabildi (yoksa error() fırlar), yani sheetState content'e enjekte edildi.
        onNodeWithText("sheet-state-", substring = true).assertIsDisplayed()
    }

    @Test
    fun `sheet dismiss (back) - overlay kapanir, arka SCREEN kalir`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("home-screen") }
                register<SheetDefault>(kind = EntryKind.BOTTOM_SHEET) { BasicText("sheet-body") }
            }
        }
        nav.navigate(SheetDefault("x"))
        waitForIdle()
        onNodeWithText("sheet-body").assertIsDisplayed()

        // Dismiss = pop (sheet onDismissRequest → NavDisplay.onBack yolu). Programatik pop aynı state değişimi.
        nav.back()
        waitForIdle()

        onNodeWithText("home-screen").assertIsDisplayed()
        assertEquals(Catalog, nav.current)
    }
}
