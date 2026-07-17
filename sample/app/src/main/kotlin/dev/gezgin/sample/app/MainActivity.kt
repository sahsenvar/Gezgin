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
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.core.compose.navTransitions
import dev.gezgin.core.compose.rememberNavigator
import dev.gezgin.sample.feature.auth.authGraphEntries
import dev.gezgin.sample.feature.home.homeGraphEntries
import dev.gezgin.sample.feature.profile.profileGraphEntries
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.gezginJson
import dev.gezgin.sample.navigation.gezginTopology

internal const val SHOWCASE_RESTORE_KEY = "sample-showcase"

// @FragmentScreen yaprakları AndroidFragment ile host edilir → host bir AppCompatActivity/FragmentActivity
// OLMALI (aksi halde runtime'da fırlatır) ve AppCompat teması gerektirir (bkz. res/values/themes.xml).
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GezginShowcaseApp(onRootBack = { finish() }) }
    }
}

@Composable
private fun GezginShowcaseApp(onRootBack: () -> Unit) {
    // Generated gezginTopology + stable gezginJson bundled into the core @Composable rememberNavigator
    // (which lives in gezgin-core — a Compose module, so it is lowered correctly; a generated @Composable
    // in the plain-JVM graph module would not be, see TopologyCodegen.generateRememberNavigator).
    val navigator = rememberNavigator(
        start = LoginScreenRoute,
        topology = gezginTopology,
        json = gezginJson,
        restoreKey = SHOWCASE_RESTORE_KEY,
        onRootBack = onRootBack,
    )

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
            authGraphEntries()
            homeGraphEntries()
            profileGraphEntries()
        }
    }
}
