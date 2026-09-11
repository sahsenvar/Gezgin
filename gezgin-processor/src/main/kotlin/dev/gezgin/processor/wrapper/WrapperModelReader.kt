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
import com.squareup.kotlinpoet.ksp.toTypeName

internal const val SCREEN_WRAPPER_FQ = "dev.gezgin.core.annotation.ScreenWrapper"
internal const val SCREEN_SLOT_FQ = "dev.gezgin.core.annotation.ScreenSlot"
internal const val FILLED_BY_FQ = "dev.gezgin.core.annotation.FilledBy"
internal const val WRAPPER_ROUTE_FQ = "dev.gezgin.core.Route"

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

  private fun configuredPackages(): List<String> =
    options["gezgin.wrapperPackages"]
      ?.split(',')
      ?.map(String::trim)
      ?.filter(String::isNotEmpty)
      .orEmpty()

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
        WrapperSlotModel(
          parameterName = parameter.name!!.asString(),
          markerFq = markerFq,
          hasDefault = parameter.hasDefault,
          parameters = type.functionArguments().map { it.toSlotType(typeParameterNames) },
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

internal fun KSType.toSlotType(typeParameterNames: List<String>): SlotType {
  val declaration = this.declaration
  if (declaration is KSTypeParameter && declaration.name.asString() in typeParameterNames) {
    return SlotType.Variable(declaration.name.asString())
  }
  if (isFunctionType) {
    return SlotType.Lambda(
      parameters = functionArguments().map { it.toSlotType(typeParameterNames) },
      returnType =
        functionReturn()?.toSlotType(typeParameterNames)
          ?: SlotType.Concrete("kotlin.Unit", com.squareup.kotlinpoet.UNIT),
    )
  }
  return SlotType.Concrete(
    declaration.qualifiedName?.asString() ?: declaration.simpleName.asString(),
    toTypeName(),
  )
}
