package dev.gezgin.sample.feature.home

import dev.gezgin.core.compose.GezginEntryScope

/** HomeGraph'ın tüm entry'leri tek bundle'da — `:app` bunu tek çağrıyla toplar (§10.1). */
fun GezginEntryScope.homeGraphEntries() {
    provideDashboardEntry()
    provideItemDetailEntry()
    provideFilterBottomSheetEntry()
    provideWelcomeEntry()
    // Faz 6.4 — `@FragmentScreen HelpFragment` için üretilen entry. `provideHelpEntry()` elle YAZILMAZ;
    // `FragmentEntryCodegen` onu `GezginFragmentEntries.kt`'ye (core-mode `GezginEntries.kt`'den ayrı
    // dosya) üretir; içi `AndroidFragment<HelpFragment>(arguments = route.toBundle(raw), onUpdate = ...)`.
    provideHelpEntry()
}
