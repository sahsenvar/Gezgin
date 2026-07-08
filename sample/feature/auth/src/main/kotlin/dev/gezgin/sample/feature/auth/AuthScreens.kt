package dev.gezgin.sample.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.gezgin.sample.navigation.AuthGraph.ForgotPasswordDialogRoute
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.AuthGraph.SignUpFlow.CredentialsScreenRoute
import dev.gezgin.sample.navigation.AuthGraph.SignUpFlow.ProfileInfoScreenRoute
import dev.gezgin.sample.navigation.AuthGraph.SignUpFlow.TermsScreenRoute
import dev.gezgin.sample.navigation.CredentialsNavigator
import dev.gezgin.sample.navigation.ForgotPasswordDialogNavigator
import dev.gezgin.sample.navigation.LoginNavigator
import dev.gezgin.sample.navigation.ProfileInfoNavigator
import dev.gezgin.sample.navigation.TermsNavigator
import kotlinx.coroutines.launch

/**
 * S2 — `:feature:auth` gerçek ekranları. Üretilen [LoginNavigator] API'si plandaki varsayılan adlardan
 * ikisinde farklı çıktı (processor keşfi, bkz. `.superpowers/sdd/sample-s2-report.md`):
 * `goToForgotPasswordDialogForResult` (plan: `goToForgotPasswordForResult`) ve `goToSignUp` (plan:
 * `goToSignUpFlow`) — isimler EdgeSpec/route simple-name'inden türetiliyor, üretilen koda uyulmuştur.
 */
@Screen
@Composable
fun LoginScreen(route: LoginScreenRoute, nav: LoginNavigator) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Giriş yap")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-posta") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { nav.loginSuccess() }) { Text("Giriş") }
                TextButton(
                    onClick = {
                        // suspend @GoForResult tüketimi: caller sonucu doğrudan await eder (VM'siz).
                        scope.launch {
                            val result = nav.goToForgotPasswordDialogForResult(email.ifBlank { null })
                            val message = when (result) {
                                is NavResult.Value -> if (result.value) {
                                    "Sıfırlama linki gönderildi"
                                } else {
                                    "Sıfırlama iptal edildi"
                                }
                                NavResult.Canceled -> "Sıfırlama iptal edildi"
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                ) { Text("Şifremi unuttum") }
                TextButton(onClick = { nav.goToSignUp() }) { Text("Kayıt ol") }
            }
        }
    }
}

/**
 * `@Dialog` kind — processor `@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal`'ı BİRBİRİNİN
 * ALTERNATİFİ olarak okur (her biri ayrı `getSymbolsWithAnnotation` sorgusu; ikisini aynı fonksiyona
 * koymak aynı route'u iki kez kaydeder → SC4 derleme hatası) — bu yüzden yalnız `@Dialog`.
 *
 * Faz 4: bu artık GERÇEK bir `DialogSceneStrategy` overlay'i — arkadaki `LoginScreenRoute` görünür
 * kalır, bu composable `androidx.compose.ui.window.Dialog` içinde çizilir. `ForgotPasswordDialogRoute`
 * (bkz. `AuthGraph.kt`) `DialogContract.dismissOnClickOutside = false` deklare eder — dışarı tık
 * KAPATMAZ; `dismissOnBackPress` varsayılan `true` — geri tuşu/Esc HÂLÂ kapatır. Her iki dismiss yolu
 * da (izin verilenler) `onDismissRequest = onBack` → `navigator.back()` → bu route `ResultRoute`
 * olduğu için caller `NavResult.Canceled` alır (aşağıdaki `goToForgotPasswordDialogForResult` çağrısı).
 */
@Dialog
@Composable
fun ForgotPasswordDialogScreen(route: ForgotPasswordDialogRoute, nav: ForgotPasswordDialogNavigator) {
    var email by remember { mutableStateOf(route.email.orEmpty()) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Şifre sıfırlama")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-posta") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { nav.backWithResult(true) }) { Text("Gönder") }
            TextButton(onClick = { nav.back() }) { Text("Vazgeç") }
        }
    }
}

@Screen
@Composable
fun CredentialsScreen(route: CredentialsScreenRoute, nav: CredentialsNavigator) {
    var email by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kayıt ol — hesap bilgileri")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-posta") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { nav.goToProfileInfo(email) }) { Text("Devam") }
        }
    }
}

@Screen
@Composable
fun ProfileInfoScreen(route: ProfileInfoScreenRoute, nav: ProfileInfoNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kayıt ol — profil bilgileri")
            Text("E-posta: ${route.email}")
            Button(onClick = { nav.goToTerms() }) { Text("Devam") }
        }
    }
}

@Screen
@Composable
fun TermsScreen(route: TermsScreenRoute, nav: TermsNavigator) {
    var name by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kayıt ol — kullanım koşulları")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Adınız (Welcome ekranı için)") },
                modifier = Modifier.fillMaxWidth(),
            )
            // Üç farklı çıkış türü — aynı ekranda: @BackToStart / @Quit / @QuitAndGoTo.
            Button(onClick = { nav.backToStart() }) { Text("Başa dön") }
            TextButton(onClick = { nav.quit() }) { Text("Vazgeç") }
            Button(onClick = { nav.quitAndGoToWelcome(name.ifBlank { null }) }) { Text("Kaydı tamamla") }
        }
    }
}
