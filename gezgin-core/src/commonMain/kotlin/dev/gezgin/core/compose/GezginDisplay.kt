@file:OptIn(GezginInternalApi::class)

package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route

/**
 * The Nav3 `NavDisplay` adapter (§2.1/§4.2/§12) — the `entries` trailing lambda collects the
 * `register<R> {... }` calls, including generated `provideXEntry` bindings, on a [GezginEntryScope]
 * receiver; the content (the `GezginKey` list) is ALWAYS read from `navigator.keysState` (it
 * carries an `id`) — NOT from `backStack` (public `Route`, id-less): because of `StateFlow`
 * equal-value dedup, a `replaceTo` to a same-value-different-id target produces no emit on
 * `backStack`, whereas [keysState][RawNavigator.keysState] emits a new list by the `id` difference.
 * Entry identity is therefore always the `contentKey`.
 *
 * **Decorators:** entries are decorated with `rememberDecoratedNavEntries` before being handed to
 * `NavDisplay`. The common decorator is `rememberSaveableStateHolderNavEntryDecorator()` (saveable
 * state = mandatory; `rememberSaveable` runs in each entry's own `contentKey`=id slot → two
 * equal-valued routes get SEPARATE saved state; the R2 saved-state side, desktop included).
 * PLATFORM: [rememberPlatformEntryDecorators] — on Android
 * `rememberViewModelStoreNavEntryDecorator()` (per-entry VM store; `LocalViewModelStoreOwner` from
 * the host Activity), on desktop the SAME per-entry VM store decorator (since the owner is not
 * guaranteed from the host, with Gezgin's own window-scoped owner). Order `[saveable] + platform`:
 * the VM store decorator depends on the `SavedStateRegistryOwner` the saveable provides → saveable
 * first/OUTER.
 *
 * **Setup guard (§12) — the part `rememberNavigator` CANNOT do:** the kind info exists only here
 * (in the entry-scope registry). AFTER the registers are collected, if the root
 * (`navigator.keys.first()`) route's registered kind is OTHER THAN `SCREEN`
 * (`Dialog`/`BottomSheet`/`FullscreenModal`), setup stops with an `error()` — it prevents violating
 * Nav3 `OverlayScene`'s `require(overlaidEntries.isNotEmpty())` invariant (that a modal cannot
 * exist without at least one normal entry beneath it) by starting a modal alone at the root. If the
 * route is NOT registered yet (kind lookup `null`) it does not blow up here — the more descriptive
 * error for that case already exists in [toNavEntry] (`no entry registered for route`). The guard
 * remains necessary even with [GezginNavDisplay] modal scenes because `OverlayScene` requires at
 * least one normal entry beneath every modal (§7).
 *
 * **Transition cascade (§9) — per-entry metadata:** as each entry is built, its own route's cascade
 * is resolved by [resolveTransition] and written into `NavEntry.metadata` through Nav3's
 * `NavDisplay.transitionSpec/popTransitionSpec/predictivePopTransitionSpec` wrappers
 * ([GezginTransition.toNavEntryMetadata]). On pop, NavDisplay reads the metadata of the scene being
 * left, so the innermost route remains authoritative in both directions. If the cascade resolves to
 * `null`, no metadata is written and NavDisplay uses its own defaults. A missing predictive
 * transition falls back to the backward transition.
 *
 * **The `entries` lambda is captured only on the FIRST composition** (`remember {
 * GezginEntryScope().apply (entries) }` — `entries` is NEVER called again on later recompositions):
 * its `register<R> { ... }` calls must be unconditional (a conditional register tied to an
 * `if`/state does not work as expected — the registration freezes at setup and does not change
 * afterward).
 */
