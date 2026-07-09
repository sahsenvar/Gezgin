package dev.gezgin.sample.app

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import dev.gezgin.sample.feature.auth.authGraphEntries
import dev.gezgin.sample.feature.home.homeGraphEntries
import dev.gezgin.sample.feature.profile.profileGraphEntries
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.gezginSerializersModule
import dev.gezgin.sample.navigation.gezginTopology
import kotlinx.serialization.json.Json

/**
 * S2 — showcase host. `start = LoginScreenRoute` (NOT `DashboardScreenRoute`, plan §N6): V1'in tek-stack
 * navigator'ında bir "önce login gate'i kontrol et" decider yok, bu yüzden en gerçekçi basit kurulum
 * `LoginScreenRoute`'tan başlamaktır — `loginSuccess()` bir `@ReplaceTo` olduğu için giriş yapınca geri tuşu
 * login'e DÖNMEZ (Dashboard stack'in tek elemanı olur). Bu, `spec §12`'nin auth-gate/decider konusunu
 * V2'ye erteleyen notuyla tutarlıdır.
 *
 * **Faz 6.4 — `AppCompatActivity` host (ZORUNLU precondition).** `ComponentActivity` iken
 * `AppCompatActivity`'ye geçirildi: `@FragmentScreen` yaprakları `androidx.fragment.compose.AndroidFragment`
 * ile host edilir ve bu composable görünüm ağacında bir `FragmentActivity`/`AppCompatActivity` host'u
 * (`FragmentManager.findFragmentManager(view)`) YOKSA runtime'da fırlatır (Task 6.0 §1e.1). Bu, Fragment
 * interop'u kullanan HER tüketicinin sağlaması gereken tek precondition'dır. `AppCompatActivity` bir
 * `AppCompat` temasını da GEREKTİRİR → `AndroidManifest.xml` teması `Theme.AppCompat.Light.NoActionBar`'a
 * çekildi (bkz. `res/values/themes.xml`).
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GezginShowcaseApp(onRootBack = { finish() }) }
    }
}

@Composable
private fun GezginShowcaseApp(onRootBack: () -> Unit) {
    val json = remember { Json { serializersModule = gezginSerializersModule } }
    val navigator = rememberNavigator(
        start = LoginScreenRoute,
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
                forward { fadeIn() togetherWith fadeOut() }
                backward { slideInHorizontally() togetherWith slideOutHorizontally() }
            },
        ) {
            authGraphEntries()    // dev.gezgin.sample.feature.auth
            homeGraphEntries()    // dev.gezgin.sample.feature.home
            profileGraphEntries() // dev.gezgin.sample.feature.profile
        }
    }
}
