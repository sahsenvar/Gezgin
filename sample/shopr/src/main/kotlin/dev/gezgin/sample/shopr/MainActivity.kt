package dev.gezgin.sample.shopr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
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

/**
 * Task 3.6 — Shopr sample'ın tek Activity'si. `rememberNavigator`/`GezginDisplay`'i codegen'in
 * ürettiği `gezginTopology`/`gezginSerializersModule` + `provideXEntry`'lerle kurar (docs/gezgin-design.md
 * §2.2/§4.2). Serializers-ON: `Json { serializersModule = gezginSerializersModule }` — PD (process
 * death) restore'un gerçek polimorfik Route serialization'ını (kctfork test altyapısında OLMAYAN
 * kotlinx-serialization compiler plugin'i burada VAR) canlı derlemede doğrular (plan §3.6 devir e).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShoprApp() }
    }
}

@Composable
private fun ShoprApp() {
    val navigator = rememberNavigator(
        start = Feed,
        topology = gezginTopology,
        json = Json { serializersModule = gezginSerializersModule },
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
