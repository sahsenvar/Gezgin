package dev.gezgin.sample.feature.home.sheet_filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.core.compose.LocalGezginSheetController
import dev.gezgin.sample.navigation.FilterBottomSheetNavigator
import dev.gezgin.sample.navigation.HomeGraph.FilterBottomSheetRoute
import dev.gezgin.sample.domain.model.SortOrder
import kotlinx.coroutines.launch

// Seçim sonrası sıra ZORUNLU: ÖNCE sheetState.hide() (kapanma animasyonu), SONRA backWithResult() —
// aksi halde back() sheet'i animasyonsuz kaybettirir.
@BottomSheet
@Composable
fun FilterSheetScreen(route: FilterBottomSheetRoute, nav: FilterBottomSheetNavigator) {
    val controller = LocalGezginSheetController.current
    val scope = rememberCoroutineScope()
    // hide() suspend animasyon → hızlı çift tık iki backWithResult dispatch edip arkadaki Dashboard'ı da
    // pop'lar; dispatched bayrağı onClick'te SENKRON (launch'tan önce) set edilir → yalnız ilk tık iş yapar.
    var dispatched by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sırala (şu an: ${route.current})")
        SortOrder.entries.forEach { candidate ->
            Button(
                onClick = onClick@{
                    if (dispatched) return@onClick
                    dispatched = true
                    scope.launch {
                        controller.hide()
                        nav.backWithResult(candidate)
                    }
                },
            ) { Text(candidate.name) }
        }
    }
}
