package dev.gezgin.processor.wrapper

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassifierReference
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import dev.gezgin.processor.codegen.NavigatorCodegen

private const val SHEET_CONTROLLER_FQ = "dev.gezgin.core.compose.GezginSheetController"

/**
 * Finds every declaration annotated with one of the discovered slot markers and resolves it against
 * the route each annotation instance names.
 *
 * A provider's parameters split into Gezgin-supplied roles — the exact route type, that route's
 * typed navigator, and the sheet controller — and slot parameters, which [WrapperBinder] later
 * matches against the slot's function type. Roles are how a generic wrapper composes with fully
 * typed navigation: the generated call closes over `route` and `nav` so the provider stays typed
 * while the wrapper never names those types.
 */
internal class SlotProviderReader(
  private val resolver: Resolver,
  private val logger: KSPLogger,
  private val markers: List<SlotMarkerModel>,
) {

  private var ok = true

  fun read(): Pair<List<SlotProviderModel>, Boolean> {
    val providers = mutableListOf<SlotProviderModel>()

    markers.forEach { marker ->
      resolver
        .getSymbolsWithAnnotation(marker.annotationFq)
        .filterIsInstance<KSFunctionDeclaration>()
        .forEach { declaration ->
          declaration.annotations
            .filter {
              it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                marker.annotationFq
            }
            .forEach { annotation ->
              annotation.routeFq(marker)?.let { routeFq ->
                providers += readProvider(declaration, marker.annotationFq, routeFq)
              }
            }
        }
    }

    providers
      .groupBy { it.routeFq to it.markerFq }
      .filterValues { it.size > 1 }
      .toSortedMap(compareBy({ it.first }, { it.second }))
      .forEach { (key, duplicates) ->
        error(
          "SW4",
          "route ${key.first} has ${duplicates.size} providers for slot marker ${key.second}: " +
            duplicates.joinToString { "${it.packageName}.${it.functionSimpleName}" } +
            "; exactly one is allowed",
        )
      }

    return providers.sortedBy { "${it.routeFq}|${it.markerFq}" } to ok
  }

  private fun KSAnnotation.routeFq(marker: SlotMarkerModel): String? {
    val named = arguments.firstOrNull { it.name?.asString() == marker.routeParamName }
    val argument = named ?: arguments.firstOrNull()
    return (argument?.value as? KSType)?.declaration?.qualifiedName?.asString()
  }

  private fun readProvider(
    declaration: KSFunctionDeclaration,
    markerFq: String,
    routeFq: String,
  ): SlotProviderModel {
    val navigatorSimpleName = navigatorSimpleNameFor(routeFq)
    val slotParams = mutableListOf<ProviderParam>()
    val roleParams = mutableListOf<ProviderRoleParam>()

    declaration.parameters.forEach { parameter ->
      val name = parameter.name!!.asString()
      // The typed navigator is emitted by THIS round, so in a single-module app its type is still
      // an error type here and resolving it would throw. Match the navigator by its written name
      // before resolving anything, exactly as the graph-side readers do.
      if (parameter.type.writtenName() == navigatorSimpleName) {
        roleParams += ProviderRoleParam(name, ProviderRole.NAVIGATOR)
        return@forEach
      }
      val type = parameter.type.resolve()
      if (type.isError) {
        error(
          "SW11",
          "parameter '$name' of ${declaration.packageName.asString()}." +
            "${declaration.simpleName.asString()} does not resolve; if it is meant to be this " +
            "route's navigator it must be written as $navigatorSimpleName",
        )
        return@forEach
      }
      when (type.declaration.qualifiedName?.asString()) {
        routeFq -> roleParams += ProviderRoleParam(name, ProviderRole.ROUTE)
        SHEET_CONTROLLER_FQ -> roleParams += ProviderRoleParam(name, ProviderRole.SHEET_CONTROLLER)
        else -> slotParams += ProviderParam(name, type.toTypeName())
      }
    }

    return SlotProviderModel(
      functionSimpleName = declaration.simpleName.asString(),
      packageName = declaration.packageName.asString(),
      markerFq = markerFq,
      routeFq = routeFq,
      receiverTypeName = declaration.extensionReceiver?.resolve()?.toTypeName(),
      slotParams = slotParams,
      roleParams = roleParams,
    )
  }

  /** `app.AppGraph.DetailRoute` -> `DetailNavigator`, matching `NavigatorCodegen`'s naming. */
  private fun navigatorSimpleNameFor(routeFq: String): String? {
    val declaration =
      resolver.getClassDeclarationByName(resolver.getKSNameFromString(routeFq)) ?: return null
    return "${NavigatorCodegen.navigatorX(declaration.simpleName.asString())}Navigator"
  }

  private fun error(code: String, message: String) {
    logger.error("[$code] $message")
    ok = false
  }
}

/**
 * The type's SOURCE-written short name, available even when the type does not resolve — which is
 * the case for a navigator emitted by the same KSP round.
 */
private fun KSTypeReference.writtenName(): String? {
  (element as? KSClassifierReference)?.referencedName()?.let {
    return it.substringAfterLast('.')
  }
  // KSP2 exposes no classifier element for a type it cannot resolve, and a same-round navigator is
  // exactly that case. The reference still renders as `<ERROR TYPE: DetailNavigator>`, which is the
  // only place the written name survives.
  val rendered = (element?.toString() ?: toString()).trim()
  val name = ERROR_TYPE_RENDER.find(rendered)?.groupValues?.get(1) ?: rendered
  return name.substringBefore('<').substringAfterLast('.').trim().takeIf { it.isNotBlank() }
}

private val ERROR_TYPE_RENDER = Regex("""<ERROR TYPE:\s*([^>]+)>""")
