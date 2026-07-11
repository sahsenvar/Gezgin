package dev.gezgin.sample.feature.home.modal_image_viewer

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.FullscreenModal
import dev.gezgin.sample.navigation.HomeGraph.ItemImageViewerRoute
import dev.gezgin.sample.navigation.ItemImageViewerNavigator

@FullscreenModal(ItemImageViewerRoute::class)
@Composable
fun ItemImageViewerModal(route: ItemImageViewerRoute, nav: ItemImageViewerNavigator) {
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
