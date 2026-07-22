package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import dev.gezgin.processor.fragment.FragmentEntryModel

private const val COMPOSE_PKG = "dev.gezgin.core.compose"
private const val FRAGMENT_RT_PKG = "dev.gezgin.core.fragment"

private val ENTRY_SCOPE = ClassName(COMPOSE_PKG, "GezginEntryScope")
private val ENTRY_KIND = ClassName(COMPOSE_PKG, "EntryKind")
private val LOCAL_ENTRY_ID = MemberName(COMPOSE_PKG, "LocalGezginEntryId")
private val LOCAL_RAW_NAVIGATOR = MemberName(COMPOSE_PKG, "LocalGezginRawNavigator")

// Route-to-Bundle encoding is keyed on `route` via `remember` so recomposition of the hosting
// entry doesn't re-serialize an unchanged route every frame (only re-runs when `route` changes).
private val REMEMBER = MemberName("androidx.compose.runtime", "remember")

// `androidx.fragment.compose.AndroidFragment` — FQ MemberName only, NO compile dependency on
// androidx.fragment anywhere in gezgin-processor (same "emit as FQ strings" discipline as
// MviEntryCodegen's
// Hilt/Koin/lifecycle references;  keeps the processor fragment-free). Pinned 1.8.9 => the
// 4-param
// overload (arguments, onUpdate) — NO `maxLifecycle`/`fragmentState`.
private val ANDROID_FRAGMENT = MemberName("androidx.fragment.compose", "AndroidFragment")

// gezgin-core runtime glue — a REAL compile dependency (gezgin-core already IS
// one for
// every Gezgin module, unlike androidx.fragment). Emitted as ordinary imported member calls.
private val TO_BUNDLE = MemberName(FRAGMENT_RT_PKG, "toBundle")
private val BIND_GEZGIN = MemberName(FRAGMENT_RT_PKG, "bindGezgin")

/**
 * Emits `fun GezginEntryScope.provideXEntry()` for every [FragmentEntryModel]
 * [dev.gezgin.processor.fragment.FragmentModelReader] resolved for brownfield Fragment interop. The
 * THIRD entry codegen, alongside core-mode [EntryCodegen] and MVI-mode [MviEntryCodegen]: same
 * `GezginEntryScope` extension + `register<Route>(...)` shape, grouped one [FileSpec] per Fragment
 * package — but into a SEPARATE `GezginFragmentEntries.kt` (mirrors `MviEntryCodegen`'s own
 * separate-file rationale) so a module mixing entry styles gets `GezginEntries.kt` /
 * `GezginMviEntries.kt` / `GezginFragmentEntries.kt` with NO same-name-same-package collision by
 * construction (function-name clashes across the kinds are prevented by `SC6` for core/MVI and by
 * `FS4` for Fragment).
 *
 * ```kotlin
 * fun GezginEntryScope.provideOrderChainEntry() {
 *     register<OrderChainRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->
 *         val raw = LocalGezginRawNavigator.current
 *         val nav = raw.orderChainNavigator(LocalGezginEntryId.current)     // factory qualified by route pkg
 *         AndroidFragment<OrderChainFragment>(                              // FQ, 4-param 1.8.9 form
 *             arguments = remember(route) { route.toBundle(raw) },         // route → Bundle (PD-safe, remembered per route)
 *             onUpdate = { fragment -> bindGezgin(fragment, route, nav) }, // live-ref re-attach (registry)
 *         )
 *     }
 * }
 * ```
 *
 * **Screen-only.** Fragment interop has no dialog/bottom-sheet/fullscreen variant — every emitted
 * `register` is `kind = EntryKind.SCREEN`, unconditionally.
 *
 * **Navigator wiring — CONDITIONAL (SC2/MV7 parity, one stage later).** A Fragment wires `nav`
 * (`val nav = raw.xNavigator(entryId)` + the 3-arg `bindGezgin(fragment, route, nav)`) **only when
 * the route actually earns a navigator** — the `navWired` flag [generate]'s `hasNavigator`
 * predicate supplies per entry (computed at the [dev.gezgin.processor.GezginProcessor] dispatch
 * site: a SAME-module route decides from the in-memory `GraphModel` via
 * [NavigatorCodegen.hasNavigator], exactly like core-mode's `SC2` and MVI-mode's `MV7`; a
 * CROSS-module route — absent from this module's model — is decided by a classpath PROBE for the
 * already-compiled `XNavigator` class, replacing the old `?: true` blind optimism that nav-wired
 * every cross-module Fragment and mis-compiled a display-only cross-module leaf). A
 * `@FragmentScreen` route with NO edges/back-edges/result-contract earns no
 * `NavigatorCodegen`-generated `xNavigator` factory — a realistic, legitimate case (a display-only
 * brownfield leaf like Settings/About that only reads `gezginArgs` and never navigates). For that
 * leaf the emitted body SUPPRESSES the `val nav = ...` line (which would be an unresolved
 * reference) and binds via the no-nav `bindGezgin(fragment, route)` overload; `gezginNav` then
 * throws the actionable `[FS5]` runtime error (gezgin-core `FragmentBinding.android.kt`) rather
 * than the codegen calling a factory that doesn't exist. The leaf is NOT rejected at KSP time (that
 * would forbid a legitimate display-only Fragment). The factory call, when wired, is qualified
 * against [FragmentEntryModel.routePackageName] — cross-module-safe, exactly like [EntryCodegen].
 */
