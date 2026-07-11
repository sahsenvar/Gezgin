package dev.gezgin.sample.feature.profile

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.feature.profile.dialog_edit_name.provideEditNameDialogEntry
import dev.gezgin.sample.feature.profile.flow_avatar.provideCropEntry
import dev.gezgin.sample.feature.profile.flow_avatar.providePickSourceEntry
import dev.gezgin.sample.feature.profile.flow_avatar.provideZoomEntry
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
