package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import kotlinx.serialization.json.Json

/**
 * Keeps the desktop navigator stable for the composition lifetime and restores its state through
 * [navigatorSaver]. Desktop has no Activity configuration-change boundary, so it does not need the
 * Android ViewModel holder and one-time adoption mechanism.
 */
@Composable
internal actual fun rememberRawNavigatorInstance(
  start: Route,
  topology: GezginTopology,
  json: Json,
  restoreKey: String,
  onRootBack: () -> Unit,
): RawNavigator =
  key(restoreKey) {
    val saver =
      remember(restoreKey) {
        navigatorSaver(
          start = start,
          topology = topology,
          json = json,
          restoreKey = restoreKey,
          onRootBack = onRootBack,
        )
      }
    rememberSaveable(restoreKey, saver = saver) {
      RawNavigator(start = start, topology = topology, onRootBack = onRootBack, json = json)
    }
  }
