package dev.gezgin.sample.feature.auth.flow_signup

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.SignUpFlow.TermsScreenRoute
import dev.gezgin.sample.navigation.TermsNavigator

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
            Button(onClick = { nav.backToStart() }) { Text("Başa dön") }
            TextButton(onClick = { nav.quit() }) { Text("Vazgeç") }
            Button(onClick = { nav.quitAndGoToWelcome(name.ifBlank { null }) }) { Text("Kaydı tamamla") }
        }
    }
}
