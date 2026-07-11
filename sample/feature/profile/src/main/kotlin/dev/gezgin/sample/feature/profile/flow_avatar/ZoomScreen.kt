package dev.gezgin.sample.feature.profile.flow_avatar

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
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.navigation.AvatarFlow.ZoomFlow.ZoomScreenRoute
import dev.gezgin.sample.navigation.ZoomNavigator

@Screen
@Composable
fun ZoomScreen(route: ZoomScreenRoute, nav: ZoomNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yakınlaştır (nested ZoomFlow)")
            Button(onClick = { nav.quitWith(AvatarChoice(uri = "zoomed://frame")) }) { Text("Bu kareyi kullan") }
            Button(onClick = { nav.back() }) { Text("Geri") }
        }
    }
}
