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
        setContent { ShoprApp(onRootBack = { finish() }) }
    }
}

/**
 * Deferred comment-fix (final-review): `onRootBack = { finish() }` — kökte (`Feed`, stack dibi)
 * geri'ye basınca gerçek Android davranışı, `platformDefaultRootBack()`'in no-op'u değil, host
 * Activity'nin kapanmasıdır (bkz. `PlatformRootBack.android.kt`'nin artık güncellenmiş TODO'su).
 * `Json { ... }` `remember` ile sarıldı — önceki hal her (potansiyel) recomposition'da yeni bir `Json`
 * instance'ı kuruyordu (ucuz değil: serializersModule + config validasyonu); PD-restore'un `Saver`ı
 * zaten TEK `json` kaynağına (encode/decode simetrisi) bağımlı olduğundan bu instance'ın kimliğinin
 * composition boyunca sabit kalması da doğru davranış.
 */
@Composable
private fun ShoprApp(onRootBack: () -> Unit) {
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
