package dev.gezgin.processor.wrapper

import com.squareup.kotlinpoet.TypeName

/** The kind annotations that may fill a wrapper's content slot. */
internal val CONTENT_MARKER_FQS =
  setOf(
    "dev.gezgin.core.annotation.Screen",
    "dev.gezgin.core.annotation.Dialog",
    "dev.gezgin.core.annotation.BottomSheet",
    "dev.gezgin.core.annotation.FullscreenModal",
  )

/**
 * A slot's declared type, in the only shape [SlotUnifier] understands: a concrete type, one of the
 * wrapper's own type parameters, or a function type over those.
 */
internal sealed interface SlotType {
  data class Concrete(val fq: String, val typeName: TypeName) : SlotType

  data class Variable(val name: String) : SlotType

  data class Lambda(val parameters: List<SlotType>, val returnType: SlotType) : SlotType
}

/** An application annotation carrying `@ScreenSlot`. */
internal data class SlotMarkerModel(val annotationFq: String, val routeParamName: String)

/** One `@FilledBy` parameter of a `@ScreenWrapper` function. */
internal data class WrapperSlotModel(
  val parameterName: String,
  val markerFq: String,
  val hasDefault: Boolean,
  val receiver: SlotType?,
  val parameters: List<SlotType>,
)

/** One `@ScreenWrapper` function. */
internal data class WrapperModel(
  val functionSimpleName: String,
  val packageName: String,
  val typeParameterNames: List<String>,
  val slots: List<WrapperSlotModel>,
) {
  val contentSlot: WrapperSlotModel?
    get() = slots.firstOrNull { it.markerFq in CONTENT_MARKER_FQS }
}

/** A value Gezgin supplies to a provider parameter rather than matching it against a slot. */
internal enum class ProviderRole {
  ROUTE,
  NAVIGATOR,
  SHEET_CONTROLLER,
}

internal data class ProviderParam(val name: String, val typeName: TypeName)

internal data class ProviderRoleParam(val name: String, val role: ProviderRole)

/** One declaration annotated with a slot marker, resolved against one route. */
internal data class SlotProviderModel(
  val functionSimpleName: String,
  val packageName: String,
  val markerFq: String,
  val routeFq: String,
  val receiverTypeName: TypeName?,
  val slotParams: List<ProviderParam>,
  val roleParams: List<ProviderRoleParam>,
)

/** The wrapper chosen for one route, with every slot filled and every type argument bound. */
internal data class WrapperBindingModel(
  val wrapper: WrapperModel,
  /** In wrapper type-parameter declaration order. */
  val typeArguments: List<TypeName>,
  /**
   * Wrapper parameter name to the provider that fills it. A slot omitted here has a Kotlin default
   * and is left out of the generated call.
   */
  val filledSlots: Map<String, SlotProviderModel>,
)
