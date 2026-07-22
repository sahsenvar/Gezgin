@file:OptIn(GezginInternalApi::class, dev.gezgin.core.ExperimentalGezginMigrationApi::class)

package dev.gezgin.core.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import dev.gezgin.core.BottomSheetContract
import dev.gezgin.core.BottomSheetDragHandleMode
import dev.gezgin.core.DialogContract
import dev.gezgin.core.FullscreenModalContract
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route

/**
 * `GezginKey` → Nav3 `NavEntry` adapter'ı : `contentKey = key.id` — instance kimliği, aynı route
 * iki kez push edilse bile Nav3'e **ayrı** decorator state (VM store/saved state) kazandırır. `key
 * = key.route` (kullanıcı route'u; Nav3 `NavDisplay`'in kendi backstack diff'i için kullanılır —
 * GezginKey zarfı kullanıcıya/Nav3'e hiç sızmaz, yalnız burada unwrap edilir).
 *
 * Lookup (registry'de route için kayıt var mı) **çağrı anında** yapılır — content lambda'sının
 * İÇİNDE değil. Kayıtsız route derlenmiş bir stack'e karışmışsa hata composable invoke edilmeden,
 * entry kurulurken patlar (erken/açık başarısızlık; sessiz boş ekran yok).
 *
 * Kind (`RegisteredEntry.kind`) → scene metadata (): `DIALOG`/`FULLSCREEN_MODAL` iken
 * `NavEntry.metadata`'ya `DialogSceneStrategy.dialog(properties)` işareti yazılır →
 * [GezginNavDisplay]'e bağlı DialogSceneStrategy o entry'yi `Dialog` overlay'inde (arka görünür)
 * render eder. `properties` route'un opsiyonel [DialogContract]/[FullscreenModalContract]'ından
 * (route-instance runtime değeri) okunur; route implement etmemişse tip-bazlı varsayılan
 * `DialogProperties` (FULLSCREEN_MODAL'da `usePlatformDefaultWidth=false` = tam-ekran) kurulur.
 * `BOTTOM_SHEET` [GEZGIN_BOTTOM_SHEET_KEY] işaretiyle [GezginBottomSheetSceneStrategy]'e bağlanır →
 * `ModalBottomSheet` overlay (el-yazımı OverlayScene).
 *
 * **Guard — modal dismissal + `@NoBack` çelişkisi (, kuruluş-zamanı RUNTIME):** modal back
 * dismissal kapalı olmalıdır. Bottom sheet ayrıca kullanıcı drag/swipe gesture'larını da
 * kapatmalıdır; outside dismissal bağımsızdır ve guard predicate'ine katılmaz. Route getter
 * değerlerini KSP okuyamadığı için contract-bearing route'lar entry kuruluşunda `require` ile
 * doğrulanır.
 *
 * **Top-entry drive** (): content [LocalGezginEntryId]/ [LocalGezginRawNavigator] ile sarılır — bir
 * entry'nin içeriği YALNIZ kendi `key.id`'siyle kurulmuş navigator'ı Local'den okuyabilir (
 * `provideXEntry` bunlardan tipli navigator kuracak).
 *
 * **`@NoBack` entry-scoped handler** (′): kayıt `noBack==true` ve entry KÖK DEĞİLSE
 * ([isRoot]==false — "root entry'de noBack yok sayılır → back = onRootBack"), content ekran
 * içeriğinden ÖNCE [GezginNoBackHandler] ile sarılır: Gezgin'in handler'ı OUTER/önce kaydolur →
 * dispatcher LIFO'sunda ekranın kendi (daha İÇ, sonra kaydolan) `BackHandler`'ı kazanır, yoksa
 * Gezgin'inki back'i yutar. [isRoot] çağıran (`GezginDisplay`) tarafından stack'in dibi
 * (`keys.first`) bilgisinden geçirilir — call-time gerçeği, capture edilmiş stale scope değil (
 * staleness notu).
 *
 * fix — **per-entry transition metadata ():** entry'nin KENDİ route'unun cascade'i
 * ([resolveTransition]: route-override > graph-mirası > app-default) çözülür ve
 * `NavEntry.metadata`'ya ([GezginTransition.toNavEntryMetadata] — Nav3'ün PUBLIC
 * `NavDisplay.transitionSpec/popTransitionSpec/ predictivePopTransitionSpec` sarmalayıcılarıyla)
 * yazılır. Top-route'tan NavDisplay-parametresi çözen ilk yaklaşım GERİ ALINDI: pop B→A'da
 * NavDisplay'in top'u artık A olduğundan B'nin `backward{}`/ `predictive{}` spec'leri hiç
 * kullanılmıyordu ( "en içteki kazanır" ihlali). Per-entry metadata'da Nav3'ün kendi
 * AnimatedContent çözümü doğru entry'nin spec'ini seçer (`Scene.metadata` default'u = son entry'nin
 * metadata'sı; pop/predictive'de çıkılan scene'inki okunur — bkz. GezginDisplay KDoc).
 */
