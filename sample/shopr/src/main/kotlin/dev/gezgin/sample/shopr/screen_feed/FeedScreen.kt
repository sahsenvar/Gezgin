package dev.gezgin.sample.shopr.screen_feed

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.shopr.nav.HomeGraph.FeaturedFeed
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed

@Screen(Feed::class)
@Screen(FeaturedFeed::class)
@Composable
fun ColumnScope.FeedScreen(state: FeedUiState, onIntent: (FeedIntent) -> Unit) {
    Text(state.headline)
    Button(onClick = { onIntent(FeedIntent.OpenCatalog) }) { Text(state.primaryActionLabel) }
}
