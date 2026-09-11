package dev.gezgin.processor.wrapper

import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.TypeName

/**
 * Chooses one wrapper per route and resolves everything the codegen needs.
 *
 * A wrapper is a candidate when the route has a provider for its content slot whose signature
 * unifies with that slot, and every slot without a Kotlin default also has a provider. Exactly one
 * candidate must survive. Having no `@ScreenWrapper` in scope at all is not an error — those
 * entries fall back to the bare content call.
 */
internal class WrapperBinder(private val logger: KSPLogger) {

  private var ok = true

  fun bind(
    wrappers: List<WrapperModel>,
    providers: List<SlotProviderModel>,
  ): Pair<Map<String, WrapperBindingModel>, Boolean> {
    if (wrappers.isEmpty()) return emptyMap<String, WrapperBindingModel>() to true

    // The routes to bind are exactly those with a content-marker provider — a `@Screen`,
    // `@Dialog`, `@BottomSheet` or `@FullscreenModal` function. Deriving the set here rather than
    // from the entry models keeps the wrapper pipeline independent of how entries are read.
    val routesWithContent =
      providers.filter { it.markerFq in CONTENT_MARKER_FQS }.map { it.routeFq }.toSet()
    val providersByRoute = providers.groupBy { it.routeFq }
    val bindings = mutableMapOf<String, WrapperBindingModel>()

    routesWithContent.sorted().forEach { routeFq ->
      val routeProviders = providersByRoute[routeFq].orEmpty().associateBy { it.markerFq }
      val candidates = wrappers.mapNotNull { tryBind(it, routeFq, routeProviders) }
      when (candidates.size) {
        1 -> bindings[routeFq] = candidates.single()
        0 ->
          error(
            "SW6",
            "route $routeFq matches none of the @ScreenWrapper functions in scope " +
              "(${wrappers.joinToString { "${it.packageName}.${it.functionSimpleName}" }}); check " +
              "the screen's receiver and parameter types against each wrapper's content slot",
          )
        else ->
          error(
            "SW6",
            "route $routeFq matches ${candidates.size} @ScreenWrapper functions " +
              "(${candidates.joinToString {
                "${it.wrapper.packageName}.${it.wrapper.functionSimpleName}"
              }}); give them distinct content-slot signatures",
          )
      }
    }

    providers
      .filter { it.routeFq in routesWithContent && it.markerFq !in CONTENT_MARKER_FQS }
      .filterNot { provider ->
        wrappers.any { wrapper -> wrapper.slots.any { it.markerFq == provider.markerFq } }
      }
      .forEach { provider ->
        error(
          "SW10",
          "${provider.packageName}.${provider.functionSimpleName} is marked ${provider.markerFq} " +
            "for route ${provider.routeFq}, but no @ScreenWrapper declares a @FilledBy slot for " +
            "that marker",
        )
      }

    return bindings to ok
  }

  private fun tryBind(
    wrapper: WrapperModel,
    routeFq: String,
    routeProviders: Map<String, SlotProviderModel>,
  ): WrapperBindingModel? {
    val contentSlot = wrapper.contentSlot ?: return null
    val screenProvider = routeProviders[contentSlot.markerFq] ?: return null

    val bindings = mutableMapOf<String, TypeName>()
    val filled = mutableMapOf<String, SlotProviderModel>()

    if (!unifySlot(contentSlot, screenProvider, bindings)) return null
    filled[contentSlot.parameterName] = screenProvider

    wrapper.slots
      .filter { it.parameterName != contentSlot.parameterName }
      .forEach { slot ->
        val provider = routeProviders[slot.markerFq]
        when {
          provider != null -> {
            if (!unifySlot(slot, provider, bindings)) {
              error(
                "SW8",
                "${provider.packageName}.${provider.functionSimpleName} does not match slot " +
                  "'${slot.parameterName}' of " +
                  "${wrapper.packageName}.${wrapper.functionSimpleName}: the slot expects " +
                  "${slot.parameters} (a receiver counts as the first entry), the provider has " +
                  "${provider.allParameterTypes()}",
              )
              return null
            }
            filled[slot.parameterName] = provider
          }
          !slot.hasDefault -> {
            error(
              "SW5",
              "route $routeFq has no provider marked ${slot.markerFq} for required slot " +
                "'${slot.parameterName}' of " +
                "${wrapper.packageName}.${wrapper.functionSimpleName}; add a provider or give the " +
                "parameter a default value",
            )
            return null
          }
        }
      }

    val unbound = wrapper.typeParameterNames.filterNot { it in bindings }
    if (unbound.isNotEmpty()) {
      error(
        "SW7",
        "type parameter(s) ${unbound.joinToString()} of " +
          "${wrapper.packageName}.${wrapper.functionSimpleName} are bound by no filled slot for " +
          "route $routeFq; surface them in a slot's signature so the processor can resolve them",
      )
      return null
    }

    return WrapperBindingModel(
      wrapper = wrapper,
      typeArguments = wrapper.typeParameterNames.map { bindings.getValue(it) },
      filledSlots = filled,
    )
  }

  /**
   * The provider's receiver counts as its first parameter, mirroring how a slot's function type
   * carries its receiver as the first type argument. A slot that declares a receiver therefore
   * requires an extension provider, and vice versa: the slot's signature IS the content function's
   * signature.
   */
  private fun unifySlot(
    slot: WrapperSlotModel,
    provider: SlotProviderModel,
    bindings: MutableMap<String, TypeName>,
  ): Boolean {
    val providerTypes = provider.allParameterTypes()
    if (slot.parameters.size != providerTypes.size) return false
    return slot.parameters.zip(providerTypes).all { (slotType, providerType) ->
      SlotUnifier.unify(slotType, providerType, bindings)
    }
  }

  private fun SlotProviderModel.allParameterTypes(): List<TypeName> =
    listOfNotNull(receiverTypeName) + slotParams.map { it.typeName }

  private fun error(code: String, message: String) {
    logger.error("[$code] $message")
    ok = false
  }
}
