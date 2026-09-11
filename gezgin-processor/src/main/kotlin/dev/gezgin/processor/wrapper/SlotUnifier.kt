package dev.gezgin.processor.wrapper

import com.squareup.kotlinpoet.LambdaTypeName
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
        val lambda = concrete as? LambdaTypeName
        when {
          lambda == null -> false
          lambda.parameters.size != slot.parameters.size -> false
          else ->
            slot.parameters.zip(lambda.parameters).all { (slotParam, concreteParam) ->
              unify(slotParam, concreteParam.type, bindings)
            } && unify(slot.returnType, lambda.returnType, bindings)
        }
      }
    }

  /**
   * `@Composable` and nullability annotations ride on the KotlinPoet type but say nothing about
   * whether a provider fits a slot, so they are dropped before comparing.
   */
  private fun TypeName.bare(): TypeName = copy(annotations = emptyList())
}
