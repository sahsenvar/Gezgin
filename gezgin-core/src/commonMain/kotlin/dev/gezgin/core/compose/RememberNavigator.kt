package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.SavedState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Stable namespace retained for callers using the original overload. */
internal const val LEGACY_REMEMBER_NAVIGATOR_RESTORE_KEY: String =
  "dev.gezgin.core.rememberNavigator.legacy"

private const val NAVIGATOR_PAYLOAD_PREFIX: String = "gezgin-navigator-v1:"

/** Internal saveable envelope carrying the exact namespace alongside an otherwise opaque value. */
internal data class NamespacedNavigatorPayload(val restoreKey: String, val value: String)

internal fun encodeNamespacedNavigatorPayload(restoreKey: String, value: String): String =
  buildString {
    append(NAVIGATOR_PAYLOAD_PREFIX)
    append(restoreKey.length)
    append(':')
    append(restoreKey)
    append(value)
  }

internal fun decodeNamespacedNavigatorPayloadOrNull(
  encoded: String,
  expectedRestoreKey: String,
): String? {
  if (!encoded.startsWith(NAVIGATOR_PAYLOAD_PREFIX)) return null
  val lengthStart = NAVIGATOR_PAYLOAD_PREFIX.length
  val lengthEnd = encoded.indexOf(':', startIndex = lengthStart)
  if (lengthEnd < 0) return null
  val restoreKeyLength = encoded.substring(lengthStart, lengthEnd).toIntOrNull() ?: return null
  if (restoreKeyLength < 0) return null
  val restoreKeyStart = lengthEnd + 1
  if (restoreKeyLength > encoded.length - restoreKeyStart) return null
  val valueStart = restoreKeyStart + restoreKeyLength
  val payload =
    NamespacedNavigatorPayload(
      restoreKey = encoded.substring(restoreKeyStart, valueStart),
      value = encoded.substring(valueStart),
    )
  return payload.value.takeIf { payload.restoreKey == expectedRestoreKey }
}

/**
 * Validates the caller-provided restoration namespace. The value itself is intentionally preserved
 * so apps can use a stable, meaningful session-generation and mode key across process recreation.
 */
internal fun restoreNamespace(restoreKey: String): String {
  require(restoreKey.isNotBlank()) { "rememberNavigator: restoreKey must not be blank." }
  return restoreKey
}

/**
 * Sets up `RawNavigator` in a platform-appropriate, IDENTITY-STABLE holder — the PD (process death)
 * simulation: the saved type is `String` (json-encoded [SavedState], [navigatorSaver]/
 * [decodeSavedStateOrNull]).
 *
 * **Stable RawNavigator across configuration changes:** instance acquisition is delegated to the
 * [rememberRawNavigatorInstance] expect/actual. The Android actual keeps the navigator in a holder
 * scoped to the host `ViewModelStoreOwner` (Activity) → a rotation (Activity recreation) preserves
 * the SAME instance; the navigator reference captured in the VM ctor keeps driving the state the
 * display observes after rotation too. On process death the holder dies too → a fresh instance is
 * set up and the serialized snapshot in `rememberSaveable` is adopted ONCE via
 * [RawNavigator.adoptRestored]. The desktop actual uses `rememberSaveable(navigatorSaver)` (on CMP
 * desktop there is NO config-change → the identity is already stable for the lifetime of the
 * composition; desktop has no Android-style configuration change).
 *
 * **Setup guard:** `start`'s flow-chain may NOT contain a member with `isResultFlow == true`
 * because a ResultFlow member cannot be opened alone (without a pending caller) as the root/first
 * entry. The modal-kind guard is NOT here — the kind info lives in the entry-scope (registry), so
 * it is applied inside [GezginDisplay] (AFTER the register lookup).
 *
 * **Current callback:** `stableOnRootBack` is set up only on the FIRST composition (the `remember`
 * init-lambda) — if the caller's `onRootBack` is a new lambda instance that closes over some state
 * (e.g. `{ someState.value }`), a STABLE (set-up-once) wrapper lambda is handed to the holder, but
 * that wrapper calls the MOST RECENT `onRootBack` on EVERY invocation (`rememberUpdatedState`).
 */
@Composable
public fun rememberNavigator(
  start: Route,
  topology: GezginTopology,
  json: Json,
  onRootBack: () -> Unit = platformDefaultRootBack(),
): RawNavigator =
  rememberNavigator(
    start = start,
    topology = topology,
    json = json,
    restoreKey = LEGACY_REMEMBER_NAVIGATOR_RESTORE_KEY,
    onRootBack = onRootBack,
  )

/**
 * Sets up a navigator in the caller's [restoreKey] namespace. The same key restores its saved
 * navigation stack; a different key creates an independent holder and saved snapshot.
 */
@Composable
public fun rememberNavigator(
  start: Route,
  topology: GezginTopology,
  json: Json,
  restoreKey: String,
  onRootBack: () -> Unit = platformDefaultRootBack(),
): RawNavigator {
  val namespace = restoreNamespace(restoreKey)
  require(topology.flowChain(start::class).none { it.isResultFlow }) {
    "rememberNavigator: start cannot be a ResultFlow member; there is no pending caller (§8.1/§12). " +
      "route: ${start::class.simpleName}"
  }
  val latestOnRootBack by rememberUpdatedState(onRootBack)
  val stableOnRootBack = remember(namespace) { { latestOnRootBack() } }
  return rememberRawNavigatorInstance(start, topology, json, namespace, stableOnRootBack)
}

