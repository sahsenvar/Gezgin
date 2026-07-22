package dev.gezgin.processor.codegen

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel

/**
 * Decides whether an entry can wire an `xNavigator` factory.
 *
 * Same-module routes use [NavigatorCodegen.hasNavigator] because their navigator is generated in
 * the current KSP round and is not on the classpath yet. Cross-module routes probe the compiled
 * `XNavigator` class and verify its `GezginNavigatorFor.route` identity against the entry route;
 * matching by derived class name alone is insufficient because distinct route names can derive the
 * same navigator name.
 *
 * The probe searches `routePackageName` because the processor's package validation guarantees that
 * generated navigators share the route package. The result is embedded in generated entry code, so
 * consumers need a clean rebuild after a dependency changes whether a route has navigation edges
 * and the build system does not invalidate the dependent KSP output.
 */
internal object NavigatorProbe {

  const val MARKER_FQ = "dev.gezgin.core.annotation.GezginNavigatorFor"

  fun routeEarnsNavigator(
    resolver: Resolver,
    routeModel: RouteModel?,
    graphsByFq: Map<String, GraphModelNode>,
    routePackageName: String,
    x: String,
    routeFq: String,
  ): Boolean =
    if (routeModel != null) {
      NavigatorCodegen.hasNavigator(routeModel, graphsByFq)
    } else {
      probeCompiledNavigator(resolver, routePackageName, x, routeFq)
    }

  /**
   * `${routePackageName}.${x}Navigator` classpath'te var VE `GezginNavigatorFor` damgası
   * `routeFq`'yi gösteriyorsa `true`. Sınıf yoksa (edge'siz route → hiç navigator yok) ya da damga
   * başka bir route'u gösteriyorsa (ada-çakışan decoy) `false`.
   */
  private fun probeCompiledNavigator(
    resolver: Resolver,
    routePackageName: String,
    x: String,
    routeFq: String,
  ): Boolean {
    val navClass =
      resolver.getClassDeclarationByName("$routePackageName.${x}Navigator") ?: return false
    val markedRouteFq =
      navClass.annotations
        .firstOrNull {
          it.annotationType.resolve().declaration.qualifiedName?.asString() == MARKER_FQ
        }
        ?.arguments
        ?.firstOrNull { it.name?.asString() == "route" }
        ?.let { it.value as? KSType }
        ?.declaration
        ?.qualifiedName
        ?.asString()
    return markedRouteFq == routeFq
  }
}
