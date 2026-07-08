package dev.gezgin.sample.feature.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.HomeGraph.WelcomeRoute
import dev.gezgin.sample.navigation.WelcomeNavigator

/**
 * S1 placeholder. WelcomeRoute is declared `@NoBack` in `:sample:navigation`; rendering its
 * `@Screen` HERE (a different module) is the CROSS-MODULE @NoBack proof — the generated
 * `GezginEntries.kt` in this package must register it with `noBack = true`, read from the route
 * declaration across the module boundary. Real UI arrives in S2.
 */
@Screen
@Composable
fun WelcomeScreen(route: WelcomeRoute, nav: WelcomeNavigator) {
    Text("welcome")
}
