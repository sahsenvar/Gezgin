package dev.gezgin.sample.hello

import dev.gezgin.core.compose.GezginEntryScope
import dev.gezgin.sample.hello.screen_contact_detail.provideContactDetailEntry
import dev.gezgin.sample.hello.screen_contact_list.provideContactListEntry

fun GezginEntryScope.ContractGraphEntries() {
  provideContactListEntry()
  provideContactDetailEntry()
}
