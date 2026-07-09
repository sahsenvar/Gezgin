package dev.gezgin.processor.fragment

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.entry.EntryFunctionModel

internal const val FRAGMENT_SCREEN_FQ = "dev.gezgin.core.annotation.FragmentScreen"
private const val ROUTE_FQ = "dev.gezgin.core.Route"
private const val NO_BACK_FQ = "dev.gezgin.core.annotation.NoBack"

/**
 * Task 6.1 — reads every `@FragmentScreen(Route::class)`-annotated CLASS (spec §11/§11.1/§11.2 brownfield
 * Fragment interop) into a validated [FragmentEntryModel] list, mirroring
 * [dev.gezgin.processor.mvi.ViewModelModelReader]'s structure — both read a CLASS-target,
 * mandatory-route-arg annotation and use the collect-all-then-fail, bracketed-code error idiom
 * (`logger.error("[FS…] …")`). [read] never throws — it reports every violation in one pass and returns
 * whether the read was clean alongside whatever models DID resolve.
 *
 * All `androidx.fragment.*` symbols are read as **string FQNs** — `gezgin-processor` gains NO compile
 * dependency on `androidx.fragment` (only the test source set stubs a local `Fragment` for fixtures),
 * exactly like the `dev.gezgin.mvi.*` reads.
 *
 * Guardrails (`FS`-family — Fragment Screen):
 * - **`FS1` — parameterized-ctor rejection (§11.1, "parametreli Fragment ctor yasak").** A
 *   `@FragmentScreen` class whose primary constructor has ANY parameter is rejected: Android recreates
 *   Fragments via a no-arg ctor after PD/config-change, so ctor params would be silently lost or crash.
 *   The route/nav arrive through `gezginArgs`/`gezginNav` (Task 6.2), never the ctor. → no model emitted.
 * - **`FS2` — route type sanity.** The resolved route type must implement `dev.gezgin.core.Route`
 *   (mirrors [dev.gezgin.processor.entry.EntryModelReader]'s `SC5` — same [getAllSuperTypes] walk; a
 *   plain interface-implementation check, not a generic-substitution case). The annotation's
 *   `route: KClass<out Route>` bound already enforces this at the frontend, so `FS2` is a defensive
 *   guard for the degenerate cases (a route arg that fails to resolve, or resolves to a non-`Route`
 *   type despite the bound). → no model emitted.
 * - **`FS3` — duplicate route registration (cross-kind aware).** A route may back only ONE registration.
 *   [FragmentModelReader] cross-checks each `@FragmentScreen`'s route against BOTH (a) the already-built
 *   [entries] (core-mode `@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal` + MVI-mode content, which
 *   already share `EntryModelReader`'s single `seenRouteFqs`) AND (b) previously-seen `@FragmentScreen`s.
 *   Mirrors `SC4`/`MV4` semantics: a route claimed by two registrations would compile two
 *   `register<Route>` calls and crash at runtime. → no model emitted for the colliding Fragment.
 * - **`FS4` — provide-name clash (cross-kind aware, mirrors `SC6`).** Two entries in the SAME package that
 *   derive the SAME `x` produce two identical-signature `provideXEntry()` declarations — and Kotlin
 *   package-level function names collide across FILES too, so this catches a Fragment entry clashing with
 *   another Fragment entry (same-kind, e.g. `Detail`/`DetailRoute` routes → both `provideDetailEntry`) AND a
 *   Fragment entry clashing with an existing core-mode / MVI-mode `provideXEntry` name (cross-kind, since
 *   `GezginFragmentEntries.kt` sits beside `GezginEntries.kt` / `GezginMviEntries.kt` in one package). Same
 *   `(packageName, x)` uniqueness key `EntryModelReader`'s `SC6` uses, cross-checked against the same
 *   materialized [entries] plus previously-seen Fragments. → no model emitted for the colliding Fragment.
 *
 * **FS3/FS4 wiring choice (post-hoc cross-check, not shared-map seeding):** rather than seeding
 * `EntryModelReader`'s private `seenRouteFqs` (which would require changing its constructor and would
 * report the cross-kind collision under `SC4`), this reader runs AFTER the entry reader and cross-checks
 * the already-built [entries] list — the materialized output of that shared map. This keeps
 * `EntryModelReader` UNTOUCHED (zero change to core-mode / MVI-mode behavior) and lets `FS3` uniformly
 * own every Fragment duplicate (same-kind AND cross-kind) under one code. See the task-6.1 report.
 */
