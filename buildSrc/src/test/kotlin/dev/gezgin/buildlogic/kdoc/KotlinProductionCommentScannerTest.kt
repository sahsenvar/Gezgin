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

  private fun scan(source: String): List<ProductionCommentFinding> =
    scanner.scan(
      KotlinSourceInput(path = "src/commonMain/kotlin/Fixture.kt", content = source.trimIndent())
    )
}
