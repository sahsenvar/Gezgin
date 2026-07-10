package dev.gezgin.sample.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.gezgin.core.annotation.Dialog
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.AvatarChoice
import dev.gezgin.sample.navigation.CropNavigator
import dev.gezgin.sample.navigation.EditNameDialogNavigator
import dev.gezgin.sample.navigation.NotificationLevel
import dev.gezgin.sample.navigation.PickSourceNavigator
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.CropScreenRoute
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.PickSourceScreenRoute
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.ZoomFlow.ZoomScreenRoute
import dev.gezgin.sample.navigation.ProfileGraph.EditNameDialogRoute
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import dev.gezgin.sample.navigation.ProfileNavigator
import dev.gezgin.sample.navigation.ZoomNavigator
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

@Dialog
@Composable
fun EditNameDialogScreen(route: EditNameDialogRoute, nav: EditNameDialogNavigator) {
    var text by remember { mutableStateOf(route.current) }
    // Dialog içeriği fillMaxSize DEĞİL — scrim üstünde wrap-content/ortalanmış görünmeli.
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Adı düzenle")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { nav.backWithResult(text) }) { Text("Kaydet") }
            TextButton(onClick = { nav.back() }) { Text("Vazgeç") }
        }
    }
}

@Screen
@Composable
fun PickSourceScreen(route: PickSourceScreenRoute, nav: PickSourceNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Avatar kaynağı seç")
            Button(onClick = { nav.goToCrop("gallery") }) { Text("Galeri") }
            Button(onClick = { nav.goToCrop("camera") }) { Text("Kamera") }
        }
    }
}

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