class FragmentModelReader(
    private val resolver: Resolver,
    private val logger: KSPLogger,
    /** The core-mode + MVI-mode entries already resolved by `EntryModelReader` — the FS3 cross-check set. */
    private val entries: List<EntryFunctionModel> = emptyList(),
) {

    private var ok = true

    // routeFq -> the registration that already owns it (an entry's fn name, or an earlier Fragment's
    // simple name). Seeded from the built entries so a Fragment colliding with a core/MVI content is
    // caught (FS3 cross-kind) exactly like two Fragments colliding (FS3 same-kind).
    private val ownerByRouteFq: MutableMap<String, String> =
        entries.associate { it.routeFq to it.functionSimpleName }.toMutableMap()

    // (packageName, x) -> the entry that already emits `provideXEntry()` there (a core/MVI fn name, or an
    // earlier Fragment's simple name). Seeded from the built entries — same `SC6` key ([EntryFunctionModel]
    // carries both `packageName` and `x`) — so a Fragment `provideXEntry` clashing with a core/MVI one is
    // caught (FS4 cross-kind) exactly like two Fragments clashing (FS4 same-kind).
    private val ownerByProvideName: MutableMap<Pair<String, String>, String> =
        entries.associate { (it.packageName to it.x) to it.functionSimpleName }.toMutableMap()

    fun read(): Pair<List<FragmentEntryModel>, Boolean> {
        val models = resolver.getSymbolsWithAnnotation(FRAGMENT_SCREEN_FQ)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { decl -> buildModel(decl) }
            .toList()
        return models to ok
    }

    private fun buildModel(decl: KSClassDeclaration): FragmentEntryModel? {
        val fragmentFq = decl.qualifiedName?.asString() ?: return null
        val fragmentSimpleName = decl.simpleName.asString()

        // FS1 — parametreli Fragment ctor yasak (§11.1). Android's no-arg-ctor recreation contract.
        val ctorParams = decl.primaryConstructor?.parameters.orEmpty()
        if (ctorParams.isNotEmpty()) {
            error(
                "FS1",
                "$fragmentSimpleName: @FragmentScreen'li Fragment'ın ctor'u parametresiz OLMALIDIR — " +
                    "şu param(lar)ı var: ${ctorParams.joinToString { it.name?.asString().orEmpty() }} " +
                    "(Android PD/config-change'de Fragment'ı argsız ctor'la yeniden yaratır; route/nav " +
                    "ctor'dan DEĞİL gezginArgs/gezginNav delege'lerinden gelir, §11.1)",
            )
            return null
        }

        // FS2 — route type sanity (mirrors SC5). The `route: KClass<out Route>` bound normally guarantees
        // this at the frontend; the null-resolve / non-Route branches are defensive.
        val annotation = decl.annotations.first { it.fqName() == FRAGMENT_SCREEN_FQ }
        val routeType = annotation.classArg("route")
        val routeDecl = routeType?.declaration as? KSClassDeclaration
        if (routeDecl == null) {
            error("FS2", "$fragmentSimpleName: @FragmentScreen(route=…) türü çözülemedi")
            return null
        }
        val routeFq = routeDecl.qualifiedName?.asString()
        val implementsRoute = routeFq == ROUTE_FQ ||
            routeDecl.getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == ROUTE_FQ }
        if (routeFq == null || !implementsRoute) {
            error(
                "FS2",
                "$fragmentSimpleName: route tipi (${routeDecl.qualifiedName?.asString()}) " +
                    "dev.gezgin.core.Route implement etmiyor",
            )
            return null
        }

        // FS3 — duplicate route registration (cross-kind: vs core/MVI entries; same-kind: vs earlier
        // @FragmentScreen). Two registrations on one route → two register<Route> calls → runtime crash.
        val previousOwner = ownerByRouteFq[routeFq]
        if (previousOwner != null) {
            error(
                "FS3",
                "route ${routeFq.substringAfterLast('.')} birden çok destination tarafından kaydediliyor: " +
                    "$previousOwner, $fragmentSimpleName — bir route'a yalnız bir @Screen/@Dialog/" +
                    "@BottomSheet/@FullscreenModal/@FragmentScreen bağlanabilir (SC4/MV4 ile aynı kural)",
            )
            return null
        }

        val packageName = decl.packageName.asString()
        val x = NavigatorCodegen.navigatorX(routeDecl.simpleName.asString())

        // FS4 — provide-name clash (cross-kind: vs core/MVI provideXEntry; same-kind: vs earlier
        // @FragmentScreen). Same (packageName, x) as an existing entry → two provideXEntry() with the same
        // name in one package (Kotlin fn names collide across files too) → "conflicting overloads". Mirrors
        // SC6 exactly. Checked AFTER FS3 so a same-route pair reports the more specific FS3 first.
        val provideKey = packageName to x
        val previousProvideOwner = ownerByProvideName[provideKey]
        if (previousProvideOwner != null) {
            error(
                "FS4",
                "$packageName paketinde provide${x}Entry() birden çok destination tarafından üretiliyor: " +
                    "$previousProvideOwner, $fragmentSimpleName — route adlarının aynı 'X' türetimine (${x}) " +
                    "çözülmesi (SC6 ile aynı kural; @FragmentScreen entry'si core/MVI provideXEntry'siyle de " +
                    "çakışabilir — aynı pakette GezginFragmentEntries.kt, GezginEntries.kt yan yana)",
            )
            return null
        }

        ownerByRouteFq[routeFq] = fragmentSimpleName
        ownerByProvideName[provideKey] = fragmentSimpleName

        return FragmentEntryModel(
            fragmentFq = fragmentFq,
            fragmentSimpleName = fragmentSimpleName,
            packageName = packageName,
            routeFq = routeFq,
            // Read off the route DECLARATION (KSP-resolvable cross-module), not this module's model —
            // same reasoning as EntryFunctionModel.routePackageName / .noBack.
            routePackageName = routeDecl.packageName.asString(),
            noBack = routeDecl.hasAnnotation(NO_BACK_FQ),
            x = x,
        )
    }

    private fun KSAnnotated.hasAnnotation(fq: String): Boolean = annotations.any { it.fqName() == fq }

    private fun KSAnnotation.fqName(): String? = annotationType.resolve().declaration.qualifiedName?.asString()

    private fun KSAnnotation.arg(name: String): KSValueArgument? =
        arguments.firstOrNull { it.name?.asString() == name } ?: defaultArguments.firstOrNull { it.name?.asString() == name }

    private fun KSAnnotation.classArg(name: String): KSType? = arg(name)?.value as? KSType

    private fun error(code: String, message: String) {
        logger.error("[$code] $message")
        ok = false
    }
}
