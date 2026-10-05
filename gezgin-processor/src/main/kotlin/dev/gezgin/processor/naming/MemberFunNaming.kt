package dev.gezgin.processor.naming

/**
 * The edge annotation a generated navigator member comes from. The names double as the `<Kind>`
 * segment of a `gezgin.naming.memberFun.<Kind>.stripSuffixes` option.
 */
internal enum class MemberKind {
  GoTo,
  ReplaceTo,
  QuitAndGoTo,
  GoForResult,
  BackTo,
  Open,
}

/** The two affix lists [MemberFunNaming] strips; each is configured independently per kind. */
private enum class Affix(val option: String) {
  Suffixes("stripSuffixes"),
  Prefixes("stripPrefixes"),
}

/**
 * Derives the `X` of generated navigator members (`goToX`, `openX`, `launchX`, `backToX`, …) from a
 * route's simple name by stripping configured prefixes and suffixes.
 *
 * Nothing is stripped by default. [stripSuffixes] and [stripPrefixes] apply to every [MemberKind];
 * an entry in [stripSuffixesByKind] or [stripPrefixesByKind] replaces the matching list for that
 * kind. Affixes are tried in list order and each is removed at most once, and a name is never
 * emptied. An edge's `name=` still wins over everything here — this only shapes the derived name.
 */
internal class MemberFunNaming(
  private val stripSuffixes: List<String> = emptyList(),
  private val stripPrefixes: List<String> = emptyList(),
  private val stripSuffixesByKind: Map<MemberKind, List<String>> = emptyMap(),
  private val stripPrefixesByKind: Map<MemberKind, List<String>> = emptyMap(),
) {

  fun x(kind: MemberKind, simpleName: String): String {
    var name = simpleName
    for (prefix in stripPrefixesByKind[kind] ?: stripPrefixes) {
      if (name.length > prefix.length && name.startsWith(prefix)) name = name.removePrefix(prefix)
    }
    for (suffix in stripSuffixesByKind[kind] ?: stripSuffixes) {
      if (name.length > suffix.length && name.endsWith(suffix)) name = name.removeSuffix(suffix)
    }
    return name
  }

  companion object {
    val Default = MemberFunNaming()

    private const val PREFIX = "gezgin.naming."
    private const val MEMBER_FUN = "${PREFIX}memberFun."

    /**
     * Reads the `gezgin.naming.memberFun.*` KSP options. Every key under `gezgin.naming.` that is
     * not understood is reported through [onError] so a typo cannot silently fall back to the
     * default naming.
     */
    fun fromOptions(options: Map<String, String>, onError: (String) -> Unit): MemberFunNaming {
      val general = mutableMapOf<Affix, List<String>>()
      val byKind = mutableMapOf<Pair<Affix, MemberKind>, List<String>>()
      options
        .filterKeys { it.startsWith(PREFIX) }
        .forEach { (key, value) ->
          val rest = key.removePrefix(MEMBER_FUN).takeIf { key.startsWith(MEMBER_FUN) }
          val affix = Affix.entries.firstOrNull { rest != null && rest.endsWith(it.option) }
          val values = value.split(',').map(String::trim).filter(String::isNotEmpty)
          when {
            rest == null || affix == null -> onError(unknownOption(key))
            rest == affix.option -> general[affix] = values
            rest.endsWith(".${affix.option}") -> {
              val kind =
                MemberKind.entries.firstOrNull { it.name == rest.removeSuffix(".${affix.option}") }
              if (kind != null) {
                byKind[affix to kind] = values
              } else {
                onError(
                  "$key names no edge kind; use one of ${MemberKind.entries.joinToString { it.name }}"
                )
              }
            }
            else -> onError(unknownOption(key))
          }
        }
      fun kindsOf(affix: Affix) =
        byKind.filterKeys { it.first == affix }.mapKeys { (key, _) -> key.second }
      return MemberFunNaming(
        stripSuffixes = general[Affix.Suffixes].orEmpty(),
        stripPrefixes = general[Affix.Prefixes].orEmpty(),
        stripSuffixesByKind = kindsOf(Affix.Suffixes),
        stripPrefixesByKind = kindsOf(Affix.Prefixes),
      )
    }

    private fun unknownOption(key: String) =
      "unknown naming option $key; supported: $MEMBER_FUN{stripSuffixes,stripPrefixes} and " +
        "$MEMBER_FUN<Kind>.{stripSuffixes,stripPrefixes}"
  }
}
