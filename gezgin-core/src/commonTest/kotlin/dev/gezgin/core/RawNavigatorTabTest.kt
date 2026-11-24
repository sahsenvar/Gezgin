@file:OptIn(dev.gezgin.core.GezginInternalApi::class)

package dev.gezgin.core

import dev.gezgin.core.fixtures.*
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class RawNavigatorTabTest {
  private val json = Json { serializersModule = tabSerializersModule }

  private fun nav(onRootBack: () -> Unit = {}) =
    RawNavigator(start = HomeTab, topology = tabTopology, onRootBack = onRootBack)

  @Test
  fun `switchTo aktif sekmeyi degistirir backStack yalniz aktif segmenti gosterir`() {
    val n = nav()
    n.navigate(TabDetail("1"))
    n.switchTo(SearchTab)

    assertEquals(SearchTab, n.current)
    assertEquals(listOf<Route>(SearchTab), n.backStack.value)
    assertEquals(listOf(HomeTab, TabDetail("1"), SearchTab), n.keys.map { it.route })
    assertEquals(listOf(SearchTab), n.activeKeys.map { it.route })
  }

  @Test
  fun `sekme durumu korunur geri donuste ayni entry kimlikleri`() {
    val n = nav()
    n.navigate(TabDetail("1"))
    val ids = n.activeKeys.map { it.id }
    n.switchTo(SearchTab)
    n.switchTo(HomeTab)

    assertEquals(listOf<Route>(HomeTab, TabDetail("1")), n.backStack.value)
    assertEquals(ids, n.activeKeys.map { it.id })
  }

  @Test
  fun `sekme gecisi TabSwitched event yayar ayni sekme no-op sessiz`() = runTest {
    val n = nav()
    val events = mutableListOf<NavEvent>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { n.events.collect { events += it } }

    n.switchTo(SearchTab)
    n.switchTo(SearchTab)
    n.switchTo(HomeTab)
    job.cancel()

    assertEquals(
      listOf<NavEvent>(
        NavEvent.TabSwitched(HomeTab, SearchTab),
        NavEvent.TabSwitched(SearchTab, HomeTab),
      ),
      events,
    )
  }

  @Test
  fun `sekme kokunda back onRootBack cagirir ve sekmeler arasi gezmez`() {
    var root = 0
    val n = nav { root++ }
    n.switchTo(SearchTab)
    n.back()

    assertEquals(1, root)
    assertEquals(SearchTab, n.current)
  }

  @Test
  fun `sekme icinde back yalniz o sekmenin yigininden pop eder`() {
    val n = nav()
    n.switchTo(SearchTab)
    n.navigate(TabDetail("s"))
    n.back()

    assertEquals(SearchTab, n.current)
    assertEquals(listOf(HomeTab, SearchTab), n.keys.map { it.route })
  }

  @Test
  fun `replaceTo koku degistirince tum segmentleri temizler ve Replaced hepsini listeler`() =
    runTest {
      val n = nav()
      n.navigate(TabDetail("1"))
      n.switchTo(SearchTab)
      val events = mutableListOf<NavEvent>()
      val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { n.events.collect { events += it } }

      n.replaceTo(TabLogin)
      job.cancel()

      assertEquals(listOf(TabLogin), n.keys.map { it.route })
      assertEquals(
        NavEvent.Replaced(listOf(HomeTab, TabDetail("1"), SearchTab), TabLogin),
        events.single(),
      )
    }

  @Test
  fun `pasif segmentte bekleyen sonuc slotu replaceTo ile dusurulur`() = runTest {
    val n = nav()
    n.navigate(TabDetail("1"))
    val callerId = n.currentEntryId
    n.launchForResult(callerId, "edge", TabDetail("pick"))
    n.switchTo(SearchTab)
    val events = mutableListOf<NavEvent>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { n.events.collect { events += it } }

    n.replaceTo(TabLogin)
    job.cancel()

    assertEquals(listOf(TabLogin), n.keys.map { it.route })
    assertTrue(events.any { it is NavEvent.ResultDropped && it.edgeId == "edge" })
  }

  @Test
  fun `tab olmayan route ile switchTo hata verir`() {
    assertFailsWith<IllegalArgumentException> { nav().switchTo(TabLogin) }
  }

  @Test
  fun `pasif segmentteki entry isOnStack ama top degil ve pinli back yok sayilir`() {
    val n = nav()
    val homeId = n.currentEntryId
    n.switchTo(SearchTab)

    assertTrue(n.isOnStack(homeId))
    n.back(homeId)
    assertEquals(SearchTab, n.current)
  }

  @Test
  fun `process death - segmentler ve aktif sekme gidis donus`() {
    val n1 = nav()
    n1.navigate(TabDetail("1"))
    n1.switchTo(SearchTab)
    n1.navigate(TabDetail("2"))
    val saved =
      json.decodeFromString(
        SavedState.serializer(),
        json.encodeToString(SavedState.serializer(), n1.save()),
      )

    val n2 = RawNavigator(HomeTab, tabTopology, onRootBack = {}, json = json, restored = saved)

    assertEquals(n1.keys, n2.keys)
    assertEquals(listOf<Route>(SearchTab, TabDetail("2")), n2.backStack.value)
    n2.switchTo(HomeTab)
    assertEquals(listOf<Route>(HomeTab, TabDetail("1")), n2.backStack.value)
    n2.navigate(TabDetail("3"))
    assertEquals(n2.keys.size, n2.keys.map { it.id }.toSet().size)
  }

  @Test
  fun `eski tek segmentli kayit tek segment olarak acilir`() {
    val legacy =
      """{"keys":[{"route":{"type":"dev.gezgin.core.fixtures.HomeTab"},"id":0},""" +
        """{"route":{"type":"dev.gezgin.core.fixtures.TabDetail","id":"1"},"id":1}],""" +
        """"nextId":2,"pendingSlots":[]}"""
    val saved = json.decodeFromString(SavedState.serializer(), legacy)

    val n = RawNavigator(HomeTab, tabTopology, onRootBack = {}, json = json, restored = saved)

    assertNull(saved.activeSegment)
    assertEquals(listOf<Route>(HomeTab, TabDetail("1")), n.backStack.value)
    assertEquals(2, n.keys.size)
    n.switchTo(SearchTab)
    n.switchTo(HomeTab)
    assertEquals(listOf<Route>(HomeTab, TabDetail("1")), n.backStack.value)
  }

  @Test
  fun `callback route gecici entry pasif segmentte de dropTransientEntries ile temizlenir`() {
    val topology =
      GezginTopology(
        flowChains = emptyMap(),
        flowStarts = emptyMap(),
        edges = emptyMap(),
        transientRoutes = setOf(TabDetail::class),
        tabRoots = setOf(HomeTab::class, SearchTab::class),
      )
    val n = RawNavigator(HomeTab, topology)
    n.navigate(TabDetail("t"))
    n.switchTo(SearchTab)

    n.dropTransientEntries()

    assertEquals(listOf(HomeTab, SearchTab), n.keys.map { it.route })
    assertEquals(SearchTab, n.current)
  }
}
