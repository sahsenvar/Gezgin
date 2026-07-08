package dev.gezgin.sample.feature.home

import dev.gezgin.core.compose.GezginEntryScope

/** HomeGraph'ın tüm entry'leri tek bundle'da — `:app` bunu tek çağrıyla toplar (§10.1). */
fun GezginEntryScope.homeGraphEntries() {
    provideDashboardEntry()
    provideItemDetailEntry()
    provideFilterBottomSheetEntry()
    provideWelcomeEntry()
}
