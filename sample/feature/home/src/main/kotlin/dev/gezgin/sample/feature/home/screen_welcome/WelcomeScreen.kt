package dev.gezgin.sample.feature.home.screen_welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.HomeGraph

@Screen(HomeGraph.WelcomeScreenRoute::class)
@Composable
fun WelcomeScreen(state: WelcomeUiState, onIntent: (WelcomeIntent) -> Unit) {
    LaunchedEffect(Unit) { onIntent(WelcomeIntent.OnAppear) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.name != null) "Hoş geldin, ${state.name}" else "Hoş geldin")
            Button(onClick = { onIntent(WelcomeIntent.Continue) }) { Text("Panoya devam") }
        }
    }
}
