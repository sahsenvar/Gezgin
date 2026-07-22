package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import dev.gezgin.processor.entry.EntryFunctionModel

private const val COMPOSE_PKG = "dev.gezgin.core.compose"

private val ENTRY_SCOPE = ClassName(COMPOSE_PKG, "GezginEntryScope")
private val ENTRY_KIND = ClassName(COMPOSE_PKG, "EntryKind")
private val LOCAL_ENTRY_ID = MemberName(COMPOSE_PKG, "LocalGezginEntryId")
private val LOCAL_RAW_NAVIGATOR = MemberName(COMPOSE_PKG, "LocalGezginRawNavigator")

/**
 * Task 3.4 — emits `fun GezginEntryScope.provideXEntry()` for every [EntryFunctionModel]
 * [dev.gezgin.processor.entry.EntryModelReader] resolved (spec §10.1/§12/§14 core-mode):
 * ```kotlin
 * fun GezginEntryScope.provideOrderChainEntry() {
 *     register<OrderChainRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->
 *         val nav = LocalGezginRawNavigator.current.orderChainNavigator(LocalGezginEntryId.current)
 *         OrderChainScreen(route, nav)
 *     }
 * }
 * ```
 *
 * One [FileSpec] (`GezginEntries.kt`) PER composable's own package (binding: "aynı pakete") — a
 * composable may live in a different module/package than the routes it registers, so (unlike
 * [NavigatorCodegen]/[TestApiCodegen], which share the single nav-topology package) this groups by
 * [EntryFunctionModel.packageName] instead. The navigator FACTORY call (`xNavigator(entryId)`) is
 * qualified against each entry's own [EntryFunctionModel.routePackageName] — the package the route
 * DECLARATION (and thus [NavigatorCodegen]'s factory) lives in. Reading it per-entry off the route
 * (rather than off one shared nav-topology package) is what makes the factory import resolve in a
 * cross-module feature, whose own model has no graphs and hence no target package of its own
 * (§3.3).
 */
internal object EntryCodegen {

  fun generate(entries: List<EntryFunctionModel>): List<FileSpec> =
    // Sort before grouping so file order (packageName) and per-file function order (routeFq,
    // unique)
    // are reproducible — KSP symbol order is not contractually stable (MN-1); graph-derived codegen
    // already sorts (ModelReader.routes.sortedBy fqName), this brings entry codegen to parity.
    entries
      .sortedWith(compareBy({ it.packageName }, { it.routeFq }))
      .groupBy { it.packageName }
      .map { (packageName, group) ->
        FileSpec.builder(packageName, "GezginEntries")
          // K4 — a nav-wired register body reads the @GezginInternalApi LocalGezginRawNavigator/
          // LocalGezginEntryId; opt in the file only when at least one entry wires nav.
          .apply { if (group.any { it.hasNavParam }) optInGezginInternalApi() }
          .apply { group.forEach { addFunction(provideEntryFun(it)) } }
          .build()
      }

  private fun provideEntryFun(entry: EntryFunctionModel): FunSpec {
    val routeClass = ClassName.bestGuess(entry.routeFq)
    val composableFun = MemberName(entry.packageName, entry.functionSimpleName)

    val callArgs = mutableListOf<CodeBlock>()
    if (entry.hasRouteParam) callArgs += CodeBlock.of("route")

    val body =
      CodeBlock.builder()
        .add(
          "register<%T>(kind = %T.%L, noBack = %L) { route ->\n",
          routeClass,
          ENTRY_KIND,
          entry.kind.name,
          entry.noBack,
        )
        .indent()
    if (entry.hasNavParam) {
      // `%M` (not `%L`) for the factory extension fun — it lives in the route's own package
      // ([EntryFunctionModel.routePackageName]), a DIFFERENT package (and, cross-module, a
      // different MODULE) than this file's, so it needs a real import, not a bare call.
      val factoryFun =
        MemberName(entry.routePackageName, NavigatorCodegen.rawFactoryFunName(entry.x))
      body.add(
        "val nav = %M.current.%M(%M.current)\n",
        LOCAL_RAW_NAVIGATOR,
        factoryFun,
        LOCAL_ENTRY_ID,
      )
      callArgs += CodeBlock.of("nav")
    }
    body.add("%M(", composableFun)
    callArgs.forEachIndexed { index, arg ->
      if (index > 0) body.add(", ")
      body.add(arg)
    }
    body.add(")\n")
    body.unindent().add("}\n")

    return FunSpec.builder("provide${entry.x}Entry")
      .receiver(ENTRY_SCOPE)
      .addCode(body.build())
      .build()
  }
}
