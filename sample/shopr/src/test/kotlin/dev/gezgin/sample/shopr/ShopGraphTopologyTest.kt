package dev.gezgin.sample.shopr

import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.OrderId
import dev.gezgin.sample.shopr.nav.FeaturedFeedNavigator
import dev.gezgin.sample.shopr.nav.FeedNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.nav.cartNavigator
import dev.gezgin.sample.shopr.nav.catalogNavigator
import dev.gezgin.sample.shopr.nav.featuredFeedNavigator
import dev.gezgin.sample.shopr.nav.feedNavigator
import dev.gezgin.sample.shopr.nav.gezginTopology
import dev.gezgin.sample.shopr.nav.orderPlacedNavigator
import dev.gezgin.sample.shopr.nav.paymentNavigator
import dev.gezgin.sample.shopr.screen_cart.CartEffect
import dev.gezgin.sample.shopr.screen_cart.CartIntent
import dev.gezgin.sample.shopr.screen_cart.CartViewModel
import dev.gezgin.sample.shopr.screen_cart.handleCartEffect
import dev.gezgin.sample.shopr.screen_catalog.CatalogEffect
import dev.gezgin.sample.shopr.screen_catalog.CatalogIntent
import dev.gezgin.sample.shopr.screen_catalog.CatalogViewModel
import dev.gezgin.sample.shopr.screen_catalog.handleCatalogEffect
import dev.gezgin.sample.shopr.screen_featured_feed.FeaturedFeedEffect
import dev.gezgin.sample.shopr.screen_featured_feed.FeaturedFeedViewModel
import dev.gezgin.sample.shopr.screen_featured_feed.handleFeaturedFeedEffect
import dev.gezgin.sample.shopr.screen_feed.FeedEffect
import dev.gezgin.sample.shopr.screen_feed.FeedIntent
import dev.gezgin.sample.shopr.screen_feed.FeedViewModel
import dev.gezgin.sample.shopr.screen_feed.handleFeedEffect
import dev.gezgin.sample.shopr.screen_order_placed.OrderPlacedEffect
import dev.gezgin.sample.shopr.screen_order_placed.OrderPlacedIntent
import dev.gezgin.sample.shopr.screen_order_placed.OrderPlacedViewModel
import dev.gezgin.sample.shopr.screen_order_placed.handleOrderPlacedEffect
import dev.gezgin.sample.shopr.screen_payment.PaymentEffect
import dev.gezgin.sample.shopr.screen_payment.PaymentIntent
import dev.gezgin.sample.shopr.screen_payment.PaymentViewModel
import dev.gezgin.sample.shopr.screen_payment.handlePaymentEffect
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
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
    fun `Cart intent payment navigation effectine donusur`() = runBlocking {
        val viewModel = CartViewModel()

        viewModel.onIntent(CartIntent.Checkout)

        assertEquals(CartEffect.NavigateToPayment, viewModel.effects.drop(1).first())
    }

    @Test
    fun `Catalog intentleri route verisini navigation effectlerine tasir`() = runBlocking {
        val productViewModel = CatalogViewModel()
        productViewModel.onIntent(CatalogIntent.OpenProduct)

        assertEquals(
            CatalogEffect.NavigateToProduct(productId = "sku-42"),
            productViewModel.effects.first(),
        )

        val checkoutViewModel = CatalogViewModel()
        checkoutViewModel.onIntent(CatalogIntent.StartCheckout)

        assertEquals(CatalogEffect.LaunchCheckout, checkoutViewModel.effects.first())

        val completedViewModel = CatalogViewModel()
        completedViewModel.onIntent(
            CatalogIntent.CheckoutResult(NavResult.Value(OrderId(value = "ORD-1001"))),
        )
        assertEquals(
            CatalogEffect.CheckoutCompleted(OrderId(value = "ORD-1001")),
            completedViewModel.effects.first(),
        )

        val canceledViewModel = CatalogViewModel()
        canceledViewModel.onIntent(CatalogIntent.CheckoutResult(NavResult.Canceled))
        assertEquals(
            CatalogEffect.ShowMessage("Ödeme iptal edildi"),
            canceledViewModel.effects.first(),
        )
    }

    @Test
    fun `OrderPlaced intentleri route-local navigation effectlerine donusur`() = runBlocking {
        val detailsViewModel = OrderPlacedViewModel(HomeGraph.OrderPlaced(orderId = "order-42"))
        detailsViewModel.onIntent(OrderPlacedIntent.ShowDetails)

        assertEquals(
            OrderPlacedEffect.ShowDetails(orderId = "order-42"),
            detailsViewModel.effects.drop(1).first(),
        )

        val feedViewModel = OrderPlacedViewModel(HomeGraph.OrderPlaced(orderId = "order-42"))
        feedViewModel.onIntent(OrderPlacedIntent.BackToFeed)

        assertEquals(OrderPlacedEffect.BackToFeed, feedViewModel.effects.drop(1).first())
    }

    @Test
    fun `Payment intent flow sonucunu navigation effectine tasir`() = runBlocking {
        val viewModel = PaymentViewModel()

        viewModel.onIntent(PaymentIntent.Pay)

        assertEquals(
            PaymentEffect.CompletePayment(OrderId(value = "ORD-1001")),
            viewModel.effects.drop(1).first(),
        )
    }

    @Test
    fun `typed handlerlar checkout result ve replace semantigini korur`() = runBlocking {
        val raw = RawNavigator(start = HomeGraph.Catalog, topology = gezginTopology)
        val catalogNav = raw.catalogNavigator(entryId = 1L)
        val messages = mutableListOf<String>()

        handleCatalogEffect(CatalogEffect.LaunchCheckout, catalogNav, messages::add)
        handleCartEffect(
            CartEffect.NavigateToPayment,
            raw.cartNavigator(entryId = 2L),
            messages::add,
        )
        val result = async { catalogNav.checkoutResults.first() }
        handlePaymentEffect(
            PaymentEffect.CompletePayment(OrderId(value = "ORD-1001")),
            raw.paymentNavigator(entryId = 3L),
            messages::add,
        )
        val viewModel = CatalogViewModel()
        viewModel.onIntent(CatalogIntent.CheckoutResult(result.await()))
        handleCatalogEffect(viewModel.effects.first(), catalogNav, messages::add)

        assertTrue(messages.isEmpty())
        assertEquals(
            listOf<Route>(HomeGraph.OrderPlaced(orderId = "ORD-1001")),
            raw.backStack.value,
        )
    }

    @Test
    fun `OrderPlaced typed handler back ve modal semantigini korur`() {
        val raw = RawNavigator(start = HomeGraph.Feed, topology = gezginTopology)
        raw.feedNavigator(entryId = 1L).goToCatalog()
        raw.catalogNavigator(entryId = 2L).replaceToOrderPlaced(orderId = "order-42")
        val orderPlacedNav = raw.orderPlacedNavigator(entryId = 3L)

        handleOrderPlacedEffect(
            OrderPlacedEffect.ShowDetails(orderId = "order-42"),
            orderPlacedNav,
            onMessage = {},
        )
        assertEquals(
            listOf<Route>(
                HomeGraph.Feed,
                HomeGraph.OrderPlaced(orderId = "order-42"),
                HomeGraph.OrderDetailsDialogRoute(orderId = "order-42"),
            ),
            raw.backStack.value,
        )

        raw.back()
        handleOrderPlacedEffect(OrderPlacedEffect.BackToFeed, orderPlacedNav, onMessage = {})

        assertEquals(listOf<Route>(HomeGraph.Feed), raw.backStack.value)
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
