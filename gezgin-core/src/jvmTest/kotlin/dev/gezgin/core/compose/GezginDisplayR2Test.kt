package dev.gezgin.core.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test

/**
 * Task 3.3 — desktop uiTest: R2 (§2.1) davranışsal kanıtı + Task 3.2 follow-up 4a (replaceTo twin).
 * `rememberSaveable` içeriği [GezginDisplay]'in wire ettiği `rememberSaveableStateHolderNavEntryDecorator`
 * sayesinde entry başına (contentKey = `GezginKey.id`) ayrı slot'ta yaşar. Bu JB CMP 1.11.0 desktop
 * portu `assertExists` yok → "yok" kontrolleri `onAllNodesWithText(...).assertCountEquals(0)`.
 *
 * **4b (StateRestorationTester) KAPSAM DIŞI — desktop'ta uygulanmamış:** `StateRestorationTester`
 * sınıfı ui-test-desktop 1.11.0'da MEVCUT ama `emulateSaveAndRestore()` skiko actual'ı
 * (`StateRestorationTester_skikoKt.platformEncodeDecode`) bir `TODO()`'dur → çağrıldığında
 * `NotImplementedError` atar (doğrulandı, task-3.3-report.md). Fabrike edilmedi; PD round-trip
 * kapsamı saf-fonksiyon [encodeNavigatorState]/[decodeNavigatorState] üzerinden RememberNavigatorSaverTest'te
 * (task 3.2 deliverable e) korunur.
 */
@OptIn(ExperimentalTestApi::class)
class GezginDisplayR2Test {

    /** Sayaç ekranı — `rememberSaveable`; tıklama artırır. Tag ile hedeflenir (aynı anda yalnız top compose edilir). */
    @Composable
    private fun CounterScreen(tag: String, label: String) {
        var count by rememberSaveable { mutableStateOf(0) }
        BasicText(text = "$label=$count", modifier = Modifier.testTag(tag).clickable { count++ })
    }

    @Test
    fun `R2 - ayni-degerli iki Detail entry AYRI saved state alir (id bazli, deger bazli degil)`() = runComposeUiTest {
        // start = Detail("42") = #a (dip). Stack sonunda [Detail(42)#a, Other, Detail(42)#b].
        val nav = RawNavigator(start = Product("42"), topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Product> { CounterScreen(tag = "detail", label = "Detail") }
                register<Catalog> { BasicText("Other") }
            }
        }

        // (1) Dipteki #a sayacını 1'e getir — SONRA üstüne push edip #a'yı compose'dan DÜŞÜR.
        onNodeWithTag("detail").performClick()
        waitForIdle()
        onNodeWithText("Detail=1").assertIsDisplayed()

        nav.navigate(Catalog)             // #a dispose olur → saveable decorator #a state'ini KAYDEDER
        nav.navigate(Product("42"))       // aynı DEĞER, farklı id → ayrı entry #b (taze, 0)
        waitForIdle()

        // (2) Top #b TAZE (0), #a'nın 1'inden BAĞIMSIZ → id-başına, değer-başına DEĞİL. #b'yi 2'ye getir.
        onNodeWithText("Detail=0").assertIsDisplayed()
        onNodeWithTag("detail").performClick()
        onNodeWithTag("detail").performClick()
        waitForIdle()
        onNodeWithText("Detail=2").assertIsDisplayed()

        // (3) İki kez geri → dipteki #a'ya dön.
        nav.back()                        // pop #b → Other
        waitForIdle()
        nav.back()                        // pop Other → #a (yeniden compose)
        waitForIdle()

        // #a HÂLÂ 1: (a) #b'nin 2'sine bulaşmadı (per-id, değer değil) — spec'in istediği R2 kanıtı;
        // (b) dispose/restore boyunca 1'i KORUDU (0'a sıfırlanmadı) → bu, saveable-state-holder
        // decorator'ının (deliverable 1) kablolandığının davranışsal kanıtı: decorator olmadan #a taze
        // (0) compose edilir ve bu "Detail=1" assertion'ı RED olur (bkz. task-3.3-report.md TDD kanıtı).
        onNodeWithText("Detail=1").assertIsDisplayed()
        onAllNodesWithText("Detail=2").assertCountEquals(0)
        onAllNodesWithText("Detail=0").assertCountEquals(0)
    }

    @Test
    fun `4a - replaceTo ayni-deger-farkli-id yeni contentKey render eder ve saved state SIFIRLANIR`() = runComposeUiTest {
        val nav = RawNavigator(start = Product("42"), topology = testTopology)
        setContent {
            GezginDisplay(navigator = nav) {
                register<Product> { CounterScreen(tag = "detail", label = "Detail") }
            }
        }
        // Sayacı artır → 1.
        onNodeWithTag("detail").performClick()
        waitForIdle()
        onNodeWithText("Detail=1").assertIsDisplayed()

        // Aynı DEĞER, YENİ id ile replace — backStack (Route listesi) DEĞİŞMEZ; keysState (id) değişir →
        // recompose + yeni contentKey → taze saveable slot (Detail=0). keysState olmadan (backStack dedup)
        // bu recompose HİÇ tetiklenmez ve ekran Detail=1'de kalırdı (4a red kanıtı, task-3.3-report.md).
        nav.replaceTo(Product("42"))
        waitForIdle()

        onNodeWithText("Detail=0").assertIsDisplayed()
        onAllNodesWithText("Detail=1").assertCountEquals(0)
    }
}
