package dev.gezgin.sample.feature.home

import dev.gezgin.core.compose.GezginEntryScope

fun GezginEntryScope.homeGraphEntries() {
    provideDashboardEntry()
    provideItemDetailEntry()
    provideFilterBottomSheetEntry()
    provideItemImageViewerEntry()
    provideWelcomeEntry()
    provideHelpEntry()
}
