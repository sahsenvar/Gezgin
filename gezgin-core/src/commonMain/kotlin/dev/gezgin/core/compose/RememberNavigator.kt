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

/**
 * Sets up `RawNavigator` in a platform-appropriate, IDENTITY-STABLE holder — the PD (process death)
 * simulation of §1.10/§12: the saved type is `String` (json-encoded [SavedState], [navigatorSaver]/
 * [decodeSavedStateOrNull]).
 *
 * **C1 — stable RawNavigator across config-change (spec §225):** instance acquisition is delegated to the
 * [rememberRawNavigatorInstance] expect/actual. The Android actual keeps the navigator in a holder scoped to
 * the host `ViewModelStoreOwner` (Activity) → a rotation (Activity recreation) preserves the SAME instance;
 * the navigator reference captured in the VM ctor keeps driving the state the display observes after rotation
 * too. On process death the holder dies too → a fresh instance is set up and the serialized snapshot in
 * `rememberSaveable` is adopted ONCE via [RawNavigator.adoptRestored]. The desktop actual uses
 * `rememberSaveable(navigatorSaver)` (on CMP desktop there is NO config-change → the identity is already
 * stable for the lifetime of the composition; Faz-3 behavior unchanged).
 *
 * **Setup guard (§12):** `start`'s flow-chain may NOT contain a member with `isResultFlow == true` — a
 * ResultFlow member cannot be opened alone (without a pending caller) as the root/first entry (§8.1). The
 * modal-kind guard is NOT here — the kind info lives in the entry-scope (registry), so it is applied inside
 * [GezginDisplay] (AFTER the register lookup).
 *
 * **Stale-lambda fix (deferred, final-review):** `stableOnRootBack` is set up only on the FIRST composition
 * (the `remember` init-lambda) — if the caller's `onRootBack` is a new lambda instance that closes over some
 * state (e.g. `{ someState.value }`), a STABLE (set-up-once) wrapper lambda is handed to the holder, but that
 * wrapper calls the MOST RECENT `onRootBack` on EVERY invocation ([rememberUpdatedState]).
 */
@Composable
public fun rememberNavigator(
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit = platformDefaultRootBack(),
): RawNavigator {
    require(topology.flowChain(start::class).none { it.isResultFlow }) {
        "rememberNavigator: start bir ResultFlow üyesi olamaz (bekleyen caller yok, §8.1/§12) — " +
            "route: ${start::class.simpleName}"
    }
    val latestOnRootBack by rememberUpdatedState(onRootBack)
    val stableOnRootBack = remember { { latestOnRootBack() } }
    return rememberRawNavigatorInstance(start, topology, json, stableOnRootBack)
}

/**
 * C1 — [RawNavigator] instance acquisition, with platform-specific identity stability. The Android actual
 * wraps it in a holder scoped to the host ViewModel (the SAME instance is preserved across config-change) +
 * adopts the `rememberSaveable` PD snapshot via [RawNavigator.adoptRestored]. The desktop actual uses
 * `rememberSaveable` ([navigatorSaver]) (no config-change → identity is already stable). For details see the
 * [rememberNavigator] KDoc.
 */
@Composable
internal expect fun rememberRawNavigatorInstance(
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): RawNavigator

/**
 * The [RawNavigator] <-> `String` `Saver` — under [navigatorSaver] it delegates to [encodeNavigatorState]/
 * [decodeNavigatorState] (a SINGLE `json` source for encode/decode symmetry, see the `RawNavigator` KDoc).
 * NOT `@Composable` — deliberate: it can be pinned directly by a unit test without a Compose runtime setup
 * (Task 3.2 deliverable e, the fallback without uiTest). The actual encode/decode logic is deliberately
 * lifted OUT of the `Saver`'s `SaverScope`-receiver `save` member into plain functions — so tests never call
 * Compose's extension-member `Saver.save` (calling it over foreign (androidx) binary metadata on Kotlin/JVM
 * proved fragile).
 */
internal fun navigatorSaver(
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): Saver<RawNavigator, String> = Saver(
    save = { nav -> encodeNavigatorState(nav, json) },
    restore = { encoded -> decodeNavigatorStateOrNull(encoded, start, topology, json, onRootBack) },
)

/** `nav.save(): SavedState` → json-encoded `String` (the encode half, see the [navigatorSaver] KDoc). */
internal fun encodeNavigatorState(nav: RawNavigator, json: Json): String =
    json.encodeToString(SavedState.serializer(), nav.save())

/** json-encoded `String` → a new `RawNavigator` (via `restored=`, the decode half, see the [navigatorSaver] KDoc). */
internal fun decodeNavigatorState(
    encoded: String,
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): RawNavigator {
    val restored = json.decodeFromString(SavedState.serializer(), encoded)
    return RawNavigator(start = start, topology = topology, onRootBack = onRootBack, json = json, restored = restored)
}

/**
 * PD-restore fault-tolerance (Important 1, final-review) — the wrapper that [navigatorSaver]'s `restore`
 * ACTUALLY calls. The saved `String` (a corrupted/schema-incompatible PD state left over from an old app
 * version, e.g. if a field name/serializer changed after a migration) MAY make [decodeNavigatorState] throw —
 * either `SerializationException` (malformed/schema-incompatible json) or `IllegalArgumentException`
 * (kotlinx.serialization is known to wrap some decode errors in this type, e.g. polymorphic/enum resolution).
 * The Compose `Saver` contract allows `restore` to return `null` — on `null` Compose falls to
 * [rememberSaveable]'s init-lambda (i.e. a fresh setup from `start`); so both exceptions CAUGHT here are
 * mapped to null (a silent fresh-start instead of a crash-loop).
 *
 * **Logging note:** there is NO logging infrastructure at this layer (neither a `Logger` interface nor a
 * platform hook) — it is swallowed silently. In a real app it is RECOMMENDED to wire this silence to a
 * telemetry/crash-reporting hook (the user may not notice the silent data loss); that infrastructure is out
 * of Faz 3 scope, so a `println` was deliberately NOT added either (it would create production log noise) —
 * see the final-review report, a TODO to track.
 */
internal fun decodeNavigatorStateOrNull(
    encoded: String,
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): RawNavigator? = try {
    decodeNavigatorState(encoded, start, topology, json, onRootBack)
        .takeIf { it.keys.isNotEmpty() }   // şema-geçerli ama BOŞ stack → fresh-start (final re-review Minor 2; boş stack composition'da keys.first() ile patlardı)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

/**
 * C1 (the Android PD-adopt path) — decodes the PD snapshot `String` directly into [SavedState] (WITHOUT
 * building a navigator; fed to [RawNavigator.adoptRestored]). The SAME fault-tolerance as
 * [decodeNavigatorStateOrNull]: if corrupted/schema-incompatible json (an old app version) throws
 * `SerializationException`/`IllegalArgumentException` → `null` (NO adopt, the navigator stays at `start`, no
 * crash-loop); a schema-valid but EMPTY stack → `null` too (prevents the `keys.first()` blow-up in composition).
 */
internal fun decodeSavedStateOrNull(encoded: String, json: Json): SavedState? = try {
    json.decodeFromString(SavedState.serializer(), encoded).takeIf { it.keys.isNotEmpty() }
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
