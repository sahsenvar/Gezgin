package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import dev.gezgin.processor.entry.EntryFunctionModel
import dev.gezgin.processor.wrapper.ProviderRole
import dev.gezgin.processor.wrapper.SlotProviderModel

private const val COMPOSE_PKG = "dev.gezgin.core.compose"

private val ENTRY_SCOPE = ClassName(COMPOSE_PKG, "GezginEntryScope")
private val ENTRY_KIND = ClassName(COMPOSE_PKG, "EntryKind")
private val LOCAL_ENTRY_ID = MemberName(COMPOSE_PKG, "LocalGezginEntryId")
private val LOCAL_RAW_NAVIGATOR = MemberName(COMPOSE_PKG, "LocalGezginRawNavigator")
private val LOCAL_SHEET_CONTROLLER = MemberName(COMPOSE_PKG, "LocalGezginSheetController")

/** Locals the register body owns; a slot lambda parameter may not shadow them. */
private val RESERVED_LOCALS = setOf("route", "nav")

/**
 * Emits `fun GezginEntryScope.provideXEntry()` for every entry whose route bound a
 * `@ScreenWrapper`.
 *
 * The wrapper call carries explicit type arguments because Kotlin cannot infer a wrapper's type
 * parameters from lambda parameter types. Every slot is passed as a NAMED argument holding a lambda
 * that closes over the register body's `route` and `nav` locals — that closure is how a wrapper
 * generic over `S`/`I`/`E` still hands a provider its fully typed navigator without ever naming the
 * navigator's type.
 */
internal object WrapperEntryCodegen {

  fun generate(entries: List<EntryFunctionModel>): List<FileSpec> =
    entries
      .filter { it.wrapper != null }
      .sortedWith(compareBy({ it.packageName }, { it.routeFq }))
      .groupBy { it.packageName }
      .map { (packageName, group) ->
        FileSpec.builder(packageName, "GezginWrapperEntries")
          .apply { if (group.any(::navWired)) optInGezginInternalApi() }
          .apply { group.forEach { addFunction(provideEntryFun(it)) } }
          .build()
      }

  private fun navWired(entry: EntryFunctionModel): Boolean =
    entry.wrapper!!.filledSlots.values.any { provider ->
      provider.roleParams.any { it.role == ProviderRole.NAVIGATOR }
    }

  private fun provideEntryFun(entry: EntryFunctionModel): FunSpec {
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

    body.add("%M<", MemberName(binding.wrapper.packageName, binding.wrapper.functionSimpleName))
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

  /** A provider parameter called `route` or `nav` would shadow the register body's own locals. */
  private fun safeLambdaName(name: String): String =
    if (name in RESERVED_LOCALS) "${name}_" else name
}
