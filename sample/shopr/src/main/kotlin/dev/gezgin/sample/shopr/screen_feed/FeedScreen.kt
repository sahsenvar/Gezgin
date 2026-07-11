package dev.gezgin.sample.shopr.screen_feed

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed
import dev.gezgin.sample.shopr.ui.ScreenChrome

@Screen(Feed::class)
@Composable
fun FeedScreen(state: FeedUiState, onIntent: (FeedIntent) -> Unit) {
    ScreenChrome(title = state.headline) {
        Button(onClick = { onIntent(FeedIntent.OpenCatalog) }) { Text("Kataloğa git") }
    }
}
