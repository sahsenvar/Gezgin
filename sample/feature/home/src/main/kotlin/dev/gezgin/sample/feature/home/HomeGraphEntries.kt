package dev.gezgin.sample.feature.home

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.feature.home.modal_image_viewer.provideItemImageViewerEntry
import dev.gezgin.sample.feature.home.screen_dashboard.provideDashboardEntry
import dev.gezgin.sample.feature.home.screen_item_detail.provideItemDetailEntry
import dev.gezgin.sample.feature.home.screen_welcome.provideWelcomeEntry
import dev.gezgin.sample.feature.home.sheet_filter.provideFilterBottomSheetEntry

fun GezginEntryScope.homeGraphEntries() {
  provideDashboardEntry()
  provideItemDetailEntry()
  provideFilterBottomSheetEntry()
  provideItemImageViewerEntry()
  provideWelcomeEntry()
  provideHelpEntry()
}
