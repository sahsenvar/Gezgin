package dev.gezgin.sample.hello.screen_contact_list

import dev.gezgin.sample.hello.nav.ContactListNavigator
import dev.gezgin.sample.hello.nav.HelloGraph
import dev.gezgin.sample.hello.ui.Effects

@Effects(HelloGraph.ContactListScreenRoute::class)
fun handleContactListEffect(effect: ContactListEffect, nav: ContactListNavigator) {
  when (effect) {
    is ContactListEffect.OpenContact -> nav.goToContactDetail(effect.id)
  }
}
