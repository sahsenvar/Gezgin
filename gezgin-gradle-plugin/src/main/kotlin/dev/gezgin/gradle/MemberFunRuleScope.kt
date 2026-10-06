package dev.gezgin.gradle

/**
 * The affix lists of one [GezginAnnotation] while a [MemberFunSpec.rule] runs. Both lists start as
 * a copy of the general lists, so changing them affects only that annotation.
 *
 * @author @sahsenvar
 */
public class MemberFunRuleScope
internal constructor(
  /** Suffixes stripped from the route name for this annotation, tried in list order. */
  public var stripSuffixes: List<String>,
  /** Prefixes stripped from the route name for this annotation, tried in list order. */
  public var stripPrefixes: List<String>,
)
