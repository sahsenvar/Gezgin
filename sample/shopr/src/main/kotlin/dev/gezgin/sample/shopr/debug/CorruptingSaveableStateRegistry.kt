package dev.gezgin.sample.shopr.debug

import android.util.Log
import androidx.compose.runtime.saveable.SaveableStateRegistry

/**
 * DEBUG-only PD-corruption harness (maestro madde-7). On restore it corrupts the Gezgin process-death
 * navigator snapshot so the library's `decodeSavedStateOrNull(...) == null` fresh-fallback path is exercised
 * on a real device WITHOUT hacking library source + rebuilding. Enabled only when the shopr Activity is
 * launched with the `corrupt_state` intent extra (see [dev.gezgin.sample.shopr.MainActivity]); otherwise this
 * class is never installed and there is zero behavior change.
 *
 * Every non-Gezgin saveable passes through untouched: the predicate matches ONLY the snapshot JSON
 * (`{"keys":...}` — `SavedState` serializes `keys` first, see gezgin-core `SavedState`/`encodeNavigatorState`),
 * never the holder-key token (`dev.gezgin.core.NavigatorHolder#<uuid>`, a plain non-JSON string that MUST
 * survive intact — corrupting it would break the ViewModel holder key, not the snapshot).
 */
internal class CorruptingSaveableStateRegistry(
    private val delegate: SaveableStateRegistry,
) : SaveableStateRegistry by delegate {

    override fun consumeRestored(key: String): Any? = corruptGezginSnapshot(delegate.consumeRestored(key))

    // rememberSaveable hands back a single value, but a saver MAY wrap it in a List<Any?> → recurse.
    private fun corruptGezginSnapshot(value: Any?): Any? = when (value) {
        is String -> if (isGezginSnapshot(value)) corrupt(value) else value
        is List<*> -> value.map { corruptGezginSnapshot(it) }
        else -> value
    }

    private fun isGezginSnapshot(s: String): Boolean = s.startsWith("{") && s.contains("\"keys\"")

    private fun corrupt(original: String): String {
        Log.i(TAG, "corrupting Gezgin PD snapshot (${original.length} chars) -> fresh-fallback")
        return CORRUPT
    }

    private companion object {
        const val TAG = "ShoprCorrupt"
        const val CORRUPT = "{corrupted"
    }
}
