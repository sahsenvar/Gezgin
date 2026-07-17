@file:OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)

package dev.gezgin.core.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavEntry
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.SheetCustom
import dev.gezgin.core.fixtures.SheetDefault
import dev.gezgin.core.fixtures.SheetDismissConfig
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch

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
                    sheetGesturesEnabled = true,
                ),
                onBack = {},
            )
        }

        assertTrue(error.message?.contains("overlaidEntries cannot be empty") == true, error.message)
    }


    @Test
    fun `scene equality sheetGesturesEnabled alanini kapsar`() {
        val underlay = NavEntry<Route>(key = Catalog, contentKey = 1L) { }
        val sheet = NavEntry<Route>(key = SheetDefault("x"), contentKey = 2L) { }

        fun scene(gesturesEnabled: Boolean) = GezginBottomSheetScene(
            key = "sheet",
            entry = sheet,
            overlaidEntries = listOf(underlay),
            props = GezginBottomSheetProps(
                skipPartiallyExpanded = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = true,
                sheetGesturesEnabled = gesturesEnabled,
            ),
            onBack = {},
        )

        assertNotEquals(scene(true), scene(false))
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
    fun `sheetGesturesEnabled true ve false Material3 dismiss semantics'ine ulasir`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("home-screen") }
                register<SheetDismissConfig>(kind = EntryKind.BOTTOM_SHEET) { BasicText("gesture-sheet") }
            }
        }
        val dismissAction = SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)

        nav.navigate(SheetDismissConfig(backDismiss = false, outsideDismiss = false, gesturesEnabled = true))
        waitForIdle()
        assertTrue(
            onAllNodes(dismissAction, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "gesture=true Material3 drag handle dismiss semantics sağlamalı",
        )

        nav.back()
        nav.navigate(SheetDismissConfig(backDismiss = false, outsideDismiss = false, gesturesEnabled = false))
        waitForIdle()
        assertTrue(
            onAllNodes(dismissAction, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "gesture=false Material3 drag handle dismiss semantics sağlamamalı",
        )
    }

    @Test
    fun `user dismiss switch'leri kapaliyken programmatic back ve hideAndBack calisir`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        val lockedSheet = SheetDismissConfig(
            backDismiss = false,
            outsideDismiss = false,
            gesturesEnabled = false,
        )
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("home-screen") }
                register<SheetDismissConfig>(kind = EntryKind.BOTTOM_SHEET) {
                    val controller = LocalGezginSheetController.current
                    val scope = rememberCoroutineScope()
                    BasicText(
                        "hide-and-back",
                        modifier = Modifier.clickable { scope.launch { controller.hideAndBack() } },
                    )
                }
            }
        }

        nav.navigate(lockedSheet)
        nav.back()
        waitForIdle()
        assertEquals(Catalog, nav.current, "user-dismiss switch'leri programmatic navigator.back'i engellememeli")

        nav.navigate(lockedSheet)
        waitForIdle()
        onNodeWithText("hide-and-back").performClick()
        waitUntil { nav.current == Catalog }
        assertEquals(Catalog, nav.current, "hideAndBack sheet'i gizleyip entry'yi poplamalı")
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
