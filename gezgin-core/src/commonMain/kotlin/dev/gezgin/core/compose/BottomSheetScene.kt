@file:OptIn(ExperimentalMaterial3Api::class)

package dev.gezgin.core.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.gezgin.core.Route
import dev.gezgin.core.BottomSheetDragHandleMode

/**
 * Gezgin-owned handle a `@BottomSheet` content uses to dismiss its sheet (§7) — read from
 * [LocalGezginSheetController]. It replaces the raw material3 `SheetState` on the public surface, so no
 * experimental third-party type is locked into Gezgin's ABI; the material `SheetState` stays an internal
 * implementation detail of [GezginBottomSheetScene].
 *
 * A bare visual [hide] on its own leaves the entry on the stack (the back button would then dismiss an
 * invisible sheet) — pair it with a navigator pop, or use [hideAndBack]. For a `ResultRoute` sheet,
 * deliver the result via the typed navigator's `backWithResult(value)` right after [hide].
 */
public interface GezginSheetController {
    /** Animate the sheet to fully hidden WITHOUT popping the entry. Pair with a navigator pop. */
    public suspend fun hide()

    /** Animate the sheet hidden, then pop this sheet entry via the navigator (Canceled for a ResultRoute sheet). */
    public suspend fun hideAndBack()
}

/**
 * The [GezginSheetController] Gezgin injects into `@BottomSheet` content. Symmetric to
 * [LocalGezginEntryId]/[LocalGezginRawNavigator]: [GezginBottomSheetScene] provides it around
 * `entry.Content()`. Gated behind [dev.gezgin.core.GezginInternalApi] is unnecessary here — the controller
 * is Gezgin-owned and safe to read; only reading it OUTSIDE a `@BottomSheet` content is an error.
 *
 * `staticCompositionLocalOf` — the value is stable per sheet entry (the controller is `remember`ed), so the
 * read-tracking cost of a dynamic local is unnecessary.
 */
public val LocalGezginSheetController: ProvidableCompositionLocal<GezginSheetController> =
    staticCompositionLocalOf {
        error("LocalGezginSheetController can only be read inside @BottomSheet content installed by GezginDisplay.")
    }

/** [GezginSheetController] backed by the material3 [SheetState] + the scene's back callback (internal impl). */
private class MaterialSheetController(
    private val sheetState: SheetState,
    private val onBack: () -> Unit,
) : GezginSheetController {
    override suspend fun hide() {
        sheetState.hide()
    }

    override suspend fun hideAndBack() {
        sheetState.hide()
        onBack()
    }
}

/** [GezginBottomSheetScene]'in `NavEntry.metadata`'da taşındığı anahtar (Gezgin-tanımlı, iki platformda
 *  AYNI string — kendi strateji'miz kendi key'ini yazıp okur, platform-içi tutarlı; DialogSceneStrategy'nin
 *  `"dialog"` key'inden ayrık, transition key'lerinden de ayrık). */
internal const val GEZGIN_BOTTOM_SHEET_KEY = "gezginBottomSheet"

/**
 * `@BottomSheet` route'unun opsiyonel [dev.gezgin.core.BottomSheetContract]'ından (runtime değer, §2.4)
 * çözülen sheet property'leri. `NavEntry.metadata`'ya [GEZGIN_BOTTOM_SHEET_KEY] altında konur; strateji
 * bunu okuyup [GezginBottomSheetScene]'i kurar. `data class` → scene eşitliği + adapter-level pin için
 * bedava `equals`/`hashCode`.
 */
internal data class GezginBottomSheetProps(
    val skipPartiallyExpanded: Boolean,
    val dismissOnBackPress: Boolean,
    val dismissOnClickOutside: Boolean,
    val sheetGesturesEnabled: Boolean,
    val dragHandleMode: BottomSheetDragHandleMode,
)

/**
 * El-yazımı BottomSheet [OverlayScene] (§7) — Nav3'te hazır `BottomSheetSceneStrategy` YOK (4.0 raporu §3)
 * → material3 `ModalBottomSheet` ile [androidx.navigation3.scene.DialogScene] şablonundan yazıldı.
 * `overlaidEntries` = alttaki entry'ler (arka SCREEN görünür kalır); `content` sheet'i overlay olarak
 * çizer ve `entry.Content()`'i [LocalGezginSheetController] ile sararak controller'ı content'e enjekte eder.
 *
 * **swipe-dismiss→Canceled + entry-pin (C-MJ-1):** `onDismissRequest = onBack` — burada [onBack]
 * strateji tarafından sahip-entry'ye PİNLENMİŞ `{ navigator.back(entryId) }` ile beslenir (tekil
 * `NavDisplay.onBack` DEĞİL). material3'te swipe-down / scrim-tap / geri-tuşu ÜÇÜ de tek
 * `onDismissRequest`'e düşer (jar-doğrulandı: `settleToDismiss`/`Scrim`/predictive-back hepsi bu
 * callback'i çağırır) → dismiss = `navigator.back(entryId)` = sheet HÂLÂ top ise pop (`ResultRoute` için
 * `Canceled`), artık top DEĞİLSE (çifte-dismiss / geç async) NO-OP → alttaki ekran poplanmaz.
 *
 * Nav3 `Scene` sözleşmesi eşitlik ister (aynı backstack için aynı scene instance'ı kullanılsın diye) →
 * [DialogScene] gibi `equals`/`hashCode` elle implement edildi.
 *
 * **Kalıntı risk — programatik pop animasyonsuz (4.4/gelecek):** kullanıcı jest'leriyle (swipe/scrim/back)
 * kapatmada material3 `ModalBottomSheet` hide-animation'ı `onDismissRequest`'ten ÖNCE oynar (görsel
 * slide-down). Ama PROGRAMATİK `navigator.back()` doğrudan entry'yi backstack'ten düşürür → scene
 * SinglePane'e recompose olur, sheet animasyonsuz kaybolur. İleride `OverlayScene.onRemove()` override'ı
 * programatik pop'a da slide-down ekleyebilir (bu görev kapsamı dışı — 4.4/gelecek notu).
 */
