package dev.gezgin.processor.serial

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

private const val SERIALIZABLE_FQ = "kotlinx.serialization.Serializable"
private const val LIST_FQ = "kotlin.collections.List"

private val BUILTIN_FQS =
  setOf(
    "kotlin.String",
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Boolean",
    "kotlin.Float",
    "kotlin.Double",
    "kotlin.Short",
    "kotlin.Byte",
    "kotlin.Char",
  )

/**
 * Decides how a persisted type reaches its serializer. The order matters: a builtin is never
 * `@Serializable`, and an annotated enum is reached through its companion rather than a generated
 * serializer.
 */
internal object SerialTypeClassifier {

  fun classify(type: KSType): SerialKind {
    val declaration =
      type.declaration as? KSClassDeclaration ?: return SerialKind.Unsupported("not a class")
    val fq =
      declaration.qualifiedName?.asString() ?: return SerialKind.Unsupported("no qualified name")

    if (fq in BUILTIN_FQS) return SerialKind.Builtin(fq)

    if (fq == LIST_FQ) {
      val element =
        type.arguments.singleOrNull()?.type?.resolve()
          ?: return SerialKind.Unsupported("List without a resolvable element type")
      val elementKind = classify(element)
      return if (elementKind is SerialKind.Unsupported) {
        SerialKind.Unsupported("List element: ${elementKind.reason}")
      } else {
        SerialKind.ListOf(elementKind)
      }
    }

    if (declaration.typeParameters.isNotEmpty()) {
      return SerialKind.Unsupported("generic types are not supported")
    }

    val annotated =
      declaration.annotations.any {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIALIZABLE_FQ
      }
    if (annotated || declaration.hasCompanionSerializer()) return SerialKind.SerializableClass
    if (declaration.classKind == ClassKind.ENUM_CLASS) return SerialKind.BareEnum

    return SerialKind.Unsupported("not @Serializable and not an enum")
  }

  /**
   * A hand-written serializer surfaced as `Companion.serializer()` is just as reachable as the one
   * `@Serializable` generates, and [dev.gezgin.processor.codegen.SerializerRef] emits the same call
   * for both. Accepting it is what lets a type carry a custom serializer without also carrying the
   * annotation.
   */
  private fun KSClassDeclaration.hasCompanionSerializer(): Boolean =
    declarations.filterIsInstance<KSClassDeclaration>().any { nested ->
      nested.isCompanionObject &&
        nested.getDeclaredFunctions().any { it.simpleName.asString() == "serializer" }
    }
}
