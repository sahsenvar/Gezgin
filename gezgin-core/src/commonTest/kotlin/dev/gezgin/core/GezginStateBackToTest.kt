package dev.gezgin.core

import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import dev.gezgin.core.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GezginStateBackToTest {
    @Test fun backToBottomInclusiveKeepsBottom_neverEmpties() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed); s.push(Catalog); s.push(Product("1"))
        val removed = s.backTo(Feed::class, inclusive = true)!!
        assertEquals(listOf(Catalog, Product("1")), removed.map { it.route })
        assertEquals(listOf<Route>(Feed), s.stack.map { it.route })
    }

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

    @Test fun nearestAncestorAmongThreeSameTypeEntries_inclusive() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Product("A"), singleTop = false); s.push(Product("B"), singleTop = false)
        s.push(Feed); s.push(Product("C"), singleTop = false); s.push(Catalog)
        val removed = s.backTo(Product::class, inclusive = true)!!   // nearest = Product("C")
        assertEquals(listOf(Product("C"), Catalog), removed.map { it.route })
        // indexOfFirst kullanan yanlış implementasyon [Product("A")]'ya kadar her şeyi silerdi:
        assertEquals(listOf(Product("A"), Product("B"), Feed), s.stack.map { it.route })
    }

    @Test fun targetIsTopOnlyReturnsNull() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Catalog)
        assertNull(s.backTo(Catalog::class, inclusive = false))
        assertEquals(1, s.stack.size)  // No mutation
    }
}
