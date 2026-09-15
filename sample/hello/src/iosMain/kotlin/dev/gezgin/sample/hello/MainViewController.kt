package dev.gezgin.sample.hello

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The entry point the Xcode project instantiates. Root back is a no-op because iOS forbids an
 * application from terminating itself, so the edge-swipe simply stops at the start destination.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { HelloApp(onRootBack = {}) }
