package dev.gezgin.processor.mvi

/**
 * The route/nav/other classification of a [ViewModelModel]'s primary-constructor params, aggregated
 * into the three booleans both MVI-mode consumers key off.
 * - [vmHasNav] — a DI-relevant ctor param is a `nav` (drives the resolver's `nav:` param +
 *   `viewModel(nav, route)`).
 * - [vmHasRoute] — a DI-relevant ctor param is the route (drives `args` in the supplied-args list).
 * - `emitDefault` — a default `viewModel` resolver is emittable AT ALL (every DI-relevant param is
 *   route/nav-typed AND no role is duplicated — see [VmDiClassifier.classify]).
 */
internal data class VmDiClassification(
  val vmHasNav: Boolean,
  val vmHasRoute: Boolean,
  val emitDefault: Boolean,
)

/**
 * Shared DI-param classification for a `@MviViewModel`'s primary constructor. Both
 * [dev.gezgin.processor.entry.EntryModelReader] (for the `MV7` nav-presence guardrail) and
 * [dev.gezgin.processor.codegen.MviEntryCodegen] (for the default `viewModel` resolver) must agree
 * on whether a VM's ctor needs `nav`/`route` and whether a default resolver is even emittable — so
 * that logic lives here once rather than being duplicated in each.
 *
 * **Classification precedence:** type decides; name is only a fallback. A param is NAV when its
 * type IS the route's `${x}Navigator` ([VmCtorParam.typeFq] == [navigatorTypeFq]). The `nav` NAME
 * classifies NAV *only* when the type failed to resolve ([VmCtorParam.isError]) — the one
 * legitimate case being a same-module `nav: XNavigator` whose navigator class isn't generated yet
 * in this KSP round. A RESOLVED non-navigator param named `nav` (e.g. A Koin `@InjectedParam nav:
 * AnalyticsTracker`) is classified by its type (OTHER) — NOT hijacked by the name, which previously
 * produced a default resolver that compiled but crashed in Koin's by-TYPE param lookup at first
 * render (and a spurious `MV7`). The route type always resolves (user-defined), so ROUTE is matched
 * by type.
 */
internal object VmDiClassifier {

  enum class Role {
    ROUTE,
    NAV,
    OTHER,
  }

  /**
   * The `${routePackageName}.${x}Navigator` FQ a same-module `nav` param would reference (see
   * [roleOf]).
   */
  fun navigatorTypeFq(routePackageName: String, x: String): String =
    "$routePackageName.${x}Navigator"

  fun roleOf(param: VmCtorParam, routeFq: String, navigatorTypeFq: String): Role =
    when {
      param.typeFq == routeFq -> Role.ROUTE
      param.typeFq == navigatorTypeFq -> Role.NAV
      // Use the name only when a same-round generated navigator type is unresolved. A resolved
      // non-navigator remains OTHER regardless of its parameter name.
      param.name == "nav" && param.isError -> Role.NAV
      else -> Role.OTHER
    }

  /**
   * The DI-relevant ctor params — the ones the default resolver must supply itself: ALL for
   * androidx; only `@Assisted`/`@InjectedParam`-annotated for Hilt-assisted/Koin; NONE for plain
   * Hilt (Hilt injects everything, Gezgin supplies nothing).
   */
  fun relevantParams(vm: ViewModelModel): List<VmCtorParam> =
    when (vm.di) {
      VmDiKind.ANDROIDX -> vm.ctorParams
      VmDiKind.HILT_ASSISTED,
      VmDiKind.KOIN -> vm.ctorParams.filter { it.diAnnotated }
      VmDiKind.HILT_PLAIN -> emptyList()
    }

  /** The aggregate [VmDiClassification] both `EntryModelReader` and `MviEntryCodegen` consume. */
  fun classify(vm: ViewModelModel, routeFq: String, navigatorTypeFq: String): VmDiClassification {
    val relevant = relevantParams(vm)
    val roles = relevant.map { it to roleOf(it, routeFq, navigatorTypeFq) }
    val routeCount = roles.count { it.second == Role.ROUTE }
    val navCount = roles.count { it.second == Role.NAV }
    // An OTHER parameter with a Kotlin default is omitted from named constructor calls; only a
    // non-defaulted OTHER parameter blocks automatic resolution.
    val blockingOtherCount = roles.count { it.second == Role.OTHER && !it.first.hasDefault }
    return VmDiClassification(
      vmHasNav = navCount > 0,
      vmHasRoute = routeCount > 0,
      // Emit a default only when every required value has a unique route or navigator role.
      emitDefault = blockingOtherCount == 0 && routeCount <= 1 && navCount <= 1,
    )
  }
}
