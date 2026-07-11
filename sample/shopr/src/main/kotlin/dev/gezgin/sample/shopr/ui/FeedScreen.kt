package dev.gezgin.sample.shopr.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.FeedNavigator
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed

@Screen
@Composable
fun FeedScreen(route: Feed, nav: FeedNavigator) {
    ScreenChrome(title = "Feed") {
        Button(onClick = { nav.goToCatalog() }) { Text("Kataloğa git") }
    }
}
