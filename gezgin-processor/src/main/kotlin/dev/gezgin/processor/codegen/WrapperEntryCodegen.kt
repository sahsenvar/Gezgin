package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.joinToCode
import dev.gezgin.processor.entry.EntryFunctionModel
import dev.gezgin.processor.routemeta.GraphMetaModel
import dev.gezgin.processor.wrapper.ProviderRole
import dev.gezgin.processor.wrapper.SlotProviderModel

private const val COMPOSE_PKG = "dev.gezgin.core.compose"

private val ENTRY_SCOPE = ClassName(COMPOSE_PKG, "GezginEntryScope")
private val ENTRY_KIND = ClassName(COMPOSE_PKG, "EntryKind")
private val LOCAL_ENTRY_ID = MemberName(COMPOSE_PKG, "LocalGezginEntryId")
private val LOCAL_RAW_NAVIGATOR = MemberName(COMPOSE_PKG, "LocalGezginRawNavigator")
private val LOCAL_SHEET_CONTROLLER = MemberName(COMPOSE_PKG, "LocalGezginSheetController")
private val GEZGIN_GRAPH = ClassName(COMPOSE_PKG, "GezginGraph")
private val GRAPH_KIND = ClassName(COMPOSE_PKG, "GraphKind")
private val REMEMBER_SCOPE = MemberName(COMPOSE_PKG, "rememberGezginWrapperScope")
private val ANNOTATION_LIST = LIST.parameterizedBy(ClassName("kotlin", "Annotation"))

/** Locals the register body owns; a slot lambda parameter may not shadow them. */
private val RESERVED_LOCALS = setOf("route", "nav", "scope")

/**
 * Emits `fun GezginEntryScope.provideXEntry()` for every entry whose route bound a
 * `@ScreenWrapper`.
 *
 * The wrapper is called on a `scope` built from the route's compile-time metadata. The call carries
 * explicit type arguments because Kotlin cannot infer a wrapper's type parameters from lambda
 * parameter types. Every slot is passed as a NAMED argument holding a lambda that closes over the
 * register body's `route` and `nav` locals — that closure is how a wrapper generic over `S`/`I`/`E`
 * still hands a provider its fully typed navigator without ever naming the navigator's type.
 */
internal object WrapperEntryCodegen {

  fun generate(entries: List<EntryFunctionModel>): List<FileSpec> =
    entries
      .filter { it.wrapper != null }
      .sortedWith(compareBy({ it.packageName }, { it.routeFq }))
      .groupBy { it.packageName }
      .map { (packageName, group) ->
        val constants = MetaConstants()
        val functions = group.map { provideEntryFun(it, constants) }
        FileSpec.builder(packageName, "GezginWrapperEntries")
          .apply { optInGezginInternalApi() }
          .apply { functions.forEach { addFunction(it) } }
          .apply { constants.all().forEach { addProperty(it) } }
          .build()
      }

  private fun navWired(entry: EntryFunctionModel): Boolean =
    entry.wrapper!!.filledSlots.values.any { provider ->
      provider.roleParams.any { it.role == ProviderRole.NAVIGATOR }
    }

  private fun provideEntryFun(entry: EntryFunctionModel, constants: MetaConstants): FunSpec {
    val binding = entry.wrapper!!
    val body =
      CodeBlock.builder()
        .add(
          "register<%T>(kind = %T.%L, noBack = %L) { route ->\n",
          ClassName.bestGuess(entry.routeFq),
          ENTRY_KIND,
          entry.kind.name,
          entry.noBack,
        )
        .indent()

    if (navWired(entry)) {
      body.add(
        "val nav = %M.current.%M(%M.current)\n",
        LOCAL_RAW_NAVIGATOR,
        MemberName(entry.routePackageName, NavigatorCodegen.rawFactoryFunName(entry.x)),
        LOCAL_ENTRY_ID,
      )
    }

    val meta =
      requireNotNull(entry.routeMeta) { "wrapped entry ${entry.routeFq} has no route metadata" }
    body.add("val scope = %M(\n", REMEMBER_SCOPE).indent()
    body.add("route = route,\n")
    body.add("routeName = %S,\n", meta.routeName)
    body.add(
      "routeAnnotations = %L,\n",
      constants.annotationsRef(entry.routeFq, meta.annotations, "gezginRouteAnnotations_"),
    )
    body.add("graph = %L,\n", constants.graphRef(meta.graph))
    body.add("noBack = %L,\n", entry.noBack)
    body.unindent().add(")\n")

    body.add(
      "scope.%M<",
      MemberName(
        binding.wrapper.packageName,
        binding.wrapper.functionSimpleName,
        isExtension = true,
      ),
    )
    binding.typeArguments.forEachIndexed { index, typeArgument ->
      if (index > 0) body.add(", ")
      body.add("%T", typeArgument)
    }
    body.add(">(\n").indent()

    // Declaration order, so the generated call reads like the wrapper's own signature.
    binding.wrapper.slots.forEach { slot ->
      val provider = binding.filledSlots[slot.parameterName] ?: return@forEach
      body.add("%L = %L,\n", slot.parameterName, slotLambda(provider))
    }

    body.unindent().add(")\n")
    body.unindent().add("}\n")

    return FunSpec.builder("provide${entry.x}Entry")
      .receiver(ENTRY_SCOPE)
      .addCode(body.build())
      .build()
  }