internal class GezginBottomSheetScene(
    override val key: Any,
    private val entry: NavEntry<Route>,
    override val overlaidEntries: List<NavEntry<Route>>,
    private val props: GezginBottomSheetProps,
    private val onBack: () -> Unit,
) : OverlayScene<Route> {

    init {
        // DialogScene paritesi (§7) — bir overlay scene altında en az bir underlaid entry ŞART. toNavEntry'nin
        // modal-kind-at-root guard'ı bunu zaten önceler; bu scene-invariant kendi başına net olsun diye defansif.
        require(overlaidEntries.isNotEmpty()) {
            "GezginBottomSheetScene: overlaidEntries cannot be empty; a BottomSheet overlay must have at " +
                "least one SCREEN entry underneath it (Nav3 OverlayScene invariant, §7). key: $key"
        }
    }

    override val entries: List<NavEntry<Route>> = listOf(entry)
    override val previousEntries: List<NavEntry<Route>> = overlaidEntries

    override val content: @Composable () -> Unit = {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = props.skipPartiallyExpanded)
        val controller = remember(sheetState) { MaterialSheetController(sheetState, onBack) }
        val properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = props.dismissOnBackPress,
            shouldDismissOnClickOutside = props.dismissOnClickOutside,
        )
        when (props.dragHandleMode) {
            BottomSheetDragHandleMode.Default -> ModalBottomSheet(
                onDismissRequest = onBack,
                sheetState = sheetState,
                sheetGesturesEnabled = props.sheetGesturesEnabled,
                properties = properties,
            ) {
                CompositionLocalProvider(LocalGezginSheetController provides controller) {
                    entry.Content()
                }
            }
            BottomSheetDragHandleMode.None -> ModalBottomSheet(
                onDismissRequest = onBack,
                sheetState = sheetState,
                sheetGesturesEnabled = props.sheetGesturesEnabled,
                dragHandle = null,
                properties = properties,
            ) {
                CompositionLocalProvider(LocalGezginSheetController provides controller) {
                    entry.Content()
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as GezginBottomSheetScene
        return key == other.key &&
            entry == other.entry &&
            overlaidEntries == other.overlaidEntries &&
            props == other.props
    }

    override fun hashCode(): Int {
        // m6 — standart zincirleme (alan-sırası-duyarlı): toplam yerine 31*(…)+… — `equals`'ın karşılaştırdığı
        // dört alanla birebir aynı sıra/set (data-class'ın üreteceğiyle eşdeğer hash kalitesi).
        var result = key.hashCode()
        result = 31 * result + entry.hashCode()
        result = 31 * result + overlaidEntries.hashCode()
        result = 31 * result + props.hashCode()
        return result
    }

    override fun toString(): String =
        "GezginBottomSheetScene(key=$key, entry=$entry, overlaidEntries=$overlaidEntries, props=$props)"
}

/**
 * BottomSheet [SceneStrategy] (§7, C-MJ-1) — top entry'nin `NavEntry.metadata`'sında [GEZGIN_BOTTOM_SHEET_KEY]
 * (adapter [toNavEntry] `kind == BOTTOM_SHEET` iken yazar) varsa bir [GezginBottomSheetScene] döndürür,
 * yoksa `null` (zincirdeki sonraki stratejiye devret). [GezginDialogSceneStrategy] deseni; `overlaidEntries =
 * entries.dropLast(1)` (arka SCREEN görünür). Dismiss, [onDismiss] ile sahip-entry id'sine
 * (`lastEntry.contentKey`) pinlenir → sheet artık top değilken pop no-op. [GezginNavDisplay] actual'larında
 * GezginDialogSceneStrategy'nin YANINA (fallback `SinglePaneSceneStrategy`'den ÖNCE) eklenir.
 */
internal class GezginBottomSheetSceneStrategy(private val onDismiss: (Long) -> Unit) : SceneStrategy<Route> {
    override fun SceneStrategyScope<Route>.calculateScene(entries: List<NavEntry<Route>>): Scene<Route>? {
        val lastEntry = entries.lastOrNull()
        val props = lastEntry?.metadata?.get(GEZGIN_BOTTOM_SHEET_KEY) as? GezginBottomSheetProps
        return props?.let {
            val entryId = lastEntry.contentKey as Long
            GezginBottomSheetScene(
                key = lastEntry.contentKey,
                entry = lastEntry,
                overlaidEntries = entries.dropLast(1),
                props = it,
                onBack = { onDismiss(entryId) },   // C-MJ-1: dismiss sahip-entry'ye pinli
            )
        }
    }
}
