package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import dev.gezgin.processor.serial.SerialKind

private const val BUILTINS_PKG = "kotlinx.serialization.builtins"

private val LIST_SERIALIZER = MemberName(BUILTINS_PKG, "ListSerializer")
private val BUILTIN_SERIALIZER = MemberName(BUILTINS_PKG, "serializer")
private val NULLABLE = MemberName(BUILTINS_PKG, "nullable")

/**
 * The code that references a type's serializer. Deliberately explicit: the reified
 * `kotlinx.serialization.serializer<T>()` would resolve too, but without the serialization compiler
 * plugin it falls back to reflection, which is JVM-only and would break both multiplatform support
 * and a graph module's ability to drop the plugin.
 */
internal object SerializerRef {

  fun of(kind: SerialKind, typeName: TypeName, isNullable: Boolean): CodeBlock {
    val bare = bareOf(kind, typeName)
    return if (isNullable) CodeBlock.of("%L.%M", bare, NULLABLE) else bare
  }

  /** `app.Sort` -> `app.SortGezginSerializer`, the object emitted by RouteSerializerCodegen. */
  fun enumSerializerName(typeName: TypeName): ClassName {
    val className = typeName.copy(nullable = false) as ClassName
    return ClassName(
      className.packageName,
      className.simpleNames.joinToString("") + "GezginSerializer",
    )
  }

  private fun bareOf(kind: SerialKind, typeName: TypeName): CodeBlock =
    when (kind) {
      // A MemberName rather than literal text: `String.serializer()` is an extension in
      // kotlinx.serialization.builtins, and only %M makes KotlinPoet import it.
      is SerialKind.Builtin ->
        CodeBlock.of("%T.%M()", typeName.copy(nullable = false), BUILTIN_SERIALIZER)
      SerialKind.SerializableClass ->
        CodeBlock.of("%T.serializer()", typeName.copy(nullable = false))
      SerialKind.BareEnum -> CodeBlock.of("%T", enumSerializerName(typeName))
      is SerialKind.ListOf -> {
        val element =
          (typeName.copy(nullable = false) as ParameterizedTypeName).typeArguments.single()
        CodeBlock.of("%M(%L)", LIST_SERIALIZER, of(kind.element, element, element.isNullable))
      }
      is SerialKind.Unsupported ->
        error("SerializerRef.of called for an unsupported kind: ${kind.reason}")
    }
}