/**
 * [RawNavigator] instance acquisition, with platform-specific identity stability. The Android
 * actual wraps it in a holder scoped to the host ViewModel (the SAME instance is preserved across
 * config-change) + adopts the `rememberSaveable` PD snapshot via [RawNavigator.adoptRestored]. The
 * desktop actual uses `rememberSaveable` ([navigatorSaver]) (no config-change → identity is already
 * stable). For details see the [rememberNavigator] KDoc.
 */
@Composable
internal expect fun rememberRawNavigatorInstance(
  start: Route,
  topology: GezginTopology,
  json: Json,
  restoreKey: String,
  onRootBack: () -> Unit,
): RawNavigator

/**
 * The [RawNavigator] <-> `String` `Saver` — under [navigatorSaver] it delegates to
 * [encodeNavigatorState]/ [decodeNavigatorState] (a SINGLE `json` source for encode/decode
 * symmetry, see the `RawNavigator` KDoc). NOT `@Composable` — deliberate: it can be pinned directly
 * by a unit test without a Compose runtime setup. The actual encode/decode logic is deliberately
 * lifted OUT of the `Saver`'s `SaverScope`-receiver `save` member into plain functions — so tests
 * never call Compose's extension-member `Saver.save` (calling it over foreign (androidx) binary
 * metadata on Kotlin/JVM proved fragile).
 */
internal fun navigatorSaver(
  start: Route,
  topology: GezginTopology,
  json: Json,
  onRootBack: () -> Unit,
): Saver<RawNavigator, String> =
  navigatorSaver(
    start = start,
    topology = topology,
    json = json,
    restoreKey = LEGACY_REMEMBER_NAVIGATOR_RESTORE_KEY,
    onRootBack = onRootBack,
  )

internal fun navigatorSaver(
  start: Route,
  topology: GezginTopology,
  json: Json,
  restoreKey: String,
  onRootBack: () -> Unit,
): Saver<RawNavigator, String> =
  Saver(
    save = { nav ->
      encodeNamespacedNavigatorPayload(
        restoreKey = restoreKey,
        value = encodeNavigatorState(nav, json),
      )
    },
    restore = { encoded ->
      decodeNamespacedNavigatorPayloadOrNull(encoded, restoreKey)?.let { snapshot ->
        decodeNavigatorStateOrNull(snapshot, start, topology, json, onRootBack)
      }
    },
  )

/**
 * `nav.save(): SavedState` → json-encoded `String` (the encode half, see the [navigatorSaver]
 * KDoc).
 */
internal fun encodeNavigatorState(nav: RawNavigator, json: Json): String =
  json.encodeToString(SavedState.serializer(), nav.save())

/**
 * Json-encoded `String` → a new `RawNavigator` (via `restored=`, the decode half, see the
 * [navigatorSaver] KDoc).
 */
internal fun decodeNavigatorState(
  encoded: String,
  start: Route,
  topology: GezginTopology,
  json: Json,
  onRootBack: () -> Unit,
): RawNavigator {
  val restored = json.decodeFromString(SavedState.serializer(), encoded)
  return RawNavigator(
    start = start,
    topology = topology,
    onRootBack = onRootBack,
    json = json,
    restored = restored,
  )
}

/**
 * Process-death restore fault tolerance — the wrapper that [navigatorSaver]'s `restore` ACTUALLY
 * calls. The saved `String` (a corrupted/schema-incompatible PD state left over from an old app
 * version, e.g. if a field name/serializer changed after a migration) MAY make
 * [decodeNavigatorState] throw — either `SerializationException` (malformed/schema-incompatible
 * json) or `IllegalArgumentException` (kotlinx.serialization is known to wrap some decode errors in
 * this type, e.g. polymorphic/enum resolution). The Compose `Saver` contract allows `restore` to
 * return `null` — on `null` Compose falls to `rememberSaveable`'s init-lambda (i.e. a fresh setup
 * from `start`); so both exceptions CAUGHT here are mapped to null (a silent fresh-start instead of
 * a crash-loop).
 *
 * **Logging note:** there is NO logging infrastructure at this layer (neither a `Logger` interface
 * nor a platform hook) — it is swallowed silently. In a real app it is RECOMMENDED to wire this
 * silence to a telemetry/crash-reporting hook (the user may not notice the silent data loss); that
 * infrastructure is not part of this low-level API, so a `println` is deliberately not used because
 * it would create production log noise.
 */
internal fun decodeNavigatorStateOrNull(
  encoded: String,
  start: Route,
  topology: GezginTopology,
  json: Json,
  onRootBack: () -> Unit,
): RawNavigator? =
  try {
    decodeNavigatorState(encoded, start, topology, json, onRootBack).takeIf {
      it.keys.isNotEmpty()
    } // A schema-valid empty stack falls back to fresh start; composition requires a first key.
  } catch (e: SerializationException) {
    null
  } catch (e: IllegalArgumentException) {
    null
  }

/**
 * (the Android PD-adopt path) — decodes the PD snapshot `String` directly into [SavedState]
 * (WITHOUT building a navigator; fed to [RawNavigator.adoptRestored]). The SAME fault-tolerance as
 * [decodeNavigatorStateOrNull]: if corrupted/schema-incompatible json (an old app version) throws
 * `SerializationException`/`IllegalArgumentException` → `null` (NO adopt, the navigator stays at
 * `start`, no crash-loop); a schema-valid but EMPTY stack → `null` too (prevents the `keys.first()`
 * blow-up in composition).
 */
internal fun decodeSavedStateOrNull(encoded: String, json: Json): SavedState? =
  try {
    json.decodeFromString(SavedState.serializer(), encoded).takeIf { it.keys.isNotEmpty() }
  } catch (e: SerializationException) {
    null
  } catch (e: IllegalArgumentException) {
    null
  }
