package dev.gezgin.sample.hello.nav

import dev.gezgin.core.Route
import dev.gezgin.core.annotation.BackTo
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph

@NavGraph
sealed interface HelloGraph : Route {

  @GoTo(ContactDetailScreenRoute::class) data object ContactListScreenRoute : HelloGraph

  @BackTo(ContactListScreenRoute::class)
  data class ContactDetailScreenRoute(val contactId: String) : HelloGraph
}
