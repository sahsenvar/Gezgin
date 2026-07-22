package dev.gezgin.buildlogic.kdoc

import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinProductionCommentScannerTest {
  private val scanner = KotlinProductionCommentScanner()

  @kotlin.test.AfterTest
  fun closeScanner() {
    scanner.close()
  }

  @Test
  fun `rejects review examples and stale production history`() {
    val findings =
      scan(
        """
            /**
             * the LIVE-reference follows spec 291 (d; ).
             * E-MJ-F1 and mN2 were jar-doğrulandı during Faz 4.
             */
            public fun fixture(): Unit = Unit

            // C-MJ-1 — İleride Gelecekte bunu değiştir.
            private fun helper(): Unit = Unit
            """
      )

    assertEquals(
      setOf(
        ProductionCommentFindingKind.INTERNAL_HISTORY,
        ProductionCommentFindingKind.TURKISH_HISTORY,
        ProductionCommentFindingKind.MALFORMED_PROSE,
      ),
      findings.map(ProductionCommentFinding::kind).toSet(),
    )
  }

  @Test
  fun `rejects mechanical list fragments and duplicate prose`() {
    val findings =
      scan(
        """
            /**
             * The concrete concrete behavior.
             * no
             *
             * @GoTo continues here.
             */
            public fun fixture(): Unit = Unit
            """
      )

    assertEquals(
      setOf(ProductionCommentFindingKind.MALFORMED_PROSE),
      findings.map(ProductionCommentFinding::kind).toSet(),
    )
  }

  @Test
  fun `scans line comment tokens independently of KDoc`() {
    val findings =
      scan(
        """
            private fun fixture(): Unit = Unit
            // Integ m4 phase 2 provenance must not survive.
            """
      )

    assertEquals(
      setOf(ProductionCommentFindingKind.INTERNAL_HISTORY),
      findings.map(ProductionCommentFinding::kind).toSet(),
    )
  }

  @Test
  fun `scans trailing line comments`() {
    val findings = scan("private val fixture = Unit // behavior provenance §4.2/M3")

    assertEquals(
      setOf(ProductionCommentFindingKind.INTERNAL_HISTORY),
      findings.map(ProductionCommentFinding::kind).toSet(),
    )
  }

  @Test
  fun `treats consecutive line comments as one prose block`() {
    val findings =
      scan(
        """
            private val fixture = Unit // The listener stays registered (
            // until its owner is destroyed).
            """
      )

    assertEquals(emptyList(), findings)
  }

  @Test
  fun `allows natural production comments and ignores code literals`() {
    val findings =
      scan(
        """
            /** UI-UX behavior shared by CI-CD and QA-QC using UTF-8 and RFC-8259 JSON (a). */
            public fun fixture(): String {
                // Android-only process-death behavior remains consumer-facing.
                return "E-MJ-F1 is code data, not a comment"
            }
            """
      )

    assertEquals(emptyList(), findings)
  }

  @Test
  fun `rejects mechanical punctuation and sentence corruption families`() {
    val findings =
      scan(
        """
            /** `GezginInternalApi` (). */
            private fun emptyParentheses(): Unit = Unit

            /** The shape uses ( DI detection). */
            private fun innerWhitespace(): Unit = Unit

            /** The spec--canonical shape is stable. */
            private fun doubledHyphen(): Unit = Unit

            /** The reader returns). the serializer stays available. */
            private fun misplacedTerminator(): Unit = Unit

            /** Test API 's typed accessor. */
            private fun separatedPossessive(): Unit = Unit

            /** The value is restored. a consumer can continue. */
            private fun lowercaseFragment(): Unit = Unit

            /** The generated call follows rule 2. */
            private fun malformedRuleLabel(): Unit = Unit

            /** The runtime guard uses (, construction-time validation). */
            private fun orphanOpeningPunctuation(): Unit = Unit

            /**
             *  Doubled prefix whitespace is mechanical damage.
             */
            private fun doubledPrefixWhitespace(): Unit = Unit
            """
      )

    assertEquals(9, findings.count { it.kind == ProductionCommentFindingKind.MALFORMED_PROSE })
  }

  @Test
  fun `allows calls code spans ranges abbreviations and lowercase identifiers`() {
    val findings =
      scan(
        """
            /**
             * Calls foo() and keeps `bar()`, `spec--canonical`, `( DI detection)`, and `value. a`.
             * Supports the 1--2 range, en/em dashes, e.g. abbreviations, and bkz. lowerCaseSymbol.
             * Rule-based dispatch accepts `rule 2` as a code value.
             */
            private fun fixture(): Unit = Unit
            """
      )

    assertEquals(emptyList(), findings)
  }

  @Test
  fun `rejects Turkish prose punctuation fragments and plain internal ids`() {
    val findings =
      scan(
        """
            // Gerçek sistem geri davranışı burada korunur.
            private fun unicodeTurkish(): Unit = Unit

            // Yalniz bu durumda ayni deger yeniden kullanilir.
            private fun asciiTurkish(): Unit = Unit

            // The value is stable , process-wide.
            private fun spacedPunctuation(): Unit = Unit

            // ;
            private fun punctuationFragment(): Unit = Unit

            // is
            private fun wordFragment(): Unit = Unit

            // Decoder state.
            // stays.
            private fun consecutiveFragments(): Unit = Unit

            // MV7 wires navigation while SC2 rejects an invalid route.
            private fun internalIds(): Unit = Unit
            """
      )

    assertEquals(7, findings.size)
  }

  @Test
  fun `allows technical ids inline diagnostics and structural markers`() {
    val findings =
      scan(
        """
            /** Supports UTF8, HTTP2, SHA256, and the diagnostic `MV7`. */
            private fun technicalIds(): Unit = Unit

            // region Resolution
            // endregion
            private fun structuralMarkers(): Unit = Unit
            """
      )

    assertEquals(emptyList(), findings)
  }

  @Test
  fun `rejects incomplete individual line comment tokens before grouping`() {
    val findings =
      scan(
        """
            // The owner keeps a business
            // namespace
            // across configuration
            // changes.
            private fun splitSentence(): Unit = Unit

            // MviEntryCodegen's
            // 4-param
            // GezginMviEntries.kt
            // Delegated
            private fun fragmentKinds(): Unit = Unit

            // writes after
            // doesn't
            // as
            private fun shortFragments(): Unit = Unit
            """
      )

    assertEquals(10, findings.count { it.kind == ProductionCommentFindingKind.MALFORMED_PROSE })
  }

  @Test
  fun `allows complete standalone line comments directives labels urls and code`() {
    val findings =
      scan(
        """
            // Intentional.

            // No-op.

            // Fallback.

            // region Resolution

            // endregion

            // language=kotlin

            //noinspection KotlinConstantConditions

            // https://example.com/contracts

            // Failure mode:

            // `MV7`
            private fun controls(): Unit = Unit
            """
      )

    assertEquals(emptyList(), findings)
  }

  private fun scan(source: String): List<ProductionCommentFinding> =
    scanner.scan(
      KotlinSourceInput(path = "src/commonMain/kotlin/Fixture.kt", content = source.trimIndent())
    )
}
