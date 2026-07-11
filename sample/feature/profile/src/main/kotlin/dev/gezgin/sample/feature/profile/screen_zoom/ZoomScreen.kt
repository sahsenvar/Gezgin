package dev.gezgin.sample.feature.profile.screen_zoom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.AvatarFlow.ZoomFlow.ZoomScreenRoute

@Screen(ZoomScreenRoute::class)
@Composable
fun ZoomScreen(state: ZoomUiState, onIntent: (ZoomIntent) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yakınlaştır (nested ZoomFlow)")
            Button(onClick = { onIntent(ZoomIntent.UseFrame) }) { Text("Bu kareyi kullan") }
            Button(onClick = { onIntent(ZoomIntent.Back) }) { Text("Geri") }
        }
    }
}
