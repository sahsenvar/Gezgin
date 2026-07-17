package dev.gezgin.sample.shopr.sheet_order_lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.sample.shopr.nav.HomeGraph

@BottomSheet(HomeGraph.OrderLockSheetRoute::class)
@Composable
fun OrderLockBottomSheet(route: HomeGraph.OrderLockSheetRoute) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sipariş ${route.orderId} doğrulanıyor")
        Text("Bu sayfa tamamlanana kadar geri, dış alan ve sürükleme ile kapatılamaz.")
    }
}
