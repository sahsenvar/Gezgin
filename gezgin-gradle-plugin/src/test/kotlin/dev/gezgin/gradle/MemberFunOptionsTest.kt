package dev.gezgin.gradle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberFunOptionsTest {
  private val suffixKey = "gezgin.naming.memberFun.stripSuffixes"
  private val prefixKey = "gezgin.naming.memberFun.stripPrefixes"

  private fun resolve(
    suffixes: List<String>? = null,
    prefixes: List<String>? = null,
    vararg rules: MemberFunRuleScope.(GezginAnnotation) -> Unit,
  ) = MemberFunOptions.resolve(suffixes, prefixes, rules.toList())

  @Test
  fun `unset configuration emits nothing`() {
    assertEquals(emptyMap(), resolve())
  }

  @Test
  fun `general lists apply to every kind without per-kind options`() {
    assertEquals(
      mapOf(suffixKey to "Route,Screen", prefixKey to "Old"),
      resolve(listOf("Route", "Screen"), listOf("Old")),
    )
  }

  @Test
  fun `an explicitly empty general list is still emitted`() {
    assertEquals(mapOf(suffixKey to ""), resolve(suffixes = emptyList()))
  }

  @Test
  fun `a rule that changes nothing emits no per-kind option`() {
    assertEquals(mapOf(suffixKey to "Route"), resolve(listOf("Route"), null, {}))
  }

  @Test
  fun `plusAssign starts from a copy of the general list`() {
    val options =
      resolve(
        listOf("Route"),
        null,
        { kind ->
          if (kind == GezginAnnotation.Open) stripSuffixes += listOf("Dialog", "BottomSheet")
        },
      )

    assertEquals(
      mapOf(
        suffixKey to "Route",
        "gezgin.naming.memberFun.Open.stripSuffixes" to "Route,Dialog,BottomSheet",
      ),
      options,
    )
  }

  @Test
  fun `assignment replaces the list for one kind only`() {
    val options =
      resolve(
        listOf("Route"),
        null,
        { kind -> if (kind == GezginAnnotation.BackTo) stripSuffixes = listOf("Screen") },
      )

    assertEquals(
      mapOf(suffixKey to "Route", "gezgin.naming.memberFun.BackTo.stripSuffixes" to "Screen"),
      options,
    )
  }

  @Test
  fun `minusAssign removes from the copy and can empty it`() {
    val options =
      resolve(
        listOf("Route", "Screen"),
        null,
        { kind ->
          if (kind == GezginAnnotation.GoTo) stripSuffixes -= "Screen"
          if (kind == GezginAnnotation.Open) stripSuffixes -= listOf("Route", "Screen")
        },
      )

    assertEquals(
      mapOf(
        suffixKey to "Route,Screen",
        "gezgin.naming.memberFun.GoTo.stripSuffixes" to "Route",
        "gezgin.naming.memberFun.Open.stripSuffixes" to "",
      ),
      options,
    )
  }

  @Test
  fun `a per-kind list is emitted without a general list`() {
    val options =
      resolve(
        rules = arrayOf({ kind -> if (kind == GezginAnnotation.Open) stripSuffixes += "Dialog" })
      )

    assertEquals(mapOf("gezgin.naming.memberFun.Open.stripSuffixes" to "Dialog"), options)
  }

  @Test
  fun `every rule runs in declaration order against the same copy`() {
    val options =
      resolve(
        listOf("Route"),
        null,
        { stripSuffixes += "A" },
        { stripSuffixes += "B" },
        { stripSuffixes = stripSuffixes.reversed() },
      )

    GezginAnnotation.entries.forEach { kind ->
      assertEquals("B,A,Route", options["gezgin.naming.memberFun.${kind.name}.stripSuffixes"])
    }
  }

  @Test
  fun `rules do not leak between kinds`() {
    val options =
      resolve(
        listOf("Route"),
        listOf("Old"),
        { kind -> if (kind == GezginAnnotation.GoTo) stripPrefixes += "Legacy" },
        { kind -> if (kind == GezginAnnotation.ReplaceTo) stripSuffixes = emptyList() },
      )

    assertEquals(
      mapOf(
        suffixKey to "Route",
        prefixKey to "Old",
        "gezgin.naming.memberFun.GoTo.stripPrefixes" to "Old,Legacy",
        "gezgin.naming.memberFun.ReplaceTo.stripSuffixes" to "",
      ),
      options,
    )
  }

  @Test
  fun `annotation names match the processor member kinds`() {
    val processorSource =
      Path.of(System.getProperty("user.dir"))
        .resolveSibling(
          "gezgin-processor/src/main/kotlin/dev/gezgin/processor/naming/MemberFunNaming.kt"
        )
    val declared =
      Regex("""enum class MemberKind \{([^}]*)}""")
        .find(Files.readString(processorSource))!!
        .groupValues[1]
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)

    assertEquals(declared, GezginAnnotation.entries.map(GezginAnnotation::name))
  }
}
