package dev.gezgin.sample.shopr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.core.compose.rememberNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed
import dev.gezgin.sample.shopr.nav.gezginSerializersModule
import dev.gezgin.sample.shopr.nav.gezginTopology
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
    // json TEK ve sabit tutulmalı — PD-restore Saver'ı encode/decode simetrisi için aynı instance'a bağımlı.
    val json = remember { Json { serializersModule = gezginSerializersModule } }
    val navigator = rememberNavigator(
        start = Feed,
        topology = gezginTopology,
        json = json,
        onRootBack = onRootBack,
    )
    GezginDisplay(navigator = navigator) {
        provideFeedEntry()
        provideCatalogEntry()
        provideProductEntry()
        provideOrderPlacedEntry()
        provideCartEntry()
        providePaymentEntry()
    }
}
