package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import kotlinx.serialization.json.Json

/**
 * Android actual (C1 — spec §225 "stable RawNavigator") — iki katman:
 *
 * 1. **Config-change (rotasyon) dayanıklılığı:** navigator, host `ViewModelStoreOwner` (Activity —
 *    `rememberNavigator` çağrısı setContent'te, GezginDisplay'in ÜSTÜNDE) scope'lu bir [NavigatorHolder]
 *    ViewModel'inde tutulur. Activity `ViewModelStore` retained-instance ile rotasyondan sağ çıktığından
 *    holder — ve içindeki AYNI `RawNavigator` — de sağ çıkar. Böylece per-entry ViewModel'in (aynı
 *    mekanizmayla yaşayan) ctor'unda yakaladığı navigator referansı rotasyondan sonra ÖLÜ olmaz;
 *    display'in gözlemlediği `keysState`'i sürmeye devam eder. (HEAD'de `rememberSaveable` restore'da YENİ
 *    bir navigator kuruyordu → VM eskisini, display yenisini tutuyordu = C1.)
 *
 * 2. **Process death:** holder ViewModel'i de ölür → taze navigator `start`'ta kurulur. `rememberSaveable`
 *    (Bundle → PD'yi atlar) serileştirilmiş [dev.gezgin.core.SavedState] snapshot'ını taşır; taze instance
 *    onu BİR KEZ [RawNavigator.adoptRestored] ile benimser. `holder.pdRestored` bayrağı instance ömrüne
 *    bağlı (config-change'te korunur → re-adopt YOK, her recomposition'da state sıfırlama YOK; PD'de taze
 *    holder ile sıfırlanır). `save` daima CANLI navigator'dan encode eder (held değer yok sayılır) →
 *    snapshot güncel. Bozuk/şema-uyumsuz snapshot [decodeSavedStateOrNull] ile `null` → sessiz fresh-start.
 */
@Composable
internal actual fun rememberRawNavigatorInstance(
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): RawNavigator {
    val holder = viewModel<NavigatorHolder>(
        factory = viewModelFactory {
            initializer {
                NavigatorHolder(
                    RawNavigator(start = start, topology = topology, onRootBack = onRootBack, json = json),
                )
            }
        },
    )
    val navigator = holder.navigator
    val pdSnapshot = rememberSaveable(
        saver = Saver<String, String>(
            save = { encodeNavigatorState(navigator, json) },   // held değer değil, CANLI navigator encode edilir
            restore = { it },
        ),
    ) { "" }
    if (pdSnapshot.isNotEmpty() && !holder.pdRestored) {
        holder.pdRestored = true
        decodeSavedStateOrNull(pdSnapshot, json)?.let(navigator::adoptRestored)
    }
    return navigator
}

/**
 * C1 — config-change'i atlayan host-scope'lu kap. Activity `ViewModelStore`'unda yaşar → rotasyondan sağ
 * çıkar; [pdRestored] snapshot-adopt'un instance başına en fazla bir kez çalışmasını garanti eder.
 */
private class NavigatorHolder(val navigator: RawNavigator) : ViewModel() {
    var pdRestored: Boolean = false
}
