package dev.gezgin.sample.feature.auth.screen_terms

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.gezgin.mvi.ObserveEffects
import dev.gezgin.mvi.annotation.EffectHandler
import dev.gezgin.sample.navigation.SignUpFlow.TermsScreenRoute
import dev.gezgin.sample.navigation.TermsNavigator
import kotlinx.coroutines.flow.Flow

@EffectHandler(TermsScreenRoute::class)
@Composable
fun TermsEffectHandler(effects: Flow<TermsEffect>, nav: TermsNavigator) {
    val context = LocalContext.current
    ObserveEffects(effects) { effect ->
        when (effect) {
            is TermsEffect.ShowMessage -> Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            TermsEffect.BackToStart -> nav.backToStart()
            TermsEffect.Quit -> nav.quit()
            is TermsEffect.Complete -> nav.quitAndGoToWelcome(effect.name)
        }
    }
}
