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
 * Desktop (JVM) actual — davranışının AYNISI: `rememberSaveable(navigatorSaver)`. CMP desktop
 * host'unda Activity/config-change YOK; composition pencere ömrü boyunca yaşar → `rememberSaveable`
 * kimliği zaten stabil tutar (VM ctor'unda yakalanan navigator referansı ölmez) ve
 * state-restoration bu Saver üzerinden çalışır. Android'in ViewModel-scope'lu holder + PD-adopt
 * mekanizmasına (yalnız rotasyonun instance'ı öldürdüğü platformda) burada gerek yok.
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
