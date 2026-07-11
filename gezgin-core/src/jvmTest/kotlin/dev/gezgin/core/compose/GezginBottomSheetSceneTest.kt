@file:OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)

package dev.gezgin.core.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavEntry
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.SheetCustom
import dev.gezgin.core.fixtures.SheetDefault
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Task 4.2 — desktop uiTest: `EntryKind.BOTTOM_SHEET` entry'nin el-yazımı [GezginBottomSheetScene]/
 * [GezginBottomSheetSceneStrategy] wiring'i üzerinden overlay olarak render olduğu + [LocalGezginSheetController]
 * enjeksiyonu + dismiss→pop kanıtı. Dialog scene testinin (GezginDisplaySceneTest) sheet paraleli.
 */
class GezginBottomSheetSceneTest {

    @Test
    fun `BottomSheet scene rejects empty overlaid entries`() {
        val error = assertFailsWith<IllegalArgumentException> {
            GezginBottomSheetScene(
                key = "sheet",
                entry = NavEntry<Route>(key = SheetDefault("x"), contentKey = 1L) { },
                overlaidEntries = emptyList(),
                props = GezginBottomSheetProps(
                    skipPartiallyExpanded = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
                onBack = {},
            )
        }

        assertTrue(error.message?.contains("overlaidEntries cannot be empty") == true, error.message)
    }

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
    fun `GezginSheetController LocalGezginSheetController uzerinden content'e enjekte edilir`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("home-screen") }
                register<SheetCustom>(kind = EntryKind.BOTTOM_SHEET) {
                    // Local'i oku: sağlanmamışsa staticCompositionLocalOf'un error() default'u fırlar →
                    // test çöker. Render olabiliyorsa scene controller'ı content'e enjekte etmiş demektir.
                    // (M3 — role artık Gezgin-sahipli GezginSheetController; material3 SheetState değil.)
                    @Suppress("UNUSED_VARIABLE")
                    val controller = LocalGezginSheetController.current
                    BasicText("sheet-controller-injected")
                }
            }
        }
        nav.navigate(SheetCustom("x"))
        waitForIdle()

        // Asıl kanıt: Local'den okuyabildi (yoksa error() fırlar), yani controller content'e enjekte edildi.
        onNodeWithText("sheet-controller-injected").assertIsDisplayed()
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
