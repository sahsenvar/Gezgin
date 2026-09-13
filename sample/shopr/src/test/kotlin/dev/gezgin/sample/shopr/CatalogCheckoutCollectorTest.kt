package dev.gezgin.sample.shopr

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.RawNavigator
import dev.gezgin.sample.shopr.nav.CartNavigator
import dev.gezgin.sample.shopr.nav.CatalogNavigator
import dev.gezgin.sample.shopr.nav.CheckoutFlow
import dev.gezgin.sample.shopr.nav.HomeGraph
import dev.gezgin.sample.shopr.nav.OrderId
import dev.gezgin.sample.shopr.nav.PaymentNavigator
import dev.gezgin.sample.shopr.nav.cartNavigator
import dev.gezgin.sample.shopr.nav.catalogNavigator
import dev.gezgin.sample.shopr.nav.gezginTopology
import dev.gezgin.sample.shopr.nav.paymentNavigator
import dev.gezgin.sample.shopr.screen_cart.CartEffect
import dev.gezgin.sample.shopr.screen_cart.handleCartEffect
import dev.gezgin.sample.shopr.screen_catalog.CatalogResultCollector
import dev.gezgin.sample.shopr.screen_catalog.CatalogViewModel
import dev.gezgin.sample.shopr.screen_catalog.handleCatalogEffect
import dev.gezgin.sample.shopr.screen_payment.PaymentEffect
import dev.gezgin.sample.shopr.screen_payment.handlePaymentEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The checkout result must re-enter `CatalogViewModel` through the route-bound collector, which is
 * the slot the screen wrapper drives. The wiring under test is exactly what `ShoprScreenRoot` does:
 * collect the ViewModel's effects into the effect provider, and compose the result collector with
 * `onIntent`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogCheckoutCollectorTest {

  @OptIn(GezginInternalApi::class)
  @Test
  fun `checkout success and cancellation re-enter CatalogViewModel through the route-bound slots`() {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()

    try {
      val success = CatalogHarness(controller)
      success.start()

      success.nav.launchCheckout()
      handleCartEffect(CartEffect.NavigateToPayment, {}, success.cartNav)
      handlePaymentEffect(
        PaymentEffect.CompletePayment(OrderId(value = "ORD-1001")),
        {},
        success.paymentNav,
      )

      awaitComposeCondition("checkout result did not replace Catalog") {
        success.raw.current == HomeGraph.OrderPlaced(orderId = "ORD-1001")
      }
      assertEquals(1, success.deliveredIntents)

      val canceled = CatalogHarness(controller)
      canceled.start()
      controller.get().runOnUiThread {
        canceled.nav.launchCheckout()
        canceled.cartNav.back()
      }

      awaitComposeCondition("checkout cancellation did not re-enter CatalogViewModel") {
        canceled.deliveredIntents == 1
      }
      awaitComposeCondition("checkout cancellation did not report the exact message") {
        canceled.messages.lastOrNull() == "Ödeme iptal edildi"
      }
      assertEquals(
        1,
        canceled.deliveredIntents,
        "the route-bound collector must re-enter the ViewModel once",
      )
      assertEquals(listOf(HomeGraph.Catalog), canceled.raw.backStack.value)
    } finally {
      controller.get().runOnUiThread { controller.get().setContent {} }
      shadowOf(Looper.getMainLooper()).idle()
      controller.pause().stop().destroy()
    }
  }
}

/** Reproduces the slot wiring `ShoprScreenRoot` performs, without a real Gezgin host. */
@OptIn(GezginInternalApi::class)
private class CatalogHarness(
  private val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
) {
  val viewModel = CatalogViewModel()
  val raw = RawNavigator(start = HomeGraph.Catalog, topology = gezginTopology)
  val messages = mutableListOf<String>()
  var deliveredIntents = 0

  val nav: CatalogNavigator =
    raw.catalogNavigator(entryId = requireNotNull(raw.entryIdOf(HomeGraph.Catalog::class)))
  val cartNav: CartNavigator
    get() = raw.cartNavigator(entryId = requireNotNull(raw.entryIdOf(CheckoutFlow.Cart::class)))

  val paymentNav: PaymentNavigator
    get() =
      raw.paymentNavigator(entryId = requireNotNull(raw.entryIdOf(CheckoutFlow.Payment::class)))

  fun start() {
    controller.get().runOnUiThread {
      controller.get().setContent {
        LaunchedEffect(viewModel) {
          viewModel.effects.collect { effect -> handleCatalogEffect(effect, messages::add, nav) }
        }
        CatalogResultCollector(
          onIntent = { intent ->
            deliveredIntents += 1
            viewModel.onIntent(intent)
          },
          nav = nav,
        )
      }
    }
    shadowOf(Looper.getMainLooper()).idle()
  }
}

private fun awaitComposeCondition(message: String, condition: () -> Boolean) {
  repeat(100) {
    shadowOf(Looper.getMainLooper()).idle()
    if (condition()) return
    Thread.sleep(10)
  }
  assertTrue(condition(), message)
}
