package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.gezgin.core.Route

/** Metadata key for Gezgin's entry-pinned dialog scene on both platforms. */
internal const val GEZGIN_DIALOG_KEY = "gezginDialog"

/** Dialog overlay whose dismissal is pinned to its owning entry through [onBack]. */
internal class GezginDialogScene(
  override val key: Any,
  private val entry: NavEntry<Route>,
  override val overlaidEntries: List<NavEntry<Route>>,
  private val dialogProperties: DialogProperties,
  private val onBack: () -> Unit,
) : OverlayScene<Route> {

  init {
    // Defend the overlay invariant even when this scene is constructed outside the normal adapter.
    require(overlaidEntries.isNotEmpty()) {
      "GezginDialogScene: overlaidEntries cannot be empty; a Dialog overlay must have at least one " +
        "SCREEN entry underneath it (Nav3 OverlayScene invariant, §7). key: $key"
    }
  }

  override val entries: List<NavEntry<Route>> = listOf(entry)
  override val previousEntries: List<NavEntry<Route>> = overlaidEntries

  override val content: @Composable () -> Unit = {
    Dialog(onDismissRequest = onBack, properties = dialogProperties) { entry.Content() }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false
    other as GezginDialogScene
    return key == other.key &&
      entry == other.entry &&
      overlaidEntries == other.overlaidEntries &&
      dialogProperties == other.dialogProperties
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + entry.hashCode()
    result = 31 * result + overlaidEntries.hashCode()
    result = 31 * result + dialogProperties.hashCode()
    return result
  }

  override fun toString(): String =
    "GezginDialogScene(key=$key, entry=$entry, overlaidEntries=$overlaidEntries, dialogProperties=$dialogProperties)"
}

/** Selects a [GezginDialogScene] from top-entry metadata and pins its dismissal to that entry. */
internal class GezginDialogSceneStrategy(private val onDismiss: (Long) -> Unit) :
  SceneStrategy<Route> {
  override fun SceneStrategyScope<Route>.calculateScene(
    entries: List<NavEntry<Route>>
  ): Scene<Route>? {
    val lastEntry = entries.lastOrNull()
    val props = lastEntry?.metadata?.get(GEZGIN_DIALOG_KEY) as? DialogProperties
    return props?.let {
      val entryId = lastEntry.contentKey as Long
      GezginDialogScene(
        key = lastEntry.contentKey,
        entry = lastEntry,
        overlaidEntries = entries.dropLast(1),
        dialogProperties = it,
        onBack = { onDismiss(entryId) }, // Dismiss the dialog entry that owns this callback.
      )
    }
  }
}
