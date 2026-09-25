package dev.gezgin.sample.feature.home.sheet_share_target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.sample.domain.model.ShareTarget
import dev.gezgin.sample.navigation.HomeGraph.ShareTargetSheetRoute

@BottomSheet(ShareTargetSheetRoute::class)
@Composable
fun ShareTargetSheet(itemId: String, onSelect: (ShareTarget) -> Unit) {
  Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("$itemId nereye paylaşılsın?")
    ShareTarget.entries.forEach { target ->
      Button(onClick = { onSelect(target) }) { Text(target.name) }
    }
  }
}
