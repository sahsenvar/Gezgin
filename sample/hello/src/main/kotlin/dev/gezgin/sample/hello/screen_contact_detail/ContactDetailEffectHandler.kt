package dev.gezgin.sample.hello.screen_contact_detail

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.hello.nav.ContactDetailNavigator
import dev.gezgin.sample.hello.nav.HelloGraph
import kotlinx.coroutines.flow.Flow

@EffectHandler(HelloGraph.ContactDetailScreenRoute::class)
@Composable
fun ContactDetailEffectHandler(effects: Flow<ContactDetailEffect>, nav: ContactDetailNavigator) {
  val context = LocalContext.current
  ObserveEffects(effects) { effect ->
    when (effect) {
      is ContactDetailEffect.ShowMessage ->
        Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
      is ContactDetailEffect.BackToList -> nav.backToContactList()
    }
  }
}
