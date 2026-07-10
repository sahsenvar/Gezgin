package dev.gezgin.sample.feature.profile

import dev.gezgin.core.compose.GezginEntryScope

fun GezginEntryScope.profileGraphEntries() {
    provideProfileEntry()
    provideSettingsEntry(buildInfo = { BuildInfo(version = "1.0.0") })
    provideNotificationsSheetEntry()
    provideEditNameDialogEntry()
    providePickSourceEntry()
    provideCropEntry()
    provideZoomEntry()
}
