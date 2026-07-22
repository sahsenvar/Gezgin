package dev.gezgin.processor.fixtures

/**
 * Task 2.6 companion to [SHOP_SOURCE]: the typed hâli of [RUNNER_SOURCE]'s scenario, but driven
 * through `GezginTestNavigator` + the generated `fromX()` accessors (§13's `dev.gezgin.test` test
 * API) instead of raw `RawNavigator.xNavigator(entryId)` calls — proves `fromFeed()`/`fromCart()`/
 * `fromPayment()` compile against the real KotlinPoet-emitted extensions AND behave like the
 * underlying `RawNavigator` they resolve to.
 */
val TEST_API_RUNNER_SOURCE =
  """
      @file:OptIn(dev.gezgin.core.GezginInternalApi::class)

      package dev.gezgin.shop

      import dev.gezgin.core.NavResult
      import dev.gezgin.test.GezginTestNavigator
      import kotlinx.coroutines.flow.first
      import kotlinx.coroutines.runBlocking

      fun typedTestApiScenario(): NavResult<Any?> {
          val nav = GezginTestNavigator(start = HomeGraph.Feed, topology = gezginTopology)
          val feedEntryId = nav.raw.currentEntryId

          // Feed --launchCheckout--> Cart (CheckoutFlow's start) via the typed fromFeed() accessor.
          nav.fromFeed().launchCheckout()

          // Cart -> Payment -> quitWith(OrderId) via fromCart()/fromPayment() — tears CheckoutFlow
          // down and delivers the result, all through generated `GezginTestNavigator` extensions.
          nav.fromCart().goToPayment()
          nav.fromPayment().quitWith(OrderId("o1"))

          return runBlocking {
              nav.raw.results<Any?>(feedEntryId, "dev.gezgin.shop.HomeGraph.Feed→dev.gezgin.shop.CheckoutFlow").first()
          }
      }
  """
    .trimIndent()