  /** `{ a, b -> provider(a = a, b = b, nav = nav) }` — one slot's lambda. */
  private fun slotLambda(provider: SlotProviderModel): CodeBlock {
    val lambdaParams = provider.slotParams.map { safeLambdaName(it.name) }
    val header = if (lambdaParams.isEmpty()) "" else "${lambdaParams.joinToString(", ")} -> "
    return CodeBlock.of("{ %L%L }", header, callProvider(provider, lambdaParams))
  }

  /** Every argument named, so a provider's declared parameter order never matters. */
  private fun callProvider(provider: SlotProviderModel, lambdaParams: List<String>): CodeBlock {
    val args = CodeBlock.builder()
    var first = true
    fun separator() {
      if (!first) args.add(", ")
      first = false
    }
    provider.slotParams.forEachIndexed { index, param ->
      separator()
      args.add("%L = %L", param.name, lambdaParams[index])
    }
    provider.roleParams.forEach { role ->
      separator()
      when (role.role) {
        ProviderRole.ROUTE -> args.add("%L = route", role.name)
        ProviderRole.NAVIGATOR -> args.add("%L = nav", role.name)
        ProviderRole.SHEET_CONTROLLER ->
          args.add("%L = %M.current", role.name, LOCAL_SHEET_CONTROLLER)
      }
    }
    return CodeBlock.of(
      "%M(%L)",
      MemberName(provider.packageName, provider.functionSimpleName),
      args.build(),
    )
  }

  /**
   * A provider parameter called `route`, `nav` or `scope` would shadow the register body's locals.
   */
  private fun safeLambdaName(name: String): String =
    if (name in RESERVED_LOCALS) "${name}_" else name
}

/**
 * File-private constants for one generated file. The scope is remembered keyed on its graph, and
 * [GEZGIN_GRAPH] has identity equality, so every graph — including the no-graph fallback — must be
 * a single constant rather than an expression rebuilt on each composition. A graph constant
 * references its parent, so the parent is registered first: top-level properties initialize in
 * textual order.
 */
private class MetaConstants {
  private val properties = linkedMapOf<String, PropertySpec>()
  private val namesByOwner = HashMap<String, String>()

  fun all(): Collection<PropertySpec> = properties.values

  fun annotationsRef(owner: String, annotations: List<CodeBlock>, prefix: String): CodeBlock {
    if (annotations.isEmpty()) return CodeBlock.of("emptyList()")
    val name = nameFor(prefix, owner)
    properties.getOrPut(name) {
      PropertySpec.builder(name, ANNOTATION_LIST, KModifier.PRIVATE)
        .initializer(CodeBlock.of("listOf(%L)", annotations.joinToCode(", ")))
        .build()
    }
    return CodeBlock.of("%N", name)
  }

  fun graphRef(graph: GraphMetaModel?): CodeBlock {
    if (graph == null) {
      properties.getOrPut(NO_GRAPH) {
        PropertySpec.builder(NO_GRAPH, GEZGIN_GRAPH, KModifier.PRIVATE)
          .initializer(
            GRAPH_INITIALIZER,
            GEZGIN_GRAPH,
            "",
            GRAPH_KIND,
            "Nav",
            "emptyList()",
            "null",
          )
          .build()
      }
      return CodeBlock.of("%N", NO_GRAPH)
    }
    val name = nameFor("gezginGraph_", graph.fq)
    if (name !in properties) {
      val parent = graph.parent?.let { graphRef(it) } ?: CodeBlock.of("null")
      val annotations = annotationsRef(graph.fq, graph.annotations, "gezginGraphAnnotations_")
      properties[name] =
        PropertySpec.builder(name, GEZGIN_GRAPH, KModifier.PRIVATE)
          .initializer(
            GRAPH_INITIALIZER,
            GEZGIN_GRAPH,
            graph.name,
            GRAPH_KIND,
            if (graph.isFlow) "Flow" else "Nav",
            annotations,
            parent,
          )
          .build()
    }
    return CodeBlock.of("%N", name)
  }

  /** Sanitizing can map two owners to one name (`a.b_c`, `a_b.c`); a suffix keeps them apart. */
  private fun nameFor(prefix: String, owner: String): String =
    namesByOwner.getOrPut(prefix + owner) {
      val base = prefix + owner.replace(Regex("[^A-Za-z0-9_]"), "_")
      var candidate = base
      var suffix = 2
      while (candidate == NO_GRAPH || candidate in namesByOwner.values) {
        candidate = "${base}_${suffix++}"
      }
      candidate
    }

  private companion object {
    const val NO_GRAPH = "gezginGraph_none"
    const val GRAPH_INITIALIZER =
      "%T(\n⇥name = %S,\nkind = %T.%L,\nannotations = %L,\nparent = %L,\n⇤)"
  }
}
