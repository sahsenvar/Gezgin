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

// `androidx.fragment.compose.AndroidFragment` — FQ MemberName only, NO compile dependency on
// androidx.fragment anywhere in gezgin-processor (same "emit as FQ strings" discipline as MviEntryCodegen's
// Hilt/Koin/lifecycle references; §11.2 keeps the processor fragment-free). Pinned 1.8.9 => the 4-param
// overload (arguments, onUpdate) — NO `maxLifecycle`/`fragmentState` (Task 6.0 §1a, re-verified in review).
private val ANDROID_FRAGMENT = MemberName("androidx.fragment.compose", "AndroidFragment")

// gezgin-core runtime glue (Task 6.2 Part B) — a REAL compile dependency (gezgin-core already IS one for
// every Gezgin module, unlike androidx.fragment). Emitted as ordinary imported member calls.
private val TO_BUNDLE = MemberName(FRAGMENT_RT_PKG, "toBundle")
private val BIND_GEZGIN = MemberName(FRAGMENT_RT_PKG, "bindGezgin")

/**
 * Task 6.2 — emits `fun GezginEntryScope.provideXEntry()` for every [FragmentEntryModel]
 * [dev.gezgin.processor.fragment.FragmentModelReader] resolved (spec §11.1 brownfield Fragment interop).
 * The THIRD entry codegen, alongside core-mode [EntryCodegen] and MVI-mode [MviEntryCodegen]: same
 * `GezginEntryScope` extension + `register<Route>(...)` shape, grouped one [FileSpec] per Fragment package —
 * but into a SEPARATE `GezginFragmentEntries.kt` (mirrors `MviEntryCodegen`'s own separate-file rationale)
 * so a module mixing entry styles gets `GezginEntries.kt` / `GezginMviEntries.kt` / `GezginFragmentEntries.kt`
 * with NO same-name-same-package collision by construction (function-name clashes across the kinds are
 * prevented by `SC6` for core/MVI and by `FS4` for Fragment).
 *
 * ```kotlin
 * fun GezginEntryScope.provideOrderChainEntry() {
 *     register<OrderChainRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->
 *         val raw = LocalGezginRawNavigator.current
 *         val nav = raw.orderChainNavigator(LocalGezginEntryId.current)     // factory qualified by route pkg
 *         AndroidFragment<OrderChainFragment>(                              // FQ, 4-param 1.8.9 form
 *             arguments = route.toBundle(raw),                             // route → Bundle (PD-safe encode)
 *             onUpdate = { fragment -> bindGezgin(fragment, route, nav) }, // live-ref re-attach (registry)
 *         )
 *     }
 * }
 * ```
 *
 * **Screen-only (§11.2).** Fragment interop has no dialog/bottom-sheet/fullscreen variant — every emitted
 * `register` is `kind = EntryKind.SCREEN`, unconditionally.
 *
 * **Navigator wiring — UNCONDITIONAL** (Task 6.0 verbatim binding shape). Unlike core-mode's conditional
 * `hasNavParam`, a Fragment always wires `nav` because `bindGezgin(fragment, route, nav)` always needs it and
 * `gezginNav` is a first-class part of the interop contract. The factory call (`raw.xNavigator(entryId)`,
 * qualified against [FragmentEntryModel.routePackageName] — cross-module-safe, exactly like [EntryCodegen]).
 * KNOWN LIMITATION: a `@FragmentScreen` route with NO edges/back-edges/result-contract earns no
 * `NavigatorCodegen`-generated `xNavigator` factory, so its emitted `raw.xNavigator(...)` would be an
 * unresolved reference — realistically never hit (a brownfield screen navigates); a future
 * `routeHasNavigator` flag on the model could gate it, deliberately deferred (YAGNI; no new cross-cutting
 * model/graph threading into the reader).
 */
object FragmentEntryCodegen {

    fun generate(entries: List<FragmentEntryModel>): List<FileSpec> =
        entries.groupBy { it.packageName }.map { (packageName, group) ->
            FileSpec.builder(packageName, "GezginFragmentEntries")
                .apply { group.forEach { addFunction(provideFragmentEntryFun(it)) } }
                .build()
        }

    private fun provideFragmentEntryFun(entry: FragmentEntryModel): FunSpec {
        val routeClass = ClassName.bestGuess(entry.routeFq)
        val fragmentClass = ClassName.bestGuess(entry.fragmentFq)
        // The navigator FACTORY extension lives in the route's OWN package ([routePackageName]) — a
        // different package (and, cross-module, a different MODULE) than this file's, so `%M` (imported),
        // identical to EntryCodegen/MviEntryCodegen.
        val factoryFun = MemberName(entry.routePackageName, NavigatorCodegen.rawFactoryFunName(entry.x))

        val body = CodeBlock.builder()
            .add(
                "register<%T>(kind = %T.SCREEN, noBack = %L) { route ->\n",
                routeClass,
                ENTRY_KIND,
                entry.noBack,
            )
            .indent()
            // `raw` bound to a val (needed TWICE: once for the factory, once for route.toBundle(raw)).
            .add("val raw = %M.current\n", LOCAL_RAW_NAVIGATOR)
            .add("val nav = raw.%M(%M.current)\n", factoryFun, LOCAL_ENTRY_ID)
            .add("%M<%T>(\n", ANDROID_FRAGMENT, fragmentClass)
            .indent()
            .add("arguments = route.%M(raw),\n", TO_BUNDLE)
            .add("onUpdate = { fragment -> %M(fragment, route, nav) },\n", BIND_GEZGIN)
            .unindent()
            .add(")\n")
            .unindent()
            .add("}\n")
            .build()

        return FunSpec.builder("provide${entry.x}Entry")
            .receiver(ENTRY_SCOPE)
            .addCode(body)
            .build()
    }
}
