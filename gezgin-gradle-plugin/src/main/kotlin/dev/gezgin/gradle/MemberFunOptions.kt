package dev.gezgin.gradle

internal object MemberFunOptions {
  private const val PREFIX = "gezgin.naming.memberFun."
  private const val SUFFIXES = "stripSuffixes"
  private const val PREFIXES = "stripPrefixes"

  fun resolve(
    suffixes: List<String>?,
    prefixes: List<String>?,
    rules: List<MemberFunRuleScope.(GezginAnnotation) -> Unit>,
  ): Map<String, String> = buildMap {
    suffixes?.let { put("$PREFIX$SUFFIXES", it.joinToString(",")) }
    prefixes?.let { put("$PREFIX$PREFIXES", it.joinToString(",")) }
    GezginAnnotation.entries.forEach { kind ->
      val scope = MemberFunRuleScope(suffixes.orEmpty(), prefixes.orEmpty())
      rules.forEach { rule -> scope.rule(kind) }
      if (scope.stripSuffixes != suffixes.orEmpty()) {
        put("$PREFIX${kind.name}.$SUFFIXES", scope.stripSuffixes.joinToString(","))
      }
      if (scope.stripPrefixes != prefixes.orEmpty()) {
        put("$PREFIX${kind.name}.$PREFIXES", scope.stripPrefixes.joinToString(","))
      }
    }
  }
}
