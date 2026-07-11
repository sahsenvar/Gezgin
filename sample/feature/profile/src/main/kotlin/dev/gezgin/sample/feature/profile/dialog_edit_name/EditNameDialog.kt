package dev.gezgin.sample.feature.profile.dialog_edit_name

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
import dev.gezgin.sample.navigation.EditNameDialogNavigator
import dev.gezgin.sample.navigation.ProfileGraph.EditNameDialogRoute

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
