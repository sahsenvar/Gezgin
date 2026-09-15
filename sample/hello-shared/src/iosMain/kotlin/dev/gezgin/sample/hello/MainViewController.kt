package dev.gezgin.sample.hello

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The entry point the Xcode project instantiates. Root back is a no-op because iOS forbids an
 * application from terminating itself, so the edge-swipe simply stops at the start destination.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
  // TEMPORARY PROBE — remove once the edge-swipe question is settled. The e2e suite proved the
  // harness can drive an iOS edge swipe (Apple's own Settings navigates back with the same
  // gesture) while this app does not move at all. That leaves one unknown: whether a back event
  // reaches Compose here and NavDisplay ignores it, or whether none arrives. This handler makes
  // the answer visible on screen, which the failing flow already screenshots.
  var backEvents by remember { mutableStateOf(0) }
  BackHandler(enabled = true) { backEvents++ }
  Column {
    if (backEvents > 0) Text("PROBE-BACK-EVENT-ARRIVED-$backEvents")
    HelloApp(onRootBack = {})
  }
}
