package dev.gezgin.sample.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.NavResult
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.DashboardNavigator
import dev.gezgin.sample.navigation.HomeGraph.DashboardRoute
import dev.gezgin.sample.navigation.HomeGraph.FilterSheetRoute
import dev.gezgin.sample.navigation.HomeGraph.ItemDetailRoute
import dev.gezgin.sample.navigation.HomeGraph.WelcomeRoute
import dev.gezgin.sample.navigation.ItemDetailNavigator
import dev.gezgin.sample.navigation.FilterSheetNavigator
import dev.gezgin.sample.navigation.SortOrder
import dev.gezgin.sample.navigation.WelcomeNavigator
import kotlinx.coroutines.launch

/** S2 — `:feature:home` gerçek ekranları. */

private val FAKE_ITEMS = (1..5).map { "item-$it" }

@Screen
@Composable
fun DashboardScreen(route: DashboardRoute, nav: DashboardNavigator) {
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
            Button(
                onClick = {
                    // suspend @GoForResult(named) tüketimi — result doğrudan liste state'ine akar.
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
fun ItemDetailScreen(route: ItemDetailRoute, nav: ItemDetailNavigator) {
    // singleTop=false ile aynı id'ye tekrar navigate edilebilir (R2: iki ItemDetailRoute("3") eşit-değer
    // ama AYRI entry/id — bu sayaç her yeni entry'de rememberSaveable'dan 0'dan başlar, aynı-değerli iki
    // ekranın gerçekten bağımsız (saveable) state taşıdığını canlı gösterir.
    var visits by rememberSaveable { mutableIntStateOf(0) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ürün: ${route.id}")
            Text("Bu ekran örneğinde ziyaret sayacı: ${++visits}")
            Button(onClick = { nav.goToRelated(route.id) }) { Text("İlgili ürün (aynı id, yeni entry)") }
            TextButton(onClick = { nav.backToDashboard() }) { Text("Panoya dön") }
        }
    }
}

/** `@BottomSheet` kind — Faz 4'e kadar plain screen render edilir (bkz. AuthScreens.kt üstteki not). */
@BottomSheet
@Composable
fun FilterSheetScreen(route: FilterSheetRoute, nav: FilterSheetNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sırala (şu an: ${route.current})")
            SortOrder.entries.forEach { candidate ->
                Button(onClick = { nav.backWithResult(candidate) }) { Text(candidate.name) }
            }
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
@Screen
@Composable
fun WelcomeScreen(route: WelcomeRoute, nav: WelcomeNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (route.name != null) "Hoş geldin, ${route.name}" else "Hoş geldin")
            Button(onClick = { nav.continueToDashboard() }) { Text("Panoya devam") }
        }
    }
}
