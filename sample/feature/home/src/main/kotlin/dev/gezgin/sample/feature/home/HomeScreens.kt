@file:OptIn(ExperimentalMaterial3Api::class)

package dev.gezgin.sample.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.NavResult
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.core.annotation.FullscreenModal
import dev.gezgin.core.annotation.Screen
import dev.gezgin.core.compose.LocalGezginSheetState
import dev.gezgin.sample.navigation.DashboardNavigator
import dev.gezgin.sample.navigation.FilterBottomSheetNavigator
import dev.gezgin.sample.navigation.HomeGraph.DashboardScreenRoute
import dev.gezgin.sample.navigation.HomeGraph.FilterBottomSheetRoute
import dev.gezgin.sample.navigation.HomeGraph.ItemDetailScreenRoute
import dev.gezgin.sample.navigation.HomeGraph.ItemImageViewerRoute
import dev.gezgin.sample.navigation.HomeGraph.WelcomeScreenRoute
import dev.gezgin.sample.navigation.ItemDetailNavigator
import dev.gezgin.sample.navigation.ItemImageViewerNavigator
import dev.gezgin.sample.navigation.SortOrder
import dev.gezgin.sample.navigation.WelcomeNavigator
import kotlinx.coroutines.launch

/** S2 — `:feature:home` gerçek ekranları. */

private val FAKE_ITEMS = (1..5).map { "item-$it" }

@Screen
@Composable
fun DashboardScreen(route: DashboardScreenRoute, nav: DashboardNavigator) {
    var order by remember { mutableStateOf(SortOrder.RELEVANCE) }
    val items = remember(order) {
        when (order) {
            SortOrder.RELEVANCE -> FAKE_ITEMS
            SortOrder.PRICE_ASC -> FAKE_ITEMS.sorted()
            SortOrder.PRICE_DESC -> FAKE_ITEMS.sortedDescending()
        }
    }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Panel — sıralama: $order")
            items.forEach { id ->
                TextButton(onClick = { nav.goToItemDetail(id) }) { Text(id) }
            }
            Button(onClick = { nav.goToProfile() }) { Text("Profil") }
            // Faz 6.4 — legacy View-tabanlı @FragmentScreen yaprağına giriş (@GoTo(HelpScreenRoute) →
            // üretilen goToHelp(topic)). Fragment gezginArgs ile `topic`'i TextView'ında gösterir.
            Button(onClick = { nav.goToHelp(topic = "navigasyon") }) { Text("Yardım (legacy Fragment)") }
            Button(
                onClick = {
                    // suspend @GoForResult(named) tüketimi — result doğrudan liste state'ine akar.
                    // CAVEAT (spec §6): `scope` burada `rememberCoroutineScope()` — bu composable'ın
                    // (VM'siz) YAŞAM SÜRESİNE bağlı. Bir config-change / process-death sırasında bu
                    // scope iptal edilir; `goToPickSortForResult` beklerken kesilirse sonuç SESSİZCE
                    // düşer (recreate sonrası ne çağrı ne de bekleyen sonuç geri gelir — FilterSheet
                    // ekranı hâlâ stack'te durur ama kimse dinlemiyordur). Bir ViewModel'in scope'u
                    // (config-change'de hayatta kalır) içinde çağrılsaydı güvenli olurdu; VM'siz kalıcı
                    // sonuç isteniyorsa `ProfileScreen`'deki launch+collect (stream) deseni kullanılmalı
                    // (`nav.launchPickAvatar()` + `LaunchedEffect(Unit) { nav.pickAvatarResults.collect
                    // {...} }` — her recomposition'da yeniden abone olur, PD-safe re-attach).
                    scope.launch {
                        val result = nav.goToPickSortForResult(order.name)
                        if (result is NavResult.Value) order = result.value
                    }
                },
            ) { Text("Sırala") }
        }
    }
}

@Screen
@Composable
fun ItemDetailScreen(route: ItemDetailScreenRoute, nav: ItemDetailNavigator) {
    // singleTop=false ile aynı id'ye tekrar navigate edilebilir (R2: iki ItemDetailScreenRoute("3") eşit-değer
    // ama AYRI entry/id — bu sayaç her yeni entry'de rememberSaveable'dan 0'dan başlar, aynı-değerli iki
    // ekranın gerçekten bağımsız (saveable) state taşıdığını canlı gösterir.
    var visits by rememberSaveable { mutableIntStateOf(0) }
    // Composition-anında yazım YASAK (recomposition'da kendi kendini artırır — R2 dersini bozar).
    // Artış entry/composable ömründe TEK SEFER, LaunchedEffect(Unit) içinde yapılır.
    LaunchedEffect(Unit) { visits++ }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ürün: ${route.id}")
            Text("Bu ekran örneğinde ziyaret sayacı: $visits")
            Button(onClick = { nav.goToRelated(route.id) }) { Text("İlgili ürün (aynı id, yeni entry)") }
            // Faz 7.2 (GAP-1) — @FullscreenModal-kind hedefe giriş (@GoTo(ItemImageViewerRoute) →
            // üretilen goToItemImageViewer(id)). Tam-ekran ürün görseli önizleyici açılır.
            Button(onClick = { nav.goToItemImageViewer(route.id) }) { Text("Görseli tam ekran gör") }
            TextButton(onClick = { nav.backToDashboard() }) { Text("Panoya dön") }
        }
    }
}

