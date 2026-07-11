package dev.gezgin.sample.feature.profile.screen_settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ThemeToggle(checked: Boolean, onToggle: () -> Unit) {
    Column {
        Text("Koyu tema")
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
