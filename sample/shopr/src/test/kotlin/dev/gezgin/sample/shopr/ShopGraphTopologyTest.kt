package dev.gezgin.sample.shopr

import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.FeaturedFeedNavigator
import dev.gezgin.sample.shopr.nav.FeedNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.nav.featuredFeedNavigator
import dev.gezgin.sample.shopr.nav.feedNavigator
import dev.gezgin.sample.shopr.nav.gezginTopology
import dev.gezgin.sample.shopr.nav.orderPlacedNavigator
import dev.gezgin.sample.shopr.screen_featured_feed.FeaturedFeedEffect
import dev.gezgin.sample.shopr.screen_featured_feed.FeaturedFeedViewModel
import dev.gezgin.sample.shopr.screen_featured_feed.handleFeaturedFeedEffect
import dev.gezgin.sample.shopr.screen_feed.FeedEffect
import dev.gezgin.sample.shopr.screen_feed.FeedIntent
import dev.gezgin.sample.shopr.screen_feed.FeedViewModel
import dev.gezgin.sample.shopr.screen_feed.handleFeedEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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

    @Test
    fun `shared Feed screen iki route-local entry ve navigator uretir`() {
        val generatedMethods = Class
            .forName("dev.gezgin.sample.shopr.screen_feed.GezginMviEntriesKt")
            .declaredMethods
            .map { it.name }

        assertTrue("provideFeedEntry" in generatedMethods)
        assertTrue("provideFeaturedFeedEntry" in generatedMethods)
        assertNotEquals(
            FeedNavigator::class.qualifiedName,
            FeaturedFeedNavigator::class.qualifiedName,
        )
    }

    @Test
    fun `Feed intent ViewModelden catalog navigation effectine donusur`() = runBlocking {
        val viewModel = FeedViewModel()

        viewModel.onIntent(FeedIntent.OpenCatalog)

        assertEquals(FeedEffect.NavigateToCatalog, viewModel.effects.first())
    }

    @Test
    fun `FeaturedFeed ayni intenti kendi route-local effectine donusturur`() = runBlocking {
        val viewModel = FeaturedFeedViewModel()

        viewModel.onIntent(FeedIntent.OpenCatalog)

        assertEquals(
            FeaturedFeedEffect.NavigateToFeaturedProduct("featured"),
            viewModel.effects.first(),
        )
    }

    @Test
    fun `route-local effect handlerlar farkli typed navigator hedeflerine gider`() {
        val feedRaw = RawNavigator(start = HomeGraph.Feed, topology = gezginTopology)
        handleFeedEffect(FeedEffect.NavigateToCatalog, feedRaw.feedNavigator(entryId = 1L))

        val featuredRaw = RawNavigator(start = HomeGraph.FeaturedFeed, topology = gezginTopology)
        handleFeaturedFeedEffect(
            FeaturedFeedEffect.NavigateToFeaturedProduct("featured"),
            featuredRaw.featuredFeedNavigator(entryId = 1L),
        )

        assertEquals(listOf<Route>(HomeGraph.Feed, HomeGraph.Catalog), feedRaw.backStack.value)
        assertEquals(
            listOf<Route>(HomeGraph.FeaturedFeed, HomeGraph.Product("featured")),
            featuredRaw.backStack.value,
        )
    }

    @Test
    fun `OrderLock gercek bottom sheet entrysi tamamen non-dismissible olur`() {
        val route = HomeGraph.OrderLockSheetRoute(orderId = "order-42")
        val raw = RawNavigator(start = HomeGraph.OrderPlaced(orderId = "order-42"), topology = gezginTopology)
        raw.orderPlacedNavigator(entryId = 1L).showOrderLock(orderId = "order-42")
        val generatedMethods = Class
            .forName("dev.gezgin.sample.shopr.sheet_order_lock.GezginEntriesKt")
            .declaredMethods
            .map { it.name }

        assertTrue("provideOrderLockSheetEntry" in generatedMethods)
        assertFalse(route.dismissOnBackPress)
        assertFalse(route.dismissOnClickOutside)
        assertFalse(route.sheetGesturesEnabled)
        assertEquals(
            listOf<Route>(HomeGraph.OrderPlaced("order-42"), route),
            raw.backStack.value,
        )
    }
}
