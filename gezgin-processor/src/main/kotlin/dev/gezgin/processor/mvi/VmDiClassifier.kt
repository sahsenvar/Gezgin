package dev.gezgin.processor.mvi

/**
 * The route/nav/other classification of a [ViewModelModel]'s primary-constructor params, aggregated
 * into the three booleans both MVI-mode consumers key off (Faz 5.2, §10.1).
 *
 * - [vmHasNav] — a DI-relevant ctor param is a `nav` (drives the resolver's `nav:` param + `viewModel(nav, route)`).
 * - [vmHasRoute] — a DI-relevant ctor param is the route (drives `args` in the supplied-args list).
 * - [emitDefault] — a default `viewModel` resolver is emittable AT ALL (every DI-relevant param is
 *   route/nav-typed AND no role is duplicated — see [VmDiClassifier.classify]).
 */
data class VmDiClassification(
    val vmHasNav: Boolean,
    val vmHasRoute: Boolean,
    val emitDefault: Boolean,
)

/**
 * Shared DI-param classification for a `@ViewModel`'s primary constructor (§10.1, Faz 5.2). Both
 * [dev.gezgin.processor.entry.EntryModelReader] (for the `MV7` nav-presence guardrail) and
 * [dev.gezgin.processor.codegen.MviEntryCodegen] (for the default `viewModel` resolver) must agree on
 * whether a VM's ctor needs `nav`/`route` and whether a default resolver is even emittable — so that
 * logic lives here ONCE rather than being duplicated in each (a drift hazard flagged in review).
 *
 * A ctor param is classified by [VmCtorParam.name]/[VmCtorParam.typeFq] because a same-module
 * `nav: XNavigator` type is not yet generated in the KSP round that reads it (its `typeFq` may be an
 * unresolved error type) — so nav is matched by the `nav` name convention as well as by type. The route
 * type always resolves (user-defined), so ROUTE is matched by type.
 */
object VmDiClassifier {

    enum class Role { ROUTE, NAV, OTHER }

    /** The `${routePackageName}.${x}Navigator` FQ a same-module `nav` param would reference (see [roleOf]). */
    fun navigatorTypeFq(routePackageName: String, x: String): String = "$routePackageName.${x}Navigator"

    fun roleOf(param: VmCtorParam, routeFq: String, navigatorTypeFq: String): Role = when {
        param.typeFq == routeFq -> Role.ROUTE
        param.name == "nav" || param.typeFq == navigatorTypeFq -> Role.NAV
        else -> Role.OTHER
    }

    /**
     * The DI-relevant ctor params — the ones the default resolver must supply itself: ALL for androidx;
     * only `@Assisted`/`@InjectedParam`-annotated for Hilt-assisted/Koin; NONE for plain Hilt (Hilt
     * injects everything, Gezgin supplies nothing).
     */
    fun relevantParams(vm: ViewModelModel): List<VmCtorParam> = when (vm.di) {
        VmDiKind.ANDROIDX -> vm.ctorParams
        VmDiKind.HILT_ASSISTED, VmDiKind.KOIN -> vm.ctorParams.filter { it.diAnnotated }
        VmDiKind.HILT_PLAIN -> emptyList()
    }

    /** The aggregate [VmDiClassification] both `EntryModelReader` (MV7) and `MviEntryCodegen` consume. */
    fun classify(vm: ViewModelModel, routeFq: String, navigatorTypeFq: String): VmDiClassification {
        val relevant = relevantParams(vm)
        val roles = relevant.map { roleOf(it, routeFq, navigatorTypeFq) }
        val routeCount = roles.count { it == Role.ROUTE }
        val navCount = roles.count { it == Role.NAV }
        val otherCount = roles.count { it == Role.OTHER }
        return VmDiClassification(
            vmHasNav = navCount > 0,
            vmHasRoute = routeCount > 0,
            // A default is emittable only when every relevant param is route/nav (no OTHER — Problem 1)
            // AND neither role is duplicated: two route- (or two nav-) typed params can't be positionally
            // disambiguated by the resolver, so a default would silently emit `VM(args, args)`. In that
            // case fall back to "no default" — the `viewModel` param becomes required (user resolves it).
            emitDefault = otherCount == 0 && routeCount <= 1 && navCount <= 1,
        )
    }
}
