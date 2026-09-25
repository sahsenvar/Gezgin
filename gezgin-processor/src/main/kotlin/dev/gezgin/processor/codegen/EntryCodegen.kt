package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import dev.gezgin.processor.entry.CallbackArgSource
import dev.gezgin.processor.entry.CallbackEntryArg
import dev.gezgin.processor.entry.EntryFunctionModel

private const val COMPOSE_PKG = "dev.gezgin.core.compose"

private val ENTRY_SCOPE = ClassName(COMPOSE_PKG, "GezginEntryScope")
private val ENTRY_KIND = ClassName(COMPOSE_PKG, "EntryKind")
private val LOCAL_ENTRY_ID = MemberName(COMPOSE_PKG, "LocalGezginEntryId")
private val LOCAL_RAW_NAVIGATOR = MemberName(COMPOSE_PKG, "LocalGezginRawNavigator")

/**
 * Emits `fun GezginEntryScope.provideXEntry()` for every [EntryFunctionModel]
 * [dev.gezgin.processor.entry.EntryModelReader] resolved in core mode:
 * ```kotlin
 * fun GezginEntryScope.provideOrderChainEntry() {
 *     register<OrderChainRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->
 *         val nav = LocalGezginRawNavigator.current.orderChainNavigator(LocalGezginEntryId.current)
 *         OrderChainScreen(route, nav)
 *     }
 * }
 * ```
 *
 * One [FileSpec] (`GezginEntries.kt`) per composable package — a composable may live in a different
 * module/package than the routes it registers, so (unlike [NavigatorCodegen]/[TestApiCodegen],
 * which share the single nav-topology package) this groups by [EntryFunctionModel.packageName]
 * instead. The navigator FACTORY call (`xNavigator(entryId)`) is qualified against each entry's own
 * [EntryFunctionModel.routePackageName] — the package the route DECLARATION (and thus
 * [NavigatorCodegen]'s factory) lives in. Reading it per-entry off the route (rather than off one
 * shared nav-topology package) is what makes the factory import resolve in a cross-module feature,
 * whose own model has no graphs and hence no target package of its own.
 */
internal object EntryCodegen {

  fun generate(entries: List<EntryFunctionModel>): List<FileSpec> =
    // Sort before grouping so package and per-file route order are reproducible. KSP symbol order
    // is not contractually stable; graph-derived codegen already sorts by fully qualified name.
    entries
      .sortedWith(compareBy({ it.packageName }, { it.routeFq }))
      .groupBy { it.packageName }
      .map { (packageName, group) ->
        FileSpec.builder(packageName, "GezginEntries")
          // A nav-wired register body reads the @GezginInternalApi LocalGezginRawNavigator and
          // LocalGezginEntryId; opt in the file only when at least one entry wires nav.
          .apply {
            if (group.any { it.hasNavParam || it.callbackArgs != null }) optInGezginInternalApi()
          }
          .apply { group.forEach { addFunction(provideEntryFun(it)) } }
          .build()
      }

  private fun provideEntryFun(entry: EntryFunctionModel): FunSpec {
    if (entry.callbackArgs != null) return provideCallbackEntryFun(entry, entry.callbackArgs)
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

  /**
   * A callback-route entry: route fields are forwarded by name, and each callback runs only while
   * the entry is still on the stack, so a late or duplicate click after the modal closed is
   * ignored. The route's `@OnDismiss` field becomes the container-dismissal hook.
   */
  private fun provideCallbackEntryFun(
    entry: EntryFunctionModel,
    args: List<CallbackEntryArg>,
  ): FunSpec {
    val routeClass = ClassName.bestGuess(entry.routeFq)
    val composableFun = MemberName(entry.packageName, entry.functionSimpleName)
    val body = CodeBlock.builder()
    body.add(
      "register<%T>(kind = %T.%L, noBack = %L",
      routeClass,
      ENTRY_KIND,
      entry.kind.name,
      entry.noBack,
    )
    entry.onDismissField?.let { body.add(", onDismiss·=·{·it.%N()·}", it) }
    body.add(") { route ->\n").indent()
    body.add("val raw = %M.current\n", LOCAL_RAW_NAVIGATOR)
    body.add("val entryId = %M.current\n", LOCAL_ENTRY_ID)
    if (args.any { it.source == CallbackArgSource.Nav }) {
      val factoryFun =
        MemberName(entry.routePackageName, NavigatorCodegen.rawFactoryFunName(entry.x))
      body.add("val nav = raw.%M(entryId)\n", factoryFun)
    }
    body.add("%M(\n", composableFun).indent()
    args.forEach { arg ->
      when (val source = arg.source) {
        CallbackArgSource.RouteInstance -> body.add("%N = route,\n", arg.name)
        CallbackArgSource.Nav -> body.add("%N = nav,\n", arg.name)
        CallbackArgSource.Field -> body.add("%N = route.%N,\n", arg.name, arg.name)
        is CallbackArgSource.Callback -> {
          val params = List(source.arity) { "p$it" }
          val lambdaHead = if (params.isEmpty()) "" else params.joinToString(", ") + " -> "
          body.add(
            "%N = { ${lambdaHead}if (raw.isOnStack(entryId)) route.%N(${params.joinToString(", ")}) },\n"
              .replace(" ", "·"),
            arg.name,
            arg.name,
          )
        }
      }
    }
    body.unindent().add(")\n")
    body.unindent().add("}\n")
    return FunSpec.builder("provide${entry.x}Entry")
      .receiver(ENTRY_SCOPE)
      .addCode(body.build())
      .build()
  }
}
