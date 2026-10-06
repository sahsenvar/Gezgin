package dev.gezgin.gradle

/**
 * The `memberFun` block of [NamingSpec]: how generated navigator member names are shortened.
 *
 * @author @sahsenvar
 */
public class MemberFunSpec {
  private var generalSuffixes: List<String>? = null
  private var generalPrefixes: List<String>? = null
  private val rules = mutableListOf<MemberFunRuleScope.(GezginAnnotation) -> Unit>()

  /** Suffixes stripped for every [GezginAnnotation]; nothing is stripped until this is set. */
  public var stripSuffixes: List<String>
    get() = generalSuffixes.orEmpty()
    set(value) {
      generalSuffixes = value
    }

  /** Prefixes stripped for every [GezginAnnotation]; nothing is stripped until this is set. */
  public var stripPrefixes: List<String>
    get() = generalPrefixes.orEmpty()
    set(value) {
      generalPrefixes = value
    }

  /**
   * Adds a rule that is evaluated once per [GezginAnnotation] when the processor options are
   * computed, after the whole build script has been configured. Rules run in declaration order.
   */
  public fun rule(block: MemberFunRuleScope.(GezginAnnotation) -> Unit) {
    rules += block
  }

  internal fun options(): Map<String, String> =
    MemberFunOptions.resolve(generalSuffixes, generalPrefixes, rules.toList())
}
