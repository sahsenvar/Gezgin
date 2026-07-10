package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import dev.gezgin.processor.model.EdgeKind
import dev.gezgin.processor.model.GraphModel
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.RouteModel

private const val CORE_PKG = "dev.gezgin.core"
private const val SERIALIZATION_MODULES_PKG = "kotlinx.serialization.modules"

private val ROUTE = ClassName(CORE_PKG, "Route")
private val GEZGIN_TOPOLOGY = ClassName(CORE_PKG, "GezginTopology")
private val FLOW_TYPE = ClassName(CORE_PKG, "FlowType")
private val EDGE_SPEC = ClassName(CORE_PKG, "EdgeSpec")
private val SERIALIZERS_MODULE = ClassName(SERIALIZATION_MODULES_PKG, "SerializersModule")
private val POLYMORPHIC = MemberName(SERIALIZATION_MODULES_PKG, "polymorphic")
private val SUBCLASS = MemberName(SERIALIZATION_MODULES_PKG, "subclass")

/**
 * The reified `kotlinx.serialization.serializer<T>()` lookup — used instead of `T.serializer()` so a
 * BUILTIN result type (`ResultRoute<Boolean>`/`<String>`/`<Int>` …) resolves too: those have no
 * companion `serializer()`, only the `kotlinx.serialization.builtins` extensions, whereas the
 * reified top-level helper covers builtin AND `@Serializable` types uniformly with one import.
 */
private val SERIALIZER = MemberName("kotlinx.serialization", "serializer")

/**
 * Task 2.4: emits the two generated-code artifacts derived from a validated [GraphModel] via
 * KotlinPoet.
 *
 * - [generateTopology] → `GezginGenerated.kt`: the loadable, executable `GezginTopology` — safe to
 *   generate unconditionally, since every result type is serializable (either `@Serializable` or a
 *   kotlinx builtin), resolved uniformly through the reified [SERIALIZER] lookup.
 * - [generateSerializers] → `GezginSerializers.kt`: the `SerializersModule` registering every
 *   concrete [dev.gezgin.core.Route] subtype for polymorphic serialization. Split into its own file
 *   (gated by the `gezgin.emitSerializers` KSP option, default `true`) purely so test compilations
 *   that can't wire the kotlinx-serialization compiler plugin can opt out of it without losing
 *   [generateTopology] coverage — `subclass(X::class)` needs no `.serializer()` call itself, but a
 *   *consumer* of the resulting module would.
 *
 * Both files are emitted into [targetPackage] — the shortest common package prefix across every
 * graph/route in the model, computed via [ClassName.bestGuess]'s "lowercase = package segment,
 * uppercase = simple-name segment" convention (the same convention this codebase's fully-qualified
 * names already follow).
 */
internal object TopologyCodegen {

    private const val GENERATED_TOPOLOGY_FILE = "GezginGenerated"
    private const val GENERATED_SERIALIZERS_FILE = "GezginSerializers"

    /** Every distinct package a graph/route in [model] declares — the `[PKG]` (M2) equality check's input. */
    fun declaredPackages(model: GraphModel): List<String> =
        (model.graphs.map { it.fqName } + model.routes.map { it.fqName })
            .map { ClassName.bestGuess(it).packageName }
            .distinct()

    /** Shortest common package prefix across every graph/route fqName in [model]. Empty if [model] is empty. */
    fun targetPackage(model: GraphModel): String =
        declaredPackages(model).reduceOrNull(::commonDotPrefix).orEmpty()

    private fun commonDotPrefix(a: String, b: String): String =
        a.split(".").zip(b.split("."))
            .takeWhile { (x, y) -> x == y }
            .joinToString(".") { it.first }

    /** `GezginGenerated.kt`: `val gezginTopology: GezginTopology = GezginTopology(...)`. */
    fun generateTopology(model: GraphModel, packageName: String): FileSpec {
        val graphsByFq = model.graphs.associateBy(GraphModelNode::fqName)
        val routesByFq = model.routes.associateBy(RouteModel::fqName)

        val topologyInit = CodeBlock.builder()
            .add("%T(\n", GEZGIN_TOPOLOGY)
            .indent()
            .add("flowChains = %L,\n", flowChainsMap(model, graphsByFq))
            .add("flowStarts = %L,\n", flowStartsMap(model))
            .add("edges = %L,\n", edgesMap(model, graphsByFq, routesByFq))
            .unindent()
            .add(")")
            .build()

        val property = PropertySpec.builder("gezginTopology", GEZGIN_TOPOLOGY)
            .initializer(topologyInit)
            .build()

        return FileSpec.builder(packageName, GENERATED_TOPOLOGY_FILE)
            .addProperty(property)
            .build()
    }

