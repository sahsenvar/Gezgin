package dev.gezgin.core

import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GezginStateBackToTest {
    @Test fun popsToNearestAncestorExclusive() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Product("A"), singleTop = false); s.push(Feed); s.push(Product("B"), singleTop = false); s.push(Catalog)
        val removed = s.backTo(Product::class, inclusive = false)!!   // nearest = Product("B")
        assertEquals(listOf(Catalog), removed.map { it.route })
        assertEquals(Product("B"), s.stack.last().route)
    }

    @Test fun missingTargetReturnsNull_noMutation() {
        val s = GezginState(emptyList(), 0, testTopology); s.push(Feed)
        assertNull(s.backTo(Catalog::class, inclusive = false))
        assertEquals(1, s.stack.size)
    }

    @Test fun inclusiveTrueAlsoRemovesTarget() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Product("A"), singleTop = false); s.push(Feed); s.push(Product("B"), singleTop = false); s.push(Catalog)
        val removed = s.backTo(Product::class, inclusive = true)!!   // nearest = Product("B"), also remove it
        assertEquals(listOf(Product("B"), Catalog), removed.map { it.route })
        assertEquals(Feed, s.stack.last().route)
    }

    @Test fun nearestAncestorNotFirst() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Product("A"), singleTop = false); s.push(Feed)
        s.push(Product("B"), singleTop = false); s.push(Catalog)
        val removed = s.backTo(Product::class, inclusive = false)!!
        // Wrong impl (indexOfFirst) would find Product("A") and remove more entries
        assertEquals(1, removed.size)
        assertEquals(listOf(Catalog), removed.map { it.route })
        assertEquals(3, s.stack.size)  // Should keep 3 entries: [Product("A"), Feed, Product("B")]
    }

    @Test fun targetIsTopOnlyReturnsNull() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Catalog)
        assertNull(s.backTo(Catalog::class, inclusive = false))
        assertEquals(1, s.stack.size)  // No mutation
    }
}
