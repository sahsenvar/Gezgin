package dev.gezgin.sample.hello.screen_contact_list

import androidx.compose.runtime.Composable
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.hello.nav.ContactListNavigator
import dev.gezgin.sample.hello.nav.HelloGraph
import kotlinx.coroutines.flow.Flow

@EffectHandler(HelloGraph.ContactListScreenRoute::class)
@Composable
fun ContactListEffectHandler(effects: Flow<ContactListEffect>, nav: ContactListNavigator) {
  ObserveEffects(effects) { effect ->
    when (effect) {
      is ContactListEffect.OpenContact -> nav.goToContactDetail(effect.id)
    }
  }
}
