package dev.gezgin.sample.shopr

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.RawNavigator
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.nav.OrderId
import dev.gezgin.sample.shopr.nav.cartNavigator
import dev.gezgin.sample.shopr.nav.catalogNavigator
import dev.gezgin.sample.shopr.nav.gezginTopology
import dev.gezgin.sample.shopr.nav.paymentNavigator
import dev.gezgin.sample.shopr.screen_cart.CartEffect
import dev.gezgin.sample.shopr.screen_cart.handleCartEffect
import dev.gezgin.sample.shopr.screen_catalog.CatalogEffect
import dev.gezgin.sample.shopr.screen_catalog.CatalogEffectHandler
import dev.gezgin.sample.shopr.screen_catalog.CatalogViewModel
import dev.gezgin.sample.shopr.screen_catalog.catalogResultIntentEffectFlow
import dev.gezgin.sample.shopr.screen_payment.PaymentEffect
import dev.gezgin.sample.shopr.screen_payment.handlePaymentEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogCheckoutCollectorTest {

    @OptIn(GezginInternalApi::class)
    @Test
    fun `checkout success re-enters CatalogViewModel before handler replaces the route`() {
        val viewModel = CatalogViewModel()
        var deliveredIntents = 0
        val effects = catalogResultIntentEffectFlow(viewModel.effects) { intent ->
            deliveredIntents += 1
            viewModel.onIntent(intent)
        }
        val raw = RawNavigator(start = HomeGraph.Catalog, topology = gezginTopology)
        val nav = raw.catalogNavigator(entryId = requireNotNull(raw.entryIdOf(HomeGraph.Catalog::class)))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()

        try {
            controller.get().setContent { CatalogEffectHandler(effects, nav) }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            nav.launchCheckout()
            handleCartEffect(
                CartEffect.NavigateToPayment,
                raw.cartNavigator(entryId = requireNotNull(raw.entryIdOf(CheckoutFlow.Cart::class))),
                onMessage = {},
            )
            handlePaymentEffect(
                PaymentEffect.CompletePayment(OrderId(value = "ORD-1001")),
                raw.paymentNavigator(entryId = requireNotNull(raw.entryIdOf(CheckoutFlow.Payment::class))),
                onMessage = {},
            )

            awaitComposeCondition("checkout result did not replace Catalog") {
                raw.current == HomeGraph.OrderPlaced(orderId = "ORD-1001")
            }
            assertEquals(1, deliveredIntents)
        } finally {
            controller.pause().stop().destroy()
        }
    }

}

private fun awaitComposeCondition(message: String, condition: () -> Boolean) {
    repeat(100) {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        if (condition()) return
        Thread.sleep(10)
    }
    assertTrue(condition(), message)
}
