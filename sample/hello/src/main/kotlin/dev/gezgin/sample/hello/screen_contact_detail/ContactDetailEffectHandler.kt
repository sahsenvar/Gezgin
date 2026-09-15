package dev.gezgin.sample.hello.screen_contact_detail

import dev.gezgin.sample.hello.nav.ContactDetailNavigator
import dev.gezgin.sample.hello.nav.HelloGraph
import dev.gezgin.sample.hello.ui.EffectHandler

@EffectHandler(HelloGraph.ContactDetailScreenRoute::class)
fun handleContactDetailEffect(
    effect: ContactDetailEffect,
    nav: ContactDetailNavigator
) {
    when (effect) {
        ContactDetailEffect.BackToList -> nav.backToContactList()
    }
}
