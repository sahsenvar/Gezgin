@file:OptIn(dev.gezgin.core.GezginInternalApi::class)

package dev.gezgin.core.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.HomeTab
import dev.gezgin.core.fixtures.SearchTab
import dev.gezgin.core.fixtures.TabDetail
import dev.gezgin.core.fixtures.TabLogin
import dev.gezgin.core.fixtures.tabTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Sekme yığını — Nav3 varsayımının kanıtı: pasif sekmenin entry'leri decorator listesinde kaldığı
 * için "pop" sayılmaz; ViewModel store'u ve `rememberSaveable` durumu sekme geçişlerinden sağ
 * çıkar, `replaceTo` ile listeden düşünce ise store temizlenir.
 */
@OptIn(ExperimentalTestApi::class)
class GezginDisplayTabTest {
  private class ProbeVm : ViewModel() {
    var cleared = 0
      private set

    override fun onCleared() {
      cleared++
    }
  }

  private val vms = mutableMapOf<String, ProbeVm>()

  @Composable
  private fun Probe(label: String) {
    val vm: ProbeVm = viewModel(factory = viewModelFactory { initializer { ProbeVm() } })
    var count by rememberSaveable { mutableStateOf(0) }
    SideEffect { vms[label] = vm }
    BasicText("$label count=$count", Modifier.testTag(label).clickable { count++ })
  }

  @Composable
  private fun Display(nav: RawNavigator) {
    GezginDisplay(navigator = nav) {
      register<HomeTab> { Probe("home") }
      register<SearchTab> { Probe("search") }
      register<TabDetail> { route -> Probe("detail-${route.id}") }
      register<TabLogin> { Probe("login") }
    }
  }

  @Test
  fun `pasif sekmenin VM ve saveable durumu sekme gecislerinden saglam cikar`() = runComposeUiTest {
    val nav = RawNavigator(start = HomeTab, topology = tabTopology)
    setContent { Display(nav) }
    waitForIdle()

    onNodeWithTag("home").performClick()
    nav.navigate(TabDetail("1"))
    waitForIdle()
    onNodeWithTag("detail-1").performClick()
    onNodeWithTag("detail-1").performClick()
    val homeVm = vms.getValue("home")
    val detailVm = vms.getValue("detail-1")

    nav.switchTo(SearchTab)
    waitForIdle()
    onNodeWithText("search count=0").assertIsDisplayed()
    onAllNodesWithText("detail-1", substring = true).assertCountEquals(0)
    assertEquals(0, homeVm.cleared, "pasif sekmenin kök VM'i temizlenmemeli")
    assertEquals(0, detailVm.cleared, "pasif sekmenin üst entry VM'i temizlenmemeli")

    onNodeWithTag("search").performClick()
    val searchVm = vms.getValue("search")
    nav.switchTo(HomeTab)
    waitForIdle()

    onNodeWithText("detail-1 count=2").assertIsDisplayed()
    assertSame(detailVm, vms.getValue("detail-1"), "geri dönüşte aynı VM")
    assertEquals(0, searchVm.cleared)

    nav.switchTo(SearchTab)
    waitForIdle()
    onNodeWithText("search count=1").assertIsDisplayed()
    assertSame(searchVm, vms.getValue("search"))
    assertEquals(0, detailVm.cleared)
    assertEquals(0, homeVm.cleared)
  }

  @Test
  fun `sekme kokunda back onRootBack cagirir ve sekmeler arasi gezmez`() = runComposeUiTest {
    var rootBack = 0
    val nav = RawNavigator(start = HomeTab, topology = tabTopology, onRootBack = { rootBack++ })
    setContent { Display(nav) }
    waitForIdle()
    nav.switchTo(SearchTab)
    waitForIdle()

    nav.back()
    waitForIdle()

    assertEquals(1, rootBack)
    onNodeWithText("search count=0").assertIsDisplayed()
  }

  @Test
  fun `tum yigini degistiren replaceTo pasif sekmelerin VM store unu temizler`() =
    runComposeUiTest {
      val nav = RawNavigator(start = HomeTab, topology = tabTopology)
      setContent { Display(nav) }
      waitForIdle()
      nav.switchTo(SearchTab)
      waitForIdle()
      val homeVm = vms.getValue("home")
      val searchVm = vms.getValue("search")

      nav.replaceTo(TabLogin)
      waitForIdle()

      onNodeWithText("login count=0").assertIsDisplayed()
      assertEquals(1, homeVm.cleared, "pasif sekme listeden düşünce store temizlenir")
      assertEquals(1, searchVm.cleared)
    }
}
