package dev.gezgin.core.compose

import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.testSerializersModule
import dev.gezgin.core.fixtures.testTopology
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val testJson = Json { serializersModule = testSerializersModule }

/**
 * Task 3.2 deliverable (e) — [navigatorSaver]'ın altındaki round-trip mantığının ([encodeNavigatorState]/
 * [decodeNavigatorState]) doğrudan pinlenmesi, Compose runtime KURULMADAN (@Composable DEĞİL, `Saver`
 * sarmalayıcısının `SaverScope`-alıcılı üyesine hiç dokunmadan). Plan'ın izin verdiği "yoksa Saver'ı
 * UI'sız birim testle pinle" fallback'i: desktop uiTest'te `StateRestorationTester` yerine bu daha
 * doğrudan/az kırılgan yol tercih edildi (bkz. task-3.2-report.md — `Saver.save`'in foreign/androidx
 * binary metadata üzerinden extension-member olarak çağrılması bu ortamda unresolved çıktı).
 */
class RememberNavigatorSaverTest {

    @Test
    fun `encode-decode stack'i ve nextId'yi korur`() {
        val nav = RawNavigator(start = Feed, topology = testTopology)
        nav.navigate(Catalog)

        val encoded = encodeNavigatorState(nav, testJson)
        val restored = decodeNavigatorState(encoded, start = Feed, topology = testTopology, json = testJson, onRootBack = {})

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
        val restored = decodeNavigatorState(encoded, start = Feed, topology = testTopology, json = testJson, onRootBack = {})

        assertEquals(stackSizeBefore, restored.keys.size)   // start yeniden push edilseydi +1 olurdu
    }
}
