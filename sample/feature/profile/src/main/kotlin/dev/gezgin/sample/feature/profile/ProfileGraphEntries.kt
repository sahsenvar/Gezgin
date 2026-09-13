package dev.gezgin.sample.feature.profile

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.feature.profile.dialog_confirm_reset.provideConfirmResetDialogEntry
import dev.gezgin.sample.feature.profile.dialog_edit_name.provideEditNameDialogEntry
import dev.gezgin.sample.feature.profile.screen_crop.provideCropEntry
import dev.gezgin.sample.feature.profile.screen_pick_source.providePickSourceEntry
import dev.gezgin.sample.feature.profile.screen_profile.provideProfileEntry
import dev.gezgin.sample.feature.profile.screen_settings.provideSettingsEntry
import dev.gezgin.sample.feature.profile.screen_zoom.provideZoomEntry
import dev.gezgin.sample.feature.profile.sheet_notification.provideNotificationsSheetEntry

fun GezginEntryScope.profileGraphEntries() {
  provideProfileEntry()
  provideSettingsEntry()
  provideNotificationsSheetEntry()
  provideEditNameDialogEntry()
  provideConfirmResetDialogEntry()
  providePickSourceEntry()
  provideCropEntry()
  provideZoomEntry()
}
