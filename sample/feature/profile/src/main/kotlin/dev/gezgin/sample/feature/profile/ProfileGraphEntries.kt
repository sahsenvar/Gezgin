package dev.gezgin.sample.feature.profile

import dev.gezgin.core.compose.GezginEntryScope

/** ProfileGraph'ın tüm entry'leri tek bundle'da — `:app` bunu tek çağrıyla toplar (§10.1). */
fun GezginEntryScope.profileGraphEntries() {
    provideProfileEntry()
    // Faz 7.2 (GAP-2) — §10.1 "Problem 2": `SettingsContent`'in rol-DIŞI `buildInfo: BuildInfo`
    // param'ı codegen'de `provideSettingsEntry`'ye ZORUNLU (default'suz) `buildInfo: @Composable () ->
    // BuildInfo` resolver param'ı olarak eklenir → kurulumda AÇIKÇA sağlanmalı. Bu explicit çağrı-yeri
    // Problem 2'nin tüketici-tarafı kanıtıdır (mekanizma yalnız processor fixture'ında değil, sample'da).
    provideSettingsEntry(buildInfo = { BuildInfo(version = "1.0.0") })
    // MVI-mode @BottomSheet (Integ M3) — generated into GezginMviEntries.kt (EntryKind.BOTTOM_SHEET).
    provideNotificationsSheetEntry()
    provideEditNameDialogEntry()
    providePickSourceEntry()
    provideCropEntry()
    provideZoomEntry()
}