    /** `GezginSerializers.kt`: `val gezginSerializersModule: SerializersModule = SerializersModule { ... }`. */
    fun generateSerializers(model: GraphModel, packageName: String): FileSpec {
        val polymorphicBody = CodeBlock.builder()
        model.routes.forEach { route ->
            polymorphicBody.addStatement("%M(%T::class)", SUBCLASS, ClassName.bestGuess(route.fqName))
        }

        val moduleInit = CodeBlock.builder()
            .add("%T {\n", SERIALIZERS_MODULE)
            .indent()
            .add("%M(%T::class) {\n", POLYMORPHIC, ROUTE)
            .indent()
            .add(polymorphicBody.build())
            .unindent()
            .add("}\n")
            .unindent()
            .add("}")
            .build()

        val property = PropertySpec.builder("gezginSerializersModule", SERIALIZERS_MODULE)
            .initializer(moduleInit)
            .build()

        return FileSpec.builder(packageName, GENERATED_SERIALIZERS_FILE)
            .addProperty(property)
            .build()
    }

    // region flowChains / flowStarts / edges

    private fun flowChainsMap(model: GraphModel, graphsByFq: Map<String, GraphModelNode>): CodeBlock {
        val entries = model.routes.filter { it.flowChainFq.isNotEmpty() }
        val builder = CodeBlock.builder().add("mapOf(\n").indent()
        entries.forEach { route ->
            val chain = CodeBlock.builder().add("listOf(")
            route.flowChainFq.forEachIndexed { index, flowFq ->
                if (index > 0) chain.add(", ")
                // OWNERSHIP semantics (spec §6): `FlowType.isResultFlow` marks the flow that OWNS a
                // result contract — DIRECT `ResultFlow<T>` declaration only, NOT the transitive
                // [GraphModelNode.isResultFlow] (a nested result-less sub-flow inherits the marker
                // but no contract). The runtime's `RawNavigator.quitWith` resolves its target via
                // `chain.indexOfLast { it.isResultFlow }`; emitting the transitive flag here made a
                // nested sub-flow (ZoomFlow) swallow `quitWith` and silently drop the value instead
                // of finishing the declaring flow (AvatarFlow).
                val ownsResultContract = graphsByFq.getValue(flowFq).declaresResultFlowDirectly
                chain.add("%T(%S, %L)", FLOW_TYPE, flowFq, ownsResultContract)
            }
            chain.add(")")
            builder.add("%T::class to %L,\n", ClassName.bestGuess(route.fqName), chain.build())
        }
        builder.unindent().add(")")
        return builder.build()
    }

    private fun flowStartsMap(model: GraphModel): CodeBlock {
        val starts = model.graphs.filter { it.isFlow && it.startFq != null }
        val builder = CodeBlock.builder().add("mapOf(\n").indent()
        starts.forEach { graph ->
            builder.add("%S to %T::class,\n", graph.fqName, ClassName.bestGuess(graph.startFq!!))
        }
        builder.unindent().add(")")
        return builder.build()
    }

    private fun edgesMap(
        model: GraphModel,
        graphsByFq: Map<String, GraphModelNode>,
        routesByFq: Map<String, RouteModel>,
    ): CodeBlock {
        val builder = CodeBlock.builder().add("mapOf(\n").indent()
        model.routes.forEach { route ->
            route.edges.filter { it.kind == EdgeKind.GO_FOR_RESULT }.forEach { edge ->
                val id = edgeId(route.fqName, edge.targetFq, edge.name)
                val resultTypeFq = graphsByFq[edge.targetFq]?.resultTypeFq
                    ?: routesByFq[edge.targetFq]?.resultTypeFq
                    ?: error(
                        "@GoForResult target ${edge.targetFq} (from ${route.fqName}) has no result " +
                            "type — should have been rejected by GezginValidator's E2 before codegen runs",
                    )
                builder.add(
                    "%S to %T(%S, %M<%T>()),\n",
                    id,
                    EDGE_SPEC,
                    id,
                    SERIALIZER,
                    ClassName.bestGuess(resultTypeFq),
                )
            }
        }
        builder.unindent().add(")")
        return builder.build()
    }

    // endregion
}