internal object FragmentEntryCodegen {

  /**
   * @param hasNavigator per-entry predicate — `true` iff the entry's route earns a
   *   `NavigatorCodegen` `xNavigator` factory (see class KDoc). Supplied by
   *   [dev.gezgin.processor.GezginProcessor], which holds the
   *   [dev.gezgin.processor.model.GraphModel] this reader is deliberately kept unaware of.
   */
  fun generate(
    entries: List<FragmentEntryModel>,
    hasNavigator: (FragmentEntryModel) -> Boolean,
  ): List<FileSpec> =
    // Sort by (packageName, routeFq[unique]) before grouping for reproducible emission, so
    // file and per-file function order don't ride on non-contractual KSP symbol order.
    entries
      .sortedWith(compareBy({ it.packageName }, { it.routeFq }))
      .groupBy { it.packageName }
      .map { (packageName, group) ->
        FileSpec.builder(packageName, "GezginFragmentEntries")
          // Every fragment register body reads LocalGezginRawNavigator and calls
          // route.toBundle,
          // all gated behind @GezginInternalApi.
          .optInGezginInternalApi()
          .apply { group.forEach { addFunction(provideFragmentEntryFun(it, hasNavigator(it))) } }
          .build()
      }

  private fun provideFragmentEntryFun(entry: FragmentEntryModel, navWired: Boolean): FunSpec {
    val routeClass = ClassName.bestGuess(entry.routeFq)
    val fragmentClass = ClassName.bestGuess(entry.fragmentFq)

    val body =
      CodeBlock.builder()
        .add(
          "register<%T>(kind = %T.SCREEN, noBack = %L) { route ->\n",
          routeClass,
          ENTRY_KIND,
          entry.noBack,
        )
        .indent()
        // `raw` bound to a val — needed for route.toBundle(raw), and (when wired) the navigator
        // factory.
        .add("val raw = %M.current\n", LOCAL_RAW_NAVIGATOR)
        .apply {
          if (navWired) {
            // The navigator FACTORY extension lives in the route's OWN package ([routePackageName])
            // —
            // a different package (and, cross-module, a different MODULE) than this file's, so `%M`
            // (imported), identical to EntryCodegen/MviEntryCodegen. Only emitted when the route
            // earns
            // a navigator (else `raw.xNavigator(...)` would be an unresolved reference).
            val factoryFun =
              MemberName(entry.routePackageName, NavigatorCodegen.rawFactoryFunName(entry.x))
            add("val nav = raw.%M(%M.current)\n", factoryFun, LOCAL_ENTRY_ID)
          }
        }
        .add("%M<%T>(\n", ANDROID_FRAGMENT, fragmentClass)
        .indent()
        .add("arguments = %M(route) { route.%M(raw) },\n", REMEMBER, TO_BUNDLE)
        .apply {
          if (navWired) {
            add("onUpdate = { fragment -> %M(fragment, route, nav) },\n", BIND_GEZGIN)
          } else {
            // No navigator for this route → no-nav bindGezgin overload; gezginNav throws [FS5].
            add("onUpdate = { fragment -> %M(fragment, route) },\n", BIND_GEZGIN)
          }
        }
        .unindent()
        .add(")\n")
        .unindent()
        .add("}\n")
        .build()

    return FunSpec.builder("provide${entry.x}Entry").receiver(ENTRY_SCOPE).addCode(body).build()
  }
}
