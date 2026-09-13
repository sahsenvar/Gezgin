package dev.gezgin.processor.wrapper

/** Deterministic text dump of the wrapper pipeline, emitted behind `gezgin.dumpWrapper`. */
internal fun dumpWrapperText(
  result: WrapperReadResult,
  providers: List<SlotProviderModel>,
  bindings: Map<String, WrapperBindingModel>,
): String = buildString {
  result.markers.forEach { marker ->
    appendLine("marker ${marker.annotationFq} route=${marker.routeParamName}")
  }
  result.wrappers.forEach { wrapper ->
    appendLine("wrapper ${wrapper.packageName}.${wrapper.functionSimpleName}")
    wrapper.typeParameterNames.forEach { appendLine("  typeParam $it") }
    wrapper.slots.forEach { slot ->
      appendLine(
        "  slot ${slot.parameterName} marker=${slot.markerFq} default=${slot.hasDefault} " +
          "params=${slot.parameters}"
      )
    }
  }
  providers
    .sortedBy { "${it.routeFq}|${it.markerFq}" }
    .forEach { provider ->
      appendLine(
        "provider ${provider.packageName}.${provider.functionSimpleName} " +
          "marker=${provider.markerFq} route=${provider.routeFq} " +
          "slotParams=${provider.slotParams.map { it.name }} " +
          "roles=${provider.roleParams.map { "${it.name}:${it.role}" }}"
      )
    }
  bindings.toSortedMap().forEach { (routeFq, binding) ->
    appendLine(
      "binding $routeFq " +
        "wrapper=${binding.wrapper.packageName}.${binding.wrapper.functionSimpleName} " +
        "typeArgs=${binding.typeArguments} filled=${binding.filledSlots.keys.sorted()}"
    )
  }
}
