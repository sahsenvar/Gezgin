package dev.gezgin.sample.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.HomeGraph.WelcomeScreenRoute
import dev.gezgin.sample.navigation.WelcomeNavigator

@Screen
@Composable
fun WelcomeScreen(route: WelcomeScreenRoute, nav: WelcomeNavigator) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (route.name != null) "Hoş geldin, ${route.name}" else "Hoş geldin")
            Button(onClick = { nav.continueToDashboard() }) { Text("Panoya devam") }
        }
    }
}