internal fun GezginEntryScope.toNavEntry(
  key: GezginKey,
  navigator: RawNavigator,
  transitions: GezginTransition?,
  isRoot: Boolean = false,
): NavEntry<Route> {
  val registered =
    registry[key.route::class]
      ?: error("No entry is registered for route: ${key.route::class.simpleName}")
  //  modal-kind-at-root guard (kuruluş-zamanı RUNTIME) — TÜM dinamik yolları tek yerde kapatır:
  // start route DIŞINDA `replaceTo(SomeDialogRoute)`/`quitAndGoTo` da tek-modal (kök) stack
  // üretebilir.
  // Bu ANA guard'dır; `GezginDisplay`'deki setup-time check yalnız start route'u kapsar → redundant
  // güvenlik ağı olarak kalır. DIALOG/FULLSCREEN_MODAL Nav3-içi
  // `require(overlaidEntries.isNotEmpty())`
  // ile çökerdi (Gezgin-mesajsız), BOTTOM_SHEET ise hiç fırlamayıp boş arka-planda sessiz
  // render'dı.
  require(!(isRoot && registered.kind != EntryKind.SCREEN)) {
    "GezginDisplay: modal kind (${registered.kind}) cannot be the only/root entry in the stack; " +
      "a modal must have at least one SCREEN entry underneath it (Nav3 OverlayScene invariant, §7). " +
      "Do not place a modal at root with replaceTo/quitAndGoTo. route: ${key.route::class.simpleName}"
  }
  val installNoBack = registered.noBack && !isRoot
  // scene wiring (): DIALOG/FULLSCREEN_MODAL ise [GEZGIN_DIALOG_KEY] (Gezgin-sahipli
  // GezginDialogSceneStrategy) işareti, BOTTOM_SHEET ise [GEZGIN_BOTTOM_SHEET_KEY]. İkisi de
  // dismiss'i
  // sahip-entry'ye pinler (). Nav3'ün built-in DialogSceneStrategy'si BIRAKILDI: dismiss'i
  // tekil
  // NavDisplay.onBack'e bağlar → entry'ye pinlenemez (bkz. DialogScene.kt). Transition metadata
  // YALNIZ
  // SCREEN kind'a yazılır (aşağıda, display ).
  val dialogProperties = resolveDialogProperties(registered.kind, key.route)
  val sheetProps = resolveBottomSheetProps(registered.kind, key.route)
  //  guard (kuruluş-zamanı runtime): @NoBack geri'yi yutar; modal back dismissal kapalı
  // olmalıdır.
  // Bottom sheet'te gesture'lar da kapalı olmalıdır. Outside dismissal bağımsız bir switch'tir.
  if (registered.noBack) {
    if (dialogProperties != null)
      requireBackDismissCompatible(key.route, dialogProperties.dismissOnBackPress)
    if (sheetProps != null) {
      requireBackDismissCompatible(key.route, sheetProps.dismissOnBackPress)
      require(!sheetProps.sheetGesturesEnabled) {
        "Bottom sheet setup conflict (${key.route::class.simpleName}): @NoBack requires " +
          "sheetGesturesEnabled=false so user drag/swipe cannot hide the sheet while keeping its " +
          "route on the stack. dismissOnClickOutside is independent and is not part of this guard (§7)."
      }
    }
  }
  // Display transition metadata YALNIZ SCREEN kind'a: modal
  // (DIALOG/FULLSCREEN_MODAL/BOTTOM_SHEET)
  // entry'sine transition anahtarları yazmak on-device'da ya etkisiz ya arka-plan SCREEN'i yanlış
  // animasyonlar (Nav3 scene-seviyesi AnimatedContent, `Scene.metadata` = son/top entry'nin
  // metadata'sını
  // okur → modal entry'nin forward/pop/predictive spec'i overlay'de yanlış uygulanır). Modal
  // kind'da ATLA.
  val transitionMetadata =
    if (registered.kind == EntryKind.SCREEN) {
      resolveTransition(key.route, transitions)?.toNavEntryMetadata().orEmpty()
    } else {
      emptyMap()
    }
  // Kind mutually-exclusive: dialogProperties (DIALOG/FULLSCREEN_MODAL) ve sheetProps
  // (BOTTOM_SHEET) aynı
  // anda dolu olamaz — her ikisi de eklense bile ayrık anahtarlar (çakışma yok). Modal kind'da
  // transitionMetadata zaten boş (). Dialog → GEZGIN_DIALOG_KEY, sheet →
  // GEZGIN_BOTTOM_SHEET_KEY;
  // ikisi de kendi Gezgin scene-strategy'siyle entry-pinli dismiss render eder ().
  val metadata: Map<String, Any> = buildMap {
    putAll(transitionMetadata)
    if (dialogProperties != null) put(GEZGIN_DIALOG_KEY, dialogProperties)
    if (sheetProps != null) put(GEZGIN_BOTTOM_SHEET_KEY, sheetProps)
  }
  return NavEntry(key = key.route, contentKey = key.id, metadata = metadata) { route ->
    CompositionLocalProvider(
      LocalGezginEntryId provides key.id,
      LocalGezginRawNavigator provides navigator,
    ) {
      if (installNoBack) GezginNoBackHandler() // OUTER: ekran içeriğinden önce kaydol (M5′)
      registered.content(route)
    }
  }
}

