package dev.gezgin.sample.shopr

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.shopr.dialog_order_details.provideOrderDetailsDialogEntry
import dev.gezgin.sample.shopr.screen_cart.provideCartEntry
import dev.gezgin.sample.shopr.screen_catalog.provideCatalogEntry
import dev.gezgin.sample.shopr.screen_feed.provideFeedEntry
import dev.gezgin.sample.shopr.screen_order_placed.provideOrderPlacedEntry
import dev.gezgin.sample.shopr.screen_payment.providePaymentEntry
import dev.gezgin.sample.shopr.screen_product.provideProductEntry

fun GezginEntryScope.shopGraphEntries() {
    provideFeedEntry()
    provideCatalogEntry()
    provideProductEntry()
    provideOrderPlacedEntry()
    provideOrderDetailsDialogEntry()
    provideCartEntry()
    providePaymentEntry()
}
