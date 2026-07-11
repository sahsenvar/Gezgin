package dev.gezgin.sample.feature.auth.screen_login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute

@Screen(LoginScreenRoute::class)
@Composable
fun LoginScreen(state: LoginUiState, onIntent: (LoginIntent) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Giriş yap")
            OutlinedTextField(
                value = state.email,
                onValueChange = { onIntent(LoginIntent.EmailChanged(it)) },
                label = { Text("E-posta") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { onIntent(LoginIntent.PasswordChanged(it)) },
                label = { Text("Şifre") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onIntent(LoginIntent.Submit) }) { Text("Giriş") }
            TextButton(onClick = { onIntent(LoginIntent.ForgotPassword) }) { Text("Şifremi unuttum") }
            TextButton(onClick = { onIntent(LoginIntent.SignUp) }) { Text("Kayıt ol") }
        }
    }
}
