package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel

private const val TEST_PKG = "dev.gezgin.test"

private val TEST_NAVIGATOR = ClassName(TEST_PKG, "GezginTestNavigator")

/**
 * Task 2.6 — §13's typed test API: a per-source `fun GezginTestNavigator.fromX(): XNavigator`
 * extension for every route [NavigatorCodegen] actually generates a navigator for (including a
 * bare route whose navigator exposes only the implicit one-step `back()` operation).
 * Each accessor resolves the NEAREST entry of its route on the stack via
 * `GezginTestNavigator.entryIdOf` (§2.6 core decision) and hands it to the same
 * `RawNavigator.xNavigator(entryId)` factory [NavigatorCodegen] already emits.
 *
 * Emitted into a SEPARATE file (`GezginTestAccessors.kt`) and gated behind the
 * `gezgin.emitTestAccessors=true` KSP option (default `false`, mirroring the `gezgin.emitSerializers`
 * opt-OUT precedent) — production modules never depend on `:gezgin-test`; only a test source set's
 * KSP configuration turns this on.
 */
internal object TestApiCodegen {

    fun generate(model: GraphModel, packageName: String): FileSpec? {
        val graphsByFq = model.graphs.associateBy(GraphModelNode::fqName)
        val funs = model.routes.mapNotNull { route -> fromFun(route, graphsByFq, packageName) }
        if (funs.isEmpty()) return null
        return FileSpec.builder(packageName, "GezginTestAccessors")
            // K4 — the generated fromX() accessors read GezginTestNavigator.raw, gated by @GezginInternalApi.
            .optInGezginInternalApi()
            .addFunctions(funs)
            .build()
    }

    private fun fromFun(
        route: RouteModel,
        graphsByFq: Map<String, GraphModelNode>,
        packageName: String,
    ): FunSpec? {
        if (!NavigatorCodegen.hasNavigator(route, graphsByFq)) return null

        val x = NavigatorCodegen.navigatorX(route.simpleName)
        val navigatorClass = ClassName(packageName, "${x}Navigator")
        val routeClass = ClassName.bestGuess(route.fqName)
        val factoryFunName = NavigatorCodegen.rawFactoryFunName(x)

        return FunSpec.builder("from$x")
            .receiver(TEST_NAVIGATOR)
            .returns(navigatorClass)
            .addStatement("return raw.%L(entryIdOf(%T::class))", factoryFunName, routeClass)
            .build()
    }
}
