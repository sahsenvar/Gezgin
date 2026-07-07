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
 *
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
 * still qualified against [navigatorPackageName] — that's where [NavigatorCodegen] emits it.
 */
object EntryCodegen {

    fun generate(entries: List<EntryFunctionModel>, navigatorPackageName: String): List<FileSpec> =
        entries.groupBy { it.packageName }.map { (packageName, group) ->
            FileSpec.builder(packageName, "GezginEntries")
                .apply { group.forEach { addFunction(provideEntryFun(it, navigatorPackageName)) } }
                .build()
        }

    private fun provideEntryFun(entry: EntryFunctionModel, navigatorPackageName: String): FunSpec {
        val routeClass = ClassName.bestGuess(entry.routeFq)
        val composableFun = MemberName(entry.packageName, entry.functionSimpleName)

        val callArgs = mutableListOf<CodeBlock>()
        if (entry.hasRouteParam) callArgs += CodeBlock.of("route")

        val body = CodeBlock.builder()
            .add(
                "register<%T>(kind = %T.%L, noBack = %L) { route ->\n",
                routeClass,
                ENTRY_KIND,
                entry.kind.name,
                entry.noBack,
            )
            .indent()
        if (entry.hasNavParam) {
            // `%M` (not `%L`) for the factory extension fun — it lives in [navigatorPackageName],
            // a DIFFERENT package than this file's when the composable's own package differs, so it
            // needs a real import, not a bare unqualified call.
            val factoryFun = MemberName(navigatorPackageName, NavigatorCodegen.rawFactoryFunName(entry.x))
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
