package dev.gezgin.processor.serial

/**
 * How a persisted type — a route constructor parameter or a result payload — reaches its
 * serializer. Classified while the KSP `Resolver` is in hand; the codegen only reads it.
 */
internal sealed interface SerialKind {
  /** A Kotlin primitive with a `kotlinx.serialization.builtins` serializer extension. */
  data class Builtin(val fq: String) : SerialKind

  /** A non-generic class (or enum) carrying `@Serializable`; reached through its companion. */
  data object SerializableClass : SerialKind

  /** An enum WITHOUT `@Serializable`; Gezgin generates a name-based serializer for it. */
  data object BareEnum : SerialKind

  data class ListOf(val element: SerialKind) : SerialKind

  /** No serializer can be referenced; validation reports the unsupported reason. */
  data class Unsupported(val reason: String) : SerialKind
}
