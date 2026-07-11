package dev.gezgin.sample.feature.profile.sheet_notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.core.compose.GezginSheetController
import dev.gezgin.sample.domain.model.NotificationLevel
import dev.gezgin.sample.navigation.ProfileGraph
import kotlinx.coroutines.launch

@BottomSheet(ProfileGraph.NotificationsSheetRoute::class)
@Composable
fun NotificationsSheetContent(
    state: NotificationsUiState,
    onIntent: (NotificationsIntent) -> Unit,
    controller: GezginSheetController,
) {
    val scope = rememberCoroutineScope()
    var dispatched by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bildirim düzeyi — seçili: ${state.selected}")
        NotificationLevel.entries.forEach { level ->
            Button(
                onClick = { onIntent(NotificationsIntent.Preview(level)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(level.name) }
        }
        Button(
            onClick = onClick@{
                if (dispatched) return@onClick
                dispatched = true
                scope.launch {
                    controller.hide()
                    onIntent(NotificationsIntent.Confirm)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Kaydet") }
    }
}