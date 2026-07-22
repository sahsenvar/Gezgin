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
 * The Nav3 `NavDisplay` adapter — the `entries` trailing lambda collects the `register<R> {... }`
 * calls, including generated `provideXEntry` bindings, on a [GezginEntryScope] receiver; the
 * content (the `GezginKey` list) is ALWAYS read from `navigator.keysState` (it carries an `id`) —
 * NOT from `backStack` (public `Route`, id-less): because of `StateFlow` equal-value dedup, a
 * `replaceTo` to a same-value-different-id target produces no emit on `backStack`, whereas
 * [keysState][RawNavigator.keysState] emits a new list by the `id` difference. Entry identity is
 * therefore always the `contentKey`.
 *
 * **Decorators:** entries are decorated with `rememberDecoratedNavEntries` before being handed to
 * `NavDisplay`. The common decorator is `rememberSaveableStateHolderNavEntryDecorator()` (saveable
 * state = mandatory; `rememberSaveable` runs in each entry's own `contentKey`=id slot → two
 * equal-valued routes get SEPARATE saved state, including on desktop). PLATFORM:
 * [rememberPlatformEntryDecorators] — on Android `rememberViewModelStoreNavEntryDecorator()`
 * (per-entry VM store; `LocalViewModelStoreOwner` from the host Activity), on desktop the SAME
 * per-entry VM store decorator (since the owner is not guaranteed from the host, with Gezgin's own
 * window-scoped owner). Order `[saveable] + platform`: the VM store decorator depends on the
 * `SavedStateRegistryOwner` the saveable provides → saveable first/OUTER.
 *
 * **Setup guard — the part `rememberNavigator` CANNOT do:** the kind info exists only here (in the
 * entry-scope registry). AFTER the registers are collected, if the root (`navigator.keys.first()`)
 * route's registered kind is OTHER THAN `SCREEN` (`Dialog`/`BottomSheet`/`FullscreenModal`), setup
 * stops with an `error()` — it prevents violating Nav3 `OverlayScene`'s
 * `require(overlaidEntries.isNotEmpty())` invariant (that a modal cannot exist without at least one
 * normal entry beneath it) by starting a modal alone at the root. If the route is NOT registered
 * yet (kind lookup `null`) it does not blow up here — the more descriptive error for that case
 * already exists in [toNavEntry] (`no entry registered for route`). The guard remains necessary
 * even with [GezginNavDisplay] modal scenes because `OverlayScene` requires at least one normal
 * entry beneath every modal.
 *
 * **Transition cascade — per-entry metadata:** as each entry is built, its own route's cascade is
 * resolved by [resolveTransition] and written into `NavEntry.metadata` through Nav3's
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
      // This early guard covers the start route. `toNavEntry` remains authoritative for dynamic
      // replacement and quit-and-navigate paths.
      require(rootKind == null || rootKind == EntryKind.SCREEN) {
        "GezginDisplay: start route cannot be a modal kind (kind=$rootKind); §12 setup " +
          "guard is permanent because a modal cannot be root and OverlayScene requires at " +
          "least one underlaid entry (§7). route: ${rootRoute::class.simpleName}"
      }
      // Inject kind lookup so replacement can reject a modal result root before mutation. Missing
      // registrations are reported later by toNavEntry with a more specific error.
      navigator.modalRootGuard = { route ->
        registered.registry[route::class]?.kind?.let { it != EntryKind.SCREEN } ?: false
      }
    }
  }
  val keys by
    navigator.keysState.collectAsState() // Entry ids make identity-only changes observable.
  // Rebuild per-entry metadata when the app-level transition default changes.
  val entryList =
    remember(keys, transitions) {
      keys.map { key ->
        scope.toNavEntry(key, navigator, transitions, isRoot = isRootEntry(keys, key.id))
      }
    }
  // Remember the combined list as well as its decorators so entry decoration does not see a fresh
  // collection identity on every recomposition.
  val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<Route>()
  val platformDecorators = rememberPlatformEntryDecorators()
  val decorators: List<NavEntryDecorator<Route>> =
    remember(saveableDecorator, platformDecorators) {
      listOf(saveableDecorator) + platformDecorators
    }
  val decoratedEntries = rememberDecoratedNavEntries(entryList, decorators)
  // Keep callback identity stable while navigator and registration scope remain unchanged.
  val onBack = remember(navigator, scope) { gezginOnBack(navigator, scope) }
  // Pin modal dismissal to its owner so duplicate or late callbacks cannot pop the next screen.
  val pinnedBack: (Long) -> Unit = remember(navigator) { { id -> navigator.back(id) } }
  // The platform wrapper reconciles Android and desktop scene-strategy signatures.
  GezginNavDisplay(
    entries = decoratedEntries,
    modifier = modifier,
    onBack = onBack,
    pinnedBack = pinnedBack,
  )
}

/**
 * The `NavDisplay` `onBack` lambda — the `@NoBack` runtime guard (′). When back is invoked it reads
 * the LIVE top entry (`navigator.keys.last()`, not a captured stale value): if the top's record has
 * `noBack==true` AND the top is NOT the root, back is SWALLOWED (no pop) — otherwise
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
    // Consume back for a non-root @NoBack entry without popping it.
  } else {
    navigator.back()
  }
}

/** Returns whether [entryId] belongs to the bottom entry that defines root behavior. */
private fun isRootEntry(keys: List<GezginKey>, entryId: Long): Boolean =
  keys.firstOrNull()?.id == entryId
