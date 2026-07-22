package dev.gezgin.sample.shopr

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.shopr.dialog_order_details.provideOrderDetailsDialogEntry
import dev.gezgin.sample.shopr.screen_cart.provideCartEntry
import dev.gezgin.sample.shopr.screen_catalog.provideCatalogEntry
import dev.gezgin.sample.shopr.screen_feed.provideFeaturedFeedEntry
import dev.gezgin.sample.shopr.screen_feed.provideFeedEntry
import dev.gezgin.sample.shopr.screen_order_placed.provideOrderPlacedEntry
import dev.gezgin.sample.shopr.screen_payment.providePaymentEntry
import dev.gezgin.sample.shopr.screen_product.provideProductEntry
import dev.gezgin.sample.shopr.sheet_order_lock.provideOrderLockSheetEntry

fun GezginEntryScope.shopGraphEntries() {
  provideFeedEntry()
  provideFeaturedFeedEntry()
  provideCatalogEntry()
  provideProductEntry()
  provideOrderPlacedEntry()
  provideOrderDetailsDialogEntry()
  provideOrderLockSheetEntry()
  provideCartEntry()
  providePaymentEntry()
}
