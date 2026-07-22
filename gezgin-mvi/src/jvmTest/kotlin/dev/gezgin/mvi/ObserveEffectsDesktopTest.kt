package dev.gezgin.mvi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gezgin.mvi.fixtures.CounterEffect
import dev.gezgin.mvi.fixtures.CounterIntent
import dev.gezgin.mvi.fixtures.CounterRoute
import dev.gezgin.mvi.fixtures.CounterViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * MN-A — desktop (jvm) MVI'nın uçtan-uca kanıtı + MJ-A stale-lambda fix'inin regresyon kilidi.
 *
 * Her iki test de `collectAsStateWithLifecycle` / [ObserveEffects]'in bağlı olduğu host-sürülen
 * `LocalLifecycleOwner`'ı `runComposeUiTest` üzerinden sağlar (JVM/desktop, cihazsız). Owner
 * STARTED'a çıkmasaydı `repeatOnLifecycle(STARTED)` HİÇ açılmaz, state başlangıç değerinde donar ve
 * efekt HİÇ teslim edilmezdi — bu testler o boşluğu (audit MN-A) doğrudan koşarak kapatır.
 */
@OptIn(ExperimentalTestApi::class)
class ObserveEffectsDesktopTest {

  // commonTest'ten miras alınan `kotlinx-coroutines-test` (ServiceLoader) `Dispatchers.Main`'i bir
  // TestMainDispatcher ile değiştirir; `setMain` çağrılmadan `Main.immediate`'a erişmek
  // (repeatOnLifecycle
  // + collectAsStateWithLifecycle içinde) fırlatır. runComposeUiTest bir Main KURMADIĞINDAN burada
  // elle
  // kuruyoruz — böylece gerçek host-sürülen lifecycle akışını (STARTED-gate) cihazsız
  // koşabiliyoruz.
  @BeforeTest fun installMain() = Dispatchers.setMain(Dispatchers.Unconfined)

  @AfterTest fun clearMain() = Dispatchers.resetMain()

  /**
   * MN-A: state gerçekten render olur (collectAsStateWithLifecycle) VE efekt ObserveEffects'le
   * gözlenir.
   */
  @Test
  fun `desktop MVI uctan-uca - state render olur, efekt gozlenir`() = runComposeUiTest {
    val vm = CounterViewModel(CounterRoute(start = 0))
    val received = mutableListOf<CounterEffect>()

    setContent {
      val state by vm.uiState.collectAsStateWithLifecycle()
      ObserveEffects(vm.effects) { received.add(it) }
      BasicText(text = "count=${state.count}", modifier = Modifier.testTag("count"))
    }
    waitForIdle()
    onNodeWithText("count=0").assertExists()

    vm.onIntent(CounterIntent.Increment) // state -> 1, Toast(count=1) yayılır
    waitUntil(timeoutMillis = 2_000) { received.isNotEmpty() }

    onNodeWithText("count=1").assertExists() // state RENDER edildi (lifecycle STARTED'a çıktı)
    assertEquals(listOf<CounterEffect>(CounterEffect.Toast("count=1")), received)
  }

  /**
   * MJ-A (RED→GREEN): recomposition'da `onEffect` yeni bir lambda ile değişince (effects +
   * lifecycleOwner stabil olduğundan LaunchedEffect RESTART ETMEZ), SONRADAN yayılan efekt YENİ
   * lambda'ya gitmeli. Fix'siz (`collect(onEffect)`) collector İLK composition'ın lambda'sını
   * sonsuza dek bağlar → `handler=0` yakalayan bayat lambda çağrılır ("h0:count=1") ve bu assert
   * KIRMIZI olurdu.
   */
  @Test
  fun `onEffect swap sonrasi yayilan efekt YENI lambda'ya gider (stale-lambda fix)`() =
    runComposeUiTest {
      val vm = CounterViewModel(CounterRoute(start = 0))
      val received = mutableListOf<String>()

      setContent {
        var handler by remember { mutableStateOf(0) }
        val current = handler // düz Int (state okuması), lambda tarafından DEĞERLE yakalanır
        ObserveEffects(vm.effects) { effect ->
          received.add("h$current:${(effect as CounterEffect.Toast).text}")
        }
        BasicText(
          text = "handler=$handler",
          modifier = Modifier.testTag("swap").clickable { handler++ }, // tık = lambda'yı değiştir
        )
      }
      waitForIdle()

      onNodeWithTag("swap").performClick() // handler 0 -> 1: onEffect yeni lambda (current=1) olur
      waitForIdle()

      vm.onIntent(CounterIntent.Increment) // Toast(count=1) — swap SONRASI yayılır
      waitUntil(timeoutMillis = 2_000) { received.isNotEmpty() }

      assertEquals(listOf("h1:count=1"), received) // bayat h0 değil, güncel h1
    }
}
