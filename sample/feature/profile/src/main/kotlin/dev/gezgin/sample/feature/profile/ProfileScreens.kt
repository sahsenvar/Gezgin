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
import dev.gezgin.sample.navigation.NotificationLevel
import dev.gezgin.sample.navigation.EditNameDialogNavigator
import dev.gezgin.sample.navigation.PickSourceNavigator
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.CropScreenRoute
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.PickSourceScreenRoute
import dev.gezgin.sample.navigation.ProfileGraph.AvatarFlow.ZoomFlow.ZoomScreenRoute
import dev.gezgin.sample.navigation.ProfileGraph.EditNameDialogRoute
import dev.gezgin.sample.navigation.ProfileGraph.ProfileScreenRoute
import dev.gezgin.sample.navigation.ProfileNavigator
import dev.gezgin.sample.navigation.ZoomNavigator
import kotlinx.coroutines.launch

/** S2 — `:feature:profile` gerçek ekranları. */

@Screen
@Composable
fun ProfileScreen(route: ProfileScreenRoute, nav: ProfileNavigator) {
    var name by remember { mutableStateOf("Gezgin Kullanıcı") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var notifications by remember { mutableStateOf(NotificationLevel.ALL) }
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
            Text("Bildirimler: $notifications")
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

// NOT: `SettingsScreen` Faz 5.3'te MVI-mode'a çevrildi — bkz. `SettingsMvi.kt`
// (`@ViewModel(SettingsScreenRoute)` + stateless `@Screen(SettingsScreenRoute)` `SettingsContent` +
// `@ScreenEffect SettingsEffects`). `logout()` artık VM'in `onIntent`'inden çağrılır (§10 A deseni).

/**
 * `@Dialog` kind — bkz. AuthScreens.kt'deki not (yalnız kind annotation'ı, `@Screen` YOK). Gerçek
 * `DialogSceneStrategy` overlay'i (Faz 4); `EditNameDialogRoute` (bkz. `ProfileGraph.kt`)
 * `DialogContract`'ın KOŞULLU desenini kullanır: `dismissOnClickOutside` route'un `current` ctor
 * param'ından hesaplanır (boşsa kapanmaz, doluysa dışarı-tık kapatır) — `ForgotPasswordDialogRoute`'un
 * SABİT `false`'unun karşıtı. Dismiss (izin verilen yollarla) → `back()` → `NavResult.Canceled`.
 */
@Dialog
@Composable
fun EditNameDialogScreen(route: EditNameDialogRoute, nav: EditNameDialogNavigator) {
    var text by remember { mutableStateOf(route.current) }
    // Gerçek Dialog overlay — içerik KOMPAKT bir kart; `fillMaxSize` DEĞİL (dialog tüm ekranı
    // kaplamamalı, scrim üstünde ortalanmış/wrap-content/gölgeli görünmeli — bkz. on-device checklist
    // madde 9). Dialog penceresi `usePlatformDefaultWidth=true` ile zaten genişliği sınırlar.
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
            // `CropScreenRoute`'un generated `quit()`u YOK (yalnız `AvatarFlow` üyeleri arasında `@Quit`
            // annotasyonu olan yok) — vazgeçmek `back()` ile PickSourceScreenRoute'a döner (escalation:
            // plandaki `nav.quit()` yerine üretilen API'ye uyuldu, bkz. rapor).
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
            // `quitWith` en yakın SÖZLEŞME SAHİBİ (ResultFlow<T>'u DOĞRUDAN deklare eden) flow'u
            // hedefler (spec §6 ownership): ZoomScreenRoute'un chain'i [AvatarFlow, ZoomFlow] ama ZoomFlow
            // kendi sözleşmesini deklare etmez — bu çağrı HEM ZoomFlow HEM AvatarFlow segmentlerini
            // tek seferde yıkar ve AvatarChoice değerini doğrudan Profile'ın `pickAvatarResults`
            // sözleşmesine teslim eder. Nested result'suz bir sub-flow İÇİNDEN dahi flow-mode sonuç
            // teslim edilebildiğinin kanıtı.
            Button(onClick = { nav.quitWith(AvatarChoice(uri = "zoomed://frame")) }) { Text("Bu kareyi kullan") }
            // Nested flow'dan geri çıkış: `back()` yalnız ZoomFlow'un kendi entry'sini pop'lar,
            // CropScreenRoute (ve dolayısıyla AvatarFlow) AÇIK kalır.
            Button(onClick = { nav.back() }) { Text("Geri") }
        }
    }
}
