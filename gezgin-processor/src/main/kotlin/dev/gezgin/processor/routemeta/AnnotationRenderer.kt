package dev.gezgin.processor.routemeta

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/** Annotations that belong to the compiler or its plugins rather than to the program. */
internal val EXCLUDED_ANNOTATION_PREFIXES =
  listOf("kotlin.", "kotlinx.serialization.", "androidx.compose.runtime.")

private val ARRAY_FACTORIES =
  mapOf(
    "kotlin.BooleanArray" to "booleanArrayOf",
    "kotlin.Boolean" to "booleanArrayOf",
    "kotlin.ByteArray" to "byteArrayOf",
    "kotlin.Byte" to "byteArrayOf",
    "kotlin.CharArray" to "charArrayOf",
    "kotlin.Char" to "charArrayOf",
    "kotlin.DoubleArray" to "doubleArrayOf",
    "kotlin.Double" to "doubleArrayOf",
    "kotlin.FloatArray" to "floatArrayOf",
    "kotlin.Float" to "floatArrayOf",
    "kotlin.IntArray" to "intArrayOf",
    "kotlin.Int" to "intArrayOf",
    "kotlin.LongArray" to "longArrayOf",
    "kotlin.Long" to "longArrayOf",
    "kotlin.ShortArray" to "shortArrayOf",
    "kotlin.Short" to "shortArrayOf",
  )

/**
 * Renders KSP annotations as runtime constructor calls, e.g. `Tracked(name = "detail")`. An
 * annotation that cannot be reproduced is skipped with `[SW15]` and never fails the build.
 */
internal class AnnotationRenderer(private val logger: KSPLogger) {

  fun renderAll(owner: String, annotations: Sequence<KSAnnotation>): List<CodeBlock> =
    annotations
      .filterNot(::isExcluded)
      .mapNotNull { annotation ->
        render(annotation)
          ?: run {
            logger.warn(
              "[SW15] Annotation @${annotation.shortName.asString()} on $owner cannot be " +
                "reproduced at runtime (it is not accessible from generated code, or an argument " +
                "has an unsupported shape); it is omitted from the annotation list"
            )
            null
          }
      }
      .toList()

  private fun isExcluded(annotation: KSAnnotation): Boolean {
    val fq =
      annotation.annotationType.resolve().declaration.qualifiedName?.asString() ?: return true
    return EXCLUDED_ANNOTATION_PREFIXES.any { fq.startsWith(it) }
  }

  private fun render(annotation: KSAnnotation): CodeBlock? {
    val type = annotation.annotationType.resolve()
    if (type.isError) return null
    val declaration = type.declaration as? KSClassDeclaration ?: return null
    if (!isAccessible(declaration)) return null
    val parameters = declaration.primaryConstructor?.parameters.orEmpty()
    val arguments =
      annotation.arguments.map { argument ->
        val name = argument.name?.asString() ?: return null
        val parameter = parameters.firstOrNull { it.name?.asString() == name } ?: return null
        val value =
          renderValue(argument.value, parameter.type.resolve(), parameter.isVararg) ?: return null
        CodeBlock.of("%L = %L", name, value)
      }
    return CodeBlock.of("%T(%L)", declaration.toClassName(), arguments.joinToCode(", "))
  }

  /**
   * The generated file can name a declaration only if it and every enclosing declaration are
   * visible to it; an internal one is visible only from its own module, i.e. when it has a source.
   */
  private fun isAccessible(declaration: KSDeclaration): Boolean {
    var current: KSDeclaration? = declaration
    while (current != null) {
      if (Modifier.PRIVATE in current.modifiers) return false
      if (Modifier.INTERNAL in current.modifiers && current.containingFile == null) return false
      current = current.parentDeclaration
    }
    return true
  }

  private fun renderValue(value: Any?, expected: KSType?, isVararg: Boolean = false): CodeBlock? =
    when (value) {
      is Boolean,
      is Int,
      is Short,
      is Byte -> CodeBlock.of("%L", value)
      is Long -> CodeBlock.of("%LL", value)
      is Float -> if (value.isFinite()) CodeBlock.of("%Lf", value) else null
      is Double -> if (value.isFinite()) CodeBlock.of("%L", value) else null
      is Char -> charLiteral(value)
      is String -> CodeBlock.of("%S", value)
      is KSAnnotation -> render(value)
      is KSClassDeclaration -> enumEntry(value)
      is KSType -> typeValue(value)
      is List<*> -> array(value, expected, isVararg)
      is Array<*> -> array(value.toList(), expected, isVararg)
      else -> null
    }

  private fun charLiteral(value: Char): CodeBlock? =
    if (value.isISOControl() || value == '\'' || value == '\\') null
    else CodeBlock.of("%L", "'$value'")

  private fun enumEntry(entry: KSClassDeclaration): CodeBlock? {
    val owner = entry.parentDeclaration as? KSClassDeclaration ?: return null
    if (!isAccessible(owner)) return null
    return CodeBlock.of("%T.%L", owner.toClassName(), entry.simpleName.asString())
  }

  private fun typeValue(type: KSType): CodeBlock? {
    if (type.isError) return null
    val declaration = type.declaration as? KSClassDeclaration ?: return null
    return when {
      declaration.classKind == ClassKind.ENUM_ENTRY -> enumEntry(declaration)
      !isAccessible(declaration) -> null
      else -> CodeBlock.of("%T::class", declaration.toClassName())
    }
  }

  /** KSP may report a vararg parameter's type as either the array type or the element type. */
  private fun array(items: List<*>, expected: KSType?, isVararg: Boolean): CodeBlock? {
    expected ?: return null
    val expectedFq = expected.declaration.qualifiedName?.asString()
    val factory: CodeBlock
    val elementType: KSType?
    when {
      expectedFq == "kotlin.Array" -> {
        elementType = expected.arguments.firstOrNull()?.type?.resolve() ?: return null
        if (elementType.isError) return null
        factory = CodeBlock.of("arrayOf<%T>", elementType.toTypeName())
      }
      expectedFq != null && expectedFq in ARRAY_FACTORIES -> {
        elementType = null
        factory = CodeBlock.of("%L", ARRAY_FACTORIES.getValue(expectedFq))
      }
      isVararg -> {
        if (expected.isError) return null
        elementType = expected
        factory = CodeBlock.of("arrayOf<%T>", expected.toTypeName())
      }
      else -> return null
    }
    val elements = items.map { renderValue(it, elementType) ?: return null }
    val call = CodeBlock.of("%L(%L)", factory, elements.joinToCode(", "))
    return if (isVararg) CodeBlock.of("*%L", call) else call
  }
}
