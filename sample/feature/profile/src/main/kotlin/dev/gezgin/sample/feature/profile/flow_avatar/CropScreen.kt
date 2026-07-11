package dev.gezgin.sample.feature.profile.flow_avatar

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
import dev.gezgin.sample.navigation.AvatarChoice
import dev.gezgin.sample.navigation.AvatarFlow.CropScreenRoute
import dev.gezgin.sample.navigation.CropNavigator

@Screen
@Composable
fun CropScreen(route: CropScreenRoute, nav: CropNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kırp (kaynak: ${route.source})")
            Button(onClick = { nav.goToZoom() }) { Text("Yakınlaştır") }
            Button(onClick = { nav.quitWith(AvatarChoice(uri = "avatar://${route.source}")) }) { Text("Kullan") }
            TextButton(onClick = { nav.back() }) { Text("Vazgeç") }
        }
    }
}
