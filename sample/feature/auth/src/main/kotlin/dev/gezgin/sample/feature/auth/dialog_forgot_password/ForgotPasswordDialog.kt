package dev.gezgin.sample.feature.auth.dialog_forgot_password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Dialog
import dev.gezgin.sample.navigation.AuthGraph.ForgotPasswordDialogRoute
import dev.gezgin.sample.navigation.ForgotPasswordDialogNavigator

// @Screen/@Dialog/@BottomSheet/@FullscreenModal birbirinin alternatifi — ikisini aynı fonksiyona
// koymak route'u iki kez kaydeder (SC4 derleme hatası).
@Dialog(ForgotPasswordDialogRoute::class)
@Composable
fun ForgotPasswordDialog(route: ForgotPasswordDialogRoute, nav: ForgotPasswordDialogNavigator) {
    var email by remember { mutableStateOf(route.email.orEmpty()) }
    // Dialog içeriği fillMaxSize DEĞİL — scrim üstünde wrap-content/ortalanmış görünmeli.
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
    ) {
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
