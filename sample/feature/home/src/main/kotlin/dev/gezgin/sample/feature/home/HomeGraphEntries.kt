package dev.gezgin.sample.feature.home

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.feature.home.modal_image_viewer.provideItemImageViewerEntry
import dev.gezgin.sample.feature.home.sheet_filter.provideFilterBottomSheetEntry

fun GezginEntryScope.homeGraphEntries() {
    provideDashboardEntry()
    provideItemDetailEntry()
    provideFilterBottomSheetEntry()
    provideItemImageViewerEntry()
    provideWelcomeEntry()
    provideHelpEntry()
}
