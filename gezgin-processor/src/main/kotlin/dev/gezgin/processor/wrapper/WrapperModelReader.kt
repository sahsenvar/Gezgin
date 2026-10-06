package dev.gezgin.processor.wrapper

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

internal const val SCREEN_WRAPPER_FQ = "dev.gezgin.core.annotation.ScreenWrapper"
internal const val SCREEN_SLOT_FQ = "dev.gezgin.core.annotation.ScreenSlot"
internal const val FILLED_BY_FQ = "dev.gezgin.core.annotation.FilledBy"
internal const val WRAPPER_ROUTE_FQ = "dev.gezgin.core.Route"
internal const val WRAPPER_SCOPE_FQ = "dev.gezgin.core.compose.GezginWrapperScope"

internal data class WrapperReadResult(
  val wrappers: List<WrapperModel>,
  val markers: List<SlotMarkerModel>,
)

/**
 * Discovers every `@ScreenWrapper` function and every `@ScreenSlot` annotation visible to this KSP
 * round.
 *
 * In-module declarations are found by annotation. Declarations compiled into a dependency are found
 * by enumerating the packages named in `gezgin.wrapperPackages`, because KSP cannot enumerate
 * classpath declarations by annotation — `getSymbolsWithAnnotation` only sees this round's sources.
 * Everything else about a classpath declaration (its meta-annotations, its constructor parameters,
 * its parameter annotations) does resolve, which is what makes the package-enumeration path enough.
 *
 * Package enumeration itself does not survive every classpath, though: in a `kspCommonMainMetadata`
 * round a project dependency arrives as Kotlin *metadata* rather than class files, and
 * `getDeclarationsFromPackage` returns nothing for it. Resolution *by name* does work there, so
 * `gezgin.wrapperDeclarations` names the wrapper function outright. One name is enough for a whole
 * vocabulary: a wrapper reaches its own `@ScreenSlot` markers through its parameters' `@FilledBy`.
 */
