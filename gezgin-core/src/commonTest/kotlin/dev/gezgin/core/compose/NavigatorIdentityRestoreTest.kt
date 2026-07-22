package dev.gezgin.core.compose

import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testSerializersModule
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.serialization.json.Json

private val testJson = Json { serializersModule = testSerializersModule }

/**
 * Faz5 **C1 (Critical) regresyonu** (spec §225 "stable RawNavigator") — config-change'te navigator
 * KİMLİĞİ stabil kalmalı: VM ctor'unda yakalanan referans, restore'dan sonra da display'in
 * gözlemlediği state'i sürmeli.
 *
 * Compose runtime + gerçek config-change bu ortamda (commonTest) simüle EDİLEMEZ — fix'in retention
 * katmanı (Android ViewModel-scope'lu holder) platform-özeldir. Bu yüzden burada fix'in TESTABLE
 * çekirdeği [RawNavigator.adoptRestored] doğrudan pinlenir (aynı instance state'i re-point eder,
 * yeni instance ÜRETMEZ) + eski çift-instance restore tasarımının VM↔display referanslarını
 * AYRIŞTIRDIĞI (C1'in ta kendisi) karakterize edilir.
 */
class NavigatorIdentityRestoreTest {

  @Test
  fun `legacy callers use one stable restore namespace`() {
    assertEquals("dev.gezgin.core.rememberNavigator.legacy", LEGACY_REMEMBER_NAVIGATOR_RESTORE_KEY)
    assertEquals(
      LEGACY_REMEMBER_NAVIGATOR_RESTORE_KEY,
      restoreNamespace(LEGACY_REMEMBER_NAVIGATOR_RESTORE_KEY),
    )
  }

  @Test
  fun `adoptRestored AYNI instance state'i re-point eder - VM referansi restore edilen state'i surer`() {
    // `navigator` = hem VM'in ctor'da yakaladığı referans, hem display'in keysState'ini collect
    // ettiği
    // kaynak — TEK instance. Config-change'te (Android holder) korunur; PD'de taze instance
    // snapshot'ı
    // adopt eder. Her iki durumda da VM + display AYNI instance'ı görür.
    val navigator = RawNavigator(start = Feed, topology = testTopology)
    val displaySource = navigator.keysState // display bunu collect eder
    val backSource = navigator.backStack
    navigator.navigate(Catalog) // [Feed, Catalog]
    val snapshot = navigator.save()

    // config-change/PD restore: AYNI instance state'i adopt eder (yeni RawNavigator KURULMAZ)
    navigator.adoptRestored(snapshot)

    // StateFlow instance'ları KORUNDU → restore'dan önce bağlanmış collector'lar kopmaz
    assertSame(displaySource, navigator.keysState)
    assertSame(backSource, navigator.backStack)
    assertEquals(listOf<Route>(Feed, Catalog), displaySource.value.map { it.route })

    // VM-referansı üzerinden navigasyon → display'in gözlemlediği kaynakta (aynı flow) anında
    // görünür
    navigator.navigate(Product("9"))
    assertEquals(Product("9"), displaySource.value.last().route)
    navigator.back()
    assertEquals(Catalog, navigator.current)
    assertEquals(Catalog, displaySource.value.last().route)
  }

  @Test
  fun `adoptRestored idempotent - ayni snapshot iki kez re-point ederse state sabit, StateFlow instance korunur (MN-1)`() {
    // MN-1 idempotence pin: adoptRestored'ın AYNI snapshot'la tekrar çağrılması state'i aynı değere
    // sabitler (ne stack büyür ne slot diriltilir). Android caller `adoptChecked` ile
    // config-change'te
    // re-adopt'u zaten engeller (o katman commonTest'te simüle edilemez); bu test altındaki runtime
    // garantisini — re-adopt'un kendisinin zararsız/idempotent olduğunu — doğrudan pinler.
    val navigator = RawNavigator(start = Feed, topology = testTopology)
    val displaySource = navigator.keysState
    navigator.navigate(Catalog)
    navigator.navigate(Product("3")) // [Feed, Catalog, Product(3)]
    val snapshot = navigator.save()

    navigator.adoptRestored(snapshot)
    val afterFirst = displaySource.value.map { it.route }
    navigator.adoptRestored(snapshot) // ikinci re-point — idempotent olmalı
    val afterSecond = displaySource.value.map { it.route }

    assertEquals(afterFirst, afterSecond)
    assertEquals(listOf<Route>(Feed, Catalog, Product("3")), afterSecond)
    assertSame(
      displaySource,
      navigator.keysState,
    ) // aynı akış instance'ı → mevcut collector'lar kopmaz
  }

  @Test
  fun `adoptRestored farkli bir stack'i benimser - taze instance snapshot'a doner (PD yolu)`() {
    // PD: rememberNavigator taze bir navigator'ı `start`'ta kurar, sonra Bundle'daki snapshot'ı
    // adopt eder.
    val session1 = RawNavigator(start = Feed, topology = testTopology)
    session1.navigate(Catalog)
    session1.navigate(Product("7")) // [Feed, Catalog, Product(7)]
    val snapshot = session1.save()

    val restored = RawNavigator(start = Feed, topology = testTopology) // taze @Feed (PD sonrası)
    restored.adoptRestored(snapshot)

    assertEquals(listOf<Route>(Feed, Catalog, Product("7")), restored.backStack.value)
    assertEquals(Product("7"), restored.current)
  }

  @Test
  fun `karakterizasyon - eski cift-instance restore VM ve display referanslarini AYRISTIRIR (C1 bug)`() {
    // HEAD davranışı: `rememberSaveable` restore'da YENİ bir RawNavigator kurardı
    // (decodeNavigatorState).
    // VM eskisini, display yenisini tutardı → VM-driven navigasyon display'de GÖRÜNMEZDİ.
    val vmHeld = RawNavigator(start = Feed, topology = testTopology)
    vmHeld.navigate(Catalog)
    val encoded = encodeNavigatorState(vmHeld, testJson)
    val displayObserved =
      decodeNavigatorState(
        encoded,
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )

    vmHeld.navigate(Product("1")) // VM eski referansı sürer
    assertNotSame(vmHeld, displayObserved)
    assertEquals(Product("1"), vmHeld.current)
    assertEquals(Catalog, displayObserved.current) // display, VM'in navigasyonunu GÖRMEZ = C1
  }

  @Test
  fun `decodeSavedStateOrNull gecerli snapshot'i decode eder, bozuk-bos null doner`() {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    nav.navigate(Catalog)
    val encoded = encodeNavigatorState(nav, testJson)

    val decoded = decodeSavedStateOrNull(encoded, testJson)
    assertNotNull(decoded)
    assertEquals(listOf<Route>(Feed, Catalog), decoded.keys.map { it.route })

    assertNull(
      decodeSavedStateOrNull("{ not valid json", testJson)
    ) // bozuk json → null (fresh-start)
    assertNull(decodeSavedStateOrNull("""{"unexpected":1}""", testJson)) // şema-uyumsuz → null
  }
}
