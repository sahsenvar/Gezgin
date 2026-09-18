package dev.gezgin.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import dev.gezgin.processor.wrapper.SlotType
import dev.gezgin.processor.wrapper.SlotUnifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotUnifierTest {

  private val detailState = ClassName("app", "DetailUiState")
  private val detailIntent = ClassName("app", "DetailIntent")
  private val detailEffect = ClassName("app", "DetailEffect")
  private val flow = ClassName("kotlinx.coroutines.flow", "Flow")

  private fun effectFlowSlot(argument: SlotType) =
    SlotType.Parameterized(
      "kotlinx.coroutines.flow.Flow",
      flow,
      listOf(argument),
      isNullable = false,
    )

  @Test
  fun `a concrete slot type matches an equal concrete type`() {
    val bindings = mutableMapOf<String, TypeName>()

    assertTrue(
      SlotUnifier.unify(SlotType.Concrete("app.DetailUiState", detailState), detailState, bindings)
    )
    assertTrue(bindings.isEmpty())
  }

  @Test
  fun `a concrete slot type rejects a different concrete type`() {
    assertFalse(
      SlotUnifier.unify(
        SlotType.Concrete("app.DetailUiState", detailState),
        detailIntent,
        mutableMapOf(),
      )
    )
  }

  @Test
  fun `a type variable binds to the concrete type`() {
    val bindings = mutableMapOf<String, TypeName>()

    assertTrue(SlotUnifier.unify(SlotType.Variable("S"), detailState, bindings))
    assertEquals(mapOf<String, TypeName>("S" to detailState), bindings)
  }

  @Test
  fun `a type variable already bound to a different type fails`() {
    val bindings = mutableMapOf<String, TypeName>("S" to detailState)

    assertFalse(SlotUnifier.unify(SlotType.Variable("S"), detailIntent, bindings))
  }

  @Test
  fun `a lambda slot binds its parameter variable`() {
    val bindings = mutableMapOf<String, TypeName>()
    val slot =
      SlotType.Lambda(listOf(SlotType.Variable("I")), SlotType.Concrete("kotlin.Unit", UNIT))
    val concrete =
      LambdaTypeName.get(
        parameters = listOf(ParameterSpec.unnamed(detailIntent)),
        returnType = UNIT,
      )

    assertTrue(SlotUnifier.unify(slot, concrete, bindings))
    assertEquals(mapOf<String, TypeName>("I" to detailIntent), bindings)
  }

  @Test
  fun `a lambda slot rejects a different arity`() {
    val slot =
      SlotType.Lambda(listOf(SlotType.Variable("I")), SlotType.Concrete("kotlin.Unit", UNIT))
    val concrete = LambdaTypeName.get(parameters = emptyList(), returnType = UNIT)

    assertFalse(SlotUnifier.unify(slot, concrete, mutableMapOf()))
  }

  @Test
  fun `a composable annotation on the provider type does not block the match`() {
    val composable = ClassName("androidx.compose.runtime", "Composable")
    val annotated =
      LambdaTypeName.get(parameters = listOf(ParameterSpec.unnamed(detailState)), returnType = UNIT)
        .copy(
          annotations = listOf(com.squareup.kotlinpoet.AnnotationSpec.builder(composable).build())
        )
    val slot =
      SlotType.Lambda(listOf(SlotType.Variable("S")), SlotType.Concrete("kotlin.Unit", UNIT))

    assertTrue(SlotUnifier.unify(slot, annotated, mutableMapOf()))
  }

  @Test
  fun `a function type rendered as kotlin Function1 also matches a lambda slot`() {
    val bindings = mutableMapOf<String, TypeName>()
    val slot =
      SlotType.Lambda(listOf(SlotType.Variable("I")), SlotType.Concrete("kotlin.Unit", UNIT))
    val asParameterized = ClassName("kotlin", "Function1").parameterizedBy(detailIntent, UNIT)

    assertTrue(SlotUnifier.unify(slot, asParameterized, bindings))
    assertEquals(mapOf<String, TypeName>("I" to detailIntent), bindings)
  }

  @Test
  fun `a parameterized slot binds the variable carried by its type argument`() {
    val bindings = mutableMapOf<String, TypeName>()

    assertTrue(
      SlotUnifier.unify(
        effectFlowSlot(SlotType.Variable("E")),
        flow.parameterizedBy(detailEffect),
        bindings,
      )
    )
    assertEquals(mapOf<String, TypeName>("E" to detailEffect), bindings)
  }

  @Test
  fun `a parameterized slot rejects a different raw type`() {
    val other = ClassName("kotlinx.coroutines.channels", "Channel")

    assertFalse(
      SlotUnifier.unify(
        effectFlowSlot(SlotType.Variable("E")),
        other.parameterizedBy(detailEffect),
        mutableMapOf(),
      )
    )
  }

  @Test
  fun `a parameterized slot rejects a type with no arguments`() {
    assertFalse(SlotUnifier.unify(effectFlowSlot(SlotType.Variable("E")), flow, mutableMapOf()))
  }

  @Test
  fun `a parameterized slot honours a binding an earlier slot already made`() {
    val bindings = mutableMapOf<String, TypeName>("E" to detailEffect)

    assertFalse(
      SlotUnifier.unify(
        effectFlowSlot(SlotType.Variable("E")),
        flow.parameterizedBy(detailIntent),
        bindings,
      )
    )
  }

  @Test
  fun `a parameterized slot descends into a lambda type argument`() {
    val bindings = mutableMapOf<String, TypeName>()
    val slot =
      effectFlowSlot(
        SlotType.Lambda(listOf(SlotType.Variable("I")), SlotType.Concrete("kotlin.Unit", UNIT))
      )
    val concrete =
      flow.parameterizedBy(
        LambdaTypeName.get(
          parameters = listOf(ParameterSpec.unnamed(detailIntent)),
          returnType = UNIT,
        )
      )

    assertTrue(SlotUnifier.unify(slot, concrete, bindings))
    assertEquals(mapOf<String, TypeName>("I" to detailIntent), bindings)
  }
}
