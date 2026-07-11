package dev.gezgin.sample.shopr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed
import dev.gezgin.sample.shopr.nav.rememberGezginNavigator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShoprApp(onRootBack = { finish() }) }
    }
}

@Composable
private fun ShoprApp(onRootBack: () -> Unit) {
    // M1 — generated convenience: bundles gezginTopology + a stable Json(gezginSerializersModule),
    // so the PD-restore Json-stability contract is handled by generated code, not a hand-written comment.
    val navigator = rememberGezginNavigator(start = Feed, onRootBack = onRootBack)
    GezginDisplay(navigator = navigator) {
        shopGraphEntries()
    }
}
