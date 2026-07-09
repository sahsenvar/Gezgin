package dev.gezgin.core.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Faz 5 recheck — C1 / MJ3: desktop (jvm) actual'ının per-entry `ViewModelStore` decorator'ını
 * ([rememberPlatformEntryDecorators]) Android ile birebir semantiğe getirdiğinin uçtan-uca (GezginDisplay
 * üzerinden) kanıtı. Üretilen MVI entry'lerinin VM'i `viewModel(factory = …)` ile çözer; bu test aynı
 * çözümleyiciyi kullanır.
 *
 * Decorator'sız (eski `emptyList()`) actual'a karşı bu test KIRMIZI'dır: ya `viewModel()` ilk render'da
 * `LocalViewModelStoreOwner` yokluğundan patlar, ya da tüm entry'ler pencere-scoped TEK store'u paylaşıp
 * `assertNotSame` (a) düşer. Fix'ten sonra üç davranış da geçer:
 *  - (a) aynı route'un iki stack instance'ı AYRI VM alır (per-entry child store, `contentKey = id`),
 *  - (b) pop edilen entry'nin VM'i `clear()`'lanır → `onCleared`,
 *  - (c) cover + recompose boyunca hâlâ stack'te olan entry AYNI VM'i korur.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopViewModelStoreDecoratorTest {

    private class ProbeVm : ViewModel() {
        var clearedCount = 0
            private set

        override fun onCleared() {
            clearedCount++
        }
    }

    @Composable
    private fun ProbeScreen(onResolved: (ProbeVm) -> Unit) {
        val vm: ProbeVm = viewModel(factory = viewModelFactory { initializer { ProbeVm() } })
        var bump by remember { mutableStateOf(0) }
        SideEffect { onResolved(vm) }   // her başarılı composition'da o an görünür entry'nin VM'ini bildir
        BasicText(
            text = "probe=$bump",
            modifier = Modifier.testTag("probe").clickable { bump++ },   // tıklama = aynı entry'yi recompose
        )
    }

    @Test
    fun `desktop entry-scoped VM - iki instance ayri VM, pop clear eder, cover-recompose korur`() =
        runComposeUiTest {
            val nav = RawNavigator(start = Product("a"), topology = testTopology)
            var topVm: ProbeVm? = null
            setContent {
                GezginDisplay(navigator = nav) {
                    register<Product> { ProbeScreen(onResolved = { topVm = it }) }
                    register<Catalog> { BasicText("Other") }
                }
            }
            waitForIdle()
            val vmA = requireNotNull(topVm) { "start entry VM çözülmeliydi (owner yoksa burada patlardı)" }

            // (c) aynı entry'yi recompose et → viewModel() AYNI instance döner (VM recomposition'da yaşar).
            onNodeWithTag("probe").performClick()
            waitForIdle()
            assertSame(vmA, topVm, "recomposition VM'i değiştirmemeli")

            // (a) aynı route TİPİNİN ikinci instance'ı (farklı arg) push → ayrı contentKey → AYRI VM.
            nav.navigate(Product("b"))
            waitForIdle()
            val vmB = requireNotNull(topVm)
            assertNotSame(vmA, vmB, "aynı route'un iki stack instance'ı AYRI VM almalı (per-entry store)")
            assertEquals(0, vmB.clearedCount)

            // (b) top'u pop et → #b'nin store'u clear → vmB.onCleared; vmA hâlâ canlı (cover'dan kurtuldu).
            nav.back()
            waitForIdle()
            assertEquals(1, vmB.clearedCount, "pop edilen entry'nin VM'i clear edilmeli (onCleared)")
            assertSame(vmA, topVm, "cover + pop sonrası dipteki entry AYNI VM'i korumalı")
            assertEquals(0, vmA.clearedCount, "hâlâ stack'te olan entry'nin VM'i clear edilmemeli")
        }
}
