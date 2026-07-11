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
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.AuthGraph.LoginScreenRoute
import dev.gezgin.sample.navigation.LoginNavigator
import kotlinx.coroutines.launch

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
