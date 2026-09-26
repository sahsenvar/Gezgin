package dev.gezgin.processor.wrapper

import com.squareup.kotlinpoet.ClassName
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
 * wrapper's own type parameters, a function type over those, or a generic type whose arguments are
 * themselves such types.
 */
internal sealed interface SlotType {
  data class Concrete(val fq: String, val typeName: TypeName) : SlotType

  data class Variable(val name: String) : SlotType

  data class Lambda(val parameters: List<SlotType>, val returnType: SlotType) : SlotType

  /**
   * A generic type that carries a type parameter in its arguments — `Flow<E>`. It cannot be a
   * [Concrete], because rendering a type variable into a [TypeName] needs the declaring function's
   * type-parameter resolver, which the reader deliberately does not carry; and it must not be one,
   * because unification has to descend into the arguments to bind the variable.
   */
  data class Parameterized(
    val fq: String,
    val rawType: ClassName,
    val arguments: List<SlotType>,
    val isNullable: Boolean,
  ) : SlotType
}

/** An application annotation carrying `@ScreenSlot`. */
internal data class SlotMarkerModel(val annotationFq: String, val routeParamName: String)

/**
 * One `@FilledBy` parameter of a `@ScreenWrapper` function.
 *
 * [parameters] holds EVERY type argument of the slot's function type except the return type, so a
 * receiver — `ColumnScope.(S, (I) -> Unit) -> Unit` — is simply its first entry. KSP does not
 * reliably mark a `@Composable` extension function type with `@ExtensionFunctionType`, so the
 * receiver is not split out; the provider side counts its own receiver the same way, which makes
 * the comparison symmetric without depending on that annotation.
 *
 * [returnType] is the slot's declared return type, or null when it is `Unit` or cannot be modelled.
 * It never decides whether a provider fits; it is only a fallback source of type-argument bindings
 * for a type parameter that no slot's parameters bind (see [WrapperBinder]).
 */
internal data class WrapperSlotModel(
  val parameterName: String,
  val markerFq: String,
  val hasDefault: Boolean,
  val parameters: List<SlotType>,
  val returnType: SlotType? = null,
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

/**
 * One declaration annotated with a slot marker, resolved against one route.
 *
 * [returnTypeCandidates] is the provider's declared return type followed by all of its supertypes,
 * so a slot returning `Vm<S, I, E>` can read its type arguments off a provider returning a concrete
 * `DetailViewModel : Vm<DetailState, DetailIntent, DetailEvent>`.
 */
internal data class SlotProviderModel(
  val functionSimpleName: String,
  val packageName: String,
  val markerFq: String,
  val routeFq: String,
  val receiverTypeName: TypeName?,
  val slotParams: List<ProviderParam>,
  val roleParams: List<ProviderRoleParam>,
  val returnTypeCandidates: List<TypeName> = emptyList(),
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
