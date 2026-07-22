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

/**
 * [GezginDialogScene]'in `NavEntry.metadata`'da taşındığı anahtar (Gezgin-tanımlı, iki platformda
 * AYNI string). Nav3'ün built-in `DialogSceneStrategy.DialogKey`'inin YERİNE kullanılır: built-in
 * DialogScene dismiss'i `SceneStrategyScope.onBack` (= tekil `NavDisplay.onBack`) üzerinden
 * çalıştırır ve bu scope'un onBack'i modül-dışından enjekte EDİLEMEZ (internal ctor) → dismiss'i
 * sahip-entry'ye pinleyemez. Bu yüzden Gezgin, [GezginBottomSheetScene] gibi, kendi DialogScene'ini
 * yazar ve dismiss'i entry-pinli `back(entryId)`'e bağlar (). Sheet key'inden ve transition
 * key'lerinden ayrık.
 */
internal const val GEZGIN_DIALOG_KEY = "gezginDialog"

/**
 * Gezgin-sahipli Dialog `OverlayScene` — Nav3'ün built-in `DialogScene`'inin birebir işlevsel
 * kopyası, TEK farkla: `onDismissRequest` sahip-entry'ye pinlenmiş [onBack]'e bağlanır (built-in
 * tekil `NavDisplay.onBack`'e bağlar → çifte-dismiss / geç-dismiss ALTTAKİ ekranı poplardı).
 * [onBack] burada `{ navigator.back(entry.contentKey) }` ile beslenir → modal artık top değilken
 * dismiss NO-OP olur. `overlaidEntries` = alttaki entry'ler (arka SCREEN görünür kalır).
 * `equals`/`hashCode` Nav3 `Scene` sözleşmesi için elle (built-in DialogScene deseni; `onBack`
 * karşılaştırılmaz — lambda kimliği kararsız).
 */
internal class GezginDialogScene(
  override val key: Any,
  private val entry: NavEntry<Route>,
  override val overlaidEntries: List<NavEntry<Route>>,
  private val dialogProperties: DialogProperties,
  private val onBack: () -> Unit,
) : OverlayScene<Route> {

  init {
    // Overlay scene invariant () — toNavEntry'nin modal-kind-at-root guard'ı bunu zaten önceler;
    // scene kendi başına net olsun diye defansif (built-in DialogScene bunu require ETMEZ ama
    // GezginBottomSheetScene ile paritede).
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

/**
 * Dialog `SceneStrategy` — top entry'nin `NavEntry.metadata`'sında [GEZGIN_DIALOG_KEY] (adapter
 * [toNavEntry] `kind == DIALOG`/`FULLSCREEN_MODAL` iken yazar) varsa bir [GezginDialogScene]
 * döndürür, yoksa `null` (zincirdeki sonraki stratejiye devret). Dismiss, [onDismiss] ile
 * sahip-entry id'sine (`lastEntry.contentKey`) pinlenir → modal artık top değilken pop no-op olur.
 * Built-in `DialogSceneStrategy`'nin yerini alır ([GezginNavDisplay] actual'larında).
 */
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
        onBack = { onDismiss(entryId) }, // C-MJ-1: dismiss sahip-entry'ye pinli
      )
    }
  }
}
