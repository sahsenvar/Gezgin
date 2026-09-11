package dev.gezgin.sample.hello.nav

import dev.gezgin.core.Route
import dev.gezgin.core.annotation.BackTo
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph
import kotlinx.serialization.Serializable

@NavGraph
@Serializable
sealed interface HelloGraph : Route {

  @GoTo(ContactDetailScreenRoute::class)
  @Serializable
  data object ContactListScreenRoute : HelloGraph

  @BackTo(ContactListScreenRoute::class)
  @Serializable
  data class ContactDetailScreenRoute(val contactId: String) : HelloGraph
}
