package dev.gezgin.sample.feature.home.screen_dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.domain.model.SortOrder
import dev.gezgin.sample.navigation.HomeGraph

private val FAKE_ITEMS = (1..5).map { "item-$it" }

@Screen(HomeGraph.DashboardScreenRoute::class)
@Composable
fun DashboardScreen(state: DashboardUiState, onIntent: (DashboardIntent) -> Unit) {
  val items =
    remember(state.order) {
      when (state.order) {
        SortOrder.RELEVANCE -> FAKE_ITEMS
        SortOrder.PRICE_ASC -> FAKE_ITEMS.sorted()
        SortOrder.PRICE_DESC -> FAKE_ITEMS.sortedDescending()
      }
    }
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Panel — sıralama: ${state.order}")
      items.forEach { id ->
        TextButton(onClick = { onIntent(DashboardIntent.OpenItem(id)) }) { Text(id) }
      }
      Button(onClick = { onIntent(DashboardIntent.OpenProfile) }) { Text("Profil") }
      Button(onClick = { onIntent(DashboardIntent.OpenHelp) }) { Text("Yardım (legacy Fragment)") }
      Button(onClick = { onIntent(DashboardIntent.PickSort) }) { Text("Sırala") }
    }
  }
}
