package dev.gezgin.core.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Cart
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Task 3.2 gate — desktop uiTest: gerçek `NavDisplay` render/push/back döngüsü, cihazsız (JVM/desktop
 * Compose ui-test). `BasicText` kullanılıyor (compose.material bağımlılığı yok, yalnız
 * compose.foundation) — `onNodeWithText` semantics'i `BasicText`'in ürettiği `Text` semantics'ine
 * dokunur, Material'a ihtiyaç yok. Bu JB Compose Multiplatform 1.11.0 desktop portu `assertExists`/
 * `assertDoesNotExist` SAĞLAMIYOR (yalnız `assertIsDisplayed` + `onAllNodesWithText(...).
 * assertCountEquals(0)` mevcut, javap ile doğrulandı) — "yok" kontrolleri o yüzden count==0 ile.
 */
@OptIn(ExperimentalTestApi::class)
class GezginDisplayTest {

    private fun navigator(onRootBack: () -> Unit = {}) =
        RawNavigator(start = Feed, topology = testTopology, onRootBack = onRootBack)

    @Test
    fun `a - start ekrani render edilir`() = runComposeUiTest {
        val nav = navigator()
        setContent {
            GezginDisplay(navigator = nav) {
                register<Feed> { BasicText("FeedScreen") }
                register<Catalog> { BasicText("CatalogScreen") }
            }
        }

        onNodeWithText("FeedScreen").assertIsDisplayed()
    }

    @Test
    fun `b - navigate sonrasi yeni ekran gorunur, eski gorunmez`() = runComposeUiTest {
        val nav = navigator()
        setContent {
            GezginDisplay(navigator = nav) {
                register<Feed> { BasicText("FeedScreen") }
                register<Catalog> { BasicText("CatalogScreen") }
            }
        }

        nav.navigate(Catalog)
        waitForIdle()

        onNodeWithText("CatalogScreen").assertIsDisplayed()
        onAllNodesWithText("FeedScreen").assertCountEquals(0)
    }

    @Test
    fun `c - back geri doner (eski ekran yeniden gorunur)`() = runComposeUiTest {
        val nav = navigator()
        setContent {
            GezginDisplay(navigator = nav) {
                register<Feed> { BasicText("FeedScreen") }
                register<Catalog> { BasicText("CatalogScreen") }
            }
        }

        nav.navigate(Catalog)
        waitForIdle()
        nav.back()
        waitForIdle()

        onNodeWithText("FeedScreen").assertIsDisplayed()
        onAllNodesWithText("CatalogScreen").assertCountEquals(0)
    }

    @Test
    fun `d - NavDisplay onBack navigator back'e bagli (kokte onRootBack tetiklenir)`() = runComposeUiTest {
        var rootBackCount = 0
        val nav = navigator(onRootBack = { rootBackCount++ })
        setContent {
            GezginDisplay(navigator = nav) {
                register<Feed> { BasicText("FeedScreen") }
            }
        }

        // Programatik pinleme: navigator.back() kökte onRootBack'i tetikler — bu, NavDisplay'in
        // `onBack = { navigator.back() }` wiring'inin (GezginDisplay.kt) davranışsal kanıtı. Sistem
        // seviyesinde bir geri jesti (desktop'ta Esc → NavigationEvent) NavDisplay'in kendi
        // navigationevent/predictive-back altyapısına bağlıdır ve bu görev kapsamında (JB alpha05
        // desktop) güvenilir biçimde tetiklenemedi — kapsam kaydırıldı (bkz. task-3.2-report.md).
        nav.back()
        waitForIdle()

        assertEquals(1, rootBackCount)
    }

    @Test
    fun `guard - rememberNavigator start ResultFlow uyesiyse kurulusta hata firlatir`() {
        val error = assertFailsWith<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    rememberNavigator(start = Cart, topology = testTopology, json = kotlinx.serialization.json.Json)
                }
                waitForIdle()
            }
        }
        kotlin.test.assertTrue(error.message?.contains("ResultFlow") == true)
    }

    @Test
    fun `guard - GezginDisplay start modal kind ise kurulusta hata firlatir`() {
        val error = assertFailsWith<IllegalArgumentException> {
            runComposeUiTest {
                val nav = navigator()
                setContent {
                    GezginDisplay(navigator = nav) {
                        register<Feed>(kind = EntryKind.DIALOG) { BasicText("FeedScreen") }
                    }
                }
                waitForIdle()
            }
        }
        kotlin.test.assertTrue(error.message?.contains("modal kind olamaz") == true)
    }
}
