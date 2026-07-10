package dev.gezgin.sample.shopr

import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.gezginTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopGraphTopologyTest {

    @Test
    fun `gezginTopology yuklenir ve Cart icin CheckoutFlow zincirini rapor eder`() {
        val chain = gezginTopology.flowChain(CheckoutFlow.Cart::class)

        assertEquals(1, chain.size)
        assertTrue(chain.single().isResultFlow, "CheckoutFlow bir ResultFlow<OrderId> — zincir bunu isaretlemeli")
    }

    @Test
    fun `bare route (Product) hic flow zincirinde degildir`() {
        val chain = gezginTopology.flowChain(dev.gezgin.sample.shopr.nav.HomeGraph.Product::class)
        assertTrue(chain.isEmpty())
    }
}