/**
 * Kind + route-instance → dialog scene `DialogProperties` (), yoksa `null` (dialog-dışı entry →
 * plain tek-pane). Property'ler route'un opsiyonel [DialogContract]/[FullscreenModalContract]'ından
 * (runtime value) okunur; route implement etmemişse tip-varsayılan `DialogProperties`:
 * - [EntryKind.DIALOG] → `DialogProperties(dismissOnBackPress, dismissOnClickOutside,
 *   usePlatformDefaultWidth)` — [DialogContract]'tan ya da (yoksa) tüm-default.
 * - [EntryKind.FULLSCREEN_MODAL] → `usePlatformDefaultWidth = false` (tam-ekran = SABİT tanım),
 *   dismiss'ler [FullscreenModalContract]'tan ya da (yoksa) default `true`.
 * - diğerleri → `null`.
 */
private fun resolveDialogProperties(kind: EntryKind, route: Route): DialogProperties? =
  when (kind) {
    EntryKind.DIALOG -> {
      val contract = route as? DialogContract
      DialogProperties(
        dismissOnBackPress = contract?.dismissOnBackPress ?: true,
        dismissOnClickOutside = contract?.dismissOnClickOutside ?: true,
        usePlatformDefaultWidth = contract?.usePlatformDefaultWidth ?: true,
      )
    }
    EntryKind.FULLSCREEN_MODAL -> {
      val contract = route as? FullscreenModalContract
      DialogProperties(
        dismissOnBackPress = contract?.dismissOnBackPress ?: true,
        dismissOnClickOutside = contract?.dismissOnClickOutside ?: true,
        usePlatformDefaultWidth = false, // tam-ekran modal tanımı
      )
    }
    EntryKind.SCREEN,
    EntryKind.BOTTOM_SHEET -> null
  }

/**
 * Kind + route-instance → BottomSheet scene [GezginBottomSheetProps] (), yoksa `null` (sheet-dışı
 * entry). Property'ler route'un opsiyonel [BottomSheetContract]'ından (runtime value) okunur; route
 * implement etmemişse tip-varsayılan (`skipPartiallyExpanded=false`, `dismissOnBackPress=true`,
 * `dismissOnClickOutside=true`, `sheetGesturesEnabled=true`, `dragHandleMode=Default`).
 */
private fun resolveBottomSheetProps(kind: EntryKind, route: Route): GezginBottomSheetProps? =
  when (kind) {
    EntryKind.BOTTOM_SHEET -> {
      val contract = route as? BottomSheetContract
      GezginBottomSheetProps(
        skipPartiallyExpanded = contract?.skipPartiallyExpanded ?: false,
        dismissOnBackPress = contract?.dismissOnBackPress ?: true,
        dismissOnClickOutside = contract?.dismissOnClickOutside ?: true,
        sheetGesturesEnabled = contract?.sheetGesturesEnabled ?: true,
        dragHandleMode = contract?.dragHandleMode ?: BottomSheetDragHandleMode.Default,
      )
    }
    EntryKind.SCREEN,
    EntryKind.DIALOG,
    EntryKind.FULLSCREEN_MODAL -> null
  }

/**
 * `@NoBack` + `dismissOnBackPress=true` çelişki guard'ı (, kuruluş-zamanı RUNTIME) — DIALOG/
 * FULLSCREEN_MODAL ve BOTTOM_SHEET modalları için ORTAK: `@NoBack` geri'yi YUTAR ([gezginOnBack]/
 * [GezginNoBackHandler]) ama `dismissOnBackPress=true` "geri modal'ı kapatsın" der → tezat.
 * `dismissOnBackPress` runtime değer (route-instance, KSP okuyamaz) → derleme yerine entry
 * kuruluşunda `require` ile reddedilir.
 */
private fun requireBackDismissCompatible(route: Route, dismissOnBackPress: Boolean) {
  require(!dismissOnBackPress) {
    "Modal setup conflict (${route::class.simpleName}): @NoBack (back is swallowed) and " +
      "dismissOnBackPress=true (back dismisses the modal) cannot be used together. In the modal " +
      "DialogContract/FullscreenModalContract/BottomSheetContract, set " +
      "`override val dismissOnBackPress = false`, or remove @NoBack (§7)."
  }
}
