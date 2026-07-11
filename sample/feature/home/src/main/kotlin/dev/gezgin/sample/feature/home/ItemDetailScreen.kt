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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.HomeGraph.ItemDetailScreenRoute
import dev.gezgin.sample.navigation.ItemDetailNavigator

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
