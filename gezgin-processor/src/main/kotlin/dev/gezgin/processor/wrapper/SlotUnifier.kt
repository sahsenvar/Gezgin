package dev.gezgin.processor.wrapper

import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName

/**
 * Matches a slot's declared type against a provider's concrete type, binding the wrapper's type
 * parameters as it goes. Deliberately shallow: no variance, no subtyping, no generic decomposition
 * beyond function types. A pair that does not match is a plain mismatch the caller reports as
 * `SW8`, and the underlying Kotlin error at the generated call site remains as a second signal.
 */
internal object SlotUnifier {

  fun unify(slot: SlotType, concrete: TypeName, bindings: MutableMap<String, TypeName>): Boolean =
    when (slot) {
      is SlotType.Concrete -> slot.typeName.bare() == concrete.bare()

      is SlotType.Variable -> {
        val existing = bindings[slot.name]
        if (existing == null) {
          bindings[slot.name] = concrete.bare()
          true
        } else {
          existing == concrete.bare()
        }
      }

      is SlotType.Lambda -> {
        val function = concrete.asFunctionType()
        when {
          function == null -> false
          function.parameters.size != slot.parameters.size -> false
          else ->
            slot.parameters.zip(function.parameters).all { (slotParam, concreteParam) ->
              unify(slotParam, concreteParam, bindings)
            } && unify(slot.returnType, function.returnType, bindings)
        }
      }
    }

  private data class FunctionShape(val parameters: List<TypeName>, val returnType: TypeName)

  /**
   * A provider's function-typed parameter reaches us either as a [LambdaTypeName] or, depending on
   * how KotlinPoet rendered the KSP type, as `kotlin.FunctionN<P…, R>`. Both describe the same
   * thing, so both are accepted.
   */
  private fun TypeName.asFunctionType(): FunctionShape? =
    when (val bare = bare()) {
      is LambdaTypeName ->
        if (bare.receiver != null) null
        else FunctionShape(bare.parameters.map { it.type }, bare.returnType)

      is ParameterizedTypeName ->
        if (!bare.rawType.simpleName.startsWith("Function")) null
        else FunctionShape(bare.typeArguments.dropLast(1), bare.typeArguments.last())

      else -> null
    }

  /**
   * `@Composable` and other annotations ride on the KotlinPoet type but say nothing about whether a
   * provider fits a slot, so they are dropped before comparing.
   */
  private fun TypeName.bare(): TypeName = copy(annotations = emptyList())
}
