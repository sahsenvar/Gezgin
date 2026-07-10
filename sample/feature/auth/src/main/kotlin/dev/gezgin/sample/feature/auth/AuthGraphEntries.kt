package dev.gezgin.sample.feature.auth

import dev.gezgin.core.compose.GezginEntryScope

fun GezginEntryScope.authGraphEntries() {
    provideLoginEntry()
    provideForgotPasswordDialogEntry()
    provideCredentialsEntry()
    provideProfileInfoEntry()
    provideTermsEntry()
}
