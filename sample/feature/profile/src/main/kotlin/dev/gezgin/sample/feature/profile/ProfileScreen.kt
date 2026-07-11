package dev.gezgin.sample.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.NavResult
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.NotificationLevel
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import dev.gezgin.sample.navigation.ProfileNavigator
import kotlinx.coroutines.launch

@Screen
@Composable
fun ProfileScreen(route: ProfileScreenRoute, nav: ProfileNavigator) {
    var name by remember { mutableStateOf("Gezgin Kullanıcı") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var notifications by remember { mutableStateOf(NotificationLevel.ALL) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        nav.pickAvatarResults.collect { result ->
            if (result is NavResult.Value) avatarUri = result.value.uri
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Profil: $name")
            Text("Avatar: ${avatarUri ?: "(seçilmedi)"}")
            Text("Bildirimler: $notifications")
            Button(
                onClick = {
                    scope.launch {
                        val result = nav.goToEditNameDialogForResult(name)
                        if (result is NavResult.Value) name = result.value
                    }
                },
            ) { Text("Adı düzenle") }
            Button(onClick = { nav.goToSettings() }) { Text("Ayarlar") }
            Button(onClick = { nav.launchPickAvatar() }) { Text("Avatar seç") }
            Button(
                onClick = {
                    scope.launch {
                        val result = nav.goToPickNotificationsForResult(notifications)
                        if (result is NavResult.Value) notifications = result.value
                    }
                },
            ) { Text("Bildirimler") }
        }
    }
}
