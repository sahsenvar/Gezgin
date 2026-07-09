package dev.gezgin.processor.fragment

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Modifier
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.entry.EntryFunctionModel

internal const val FRAGMENT_SCREEN_FQ = "dev.gezgin.core.annotation.FragmentScreen"
private const val ROUTE_FQ = "dev.gezgin.core.Route"
private const val NO_BACK_FQ = "dev.gezgin.core.annotation.NoBack"
private const val FRAGMENT_FQ = "androidx.fragment.app.Fragment"

// Modal presentation contracts (§7) — a @FragmentScreen route implementing one is FS7 (fragment interop
// is screen-only §11.2; the contract would be silently ignored).
private val MODAL_CONTRACT_FQS = setOf(
    "dev.gezgin.core.DialogContract",
    "dev.gezgin.core.FullscreenModalContract",
    "dev.gezgin.core.BottomSheetContract",
)

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
 * - **`FS1` — FragmentFactory-instantiability (§11.1).** Android recreates Fragments after PD/config-
 *   change via a PUBLIC no-arg ctor (`clazz.getConstructor().newInstance()`). `FS1` rejects every shape
 *   that would pass the frontend but crash inside `FragmentFactory.instantiate`: an `abstract` class
 *   (`InstantiationException`); an `inner` class (its ctor carries the outer instance); a parameterized
 *   primary ctor (the common case — a tailored gezginArgs/gezginNav message); and a class with only
 *   secondary ctors or a private/protected ctor (no accessible no-arg ctor → `NoSuchMethodException`).
 *   route/nav arrive through `gezginArgs`/`gezginNav` (Task 6.2), never the ctor. → no model emitted.
 * - **`FS2` — route type sanity + no bare `Route`.** The resolved route type must implement
 *   `dev.gezgin.core.Route` (mirrors `SC5` — same [getAllSuperTypes] walk) AND must not be the bare
 *   `Route` interface itself: unlike `@Screen` (where `Route::class` is a "derive from `route:` param"
 *   SENTINEL), `@FragmentScreen`'s route arg is mandatory and concrete, so `@FragmentScreen(Route::class)`
 *   would emit a `register<Route>` no concrete-class push ever matches — a DEAD registration. → no model.
 * - **`FS7` — screen-only, no modal contract (§11.2).** A route implementing a modal presentation
 *   contract (`DialogContract`/`BottomSheetContract`/`FullscreenModalContract`) is rejected: fragment
 *   interop always registers `kind = SCREEN`, so the contract would be SILENTLY ignored (the user asked
 *   for a modal, got a full-screen fragment). Hard-error like `SC8`/`MV8` (silent-drop). → no model.
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
 * - **`FS5` — nav-wiring guard (dispatch-site + RUNTIME, NOT a KSP rejection here).** Core-mode's `SC2` and
 *   MVI-mode's `MV7` REJECT a nav-wanting entry whose route earns no navigator. A `@FragmentScreen` can't be
 *   rejected the same way: an edge-less leaf (a display-only brownfield screen that only reads `gezginArgs`
 *   and never navigates) is LEGITIMATE. So `FS5` is split and lives OUTSIDE this graph-unaware reader: the
 *   whether-the-route-earns-a-navigator predicate (`NavigatorCodegen.hasNavigator` for a same-module route —
 *   exactly like `SC2`/`MV7`; a classpath probe for the compiled `XNavigator` class for a cross-module route,
 *   replacing the earlier `?: true` optimism) is computed at [dev.gezgin.processor.GezginProcessor]'s
 *   codegen dispatch site, [dev.gezgin.processor.codegen.FragmentEntryCodegen] SUPPRESSES nav wiring (no
 *   `val nav = raw.xNavigator(...)`, binds via the no-nav `bindGezgin(fragment, route)` overload) when it's
 *   false, and `gezginNav` throws the actionable `[FS5]` error at runtime (gezgin-core
 *   `FragmentBinding.android.kt`). This reader is untouched (no `GraphModel` in its ctor).
 * - **`FS6` — annotated class must BE a `Fragment`.** The annotated CLASS must extend
 *   `androidx.fragment.app.Fragment` ([getAllSuperTypes] string-FQN walk against [FRAGMENT_FQ], the EXACT
 *   mechanism `FS2` uses for its Route check). This covers a case `FS2` **structurally cannot**: `FS2`
 *   validates the annotation's `route` ARG type, `FS6` validates the ANNOTATED CLASS's own supertype — two
 *   different things. The frontend's `route: KClass<out Route>` bound leaves the class type unconstrained, so
 *   annotating a plain class / an `Activity` by mistake would otherwise surface as a confusing `T : Fragment`
 *   type-bound error inside the GENERATED `AndroidFragment<XFragment>` call, not an actionable `[FS6]`. → no
 *   model emitted.
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

        // FS1 — the Fragment must be FragmentFactory-instantiable: Android recreates it after PD/config-
        // change via a PUBLIC no-arg constructor (`clazz.getConstructor().newInstance()`). Four shapes
        // otherwise pass the frontend but crash at first display inside FragmentFactory.instantiate:
        //   abstract → InstantiationException; inner → ctor carries the outer instance → NoSuchMethodException;
        //   parameterized primary ctor / secondary-ctor-only / private ctor → no accessible no-arg ctor.
        // route/nav arrive through gezginArgs/gezginNav (Task 6.2), never the ctor (§11.1).
        if (Modifier.ABSTRACT in decl.modifiers) {
            error(
                "FS1",
                "$fragmentSimpleName: @FragmentScreen abstract bir sınıfa konamaz — Android onu argsız " +
                    "ctor'la yeniden yaratamaz (FragmentFactory.instantiate → InstantiationException). " +
                    "Somut bir Fragment alt sınıfı yap (§11.1)",
            )
            return null
        }
        if (Modifier.INNER in decl.modifiers) {
            error(
                "FS1",
                "$fragmentSimpleName: @FragmentScreen'li Fragment `inner` olamaz — inner ctor dış-sınıf " +
                    "örneğini taşır, argsız yeniden-yaratma çöker (NoSuchMethodException). Top-level ya da " +
                    "`static` (nested) bir sınıf yap (§11.1)",
            )
            return null
        }

        // FS1 — parametreli primary ctor yasak (§11.1) — en sık hata, özel/aksiyonel mesaj.
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

        // FS1 — erişilebilir (public) argümansız ctor şart (yalnız-secondary-ctor'lu ya da private-ctor'lu
        // sınıfları yakalar; bunlarda `getConstructor()` public argsız ctor bulamaz → NoSuchMethodException).
        val hasPublicNoArgCtor = decl.getConstructors().any { it.parameters.isEmpty() && it.isPublic() }
        if (!hasPublicNoArgCtor) {
            error(
                "FS1",
                "$fragmentSimpleName: @FragmentScreen'li Fragment'ın erişilebilir (public) argümansız bir " +
                    "ctor'u yok — yalnız secondary ctor'lu ya da private/protected ctor'lu bir sınıf argsız " +
                    "yeniden-yaratmada çöker (NoSuchMethodException). Public bir `constructor()` (ya da hiç " +
                    "ctor bildirmeyen) Fragment yaz (§11.1)",
            )
            return null
        }

        // FS6 — the annotated CLASS must actually extend `androidx.fragment.app.Fragment`. This checks the
        // ANNOTATED CLASS's own supertype — structurally DIFFERENT from FS2, which checks the annotation's
        // ROUTE ARG type; FS2 cannot cover it. The Kotlin frontend does NOT block a non-Fragment class here
        // (@FragmentScreen's only type bound is on `route: KClass<out Route>`), so without FS6 a realistic
        // mistake — annotating a plain class or an Activity — surfaces as a confusing `T : Fragment`
        // type-bound error deep inside the GENERATED `AndroidFragment<XFragment>` call rather than an
        // actionable [FS*] KSP diagnostic pointing at the user's own annotation. Same string-FQN
        // `getAllSuperTypes()` walk FS2 uses for its Route-implementation check.
        val extendsFragment = decl.getAllSuperTypes().any {
            it.declaration.qualifiedName?.asString() == FRAGMENT_FQ
        }
        if (!extendsFragment) {
            error(
                "FS6",
                "$fragmentSimpleName: @FragmentScreen yalnız bir androidx.fragment.app.Fragment alt sınıfına " +
                    "konulabilir — bu sınıf Fragment'ı extend ETMİYOR (düz bir sınıf/Activity'ye yanlışlıkla " +
                    "eklenmiş olabilir). Fragment'ı `class $fragmentSimpleName : Fragment()` yap ya da " +
                    "@FragmentScreen'i kaldır (route/nav bir Fragment host'una gezginArgs/gezginNav ile " +
                    "teslim edilir, §11.1)",
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
        // FS2 — the bare `Route` interface itself is NOT a valid destination. Unlike @Screen (where
        // `Route::class` is a "derive from the route: param" SENTINEL), @FragmentScreen's route arg is
        // MANDATORY and concrete — `@FragmentScreen(Route::class)` would emit `register<Route>` which no
        // concrete-class push (`key.route::class`) ever matches → DEAD registration (runtime "no entry").
        if (routeFq == ROUTE_FQ) {
            error(
                "FS2",
                "$fragmentSimpleName: @FragmentScreen(Route::class) — Route arayüzünün KENDİSİ route olamaz " +
                    "(somut bir route sınıfı ver). @Screen'deki `Route::class` = 'route: param'ından türet' " +
                    "sentinel'inin @FragmentScreen'de karşılığı yok; register<Route> hiçbir push'la eşleşmez " +
                    "→ ölü kayıt (§11.1)",
            )
            return null
        }
        val implementsRoute = routeDecl.getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == ROUTE_FQ }
        if (routeFq == null || !implementsRoute) {
            error(
                "FS2",
                "$fragmentSimpleName: route tipi (${routeDecl.qualifiedName?.asString()}) " +
                    "dev.gezgin.core.Route implement etmiyor",
            )
            return null
        }

        // FS7 (Faz-6 recheck) — Fragment interop is screen-only (§11.2). A route implementing a modal
        // presentation contract (Dialog/BottomSheet/FullscreenModal) would be registered as a plain SCREEN
        // (FragmentEntryCodegen unconditionally `kind = SCREEN`) with the contract SILENTLY ignored — the
        // user asked for a modal but gets a full-screen fragment, no diagnostic. Rejected like SC8/MV8
        // (silent-drop → hard error): fragment interop cannot present modals.
        val modalContract = routeDecl.getAllSuperTypes()
            .mapNotNull { it.declaration.qualifiedName?.asString() }
            .firstOrNull { it in MODAL_CONTRACT_FQS }
        if (modalContract != null) {
            error(
                "FS7",
                "$fragmentSimpleName: route ${routeFq.substringAfterLast('.')} " +
                    "${modalContract.substringAfterLast('.')} implement ediyor ama @FragmentScreen ekranları " +
                    "YALNIZ screen-mode render edilir (§11.2) — modal contract SESSİZCE yok sayılırdı " +
                    "(kullanıcı modal isterken düz tam-ekran fragment alırdı). Contract'ı route'tan kaldır ya " +
                    "da modal'ı Fragment yerine @Dialog/@BottomSheet/@FullscreenModal composable ile kur",
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
