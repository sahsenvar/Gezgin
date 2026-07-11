package dev.gezgin.sample.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import dev.gezgin.core.compose.LocalGezginSheetController
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
            Button(onClick = { nav.goToHelp(topic = "navigasyon") }) { Text("Yardım (legacy Fragment)") }
            Button(
                onClick = {
                    // HAZARD (spec §6): rememberCoroutineScope composable ömrüne bağlı — config-change/
                    // process-death'te suspend result SESSİZCE düşer. PD-safe sonuç için VM scope'u ya da
                    // ProfileScreen'deki launch+collect (stream) deseni kullanılmalı.
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
    var visits by rememberSaveable { mutableIntStateOf(0) }
    // Composition-anında artış YASAK (her recomposition'da tetiklenir) — LaunchedEffect ile entry ömründe tek sefer.
    LaunchedEffect(Unit) { visits++ }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ürün: ${route.id}")
            Text("Bu ekran örneğinde ziyaret sayacı: $visits")
            Button(onClick = { nav.goToRelated(route.id) }) { Text("İlgili ürün (aynı id, yeni entry)") }
            Button(onClick = { nav.goToItemImageViewer(route.id) }) { Text("Görseli tam ekran gör") }
            TextButton(onClick = { nav.backToDashboard() }) { Text("Panoya dön") }
        }
    }
}

// Seçim sonrası sıra ZORUNLU: ÖNCE sheetState.hide() (kapanma animasyonu), SONRA backWithResult() —
// aksi halde back() sheet'i animasyonsuz kaybettirir.
@BottomSheet
@Composable
fun FilterSheetScreen(route: FilterBottomSheetRoute, nav: FilterBottomSheetNavigator) {
    val controller = LocalGezginSheetController.current
    val scope = rememberCoroutineScope()
    // hide() suspend animasyon → hızlı çift tık iki backWithResult dispatch edip arkadaki Dashboard'ı da
    // pop'lar; dispatched bayrağı onClick'te SENKRON (launch'tan önce) set edilir → yalnız ilk tık iş yapar.
    var dispatched by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sırala (şu an: ${route.current})")
        SortOrder.entries.forEach { candidate ->
            Button(
                onClick = onClick@{
                    if (dispatched) return@onClick
                    dispatched = true
                    scope.launch {
                        controller.hide()
                        nav.backWithResult(candidate)
                    }
                },
            ) { Text(candidate.name) }
        }
    }
}

@FullscreenModal
@Composable
fun ItemImageViewerScreen(route: ItemImageViewerRoute, nav: ItemImageViewerNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ürün görseli — tam ekran")
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
