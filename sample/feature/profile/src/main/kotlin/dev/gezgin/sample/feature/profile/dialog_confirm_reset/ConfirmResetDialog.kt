package dev.gezgin.sample.feature.profile.dialog_confirm_reset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Dialog
import dev.gezgin.sample.navigation.ConfirmResetDialogNavigator
import dev.gezgin.sample.navigation.ProfileGraph.ConfirmResetDialogRoute

@Dialog(ConfirmResetDialogRoute::class)
@Composable
fun ConfirmResetDialog(route: ConfirmResetDialogRoute, nav: ConfirmResetDialogNavigator) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Adı sıfırla?")
            Button(onClick = { nav.backWithResult(true) }) { Text("Evet") }
            TextButton(onClick = { nav.backWithResult(false) }) { Text("Hayır") }
        }
    }
}
