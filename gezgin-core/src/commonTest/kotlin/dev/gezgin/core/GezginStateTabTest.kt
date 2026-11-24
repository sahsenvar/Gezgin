@file:OptIn(GezginInternalApi::class)

package dev.gezgin.core

import dev.gezgin.core.fixtures.HomeTab
import dev.gezgin.core.fixtures.ProfileTab
import dev.gezgin.core.fixtures.SearchTab
import dev.gezgin.core.fixtures.TabDetail
import dev.gezgin.core.fixtures.TabLogin
import dev.gezgin.core.fixtures.tabTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GezginStateTabTest {
  private fun state() = GezginState(emptyList(), 0, tabTopology).also { it.push(HomeTab) }

  @Test
  fun `ilk switch hedef sekmeyi kok ile kurar ve onceki segmenti saklar`() {
    val s = state()
    s.push(TabDetail("1"))
    val sw = assertNotNull(s.switchTo(SearchTab))

    assertTrue(sw.created)
    assertEquals(HomeTab, sw.from.route)
    assertEquals(SearchTab, sw.to.route)
    assertEquals(listOf(SearchTab), s.stack.map { it.route })
    assertEquals(listOf(HomeTab, TabDetail("1"), SearchTab), s.allKeys.map { it.route })
  }

  @Test
  fun `geri donuste entry kimlikleri ve yigin ayni kalir`() {
    val s = state()
    val detail = s.push(TabDetail("1"))!!
    val before = s.allKeys.filter { it.route == HomeTab || it.route == TabDetail("1") }
    s.switchTo(SearchTab)
    s.push(TabDetail("2"))

    val sw = assertNotNull(s.switchTo(HomeTab))

    assertTrue(!sw.created)
    assertEquals(before, s.stack)
    assertEquals(detail.id, s.stack.last().id)
    assertEquals(
      listOf(SearchTab, TabDetail("2"), HomeTab, TabDetail("1")),
      s.allKeys.map { it.route },
    )
  }

  @Test
  fun `ayni sekmeye tekrar switch no-op`() {
    val s = state()
    s.push(TabDetail("1"))
    val before = s.allKeys

    assertNull(s.switchTo(HomeTab))
    assertEquals(before, s.allKeys)
  }

  @Test
  fun `segment kokunde pop null doner ve sekmeler arasi geri yoktur`() {
    val s = state()
    s.switchTo(SearchTab)

    assertNull(s.pop())
    assertEquals(listOf(SearchTab), s.stack.map { it.route })
  }

  @Test
  fun `push ve backTo yalniz aktif segmentte calisir`() {
    val s = state()
    s.push(TabDetail("1"))
    s.switchTo(SearchTab)
    s.push(TabDetail("2"))
    s.push(TabDetail("3"))

    val removed = assertNotNull(s.backTo(SearchTab::class, inclusive = false))

    assertEquals(listOf(TabDetail("2"), TabDetail("3")), removed.map { it.route })
    assertEquals(listOf(HomeTab, TabDetail("1"), SearchTab), s.allKeys.map { it.route })
  }

  @Test
  fun `tum yigini kesen replaceTo butun segmentleri temizler segment kimligi korunur`() {
    val s = state()
    s.switchTo(SearchTab)
    s.switchTo(ProfileTab)
    val segmentBefore = s.activeSegment

    s.replaceUpTo(TabLogin, clearUpTo = null, inclusive = true)

    assertEquals(listOf(TabLogin), s.allKeys.map { it.route })
    assertEquals(segmentBefore, s.activeSegment)
    assertEquals(s.activeSegment, s.stack.single().segment)
  }

  @Test
  fun `kismi replaceTo pasif segmentlere dokunmaz`() {
    val s = state()
    s.push(TabDetail("1"))
    s.switchTo(SearchTab)
    s.push(TabDetail("2"))

    s.replaceUpTo(TabDetail("3"), clearUpTo = null, inclusive = true)

    assertEquals(
      listOf(HomeTab, TabDetail("1"), SearchTab, TabDetail("3")),
      s.allKeys.map { it.route },
    )
  }

  @Test
  fun `tab olmayan hedefe switch hata verir`() {
    assertFailsWith<IllegalArgumentException> { state().switchTo(TabLogin) }
  }

  @Test
  fun `aktif segmentin koku tab degilse switch hata verir`() {
    val s = GezginState(emptyList(), 0, tabTopology)
    s.push(TabLogin)
    s.push(HomeTab)

    assertFailsWith<IllegalStateException> { s.switchTo(SearchTab) }
  }

  @Test
  fun `removeAll pasif segmentlerdeki entryleri de kaldirir`() {
    val s = state()
    s.push(TabDetail("1"))
    s.switchTo(SearchTab)

    val removed = s.removeAll { it.route is TabDetail }

    assertEquals(listOf(TabDetail("1")), removed.map { it.route })
    assertEquals(listOf(HomeTab, SearchTab), s.allKeys.map { it.route })
  }

  @Test
  fun `tabsiz topolojide tek segment duz liste gibi davranir`() {
    val s = GezginState(emptyList(), 0, GezginTopology(emptyMap(), emptyMap(), emptyMap()))
    s.push(HomeTab)
    s.push(TabDetail("1"))

    assertEquals(s.stack, s.allKeys)
    assertEquals(listOf(HomeTab, TabDetail("1")), s.stack.map { it.route })
  }

  @Test
  fun `kayittan kurulan state aktif segmenti ve gruplamayi korur`() {
    val s = state()
    s.push(TabDetail("1"))
    s.switchTo(SearchTab)
    val restored = GezginState(s.allKeys, s.nextId, tabTopology, activeSegment = s.activeSegment)

    assertEquals(s.allKeys, restored.allKeys)
    assertEquals(s.stack, restored.stack)
  }
}
