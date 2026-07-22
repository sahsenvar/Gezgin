package dev.gezgin.sample.feature.home.screen_item_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.HomeGraph

@Screen(HomeGraph.ItemDetailScreenRoute::class)
@Composable
fun ItemDetailScreen(state: ItemDetailUiState, onIntent: (ItemDetailIntent) -> Unit) {
  // Sayaç composition-anında DEĞİL, entry ömründe tek sefer artmalı → LaunchedEffect ile OnAppear.
  LaunchedEffect(Unit) { onIntent(ItemDetailIntent.OnAppear) }
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Ürün: ${state.id}")
      Text("Bu ekran örneğinde ziyaret sayacı: ${state.visits}")
      Button(onClick = { onIntent(ItemDetailIntent.OpenRelated) }) {
        Text("İlgili ürün (aynı id, yeni entry)")
      }
      Button(onClick = { onIntent(ItemDetailIntent.OpenImage) }) { Text("Görseli tam ekran gör") }
      TextButton(onClick = { onIntent(ItemDetailIntent.Back) }) { Text("Panoya dön") }
    }
  }
}
