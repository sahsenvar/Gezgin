package dev.gezgin.sample.feature.home

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.feature.home.dialog_delete_item.provideDeleteItemDialogEntry
import dev.gezgin.sample.feature.home.modal_image_viewer.provideItemImageViewerEntry
import dev.gezgin.sample.feature.home.screen_dashboard.provideDashboardEntry
import dev.gezgin.sample.feature.home.screen_item_detail.provideItemDetailEntry
import dev.gezgin.sample.feature.home.screen_welcome.provideWelcomeEntry
import dev.gezgin.sample.feature.home.sheet_filter.provideFilterBottomSheetEntry
import dev.gezgin.sample.feature.home.sheet_share_target.provideShareTargetSheetEntry

fun GezginEntryScope.homeGraphEntries() {
  provideDashboardEntry()
  provideItemDetailEntry()
  provideFilterBottomSheetEntry()
  provideDeleteItemDialogEntry()
  provideShareTargetSheetEntry()
  provideItemImageViewerEntry()
  provideWelcomeEntry()
  provideHelpEntry()
}
