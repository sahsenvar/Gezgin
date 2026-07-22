package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import dev.gezgin.core.Route

/**
 * Platform'a bağlı `NavEntryDecorator` alt-kümesi. [GezginDisplay] ORTAK
 * `rememberSaveableStateHolderNavEntryDecorator()`'i her iki platformda commonMain'de ekler
 * (saveable state = zorunlu, non-optional; `LocalViewModelStoreOwner` gerektirmez, desktop dahil
 * çalışır) ve BUNA bu expect'in döndürdüğü platform-özel decorator'ları EKLER.
 *
 * **Platform ayrımı (bilinçli, kısayol değil):** `rememberViewModelStoreNavEntryDecorator()`
 * (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3`) commonMain'de tanımlı AMA
 * çağrı anında `checkNotNull(LocalViewModelStoreOwner.current)` yapar. Android'de host
 * `ComponentActivity` bunu sağlar → gerçek per-entry `ViewModelStore` (eşit-değerli iki route ayrı
 * VM store alır, nin VM tarafı). CMP desktop host'unda `LocalViewModelStoreOwner` garanti DEĞİL
 * (özellikle `runComposeUiTest`/`setContent`) → çağrı `IllegalStateException` atardı. Bu yüzden
 * desktop actual'ı **boş liste** (no-op) döndürür; saveable-state-holder decorator'ı desktop'ta nin
 * saved-state tarafını zaten karşılar (bkz. GezginDisplayR2Test).
 */
@Composable internal expect fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>>

/**
 * `@NoBack` entry-scoped Gezgin-sahipli geri-handler'ı (′). [toNavEntry] `noBack==true` (ve kök
 * DEĞİL) entry'lerde bunu ekran içeriğinden ÖNCE (OUTER) çağırır → dispatcher LIFO'sunda ekranın
 * kendi `BackHandler`'ı (varsa, sonra/İÇ kaydolur) kazanır; yoksa Gezgin'inki back'i yutar.
 *
 * **Platform ayrımı:** Android actual'ı gerçek `androidx.activity.compose.BackHandler(enabled =
 * true)` kurar (sistem-back'i entry düzeyinde tüketir; predictive preview o entry'de başlamaz).
 * Desktop'ta sistem-back/predictive-back kavramı YOK → desktop actual'ı no-op; desktop'ta
 * geri-yutma davranışı [gezginOnBack] guard'ıyla (her iki platformda test edilebilir) sağlanır.
 */
@Composable internal expect fun GezginNoBackHandler()

/**
 * Nav3 `NavDisplay` çağrısının platform-özel sarmalayıcısı (scene wiring). `NavDisplay`'in
 * scene-strategy parametresi iki hedefte GERÇEKTEN uzlaşmaz imzalı → `expect/actual` ZORUNLU (bir
 * commonMain sarmalayıcı YETMEZ). The relevant non-deprecated `navigation3-ui` overloads are:
 * - **desktop (JB `1.0.0-alpha05`)**: `sceneStrategy: SceneStrategy<T> = SinglePaneSceneStrategy()`
 *   — TEKİL; `sceneDecoratorStrategies`/`sharedTransitionScope` YOK.
 * - **android (Google `1.1.4`)**: `sceneStrategies: List<SceneStrategy<T>> =
 *   listOf(SinglePaneSceneStrategy())` — ÇOĞUL liste; ayrıca `sceneDecoratorStrategies`,
 *   `sharedTransitionScope` var. Parametre hem ADI hem ARİTESİ farklı (isimli argüman hedefe göre
 *   kırılır) → ortak alt-kümede DEĞİL. Actual'lar Gezgin-sahipli
 *   [GezginDialogSceneStrategy] + [GezginBottomSheetSceneStrategy]'yi (dismiss'i sahip-entry'ye
 *   pinler) fallback `SinglePaneSceneStrategy` ile zincirler (`then`/liste) — modal-metadata'lı
 *   entry overlay, diğerleri tek-pane. Transition spec'leri buraya GEÇMEZ (per-entry
 *   `NavEntry.metadata` ile iner, bkz. [GezginDisplay] KDoc).
 *
 * [onBack] — tekil sistem-back (Nav3 `NavDisplay.onBack`, ekran/predictive back → [gezginOnBack]).
 * [pinnedBack] — modal (dialog/sheet) dismiss'inin sahip-entry'ye pinli `navigator.back(entryId)`
 * kancası (); Gezgin scene-strategy'lerine geçirilir. İkisi AYRI: sistem-back canlı-top'a,
 * modal-dismiss kendi entry'sine bağlanır.
 */
@Composable
internal expect fun GezginNavDisplay(
  entries: List<NavEntry<Route>>,
  modifier: Modifier,
  onBack: () -> Unit,
  pinnedBack: (Long) -> Unit,
)
