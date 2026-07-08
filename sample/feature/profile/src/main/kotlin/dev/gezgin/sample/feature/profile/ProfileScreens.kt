package dev.gezgin.sample.feature.profile

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.gezgin.core.annotation.Screen
import dev.gezgin.sample.navigation.ProfileGraph.ProfileRoute
import dev.gezgin.sample.navigation.ProfileNavigator

/**
 * S1 placeholder — one `@Screen` over the cross-module `ProfileRoute`; `ProfileNavigator` (with its
 * launch/results/suspend result-members) is generated in `:sample:navigation`. Real UI arrives in S2.
 */
@Screen
@Composable
fun ProfileScreen(route: ProfileRoute, nav: ProfileNavigator) {
    Text("profile")
}
