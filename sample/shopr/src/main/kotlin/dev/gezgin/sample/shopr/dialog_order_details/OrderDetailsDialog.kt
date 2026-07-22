package dev.gezgin.sample.shopr.dialog_order_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.Dialog
import dev.gezgin.sample.shopr.nav.HomeGraph.OrderDetailsDialogRoute
import dev.gezgin.sample.shopr.nav.OrderDetailsDialogNavigator

@Dialog(OrderDetailsDialogRoute::class)
@Composable
fun OrderDetailsDialog(route: OrderDetailsDialogRoute, nav: OrderDetailsDialogNavigator) {
  Surface(
    shape = MaterialTheme.shapes.large,
    tonalElevation = 6.dp,
    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
  ) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Sipariş detayı")
      Text("Sipariş #${route.orderId}")
      Button(onClick = { nav.backToOrderPlaced() }) { Text("Kapat") }
    }
  }
}
