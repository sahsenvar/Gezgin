package dev.gezgin.sample.feature.profile

import dev.gezgin.core.compose.GezginEntryScope

/** ProfileGraph'ın tüm entry'leri tek bundle'da — `:app` bunu tek çağrıyla toplar (§10.1). */
fun GezginEntryScope.profileGraphEntries() {
    provideProfileEntry()
    provideSettingsEntry()
    provideEditNameDialogEntry()
    providePickSourceEntry()
    provideCropEntry()
    provideZoomEntry()
}
