package dev.gezgin.sample.feature.auth

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.feature.auth.dialog_forgot_password.provideForgotPasswordDialogEntry
import dev.gezgin.sample.feature.auth.flow_signup.provideCredentialsEntry
import dev.gezgin.sample.feature.auth.flow_signup.provideProfileInfoEntry
import dev.gezgin.sample.feature.auth.flow_signup.provideTermsEntry

fun GezginEntryScope.authGraphEntries() {
    provideLoginEntry()
    provideForgotPasswordDialogEntry()
    provideCredentialsEntry()
    provideProfileInfoEntry()
    provideTermsEntry()
}
