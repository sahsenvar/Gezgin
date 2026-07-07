package dev.gezgin.processor.fixtures

/**
 * Task 2.5 companion to [SHOP_SOURCE]: a tiny call-site compiled ALONGSIDE it that exercises the
 * generated per-source navigators through their real (KotlinPoet-emitted) API — proving the
 * generated API compiles and works, rather than reflecting into it method-by-method. Split into two
 * non-suspend, void top-level functions (not one) so [dev.gezgin.processor.NavigatorCodegenTest] can
 * inspect `raw.backStack` between the "enter the flow" and "tear it down" halves of the scenario
 * without needing Continuation-based reflection for a suspend function.
 */
val RUNNER_SOURCE = """
    package dev.gezgin.shop

    import dev.gezgin.core.RawNavigator

    /** Feed → launchCheckout(): pushes CheckoutFlow's start (Cart) and opens the result slot. */
    fun launchCheckout(raw: RawNavigator) {
        val feedNav = raw.feedNavigator(raw.currentEntryId)
        feedNav.launchCheckout()
    }

    /** Cart → goToPayment(); Payment → quitWith(OrderId(...)): tears CheckoutFlow down + delivers. */
    fun finishCheckout(raw: RawNavigator) {
        val cartNav = raw.cartNavigator(raw.currentEntryId)
        cartNav.goToPayment()
        val paymentNav = raw.paymentNavigator(raw.currentEntryId)
        paymentNav.quitWith(OrderId("done"))
    }
""".trimIndent()

/**
 * Negative counterpart of [RUNNER_SOURCE] (Task 2.5's core value proposition): `Feed` never
 * declares a `@GoTo(Payment::class)` edge, so `FeedNavigator` has no `goToPayment` member — this
 * must fail to COMPILE (unresolved reference), not merely fail at runtime.
 */
val UNDECLARED_EDGE_RUNNER_SOURCE = """
    package dev.gezgin.shop

    import dev.gezgin.core.RawNavigator

    fun badCall(raw: RawNavigator) {
        val feedNav = raw.feedNavigator(raw.currentEntryId)
        feedNav.goToPayment()
    }
""".trimIndent()
