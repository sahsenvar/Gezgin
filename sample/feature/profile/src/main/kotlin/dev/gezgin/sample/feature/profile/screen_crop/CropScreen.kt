package dev.gezgin.sample.feature.profile.screen_crop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.AvatarFlow.CropScreenRoute

@Screen(CropScreenRoute::class)
@Composable
fun CropScreen(state: CropUiState, onIntent: (CropIntent) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kırp (kaynak: ${state.source})")
            Button(onClick = { onIntent(CropIntent.Zoom) }) { Text("Yakınlaştır") }
            Button(onClick = { onIntent(CropIntent.Use) }) { Text("Kullan") }
            TextButton(onClick = { onIntent(CropIntent.Cancel) }) { Text("Vazgeç") }
        }
    }
}
