@file:OptIn(ExperimentalMaterial3Api::class)

package dev.gezgin.core.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import dev.gezgin.core.Route

/**
 * `@BottomSheet` content'ine Gezgin'in enjekte ettiği [SheetState] (§7) — **register imzası
 * DEĞİŞMEDEN** sheet durumunu content'e taşıyan rol-param'ı. [LocalGezginEntryId]/
 * [LocalGezginRawNavigator] deseniyle simetrik: [GezginBottomSheetScene] `ModalBottomSheet`'i kurarken
 * `sheetState`'i bu Local ile sarar; `@BottomSheet` content'i `LocalGezginSheetState.current` ile okur.
 *
 * **DİKKAT — ÇIPLAK `hide()` state-desync üretir (m1):** bir buton'dan sheet'i kapatmak için
 * `scope.launch { sheetState.hide() }` TEK BAŞINA YETMEZ — `hide()` yalnız sheet'i GÖRSEL olarak
 * Hidden'a animasyonlar; `onDismissRequest`'i TETİKLEMEZ, dolayısıyla entry stack'in top'unda KALIR
 * (geri tuşu artık "görünmez bir sheet"i kapatır). Doğru desen: gizle, SONRA navigator'la pop et —
 * `scope.launch { sheetState.hide(); nav.back() }` (ya da `ResultRoute` sheet'inde
 * `sheetState.hide(); nav.backWithResult(value)`). Bu, `@NoBack`×sheet yasağının [EntryAdapter]'daki
 * gerekçesiyle (aynı desync) simetriktir — kütüphane kendi KDoc'unda o tuzağı önermemeli.
 *
 * **sheetState enjeksiyon kararı = CompositionLocal (register imzası sabit):** alternatif (register'a
 * `@BottomSheet` özel overload) imzayı çoğaltırdı. Local ile `register<R> { ... }` tek imza kalır; Faz 3.4
 * codegen'i İLERİDE BOTTOM_SHEET content'inin `sheetState` param'ını bu Local'dan besleyecek şekilde
 * genişletilebilir (bu görevde core-mode register + Local yeterli — 4.4/gelecek notu).
 *
 * `staticCompositionLocalOf` — değer sheet-entry başına stabil (entry recompose olduğunda `SheetState`
 * instance'ı `remember`'lı kalır); dynamic read-tracking maliyeti gereksiz.
 */
val LocalGezginSheetState = staticCompositionLocalOf<SheetState> {
    error("LocalGezginSheetState can only be read inside @BottomSheet content installed by GezginDisplay.")
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
)

/**
 * El-yazımı BottomSheet [OverlayScene] (§7) — Nav3'te hazır `BottomSheetSceneStrategy` YOK (4.0 raporu §3)
 * → material3 `ModalBottomSheet` ile [androidx.navigation3.scene.DialogScene] şablonundan yazıldı.
 * `overlaidEntries` = alttaki entry'ler (arka SCREEN görünür kalır); `content` sheet'i overlay olarak
 * çizer ve `entry.Content()`'i [LocalGezginSheetState] ile sararak sheetState'i content'e enjekte eder.
 *
 * **swipe-dismiss→Canceled:** `onDismissRequest = onBack` (= `SceneStrategyScope.onBack` =
 * `NavDisplay.onBack` = Gezgin [gezginOnBack]). material3'te swipe-down / scrim-tap / geri-tuşu ÜÇÜ de
 * tek `onDismissRequest`'e düşer (jar-doğrulandı: `settleToDismiss`/`Scrim`/predictive-back hepsi bu
 * callback'i çağırır) → dismiss = `navigator.back()` = `ResultRoute` sheet için `Canceled` (dialog'daki
 * 4.1 mekanizmasının aynısı, ek kod gerekmez).
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
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = sheetState,
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = props.dismissOnBackPress,
                shouldDismissOnClickOutside = props.dismissOnClickOutside,
            ),
        ) {
            CompositionLocalProvider(LocalGezginSheetState provides sheetState) {
                entry.Content()
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
 * BottomSheet [SceneStrategy] (§7) — top entry'nin `NavEntry.metadata`'sında [GEZGIN_BOTTOM_SHEET_KEY]
 * (adapter [toNavEntry] `kind == BOTTOM_SHEET` iken yazar) varsa bir [GezginBottomSheetScene] döndürür,
 * yoksa `null` (zincirdeki sonraki stratejiye devret). `DialogSceneStrategy` deseni; `overlaidEntries =
 * entries.dropLast(1)` (arka SCREEN görünür). [GezginNavDisplay] actual'larında DialogSceneStrategy'nin
 * YANINA (fallback `SinglePaneSceneStrategy`'den ÖNCE) eklenir.
 */
internal class GezginBottomSheetSceneStrategy : SceneStrategy<Route> {
    override fun SceneStrategyScope<Route>.calculateScene(entries: List<NavEntry<Route>>): Scene<Route>? {
        val lastEntry = entries.lastOrNull()
        val props = lastEntry?.metadata?.get(GEZGIN_BOTTOM_SHEET_KEY) as? GezginBottomSheetProps
        return props?.let {
            GezginBottomSheetScene(
                key = lastEntry.contentKey,
                entry = lastEntry,
                overlaidEntries = entries.dropLast(1),
                props = it,
                onBack = onBack,
            )
        }
    }
}
