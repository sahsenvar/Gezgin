package dev.gezgin.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import dev.gezgin.processor.codegen.SerializerRef
import dev.gezgin.processor.serial.SerialKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializerRefTest {

  @Test
  fun `a builtin resolves through the builtins extension`() {
    val ref = SerializerRef.of(SerialKind.Builtin("kotlin.String"), STRING, isNullable = false)

    assertEquals("kotlin.String.serializer()", ref.toString())
  }

  @Test
  fun `a nullable type wraps the reference`() {
    val ref = SerializerRef.of(SerialKind.Builtin("kotlin.Int"), INT, isNullable = true)

    assertEquals("kotlin.Int.serializer().nullable", ref.toString())
  }

  @Test
  fun `a serializable class resolves through its companion`() {
    val filter = ClassName("app", "Filter")

    val ref = SerializerRef.of(SerialKind.SerializableClass, filter, isNullable = false)

    assertEquals("app.Filter.serializer()", ref.toString())
  }

  @Test
  fun `a bare enum resolves to its generated serializer`() {
    val sort = ClassName("app", "Sort")

    val ref = SerializerRef.of(SerialKind.BareEnum, sort, isNullable = false)

    assertEquals("app.SortGezginSerializer", ref.toString())
  }

  @Test
  fun `a list wraps its element reference`() {
    val ref =
      SerializerRef.of(
        SerialKind.ListOf(SerialKind.Builtin("kotlin.String")),
        LIST.parameterizedBy(STRING),
        isNullable = false,
      )

    assertEquals(
      "kotlinx.serialization.builtins.ListSerializer(kotlin.String.serializer())",
      ref.toString(),
    )
  }
}
