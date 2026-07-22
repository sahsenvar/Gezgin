package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Keeps one identity-stable [RawNavigator] in an Activity-scoped [NavigatorHolder]. Configuration
 * changes retain the same facade, so ViewModels keep observing the same state flows. After process
 * death, `rememberSaveable` restores a serialized snapshot into a fresh holder exactly once.
 * Corrupt or schema-incompatible snapshots fall back to [start] without partially mutating the
 * navigator.
 *
 * A saveable call-site token forms part of the holder key. Independent navigator calls in the same
 * owner therefore receive distinct holders, while configuration and process restoration recover the
 * correct one for each call site.
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
    // The token remains call-site-specific so two navigators in one owner can share a business
    // namespace without sharing a holder. Both the Compose group and input invalidate it when
    // `restoreKey` changes.
    val callSiteToken =
      rememberSaveable(
        restoreKey,
        saver =
          Saver<String, String>(
            save = { token -> encodeNamespacedNavigatorPayload(restoreKey, token) },
            restore = { encoded -> decodeNamespacedNavigatorPayloadOrNull(encoded, restoreKey) },
          ),
      ) {
        UUID.randomUUID().toString()
      }
    val holderKey = "dev.gezgin.core.NavigatorHolder#$restoreKey#$callSiteToken"
    val holder =
      viewModel<NavigatorHolder>(
        key = holderKey,
        factory =
          viewModelFactory {
            initializer {
              NavigatorHolder(
                RawNavigator(
                  start = start,
                  topology = topology,
                  onRootBack = onRootBack,
                  json = json,
                )
              )
            }
          },
      )
    val navigator = holder.navigator
    val pdSnapshot =
      rememberSaveable(
        restoreKey,
        saver =
          Saver<String, String>(
            save = {
              encodeNamespacedNavigatorPayload(
                restoreKey = restoreKey,
                value = encodeNavigatorState(navigator, json),
              )
            }, // Always encode the live navigator rather than the previous saved value.
            restore = { encoded -> decodeNamespacedNavigatorPayloadOrNull(encoded, restoreKey) },
          ),
      ) {
        ""
      }
    if (!holder.adoptChecked) {
      holder.adoptChecked = true
      if (pdSnapshot.isNotEmpty()) {
        // Structural decoding leaves slot payloads opaque. Decode them during atomic adoption and
        // fall back to start if an edge was removed, renamed, or changed incompatibly.
        decodeSavedStateOrNull(pdSnapshot, json)?.let { restored ->
          try {
            navigator.adoptRestored(restored)
          } catch (e: SerializationException) {
            // Preserve the fresh start when a payload no longer matches its serializer schema.
          } catch (e: IllegalArgumentException) {
            // Preserve the fresh start when the saved edge or result serializer no longer exists.
          }
        }
      }
    }
    navigator
  }

/** Activity-scoped holder that adopts a saved snapshot at most once during its lifetime. */
private class NavigatorHolder(val navigator: RawNavigator) : ViewModel() {
  var adoptChecked: Boolean = false
}
