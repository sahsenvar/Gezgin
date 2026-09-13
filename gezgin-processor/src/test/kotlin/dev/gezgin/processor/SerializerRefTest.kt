package dev.gezgin.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import dev.gezgin.processor.codegen.SerializerRef
import dev.gezgin.processor.serial.SerialKind
import kotlin.test.Test
import kotlin.test.assertContains

class SerializerRefTest {

  /**
   * Renders the reference the way it actually reaches a generated file. Asserting on
   * `CodeBlock.toString()` instead would miss the thing most likely to be wrong: an extension
   * referenced without the import that makes it resolve.
   */
  private fun render(kind: SerialKind, typeName: TypeName, nullable: Boolean) =
    FileSpec.builder("app", "Probe")
      .addProperty(
        PropertySpec.builder(
            "ref",
            ClassName("kotlinx.serialization", "KSerializer").parameterizedBy(STAR),
          )
          .initializer(SerializerRef.of(kind, typeName, nullable))
          .build()
      )
      .build()
      .toString()

  @Test
  fun `a builtin resolves through the builtins extension`() {
    val file = render(SerialKind.Builtin("kotlin.String"), STRING, nullable = false)

    assertContains(file, "import kotlinx.serialization.builtins.serializer")
    assertContains(file, "String.serializer()")
  }

  @Test
  fun `a nullable type wraps the reference`() {
    val file = render(SerialKind.Builtin("kotlin.Int"), INT, nullable = true)

    assertContains(file, "import kotlinx.serialization.builtins.nullable")
    assertContains(file, "Int.serializer().nullable")
  }

  @Test
  fun `a serializable class resolves through its companion`() {
    val file = render(SerialKind.SerializableClass, ClassName("app", "Filter"), nullable = false)

    assertContains(file, "Filter.serializer()")
  }

  @Test
  fun `a bare enum resolves to its generated serializer`() {
    val file = render(SerialKind.BareEnum, ClassName("app", "Sort"), nullable = false)

    assertContains(file, "SortGezginSerializer")
  }

  @Test
  fun `a list wraps its element reference`() {
    val file =
      render(
        SerialKind.ListOf(SerialKind.Builtin("kotlin.String")),
        LIST.parameterizedBy(STRING),
        nullable = false,
      )

    assertContains(file, "import kotlinx.serialization.builtins.ListSerializer")
    assertContains(file, "ListSerializer(String.serializer())")
  }
}
