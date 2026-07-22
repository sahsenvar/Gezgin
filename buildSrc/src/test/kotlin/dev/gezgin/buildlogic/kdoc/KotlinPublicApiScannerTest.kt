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
        val result = scan(
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
            """,
        )

        assertEquals(listOf("Service", "refresh", "Payload", "value"), result.declarations.map { it.name })
        assertTrue(result.declarations.single { it.name == "refresh" }.excluded)
        assertEquals(0, result.findings.size)
    }

    @Test
    fun `uses the syntax tree for top level distinction regardless of indentation`() {
        val result = scan(
            """
                /**
                 * An outer type.
                 * @author @sahsenvar
                 */
                public open class Outer {
                    /** A nested type. */
                    public class Nested
                }
            """,
        )

        assertTrue(result.declarations.single { it.name == "Outer" }.topLevelType)
        assertFalse(result.declarations.single { it.name == "Nested" }.topLevelType)
        assertEquals(0, result.findings.size)
    }

    @Test
    fun `distinguishes same line declaration annotations from constructor annotations`() {
        val result = scan(
            """
            /**
             * A directly excluded seam.
             * @author @sahsenvar
             */
            @GezginInternalApi public class DirectSeam

            /** A consumer-visible flow type. */
            public class FlowType @GezginInternalApi constructor(
                /** The id. */ public val id: String,
            )
            """,
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
        val result = scan(
            """
            /**
             * A host.
             * @author @sahsenvar
             */
            public class Host {
                public suspend override fun first(): Unit = Unit
                override public suspend fun second(): Unit = Unit
            }
            """,
        )

        assertTrue(result.declarations.single { it.name == "first" }.excluded)
        assertTrue(result.declarations.single { it.name == "second" }.excluded)
        assertEquals(0, result.findings.size)
    }

    @Test
    fun `excludes generated files and directly internal api declaration subtrees`() {
        val generated = scanner.scan(
            KotlinSourceInput(
                path = "build/generated/Generated.kt",
                content = "public class GeneratedType",
                generated = true,
            ),
        )
        assertEquals(1, generated.excludedCount)
        assertEquals(0, generated.findings.size)

        val internal = scan(
            """
            /** Internal generated-code seam. */
            @GezginInternalApi
            public class InternalSeam(
                public val value: String,
            ) {
                public fun call(): Unit = Unit
            }
            """,
        )
        assertTrue(internal.declarations.all { it.excluded })
        assertEquals(0, internal.findings.size)
    }

    @Test
    fun `reports missing KDoc and exact top level author independently`() {
        val result = scan(
            """
            public class MissingBoth

            /** Has type documentation but no author tag. */
            public class MissingAuthor

            public fun missingFunctionKDoc(): Unit = Unit
            """,
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

    private fun scan(source: String): KotlinPublicApiScanResult =
        scanner.scan(KotlinSourceInput(path = "src/main/kotlin/Fixture.kt", content = source.trimIndent()))
}
