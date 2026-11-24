package dev.gezgin.core.compose

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.HomeTab
import dev.gezgin.core.fixtures.TabDetail
import dev.gezgin.core.fixtures.TabOwnSwitch
import dev.gezgin.core.fixtures.routeSwitchTransitionFixture
import dev.gezgin.core.fixtures.tabTopology
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GezginTabTransitionTest {
  private val forwardKey = NavDisplay.transitionSpec { null }.keys.single()

  private fun scope() =
    GezginEntryScope().apply {
      register<HomeTab> {}
      register<TabDetail> {}
    }

  @Test
  fun `tab topolojisinde entry segment etiketi ve tab-aware forward tasir`() {
    val nav = RawNavigator(start = HomeTab, topology = tabTopology)
    val entry =
      scope().toNavEntry(GezginKey(HomeTab, id = 4L, segment = 4L), nav, transitions = null)

    assertEquals(4L, entry.metadata[GEZGIN_TAB_SEGMENT_KEY])
    assertTrue(
      forwardKey in entry.metadata,
      "geçiş tanımı olmasa da sekme geçişi için forward yazılır",
    )
  }

  @Test
  fun `tabsiz topolojide metadata degismez`() {
    val nav = RawNavigator(start = HomeTab, topology = testTopology)
    val entry = scope().toNavEntry(GezginKey(HomeTab, id = 4L), nav, transitions = null)

    assertTrue(
      entry.metadata.isEmpty(),
      "tabsız uygulamada metadata boş kalmalı: ${entry.metadata.keys}",
    )
  }

  @Test
  fun `switch gecisi route once app sonra yerlesik capraz solma kaskadi`() {
    val app = transition { switch { fadeIn() togetherWith fadeOut() } }

    assertSame(routeSwitchTransitionFixture.switch, resolveTabSwitchTransition(TabOwnSwitch, app))
    assertSame(app.switch, resolveTabSwitchTransition(HomeTab, app))
    assertNotNull(resolveTabSwitchTransition(HomeTab, null), "yerleşik varsayılan her zaman vardır")
  }
}
