package dev.gezgin.processor.model

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

private const val ON_DISMISS_FQ = "dev.gezgin.core.annotation.OnDismiss"
private const val UNIT_FQ = "kotlin.Unit"

/** Reads a function-typed parameter as a callback; `null` for any other type. */
internal fun KSType.callbackModelOrNull(): CallbackModel? {
  if (!isFunctionType && !isSuspendFunctionType) return null
  val returnType = arguments.lastOrNull()?.type?.resolve()
  return CallbackModel(
    isSuspend = isSuspendFunctionType,
    returnsUnit = returnType?.declaration?.qualifiedName?.asString() == UNIT_FQ,
    parameterCount = (arguments.size - 1).coerceAtLeast(0),
  )
}

internal fun KSValueParameter.isOnDismiss(): Boolean =
  annotations.any {
    it.annotationType.resolve().declaration.qualifiedName?.asString() == ON_DISMISS_FQ
  }

/** Whether the class named [fq] declares a function-typed constructor parameter. */
internal fun Resolver.isCallbackRoute(fq: String): Boolean =
  getClassDeclarationByName(fq)?.primaryConstructor?.parameters.orEmpty().any {
    it.type.resolve().callbackModelOrNull() != null
  }
