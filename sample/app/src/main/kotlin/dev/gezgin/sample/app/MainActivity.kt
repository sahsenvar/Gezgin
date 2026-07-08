package dev.gezgin.sample.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.core.compose.navTransitions
import dev.gezgin.core.compose.rememberNavigator
import dev.gezgin.sample.feature.auth.provideCredentialsEntry
import dev.gezgin.sample.feature.auth.provideForgotPasswordDialogEntry
import dev.gezgin.sample.feature.auth.provideLoginEntry
import dev.gezgin.sample.feature.auth.provideProfileInfoEntry
import dev.gezgin.sample.feature.auth.provideTermsEntry
import dev.gezgin.sample.feature.home.provideDashboardEntry
import dev.gezgin.sample.feature.home.provideFilterSheetEntry
import dev.gezgin.sample.feature.home.provideItemDetailEntry
import dev.gezgin.sample.feature.home.provideWelcomeEntry
import dev.gezgin.sample.feature.profile.provideCropEntry
import dev.gezgin.sample.feature.profile.provideEditNameDialogEntry
import dev.gezgin.sample.feature.profile.providePickSourceEntry
import dev.gezgin.sample.feature.profile.provideProfileEntry
import dev.gezgin.sample.feature.profile.provideSettingsEntry
import dev.gezgin.sample.feature.profile.provideZoomEntry
import dev.gezgin.sample.navigation.AuthGraph.LoginRoute
import dev.gezgin.sample.navigation.gezginSerializersModule
import dev.gezgin.sample.navigation.gezginTopology
import kotlinx.serialization.json.Json

/**
 * S2 — showcase host. `start = LoginRoute` (NOT `DashboardRoute`, plan §N6): V1'in tek-stack
 * navigator'ında bir "önce login gate'i kontrol et" decider yok, bu yüzden en gerçekçi basit kurulum
 * `LoginRoute`'tan başlamaktır — `loginSuccess()` bir `@ReplaceTo` olduğu için giriş yapınca geri tuşu
 * login'e DÖNMEZ (Dashboard stack'in tek elemanı olur). Bu, `spec §12`'nin auth-gate/decider konusunu
 * V2'ye erteleyen notuyla tutarlıdır.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GezginShowcaseApp(onRootBack = { finish() }) }
    }
}

@Composable
private fun GezginShowcaseApp(onRootBack: () -> Unit) {
    val json = remember { Json { serializersModule = gezginSerializersModule } }
    val navigator = rememberNavigator(
        start = LoginRoute,
        topology = gezginTopology,
        json = json,
        onRootBack = onRootBack,
    )

    // Observe-only middleware deseni (§10): navigasyon olaylarını logcat'e yazar, akışı hiçbir şekilde
    // etkilemez (navigator'ın kendisine dokunmaz — yalnız `events`'i collect eder).
    LaunchedEffect(navigator) {
        navigator.events.collect { event -> Log.d("GezginNav", event.toString()) }
    }

    MaterialTheme {
        GezginDisplay(
            navigator = navigator,
            transitions = navTransitions {
                default {
                    forward { fadeIn() togetherWith fadeOut() }
                    back { slideInHorizontally() togetherWith slideOutHorizontally() }
                }
            },
        ) {
            // AuthGraph
            provideLoginEntry()
            provideForgotPasswordDialogEntry()
            provideCredentialsEntry()
            provideProfileInfoEntry()
            provideTermsEntry()
            // HomeGraph
            provideDashboardEntry()
            provideItemDetailEntry()
            provideFilterSheetEntry()
            provideWelcomeEntry()
            // ProfileGraph
            provideProfileEntry()
            provideSettingsEntry()
            provideEditNameDialogEntry()
            providePickSourceEntry()
            provideCropEntry()
            provideZoomEntry()
        }
    }
}