/**
 * `@BottomSheet` kind — gerçek `ModalBottomSheet` overlay'i (Faz 4: `GezginBottomSheetSceneStrategy`,
 * arkadaki `DashboardScreenRoute` görünür kalır). `sheetState` `LocalGezginSheetState.current` ile
 * enjekte edilir (register imzası değişmedi — bkz. `BottomSheetScene.kt` KDoc'u); bir seçime tıklandığında
 * spec §7 deseni izlenir: ÖNCE `sheetState.hide()` (kapanma animasyonu tamamlanır), SONRA
 * `backWithResult(...)` (programatik pop + sonuç teslimi) — aksi halde `back()` sheet'i animasyonsuz
 * kaybettirirdi (bkz. `GezginBottomSheetScene` KDoc'undaki "kalıntı risk" notu). Kullanıcı swipe-down/
 * scrim-tap/geri-tuşu ile kapatırsa (`FilterBottomSheetRoute.BottomSheetContract` varsayılanları — bkz.
 * `HomeGraph.kt`) `onDismissRequest` → `back()` → `NavResult.Canceled` (Dashboard'daki
 * `goToPickSortForResult` çağrısı bunu görür).
 */
@BottomSheet
@Composable
fun FilterSheetScreen(route: FilterBottomSheetRoute, nav: FilterBottomSheetNavigator) {
    val sheetState = LocalGezginSheetState.current
    val scope = rememberCoroutineScope()
    // Double-click idempotency (referans sample dersi): `hide()` bir suspend animasyon → hızlı iki tık
    // İKİ coroutine başlatıp iki `backWithResult` dispatch edebilir; ikincisi arkadaki Dashboard'ı da
    // pop'lardı. `dispatched` bayrağı onClick'te SENKRON set edilir (launch'tan önce) → yalnız İLK tık
    // hide+backWithResult yapar, sonrakiler no-op.
    var dispatched by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sırala (şu an: ${route.current})")
        SortOrder.entries.forEach { candidate ->
            Button(
                onClick = onClick@{
                    if (dispatched) return@onClick
                    dispatched = true
                    scope.launch {
                        sheetState.hide()
                        nav.backWithResult(candidate)
                    }
                },
            ) { Text(candidate.name) }
        }
    }
}

/**
 * `@NoBack` route'u — declaration `:sample:navigation`'da, `@Screen` composable'ı BURADA
 * (`:feature:home`): sistem/predictive geri bu ekranda `GezginDisplay`'in `gezginOnBack` guard'ı
 * tarafından yutulur (cross-module okuma — bkz. `EntryModelReader.noBack`, declaration-tabanlı).
 * `WelcomeNavigator`'da generated `back()` YOK — declared `@ReplaceTo` (`continueToDashboard`) tek
 * çıkış.
 */
/**
 * Faz 7.2 (GAP-1) — `@FullscreenModal` kind: tam-ekran ürün görseli önizleyici. `@Dialog`/`@BottomSheet`
 * gibi kind composable'da yaşar; composable = SADECE içerik. Faz 4'ün `DialogSceneStrategy`'si bu route'u
 * `DialogProperties(usePlatformDefaultWidth = false)` ile tam-ekran render eder (scrim'siz/kenar-boşluksuz —
 * `DialogContract`'tan AYRI render kontratı; `FullscreenModalContract`'ın `usePlatformDefaultWidth`'i YOK).
 *
 * `ItemImageViewerRoute.dismissOnClickOutside = false` (bkz. `HomeGraph.kt`) → dış-tık kapatmaz;
 * `dismissOnBackPress` varsayılan `true` → geri tuşu/predictive-gesture kapatır (`onDismissRequest` →
 * `back()`). Explicit "Kapat" düğmesi `nav.backToItemDetail()` ile açan `ItemDetailScreenRoute`'a pop'lar
 * (görsel doğrudan onun üstünde açıldığı için bu, `back()`'in de yapacağı şeydir; route'un modal-çıkışını
 * TİPLİ navigator üzerinden yürütür — düz `@GoTo` girişi + tipli geri, result taşımadan).
 */
@FullscreenModal
@Composable
fun ItemImageViewerScreen(route: ItemImageViewerRoute, nav: ItemImageViewerNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ürün görseli — tam ekran")
            // Görsel yer tutucu — gerçek uygulamada bir Coil/AsyncImage olurdu; referans örnekte
            // tam-ekran render'ı kanıtlamak için ekranı dolduran bir placeholder yeterli.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("Görsel: ${route.id}")
            }
            Button(
                onClick = { nav.backToItemDetail() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Kapat") }
        }
    }
}

@Screen
@Composable
fun WelcomeScreen(route: WelcomeScreenRoute, nav: WelcomeNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (route.name != null) "Hoş geldin, ${route.name}" else "Hoş geldin")
            Button(onClick = { nav.continueToDashboard() }) { Text("Panoya devam") }
        }
    }
}
