package dev.gezgin.gradle

/**
 * The edge annotation a generated navigator member comes from. The constant names are the `<Kind>`
 * segment of the processor's `gezgin.naming.memberFun.<Kind>.*` options.
 *
 * @author @sahsenvar
 */
public enum class GezginAnnotation {
  /** `@GoTo` edges. */
  GoTo,

  /** `@ReplaceTo` edges. */
  ReplaceTo,

  /** `@QuitAndGoTo` edges. */
  QuitAndGoTo,

  /** `@GoForResult` edges. */
  GoForResult,

  /** `@BackTo` edges. */
  BackTo,

  /** `@Open` edges. */
  Open,
}
