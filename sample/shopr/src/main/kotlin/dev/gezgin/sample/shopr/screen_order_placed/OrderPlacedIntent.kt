package dev.gezgin.sample.shopr.screen_order_placed

sealed interface OrderPlacedIntent {
    data object BackToFeed : OrderPlacedIntent
    data object ShowDetails : OrderPlacedIntent
}