internal class WrapperModelReader(
  private val resolver: Resolver,
  private val logger: KSPLogger,
  private val options: Map<String, String>,
) {

  private var ok = true

  fun read(): Pair<WrapperReadResult, Boolean> {
    val wrapperDecls = mutableListOf<KSFunctionDeclaration>()
    val markerDecls = mutableListOf<KSClassDeclaration>()

    resolver
      .getSymbolsWithAnnotation(SCREEN_WRAPPER_FQ)
      .filterIsInstance<KSFunctionDeclaration>()
      .forEach { wrapperDecls += it }
    resolver
      .getSymbolsWithAnnotation(SCREEN_SLOT_FQ)
      .filterIsInstance<KSClassDeclaration>()
      .forEach { markerDecls += it }

    configuredPackages().forEach { pkg ->
      val (pkgWrappers, pkgMarkers) = enumeratePackage(pkg)
      if (pkgWrappers.isEmpty() && pkgMarkers.isEmpty()) {
        error(
          "SW9",
          "gezgin.wrapperPackages names '$pkg' but it declares no @ScreenWrapper function and no " +
            "@ScreenSlot annotation; remove it or correct the package name",
        )
      }
      wrapperDecls += pkgWrappers
      markerDecls += pkgMarkers
    }

    // The kind annotations are slot markers too, and always arrive from the gezgin-core classpath.
    configuredDeclarations().forEach { fq ->
      val (namedWrappers, namedMarkers) = resolveDeclaration(fq)
      if (namedWrappers.isEmpty() && namedMarkers.isEmpty()) {
        error(
          "SW9",
          "gezgin.wrapperDeclarations names '$fq' but it does not resolve to a @ScreenWrapper " +
            "function or a @ScreenSlot annotation; remove it or correct the name",
        )
      }
      wrapperDecls += namedWrappers
      markerDecls += namedMarkers
    }

    // A wrapper carries its own vocabulary: every marker it uses is named by a parameter's
    // @FilledBy, so naming the wrapper is enough even when the markers cannot be enumerated.
    wrapperDecls
      .flatMap { it.parameters }
      .mapNotNull { it.filledByMarkerFq() }
      .distinct()
      .forEach { fq ->
        resolver.getClassDeclarationByName(resolver.getKSNameFromString(fq))?.let {
          markerDecls += it
        }
      }

    CONTENT_MARKER_FQS.forEach { fq ->
      resolver.getClassDeclarationByName(resolver.getKSNameFromString(fq))?.let {
        markerDecls += it
      }
    }

    val markers =
      markerDecls
        .distinctBy { it.qualifiedName?.asString() }
        .mapNotNull(::readMarker)
        .sortedBy { it.annotationFq }
    val wrappers =
      wrapperDecls
        .distinctBy { "${it.packageName.asString()}.${it.simpleName.asString()}" }
        .map(::readWrapper)
        .sortedBy { "${it.packageName}.${it.functionSimpleName}" }

    return WrapperReadResult(wrappers, markers) to ok
  }

  private fun configuredPackages(): List<String> = optionList("gezgin.wrapperPackages")

  private fun configuredDeclarations(): List<String> = optionList("gezgin.wrapperDeclarations")

  private fun optionList(key: String): List<String> =
    options[key]?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()

  /**
   * Resolves one fully-qualified name to a `@ScreenWrapper` function or a `@ScreenSlot` annotation.
   *
   * Unlike [enumeratePackage] this works against a Kotlin metadata classpath as well as a JVM one,
   * which is what makes a `kspCommonMainMetadata` round able to see a wrapper from another module.
   */
  @OptIn(KspExperimental::class)
  private fun resolveDeclaration(
    fq: String
  ): Pair<List<KSFunctionDeclaration>, List<KSClassDeclaration>> {
    val name = resolver.getKSNameFromString(fq)
    val wrappers =
      resolver
        .getFunctionDeclarationsByName(name, includeTopLevel = true)
        .filter { it.hasAnnotation(SCREEN_WRAPPER_FQ) }
        .toList()
    val markers =
      listOfNotNull(
        resolver.getClassDeclarationByName(name)?.takeIf { it.hasAnnotation(SCREEN_SLOT_FQ) }
      )
    return wrappers to markers
  }

  @OptIn(KspExperimental::class)
  private fun enumeratePackage(
    pkg: String
  ): Pair<List<KSFunctionDeclaration>, List<KSClassDeclaration>> {
    val declarations = resolver.getDeclarationsFromPackage(pkg).toList()
    return declarations.filterIsInstance<KSFunctionDeclaration>().filter {
      it.hasAnnotation(SCREEN_WRAPPER_FQ)
    } to
      declarations.filterIsInstance<KSClassDeclaration>().filter {
        it.hasAnnotation(SCREEN_SLOT_FQ)
      }
  }

  private fun readMarker(declaration: KSClassDeclaration): SlotMarkerModel? {
    val fq = declaration.qualifiedName?.asString() ?: return null
    val routeParams =
      declaration.primaryConstructor?.parameters.orEmpty().filter {
        it.type.resolve().isRouteKClass()
      }
    if (routeParams.size != 1) {
      error(
        "SW3",
        "@ScreenSlot annotation $fq must declare exactly one KClass<out Route> parameter naming " +
          "the route its providers serve (found ${routeParams.size}); further parameters are " +
          "allowed and ignored",
      )
      return null
    }
    return SlotMarkerModel(fq, routeParams.single().name!!.asString())
  }

  private fun readWrapper(declaration: KSFunctionDeclaration): WrapperModel {
    val packageName = declaration.packageName.asString()
    val simpleName = declaration.simpleName.asString()
    val typeParameterNames = declaration.typeParameters.map { it.name.asString() }

    val receiverFq =
      declaration.extensionReceiver?.resolve()?.declaration?.qualifiedName?.asString()
    if (receiverFq != WRAPPER_SCOPE_FQ) {
      error(
        "SW13",
        "@ScreenWrapper $packageName.$simpleName does not declare a GezginWrapperScope receiver. " +
          "Screen wrappers reach the route, graph and back-stack state through that scope. " +
          "Declare it as `fun <…> GezginWrapperScope.$simpleName(…)`",
      )
    }
    declaration.parameters
      .filter { !it.hasFilledBy() && !it.hasDefault }
      .forEach { parameter ->
        error(
          "SW14",
          "Parameter '${parameter.name?.asString()}' of @ScreenWrapper $packageName.$simpleName " +
            "cannot be filled: it has no @FilledBy and no default value. Add " +
            "@FilledBy(<Marker>::class) to make it a slot. Route, graph and back-stack data are " +
            "read from the receiver instead (`route`, `graph`, `isTop`, …)",
        )
      }

    val slots =
      declaration.parameters.mapNotNull { parameter ->
        val markerFq = parameter.filledByMarkerFq() ?: return@mapNotNull null
        val type = parameter.type.resolve()
        if (!type.isFunctionType) {
          error(
            "SW2",
            "@FilledBy parameter '${parameter.name?.asString()}' of $packageName.$simpleName must " +
              "be a function type; it is ${type.declaration.qualifiedName?.asString()}",
          )
          return@mapNotNull null
        }
        val slotParameters =
          type.functionArguments().map { argument ->
            argument.toSlotType(typeParameterNames)
              ?: run {
                error(
                  "SW12",
                  "@FilledBy parameter '${parameter.name?.asString()}' of $packageName.$simpleName " +
                    "uses type parameter " +
                    "'${argument.foreignTypeParameter(typeParameterNames) ?: argument}', which the " +
                    "wrapper does not declare; a slot may only use concrete types, the wrapper's " +
                    "own type parameters, function types over those, and generic types whose " +
                    "arguments are themselves such types",
                )
                return@mapNotNull null
              }
          }
        WrapperSlotModel(
          parameterName = parameter.name!!.asString(),
          markerFq = markerFq,
          hasDefault = parameter.hasDefault,
          parameters = slotParameters,
          returnType =
            type
              .functionReturn()
              ?.takeUnless { it.declaration.qualifiedName?.asString() == "kotlin.Unit" }
              ?.toSlotType(typeParameterNames),
        )
      }

    val model = WrapperModel(simpleName, packageName, typeParameterNames, slots)
    if (model.contentSlot == null) {
      error(
        "SW1",
        "@ScreenWrapper $packageName.$simpleName declares no content slot; one @FilledBy " +
          "parameter must name a kind annotation (@Screen, @Dialog, @BottomSheet or " +
          "@FullscreenModal) so the screen body has somewhere to go",
      )
    }
    return model
  }

  private fun error(code: String, message: String) {
    logger.error("[$code] $message")
    ok = false
  }
}

