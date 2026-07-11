package dev.gezgin.sample.shopr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed
import dev.gezgin.sample.shopr.nav.rememberGezginNavigator
import dev.gezgin.sample.shopr.ui.provideCartEntry
import dev.gezgin.sample.shopr.ui.provideCatalogEntry
import dev.gezgin.sample.shopr.ui.provideFeedEntry
import dev.gezgin.sample.shopr.ui.provideOrderPlacedEntry
import dev.gezgin.sample.shopr.ui.providePaymentEntry
import dev.gezgin.sample.shopr.ui.provideProductEntry
import kotlinx.serialization.json.Json

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
        provideFeedEntry()
        provideCatalogEntry()
        provideProductEntry()
        provideOrderPlacedEntry()
        provideCartEntry()
        providePaymentEntry()
    }
}
