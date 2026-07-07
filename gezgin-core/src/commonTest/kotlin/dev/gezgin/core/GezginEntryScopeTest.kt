package dev.gezgin.core

import dev.gezgin.core.compose.EntryKind
import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.core.compose.toNavEntry
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Task 3.1 — saf-JVM registry/adapter testleri. Composable içerikler burada hiç INVOKE EDİLMEZ
 * (yalnız kayıt/lookup mekaniği doğrulanır); Compose runtime'ın gerçek render davranışı Faz 3.2
 * desktop uiTest'inin işi.
 *
 * Task 3.2 devri: `toNavEntry(key)` → `toNavEntry(key, navigator)` (top-entry-drive, additive) —
 * bu testlerde navigator yalnız parametre olarak geçiyor, hiçbir assertion navigator davranışına
 * bakmıyor (o [dev.gezgin.core.compose.GezginDisplayTest]'in işi).
 */
class GezginEntryScopeTest {
    private val navigator = RawNavigator(start = Feed, topology = testTopology)

    @Test
    fun `register - iki route kaydeder, ikisi de registry'de ve kind'lari dogru`() {
        val scope = GezginEntryScope()
        scope.register<Feed>(kind = EntryKind.SCREEN) { }
        scope.register<Product>(kind = EntryKind.DIALOG) { }

        assertEquals(2, scope.registry.size)
        assertEquals(EntryKind.SCREEN, scope.registry.getValue(Feed::class).kind)
        assertEquals(EntryKind.DIALOG, scope.registry.getValue(Product::class).kind)
    }

    @Test
    fun `register - varsayilan kind SCREEN'dir`() {
        val scope = GezginEntryScope()
        scope.register<Feed> { }

        assertEquals(EntryKind.SCREEN, scope.registry.getValue(Feed::class).kind)
    }

    @Test
    fun `register - cift kayit aciklayici hata firlatir`() {
        val scope = GezginEntryScope()
        scope.register<Feed> { }

        val error = assertFailsWith<IllegalStateException> {
            scope.register<Feed> { }
        }
        assertTrue(
            error.message?.contains("Feed") == true,
            "Hata mesaji route adini icermeli, actual: ${error.message}",
        )
    }

    @Test
    fun `toNavEntry - kayitsiz route icin lookup aninda hata firlatir (content invoke edilmeden)`() {
        val scope = GezginEntryScope()
        // Catalog hiç kaydedilmedi.
        val key = GezginKey(route = Catalog, id = 1L)

        val error = assertFailsWith<IllegalStateException> {
            scope.toNavEntry(key, navigator)
        }
        assertTrue(
            error.message?.contains("Catalog") == true,
            "Hata mesaji route adini icermeli, actual: ${error.message}",
        )
    }

    @Test
    fun `toNavEntry - contentKey key id'ye esittir (R2)`() {
        val scope = GezginEntryScope()
        scope.register<Feed> { }
        val key = GezginKey(route = Feed, id = 42L)

        val navEntry = scope.toNavEntry(key, navigator)

        assertEquals(42L, navEntry.contentKey)
    }

    @Test
    fun `toNavEntry - farkli id'ler ayni route icin farkli contentKey uretir (aynı-route iki instance ayrimi)`() {
        val scope = GezginEntryScope()
        scope.register<Product> { }

        val first = scope.toNavEntry(GezginKey(route = Product("a"), id = 1L), navigator)
        val second = scope.toNavEntry(GezginKey(route = Product("a"), id = 2L), navigator)

        assertEquals(1L, first.contentKey)
        assertEquals(2L, second.contentKey)
        assertTrue(first.contentKey != second.contentKey)
    }
}
