package dev.gezgin.sample.shopr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.core.compose.rememberNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed
import dev.gezgin.sample.shopr.nav.gezginJson
import dev.gezgin.sample.shopr.nav.gezginTopology

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShoprApp(onRootBack = { finish() }) }
    }
}

@Composable
private fun ShoprApp(onRootBack: () -> Unit) {
    val navigator = rememberNavigator(
        start = Feed,
        topology = gezginTopology,
        json = gezginJson,
        onRootBack = onRootBack,
    )
    GezginDisplay(navigator = navigator) {
        shopGraphEntries()
    }
}
