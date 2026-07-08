package dev.gezgin.core.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test

/**
 * Faz 4.0 spike — desktop uiTest: bir `EntryKind.DIALOG` entry'nin [GezginNavDisplay]/DialogSceneStrategy
 * wiring'i üzerinden çökmeden overlay olarak render olduğu smoke kanıtı. **En güçlü/en güvenilir
 * assertion = "arka görünür":** dialog entry top iken alttaki (overlaid) SCREEN entry HÂLÂ görünür.
 * Bu, Faz 3 davranışından (dialog kind = SinglePaneScene = üstteki TEK entry render, alt REPLACE) net
 * ayrımdır → scene overlay'in gerçekten devrede olduğunu kanıtlar (wiring olmasa "home" pop-out olurdu).
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

        onNodeWithText("home-screen").assertIsDisplayed()  // overlaid entry hâlâ compose ediliyor
        onNodeWithText("dialog-body").assertIsDisplayed()  // dialog içeriği render oldu
    }

    @Test
    fun `SCREEN kind - Faz 3 tek-pane davranisi bozulmaz`() = runComposeUiTest {
        val nav = RawNavigator(start = Catalog, topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Catalog> { BasicText("catalog") }
                register<Product> { BasicText("product") }  // kind = SCREEN (default)
            }
        }
        onNodeWithText("catalog").assertIsDisplayed()
        nav.navigate(Product("x"))
        waitForIdle()
        // SCREEN push = tek-pane → yalnız top görünür (overlay DEĞİL).
        onNodeWithText("product").assertIsDisplayed()
    }
}
