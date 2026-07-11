package dev.gezgin.sample.feature.profile

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.feature.profile.sheet_notification.provideNotificationsSheetEntry

fun GezginEntryScope.profileGraphEntries() {
    provideProfileEntry()
    provideSettingsEntry(buildInfo = { BuildInfo(version = "1.0.0") })
    provideNotificationsSheetEntry()
    provideEditNameDialogEntry()
    providePickSourceEntry()
    provideCropEntry()
    provideZoomEntry()
}
