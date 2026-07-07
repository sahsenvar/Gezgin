package dev.gezgin.core

import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GezginStateReplaceTest {
    @Test fun replaceSelfSwapsTop() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed); s.push(Product("1"))
        s.replaceUpTo(Catalog, clearUpTo = null, inclusive = true)
        assertEquals(listOf(Feed, Catalog), s.stack.map { it.route })
    }
    @Test fun replaceUpToAncestorInclusiveClearsThrough() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed); s.push(Catalog); s.push(Product("1"))
        s.replaceUpTo(Product("2"), clearUpTo = Catalog::class, inclusive = true)
        assertEquals(listOf(Feed, Product("2")), s.stack.map { it.route })
    }
    @Test fun popOnLastEntryReturnsNull_stackIntact() {
        val s = GezginState(emptyList(), 0, testTopology); s.push(Feed)
        assertNull(s.pop())                                        // empty-stack invariant'ı (§8.1)
        assertEquals(1, s.stack.size)
    }
}
