package dev.gezgin.sample.feature.auth

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.feature.auth.dialog_forgot_password.provideForgotPasswordDialogEntry
import dev.gezgin.sample.feature.auth.screen_credentials.provideCredentialsEntry
import dev.gezgin.sample.feature.auth.screen_login.provideLoginEntry
import dev.gezgin.sample.feature.auth.screen_profile_info.provideProfileInfoEntry
import dev.gezgin.sample.feature.auth.screen_terms.provideTermsEntry

fun GezginEntryScope.authGraphEntries() {
  provideLoginEntry()
  provideForgotPasswordDialogEntry()
  provideCredentialsEntry()
  provideProfileInfoEntry()
  provideTermsEntry()
}
