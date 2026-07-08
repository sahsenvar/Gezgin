package dev.gezgin.sample.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import dev.gezgin.sample.navigation.PickSourceNavigator
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.CropRoute
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.PickSourceRoute
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.ZoomFlow.ZoomRoute
import dev.gezgin.sample.navigation.ProfileGraph.EditNameDialog
import dev.gezgin.sample.navigation.ProfileGraph.ProfileRoute
import dev.gezgin.sample.navigation.ProfileGraph.SettingsRoute
import dev.gezgin.sample.navigation.ProfileNavigator
import dev.gezgin.sample.navigation.SettingsNavigator
import dev.gezgin.sample.navigation.ZoomNavigator
import kotlinx.coroutines.launch

/** S2 — `:feature:profile` gerçek ekranları. */

@Screen
@Composable
fun ProfileScreen(route: ProfileRoute, nav: ProfileNavigator) {
    var name by remember { mutableStateOf("Gezgin Kullanıcı") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Launch+collect deseni (§6, VM'siz): `launchPickAvatar()` tetikler, `pickAvatarResults` Flow'u
    // her (re)composition'da (dolayısıyla her process-death sonrası recreation'da) YENİDEN toplanır —
    // PD-safe re-attach; bekleyen bir sonuç varsa collector kurulur kurulmaz teslim edilir.
    LaunchedEffect(Unit) {
        nav.pickAvatarResults.collect { result ->
            if (result is NavResult.Value) avatarUri = result.value.uri
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Profil: $name")
            Text("Avatar: ${avatarUri ?: "(seçilmedi)"}")
            Button(
                onClick = {
                    // suspend @GoForResult tüketimi — Dashboard'daki `pickSort` ile aynı desen, farklı
                    // hedef (Dialog).
                    scope.launch {
                        val result = nav.goToEditNameDialogForResult(name)
                        if (result is NavResult.Value) name = result.value
                    }
                },
            ) { Text("Adı düzenle") }
            Button(onClick = { nav.goToSettings() }) { Text("Ayarlar") }
            Button(onClick = { nav.launchPickAvatar() }) { Text("Avatar seç") }
        }
    }
}

@Screen
@Composable
fun SettingsScreen(route: SettingsRoute, nav: SettingsNavigator) {
    var darkTheme by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ayarlar")
            ThemeToggle(darkTheme) { darkTheme = it }
            // `logout()` = `replaceTo(LoginRoute, clearUpTo = DashboardRoute, inclusive = true)`:
            // stack Dashboard dahil temizlenir, yerine Login gelir (geri ile Dashboard'a dönülmez).
            Button(onClick = { nav.logout() }) { Text("Çıkış yap") }
        }
    }
}

@Composable
private fun ThemeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column {
        Text("Koyu tema")
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** `@Dialog` kind — bkz. AuthScreens.kt'deki not (yalnız kind annotation'ı, `@Screen` YOK). */
@Dialog
@Composable
fun EditNameDialogScreen(route: EditNameDialog, nav: EditNameDialogNavigator) {
    var text by remember { mutableStateOf(route.current) }
    Surface(modifier = Modifier.fillMaxSize()) {
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
fun PickSourceScreen(route: PickSourceRoute, nav: PickSourceNavigator) {
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
fun CropScreen(route: CropRoute, nav: CropNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kırp (kaynak: ${route.source})")
            Button(onClick = { nav.goToZoom() }) { Text("Yakınlaştır") }
            Button(onClick = { nav.quitWith(AvatarChoice(uri = "avatar://${route.source}")) }) { Text("Kullan") }
            // `CropRoute`'un generated `quit()`u YOK (yalnız `AvatarFlow` üyeleri arasında `@Quit`
            // annotasyonu olan yok) — vazgeçmek `back()` ile PickSourceRoute'a döner (escalation:
            // plandaki `nav.quit()` yerine üretilen API'ye uyuldu, bkz. rapor).
            TextButton(onClick = { nav.back() }) { Text("Vazgeç") }
        }
    }
}

@Screen
@Composable
fun ZoomScreen(route: ZoomRoute, nav: ZoomNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yakınlaştır (nested ZoomFlow)")
            // Nested flow'dan çıkış: `ZoomRoute`'ta da (Crop gibi) generated `quit()` yok — `back()`
            // yalnız ZoomFlow'un kendi entry'sini pop'lar, CropRoute (ve dolayısıyla AvatarFlow) AÇIK
            // kalır (plan notu: "yalnız ZoomFlow kapanır, AvatarFlow kalır" — aynı sonuç, üretilen API).
            Button(onClick = { nav.back() }) { Text("Geri") }
        }
    }
}
