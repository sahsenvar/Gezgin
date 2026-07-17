package dev.gezgin.sample.shopr.screen_feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.mvi.annotation.BottomBar
import dev.gezgin.mvi.annotation.TopBar
import dev.gezgin.sample.shopr.nav.HomeGraph

@TopBar(HomeGraph.Feed::class)
@Composable
fun FeedTopBar(state: FeedUiState, onIntent: (FeedIntent) -> Unit) {
    Surface {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Shopr")
            Button(onClick = { onIntent(FeedIntent.OpenCatalog) }) {
                Text(state.headline)
            }
        }
    }
}

@BottomBar(HomeGraph.Feed::class)
@Composable
fun FeedBottomBar(state: FeedUiState, onIntent: (FeedIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = { onIntent(FeedIntent.OpenCatalog) }) {
            Text(state.primaryActionLabel)
        }
    }
}