@Composable
public fun GezginDisplay(
  navigator: RawNavigator,
  modifier: Modifier = Modifier,
  transitions: GezginTransition? = null,
  entries: GezginEntryScope.() -> Unit,
) {
  val scope = remember {
    GezginEntryScope().apply(entries).also { registered ->
      val rootRoute = navigator.keys.first().route
      val rootKind = registered.registry[rootRoute::class]?.kind
      // NOT: Bu YALNIZ start route'u kapsayan erken/redundant güvenlik ağıdır. ASIL
      // modal-kind-at-root
      // guard'ı [toNavEntry]'dedir (her entry kurulurken `isRoot` ile) — o TÜM dinamik yolları da
      // (replaceTo/quitAndGoTo ile modal'ı köke koyma) kapatır. Bu check yine de erken/açık kalır.
      require(rootKind == null || rootKind == EntryKind.SCREEN) {
        "GezginDisplay: start route cannot be a modal kind (kind=$rootKind); §12 setup " +
          "guard is permanent because a modal cannot be root and OverlayScene requires at " +
          "least one underlaid entry (§7). route: ${rootRoute::class.simpleName}"
      }
      // M4 — kind-lookup kancasını navigator'a enjekte et: replaceTo (clearUpTo=root) MUTASYONDAN
      // ÖNCE, sonuçtaki kök modal olacaksa reddedebilsin. Kind yalnız burada (registry) bilinir;
      // kayıtsız route → `false` (modal değil varsay; kayıtsızlığın açık hatası toNavEntry'de).
      navigator.modalRootGuard = { route ->
        registered.registry[route::class]?.kind?.let { it != EntryKind.SCREEN } ?: false
      }
    }
  }
  val keys by
    navigator.keysState.collectAsState() // id taşır → id-only değişim de recompose eder (4a)
  // Transition cascade PER-ENTRY metadata'yla iner (bkz. dosya başı KDoc) — `transitions` remember
  // anahtarı: app-seviyesi default değişirse entry metadata'ları yeniden kurulmalı.
  val entryList =
    remember(keys, transitions) {
      keys.map { key ->
        scope.toNavEntry(key, navigator, transitions, isRoot = isRootEntry(keys, key.id))
      }
    }
  // `remember` ile sabitlenmiş kimlik (Important 3, validation): decorator @Composable'ları
  // ([rememberSaveableStateHolderNavEntryDecorator]/[rememberPlatformEntryDecorators]) her ikisi de
  // KENDİ içeriklerini `remember`'lasa da, bu ikisini birleştiren `listOf(...) + ...` HER
  // recomposition'da taze bir `List` instance'ı üretiyordu — `rememberDecoratedNavEntries`'e her
  // seferinde "değişti" görünen bir liste geçiyordu (referans-eşitliği yoksa cache miss).
  // Anahtarlar
  // decorator'ların kendileri: onlar stabilse (normal durum — navigator/scope her recomposition'da
  // değişmez) bu liste artık stabil kalır.
  val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<Route>()
  val platformDecorators = rememberPlatformEntryDecorators()
  val decorators: List<NavEntryDecorator<Route>> =
    remember(saveableDecorator, platformDecorators) {
      listOf(saveableDecorator) + platformDecorators
    }
  val decoratedEntries = rememberDecoratedNavEntries(entryList, decorators)
  // `gezginOnBack(navigator, scope)` de bir kurucu fonksiyon çağrısı — SABİT `navigator`/`scope`
  // (aynı composition boyunca) için `remember` olmadan her recomposition'da yeni bir lambda
  // instance'ı
  // üretiyordu (NavDisplay'in `onBack` parametresi identity ile karşılaştırılabilir call-site'lar
  // için gereksiz iş). Davranış AYNI — sadece kimlik stabilize edildi.
  val onBack = remember(navigator, scope) { gezginOnBack(navigator, scope) }
  // C-MJ-1 — modal (dialog/sheet) dismiss'ini sahip-entry'ye pinleyen back kancası:
  // `navigator.back(id)`
  // yalnız o entry HÂLÂ top ise pop eder, değilse no-op → çifte-dismiss / hide-animasyon penceresi
  // / geç
  // async back ALTTAKİ ekranı poplamaz. `remember(navigator)` → stabil kimlik (GezginNavDisplay'in
  // `remember(pinnedBack)` scene-strateji anahtarı stabil kalsın).
  val pinnedBack: (Long) -> Unit = remember(navigator) { { id -> navigator.back(id) } }
  // scene wiring: `NavDisplay` çağrısı platform-özel sarmalayıcıda ([GezginNavDisplay]) —
  // sceneStrategy imzası android/desktop uzlaşmaz (expect/actual, bkz. PlatformDisplay.kt KDoc).
  // GezginDialogSceneStrategy/GezginBottomSheetSceneStrategy modal-metadata'lı entry'yi overlay
  // render
  // eder; dismiss `pinnedBack` ile sahip-entry'ye pinli.
  GezginNavDisplay(
    entries = decoratedEntries,
    modifier = modifier,
    onBack = onBack,
    pinnedBack = pinnedBack,
  )
}

/**
 * The `NavDisplay` `onBack` lambda — the `@NoBack` runtime guard (M5′, §4.2). When back is invoked
 * it reads the LIVE top entry (`navigator.keys.last()`, not a captured stale value): if the top's
 * record has `noBack==true` AND the top is NOT the root, back is SWALLOWED (no pop) — otherwise
 * `navigator.back()`. **Root exemption:** while the stack has a single entry (`keys.size <= 1`),
 * `noBack` is ignored → `back()` (at the bottom this hits `onRootBack`; the user is not trapped in
 * the app).
 *
 * On desktop this is the BEHAVIORAL carrier of `@NoBack` (no system-back/predictive,
 * [GezginNoBackHandler] is a no-op on desktop). On Android a real entry-scoped
 * [GezginNoBackHandler] is also set up; there this guard acts as a safety net that swallows back in
 * `NavDisplay`'s own `onBack` as well. NOT `@Composable` → it can be pinned with unit/uiTest
 * without a Compose setup.
 */
internal fun gezginOnBack(navigator: RawNavigator, scope: GezginEntryScope): () -> Unit = {
  val keys = navigator.keys
  val top = keys.lastOrNull()
  val isRoot = top == null || isRootEntry(keys, top.id)
  val topNoBack = top != null && scope.registry[top.route::class]?.noBack == true
  if (topNoBack && !isRoot) {
    // @NoBack top entry (kök değil): Gezgin-sahipli geri-yutma — pop YOK (M5′).
  } else {
    navigator.back()
  }
}

/**
 * consolidation — [GezginDisplay]'s per-entry `isRoot` (`key.id == keys.first().id`) and
 * [gezginOnBack]'s old `keys.size <= 1` predicate were asking the SAME thing (when the
 * bottom-of-stack entry is the TOP, both were equal for a single entry — `size<=1` ⟺ top.id ==
 * first.id): merged into a single helper.
 */
private fun isRootEntry(keys: List<GezginKey>, entryId: Long): Boolean =
  keys.firstOrNull()?.id == entryId
