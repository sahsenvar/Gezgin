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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.NavResult
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.DashboardNavigator
import dev.gezgin.sample.navigation.HomeGraph.DashboardScreenRoute
import dev.gezgin.sample.navigation.SortOrder
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
