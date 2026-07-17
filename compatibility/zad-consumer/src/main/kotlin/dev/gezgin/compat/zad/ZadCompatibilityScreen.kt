package dev.gezgin.compat.zad

import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen

@Screen(ZadCompatibilityRoute::class)
@Composable
fun ZadCompatibilityScreen(
    state: ZadCompatibilityState,
    onIntent: (ZadCompatibilityIntent) -> Unit,
) {
    state.hashCode()
    onIntent.hashCode()
}