private fun KSAnnotated.hasAnnotation(fq: String): Boolean = annotations.any { it.isNamed(fq) }

private fun com.google.devtools.ksp.symbol.KSAnnotation.isNamed(fq: String): Boolean =
  annotationType.resolve().declaration.qualifiedName?.asString() == fq

private fun KSValueParameter.hasFilledBy(): Boolean = annotations.any { it.isNamed(FILLED_BY_FQ) }

private fun KSValueParameter.filledByMarkerFq(): String? =
  annotations
    .firstOrNull { it.isNamed(FILLED_BY_FQ) }
    ?.arguments
    ?.firstOrNull()
    ?.let { (it.value as? KSType)?.declaration?.qualifiedName?.asString() }

private fun KSType.isRouteKClass(): Boolean {
  if (declaration.qualifiedName?.asString() != "kotlin.reflect.KClass") return false
  val argument = arguments.firstOrNull()?.type?.resolve()?.declaration ?: return false
  if (argument.qualifiedName?.asString() == WRAPPER_ROUTE_FQ) return true
  return (argument as? KSClassDeclaration)?.getAllSuperTypes()?.any {
    it.declaration.qualifiedName?.asString() == WRAPPER_ROUTE_FQ
  } == true
}

/** Every function-type argument except the return type — a receiver is simply the first one. */
private fun KSType.functionArguments(): List<KSType> =
  arguments.dropLast(1).mapNotNull { it.type?.resolve() }

private fun KSType.functionReturn(): KSType? = arguments.lastOrNull()?.type?.resolve()

/**
 * Models one slot type, or null when Gezgin cannot express it — the caller turns that into `SW12`.
 *
 * A type variable only renders into a [com.squareup.kotlinpoet.TypeName] with the declaring
 * function's `TypeParameterResolver`, which this reader deliberately does not carry: the wrapper's
 * type arguments are not known until a route binds them. So a type that carries a variable must
 * never reach [toTypeName]. A variable standing alone becomes a [SlotType.Variable]; one under a
 * function type or under a generic type's arguments is decomposed; a variable the wrapper does not
 * declare — from an enclosing generic class, say — is reported rather than crashed on.
 */
internal fun KSType.toSlotType(typeParameterNames: List<String>): SlotType? {
  val declaration = this.declaration
  if (declaration is KSTypeParameter) {
    val name = declaration.name.asString()
    return if (name in typeParameterNames) SlotType.Variable(name) else null
  }
  if (isFunctionType) {
    val parameters = functionArguments().map { it.toSlotType(typeParameterNames) ?: return null }
    val declaredReturn = functionReturn()
    val returnType =
      if (declaredReturn == null) SlotType.Concrete("kotlin.Unit", UNIT)
      else declaredReturn.toSlotType(typeParameterNames) ?: return null
    return SlotType.Lambda(parameters, returnType)
  }
  val fq = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
  if (!carriesTypeParameter()) return SlotType.Concrete(fq, toTypeName())
  val rawType = (declaration as? KSClassDeclaration)?.toClassName() ?: return null
  val typeArguments =
    arguments.map { argument ->
      argument.type?.resolve()?.toSlotType(typeParameterNames) ?: return null
    }
  return SlotType.Parameterized(fq, rawType, typeArguments, isMarkedNullable)
}

/** True when a type variable appears anywhere inside, which is what rules out rendering it. */
private fun KSType.carriesTypeParameter(): Boolean =
  declaration is KSTypeParameter ||
    arguments.any { it.type?.resolve()?.carriesTypeParameter() == true }

/** The first type variable inside that the wrapper does not declare, for the `SW11` message. */
private fun KSType.foreignTypeParameter(typeParameterNames: List<String>): String? {
  val declaration = this.declaration
  if (declaration is KSTypeParameter) {
    return declaration.name.asString().takeUnless { it in typeParameterNames }
  }
  return arguments.firstNotNullOfOrNull {
    it.type?.resolve()?.foreignTypeParameter(typeParameterNames)
  }
}
