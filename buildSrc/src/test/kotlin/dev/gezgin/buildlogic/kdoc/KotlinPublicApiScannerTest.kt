package dev.gezgin.buildlogic.kdoc

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinPublicApiScannerTest {
  private val scanner = KotlinPublicApiScanner()

  @AfterTest
  fun closeScanner() {
    scanner.close()
  }

  @Test
  fun `parses multiline declarations and modifier ordering`() {
    val result =
      scan(
        """
            /**
             * A service.
             * @author @sahsenvar
             */
            public
            abstract
            class Service {
                public suspend override fun refresh(): Unit = Unit
            }

            /**
             * A payload.
             * @author @sahsenvar
             * @property value payload value
             */
            public data class Payload(
                public val value: String,
            )
            """
      )

    assertEquals(
      listOf("Service", "refresh", "Payload", "value"),
      result.declarations.map { it.name },
    )
    assertTrue(result.declarations.single { it.name == "refresh" }.excluded)
    assertEquals(0, result.findings.size)
  }

  @Test
  fun `uses the syntax tree for top level distinction regardless of indentation`() {
    val result =
      scan(
        """
                /**
                 * An outer type.
                 * @author @sahsenvar
                 */
                public open class Outer {
                    /** A nested type. */
                    public class Nested
                }
            """
      )

    assertTrue(result.declarations.single { it.name == "Outer" }.topLevelType)
    assertFalse(result.declarations.single { it.name == "Nested" }.topLevelType)
    assertEquals(0, result.findings.size)
  }

  @Test
  fun `distinguishes same line declaration annotations from constructor annotations`() {
    val result =
      scan(
        """
            import dev.gezgin.core.GezginInternalApi

            /**
             * A directly excluded seam.
             * @author @sahsenvar
             */
            @GezginInternalApi public class DirectSeam

            /** A consumer-visible flow type. */
            public class FlowType @GezginInternalApi constructor(
                /** The id. */ public val id: String,
            )
            """
      )

    assertTrue(result.declarations.single { it.name == "DirectSeam" }.excluded)
    assertFalse(result.declarations.single { it.name == "FlowType" }.excluded)
    assertEquals(
      listOf(KDocFindingKind.MISSING_AUTHOR),
      result.findings.filter { it.declaration.name == "FlowType" }.map { it.kind },
    )
  }

  @Test
  fun `excludes overrides in any legal modifier order`() {
    val result =
      scan(
        """
            /**
             * A host.
             * @author @sahsenvar
             */
            public class Host {
                public suspend override fun first(): Unit = Unit
                override public suspend fun second(): Unit = Unit
            }
            """
      )

    assertTrue(result.declarations.single { it.name == "first" }.excluded)
    assertTrue(result.declarations.single { it.name == "second" }.excluded)
    assertEquals(0, result.findings.size)
  }

  @Test
  fun `excludes generated files and directly internal api declaration subtrees`() {
    val generated =
      scanner.scan(
        KotlinSourceInput(
          path = "build/generated/Generated.kt",
          content = "public class GeneratedType",
          generated = true,
        )
      )
    assertEquals(1, generated.excludedCount)
    assertEquals(0, generated.findings.size)

    val internal =
      scan(
        """
            import dev.gezgin.core.GezginInternalApi

            /** Internal generated-code seam. */
            @GezginInternalApi
            public class InternalSeam(
                public val value: String,
            ) {
                public fun call(): Unit = Unit
            }
            """
      )
    assertTrue(internal.declarations.all { it.excluded })
    assertEquals(0, internal.findings.size)
  }

  @Test
  fun `inventories public enum entries and reports their missing KDoc`() {
    val result =
      scan(
        """
            /**
             * A display mode.
             * @author @sahsenvar
             */
            public enum class DisplayMode {
                /** Uses the default presentation. */
                Default,
                None,
            }
            """
      )

    assertEquals(listOf("DisplayMode", "Default", "None"), result.declarations.map { it.name })
    assertEquals("enum entry", result.declarations.single { it.name == "Default" }.kind)
    assertEquals(
      listOf(KDocFindingKind.MISSING_KDOC),
      result.findings.filter { it.declaration.name == "None" }.map { it.kind },
    )
  }

  @Test
  fun `inventories public secondary constructors and requires their own KDoc`() {
    val result =
      scan(
        """
            /**
             * A service.
             * @author @sahsenvar
             */
            public class Service {
                /** Creates a service from text. */
                public constructor(value: String)

                public constructor(value: Int)
            }
            """
      )

    val constructors = result.declarations.filter { it.kind == "constructor" }
    assertEquals(2, constructors.size)
    assertEquals(1, constructors.count { it.hasKDoc })
    assertEquals(
      listOf(KDocFindingKind.MISSING_KDOC),
      result.findings.filter { it.declaration.kind == "constructor" }.map { it.kind },
    )
  }

  @Test
  fun `resolves internal api annotation identity without trusting its short name`() {
    val alias =
      scan(
        """
            package consumer

            import dev.gezgin.core.GezginInternalApi as InternalMarker

            @InternalMarker public class AliasExcluded
            """
      )
    assertTrue(alias.declarations.single { it.name == "AliasExcluded" }.excluded)

    val explicit =
      scan(
        """
            package consumer

            import dev.gezgin.core.GezginInternalApi

            @GezginInternalApi public class ExplicitExcluded
            """
      )
    assertTrue(explicit.declarations.single { it.name == "ExplicitExcluded" }.excluded)

    val star =
      scan(
        """
            package consumer

            import dev.gezgin.core.*

            @GezginInternalApi public class StarExcluded
            """
      )
    assertTrue(star.declarations.single { it.name == "StarExcluded" }.excluded)

    val samePackage =
      scan(
        """
            package dev.gezgin.core

            @GezginInternalApi public class SamePackageExcluded
            """
      )
    assertTrue(samePackage.declarations.single { it.name == "SamePackageExcluded" }.excluded)

    val fullyQualified =
      scan(
        """
            package consumer

            @dev.gezgin.core.GezginInternalApi public class FullyQualifiedExcluded
            """
      )
    assertTrue(fullyQualified.declarations.single { it.name == "FullyQualifiedExcluded" }.excluded)

    val unrelated =
      scan(
        """
            package consumer

            private annotation class GezginInternalApi

            /**
             * A consumer-visible type.
             * @author @sahsenvar
             */
            @GezginInternalApi public class ConsumerVisible
            """
      )
    assertFalse(unrelated.declarations.single { it.name == "ConsumerVisible" }.excluded)
    assertEquals(0, unrelated.findings.size)

    val unrelatedImport =
      scan(
        """
            package consumer

            import unrelated.GezginInternalApi

            /**
             * Another consumer-visible type.
             * @author @sahsenvar
             */
            @GezginInternalApi public class ExplicitlyUnrelated
            """
      )
    assertFalse(unrelatedImport.declarations.single { it.name == "ExplicitlyUnrelated" }.excluded)
    assertEquals(0, unrelatedImport.findings.size)
  }

  @Test
  fun `reports missing KDoc and exact top level author independently`() {
    val result =
      scan(
        """
            public class MissingBoth

            /** Has type documentation but no author tag. */
            public class MissingAuthor

            public fun missingFunctionKDoc(): Unit = Unit
            """
      )

    assertEquals(
      setOf(KDocFindingKind.MISSING_KDOC, KDocFindingKind.MISSING_AUTHOR),
      result.findings.filter { it.declaration.name == "MissingBoth" }.map { it.kind }.toSet(),
    )
    assertEquals(
      listOf(KDocFindingKind.MISSING_AUTHOR),
      result.findings.filter { it.declaration.name == "MissingAuthor" }.map { it.kind },
    )
    assertEquals(
      listOf(KDocFindingKind.MISSING_KDOC),
      result.findings.filter { it.declaration.name == "missingFunctionKDoc" }.map { it.kind },
    )
  }

  @Test
  fun `rejects Turkish public KDoc with and without diacritics`() {
    val result =
      scan(
        """
            /**
             * Gezgin durumunu kullanıcıya döndürür.
             * @author @sahsenvar
             */
            public class TurkishWithDiacritics

            /** Kullanici icin sonuc doner. */
            public fun turkishWithoutDiacritics(): Unit = Unit
            """
      )

    assertEquals(
      setOf("TurkishWithDiacritics", "turkishWithoutDiacritics"),
      result.findings
        .filter { it.kind == KDocFindingKind.NON_ENGLISH_KDOC }
        .map { it.declaration.name }
        .toSet(),
    )
  }

  @Test
  fun `rejects implementation process artifacts only for included public KDoc`() {
    val result =
      scan(
        """
            import dev.gezgin.core.GezginInternalApi

            /**
             * Task 4 deliverable retained from the final-review report.
             * @author @sahsenvar
             */
            public class ProcessArtifact

            /** Phase migration TODO. */
            public fun processFunction(): Unit = Unit

            /** Spike report from a review checkpoint and implementation brief. */
            public fun processReport(): Unit = Unit

            /** Faz 3 internal implementation report. */
            @GezginInternalApi public class ExcludedProcessArtifact
            """
      )

    assertEquals(
      setOf("ProcessArtifact", "processFunction", "processReport"),
      result.findings
        .filter { it.kind == KDocFindingKind.PROCESS_ARTIFACT_KDOC }
        .map { it.declaration.name }
        .toSet(),
    )
    assertTrue(result.declarations.single { it.name == "ExcludedProcessArtifact" }.excluded)
  }

  @Test
  fun `allows process words when they describe API behavior in natural English`() {
    val result =
      scan(
        """
            /** Schedules a task for later execution. */
            public fun scheduleTask(): Unit = Unit

            /** Returns a report for the current route. */
            public fun currentReport(): Unit = Unit

            /** Lets callers review the route and persist checkpoint state. */
            public fun inspectRoute(): Unit = Unit
            """
      )

    assertEquals(
      emptyList(),
      result.findings.filter { it.kind == KDocFindingKind.PROCESS_ARTIFACT_KDOC },
    )
  }

  @Test
  fun `rejects internal spec notation only for included public KDoc`() {
    val result =
      scan(
        """
            import dev.gezgin.core.GezginInternalApi

            /** Follows the modal rules from §7, the m5 milestone, and C-MJ-1. */
            public fun internalNotation(): Unit = Unit

            /** Behavior was jar-verified against review provenance for Fix 9. */
            public fun testProvenance(): Unit = Unit

            /** Normal consumer-facing API documentation. */
            @GezginInternalApi public fun excludedNotation(): Unit = Unit
            """
      )

    assertEquals(
      setOf("internalNotation", "testProvenance"),
      result.findings
        .filter { it.kind == KDocFindingKind.INTERNAL_SPEC_KDOC }
        .map { it.declaration.name }
        .toSet(),
    )
    assertTrue(result.declarations.single { it.name == "excludedNotation" }.excluded)
  }

  @Test
  fun `rejects compact internal identifier families`() {
    val result =
      scan(
        """
            /** Carries the MJ-A stale-lambda fix. */
            public fun staleLambda(): Unit = Unit

            /** Implements E-MJ-F1 post-bind behavior. */
            public fun postBind(): Unit = Unit

            /** Memoizes route arguments according to mN2. */
            public fun memoizedArgs(): Unit = Unit
            """
      )

    assertEquals(
      setOf("staleLambda", "postBind", "memoizedArgs"),
      result.findings
        .filter { it.kind == KDocFindingKind.INTERNAL_SPEC_KDOC }
        .map { it.declaration.name }
        .toSet(),
    )
  }

  @Test
  fun `allows established consumer-facing technical tokens`() {
    val result =
      scan(
        """
            /** Uses UTF-8 and RFC-8259 JSON in an Android-only KMP/MVI UI-UX API. */
            public fun consumerTerms(): Unit = Unit

            /** Preserves process-death state through CI-CD and QA-QC API class names. */
            public fun lifecycleTerms(): Unit = Unit
            """
      )

    assertEquals(
      emptyList(),
      result.findings.filter { it.kind == KDocFindingKind.INTERNAL_SPEC_KDOC },
    )
  }

  @Test
  fun `rejects deterministic prose defects`() {
    val result =
      scan(
        """
            /** Generated accessors). */
            public fun unmatchedDelimiter(): Unit = Unit

            /** Uses the concrete concrete layout choice. */
            public fun duplicateWord(): Unit = Unit
            """
      )

    assertEquals(
      setOf("unmatchedDelimiter", "duplicateWord"),
      result.findings
        .filter { it.kind == KDocFindingKind.MALFORMED_KDOC }
        .map { it.declaration.name }
        .toSet(),
    )
  }

  private fun scan(source: String): KotlinPublicApiScanResult =
    scanner.scan(
      KotlinSourceInput(path = "src/main/kotlin/Fixture.kt", content = source.trimIndent())
    )
}
