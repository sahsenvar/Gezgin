package dev.gezgin.sample.feature.auth

import dev.gezgin.core.compose.GezginEntryScope

/** AuthGraph'ın tüm entry'leri tek bundle'da — `:app` bunu tek çağrıyla toplar (§10.1). */
fun GezginEntryScope.authGraphEntries() {
    provideLoginEntry()
    provideForgotPasswordDialogEntry()
    provideCredentialsEntry()
    provideProfileInfoEntry()
    provideTermsEntry()
}
