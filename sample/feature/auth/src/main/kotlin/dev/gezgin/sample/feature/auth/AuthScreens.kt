package dev.gezgin.sample.feature.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.AuthGraph.LoginRoute
import dev.gezgin.sample.navigation.LoginNavigator

/**
 * S1 placeholder — one `@Screen` over a cross-module route (its `LoginNavigator` is generated in
 * `:sample:navigation`). Presence of this composable makes the processor emit `GezginEntries.kt` in
 * THIS module's package, importing the factory from the route's package (cross-module wiring proof).
 * Real UI arrives in S2.
 */
@Screen
@Composable
fun LoginScreen(route: LoginRoute, nav: LoginNavigator) {
    Text("login")
}
