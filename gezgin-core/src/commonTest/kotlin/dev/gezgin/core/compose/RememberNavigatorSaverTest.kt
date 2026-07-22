package dev.gezgin.core.compose

import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.SavedState
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Otp
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testSerializersModule
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private val testJson = Json { serializersModule = testSerializersModule }

/**
 * Task 3.2 deliverable (e) — [navigatorSaver]'ın altındaki round-trip mantığının
 * ([encodeNavigatorState]/ [decodeNavigatorState]) doğrudan pinlenmesi, Compose runtime KURULMADAN
 * (@Composable DEĞİL, `Saver` sarmalayıcısının `SaverScope`-alıcılı üyesine hiç dokunmadan).
 * Plan'ın izin verdiği "yoksa Saver'ı UI'sız birim testle pinle" fallback'i: desktop uiTest'te
 * `StateRestorationTester` yerine bu daha doğrudan/az kırılgan yol tercih edildi (bkz.
 * task-3.2-report.md — `Saver.save`'in foreign/androidx binary metadata üzerinden extension-member
 * olarak çağrılması bu ortamda unresolved çıktı).
 */
class RememberNavigatorSaverTest {

  @Test
  fun `same restoreKey restores its pushed snapshot while changed restoreKey starts fresh`() {
    val signedIn = RawNavigator(start = Feed, topology = testTopology)
    signedIn.navigate(Catalog)
    val snapshots =
      mapOf(restoreNamespace("account-42") to encodeNavigatorState(signedIn, testJson))

    val restored =
      decodeNavigatorState(
        encoded = snapshots.getValue(restoreNamespace("account-42")),
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )
    val changedKeyNavigator =
      snapshots[restoreNamespace("account-99")]?.let {
        decodeNavigatorState(it, Product("fresh"), testTopology, testJson, onRootBack = {})
      } ?: RawNavigator(start = Product("fresh"), topology = testTopology)

    assertEquals(listOf(Feed, Catalog), restored.keys.map { it.route })
    assertEquals(Product("fresh"), changedKeyNavigator.current)
    assertEquals(1, changedKeyNavigator.keys.size)
  }

  @Test
  fun `blank restoreKey is rejected with rememberNavigator guidance`() {
    val failure = kotlin.test.assertFailsWith<IllegalArgumentException> { restoreNamespace("   ") }

    assertTrue(failure.message.orEmpty().contains("rememberNavigator"))
    assertTrue(failure.message.orEmpty().contains("restoreKey"))
  }

  @Test
  fun `corrupt namespaced payload length resets instead of throwing`() {
    assertNull(
      decodeNamespacedNavigatorPayloadOrNull(
        encoded = "gezgin-navigator-v1:2147483647:Aasnapshot",
        expectedRestoreKey = "Aa",
      )
    )
  }

  @Test
  fun `encode-decode stack'i ve nextId'yi korur`() {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    nav.navigate(Catalog)

    val encoded = encodeNavigatorState(nav, testJson)
    val restored =
      decodeNavigatorState(
        encoded,
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )

    assertEquals(nav.keys.map { it.route }, restored.keys.map { it.route })
    assertEquals(nav.keys.map { it.id }, restored.keys.map { it.id })
    assertEquals(Catalog, restored.current)
  }

  @Test
  fun `decode sonrasi start TEKRAR PUSH EDILMEZ (restored stack tek kaynak)`() {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    nav.navigate(Catalog)
    val stackSizeBefore = nav.keys.size

    val encoded = encodeNavigatorState(nav, testJson)
    val restored =
      decodeNavigatorState(
        encoded,
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )

    assertEquals(stackSizeBefore, restored.keys.size) // start yeniden push edilseydi +1 olurdu
  }

  // Important 1 (final-review) — PD restore fault-tolerance: bozuk/eski-şema bir kayıtlı state
  // crash-loop'a değil, `null`'a (Saver sözleşmesi → fresh init at `start`) düşmeli.

  @Test
  fun `bozuk json ile decodeNavigatorStateOrNull null doner (crash-loop yerine fresh-start)`() {
    val restored =
      decodeNavigatorStateOrNull(
        encoded = "{ this is not valid json at all",
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )

    assertNull(restored)
  }

  @Test
  fun `sema-disi (eksik alan) json ile decodeNavigatorStateOrNull null doner`() {
    // Gecerli JSON ama SavedState semasina uymuyor (beklenen alanlar yok) — SerializationException.
    val restored =
      decodeNavigatorStateOrNull(
        encoded = """{"unexpectedField": 42}""",
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )

    assertNull(restored)
  }

  @Test
  fun `gecerli JSON ama bilinmeyen route tipi (module'e kayitsiz) ile null doner`() {
    // "Uygulama guncellemesi bir route'u kaldirdi" senaryosu: state, Otp'yi TANIYAN daha genis bir
    // module ile encode edilir (yapisal olarak tamamen gecerli JSON), ama testJson'in
    // testSerializersModule'unde Otp POLIMORFIK KAYITLI DEGIL — decode'da polymorphic-discriminator
    // cozulemez (SerializationException) → null (fresh-start fallback), crash degil.
    val widerJson = Json {
      serializersModule = SerializersModule {
        include(testSerializersModule)
        polymorphic(dev.gezgin.core.Route::class) { subclass(Otp::class) }
      }
    }
    val staleState =
      SavedState(
        keys = listOf(GezginKey(route = Otp, id = 1L)),
        nextId = 2L,
        pendingSlots = emptyList(),
      )
    val encoded = widerJson.encodeToString(SavedState.serializer(), staleState)

    val restored =
      decodeNavigatorStateOrNull(
        encoded,
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )

    assertNull(restored)
  }

  @Test
  fun `sema-gecerli ama BOS stack'li state ile null doner (composition'da keys-first patlamasi onlenir)`() {
    // Final re-review Minor 2: kesyfsel olarak uretilemese de sema-gecerli bir bos-keys SavedState
    // decode'dan gecer, sonra GezginDisplay `keys.first()` ile composition'da patlardi →
    // fresh-start.
    val emptyState = SavedState(keys = emptyList(), nextId = 0L, pendingSlots = emptyList())
    val encoded = testJson.encodeToString(SavedState.serializer(), emptyState)

    val restored =
      decodeNavigatorStateOrNull(
        encoded,
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )

    assertNull(restored)
  }

  @Test
  fun `gecerli encoded state ile decodeNavigatorStateOrNull normal restore doner`() {
    val nav = RawNavigator(start = Feed, topology = testTopology)
    nav.navigate(Catalog)
    val encoded = encodeNavigatorState(nav, testJson)

    val restored =
      decodeNavigatorStateOrNull(
        encoded,
        start = Feed,
        topology = testTopology,
        json = testJson,
        onRootBack = {},
      )

    assertNotNull(restored)
    assertEquals(Catalog, restored.current)
  }
}
