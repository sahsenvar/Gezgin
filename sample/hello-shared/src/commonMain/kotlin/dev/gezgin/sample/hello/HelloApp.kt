package dev.gezgin.sample.hello

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.core.compose.rememberNavigator
import dev.gezgin.sample.hello.nav.HelloGraph
import dev.gezgin.sample.hello.nav.gezginJson
import dev.gezgin.sample.hello.nav.gezginTopology

/**
 * The whole application, shared by every platform. Only the host differs: an Activity on Android
 * and a `UIViewController` on iOS, each supplying its own root-back policy.
 */
@Composable
fun HelloApp(onRootBack: () -> Unit) {
  val navigator =
    rememberNavigator(
      start = HelloGraph.ContactListScreenRoute,
      topology = gezginTopology,
      json = gezginJson,
      restoreKey = "hello",
      onRootBack = onRootBack,
    )
  MaterialTheme {
    GezginDisplay(navigator = navigator) {
      ContractGraphEntries()
      // OnboardingGraphEntries()
      // LoginGraphEntries()
      // …
    }
  }
}
