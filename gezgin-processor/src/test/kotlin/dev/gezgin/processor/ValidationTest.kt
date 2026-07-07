package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 2.3 gate: for every rule in [GezginValidator], a minimal source violating it must fail
 * compilation with a `[<code>]`-prefixed KSP error message, and the shared [SHOP_SOURCE] fixture
 * (already pinned violation-free by [ModelReaderTest]) must keep compiling clean now that
 * validation runs on every round.
 */
@OptIn(ExperimentalCompilerApi::class)
class ValidationTest {

    private fun assertViolates(code: String, source: String) {
        val result = compileGezgin(SourceFile.kotlin("Source.kt", source))
        assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertContains(result.messages, "[$code]", message = result.messages)
    }

    private fun assertCompilesClean(source: String) {
        val result = compileGezgin(SourceFile.kotlin("Source.kt", source))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    // region Shared positive

    @Test
    fun `SHOP_SOURCE fixture remains violation-free`() {
        val result = compileGezgin(SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    // endregion

    // region Rule-boundary positives (spec §8.1/§4.2 adjudication — each must compile clean)

    @Test
    fun `positive - GoTo and ReplaceTo into a non-result FlowGraph container are allowed`() {
        assertCompilesClean(
            """
            package dev.gezgin.pos.a

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.ReplaceTo
            import dev.gezgin.core.annotation.StartDestination

            @NavGraph
            interface HomeGraph : Route {
                @GoTo(OnboardingFlow::class)
                @ReplaceTo(OnboardingFlow::class)
                data object Feed : HomeGraph
            }

            @FlowGraph
            interface OnboardingFlow : Route {
                @StartDestination
                data object Welcome : OnboardingFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `positive - GoForResult from inside a flow to an external ResultRoute is allowed`() {
        assertCompilesClean(
            """
            package dev.gezgin.pos.b

            import dev.gezgin.core.ResultRoute
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.GoForResult
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.StartDestination
            import kotlinx.serialization.KSerializer
            import kotlinx.serialization.descriptors.SerialDescriptor
            import kotlinx.serialization.descriptors.buildClassSerialDescriptor
            import kotlinx.serialization.encoding.Decoder
            import kotlinx.serialization.encoding.Encoder

            // Test-only stub (no kotlinx-serialization plugin in kctfork) — Task 2.4's codegen
            // always emits a real `Picked.serializer()` call for this @GoForResult edge. The
            // factory call itself must succeed (it runs eagerly if the generated topology is ever
            // classloaded); only the actual (de)serialize methods are unsupported.
            data class Picked(val v: String) {
                companion object {
                    fun serializer(): KSerializer<Picked> = object : KSerializer<Picked> {
                        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Picked")
                        override fun serialize(encoder: Encoder, value: Picked): Unit =
                            throw UnsupportedOperationException("test stub")
                        override fun deserialize(decoder: Decoder): Picked =
                            throw UnsupportedOperationException("test stub")
                    }
                }
            }

            @NavGraph
            interface HomeGraph : Route {
                data object Picker : HomeGraph, ResultRoute<Picked>
            }

            @FlowGraph
            interface CheckoutFlow : Route {
                @StartDestination
                @GoForResult(HomeGraph.Picker::class)
                data object Cart : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `positive - QuitAndGoTo from a non-result flow member to a normal route is allowed`() {
        assertCompilesClean(
            """
            package dev.gezgin.pos.c

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.QuitAndGoTo
            import dev.gezgin.core.annotation.StartDestination

            @NavGraph
            interface HomeGraph : Route {
                data object Landing : HomeGraph
            }

            @FlowGraph
            interface OnboardingFlow : Route {
                @StartDestination
                data object Welcome : OnboardingFlow

                @QuitAndGoTo(HomeGraph.Landing::class)
                data object Done : OnboardingFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `positive - GoTo from a ResultFlow member to its own start is allowed (E1 inside-exemption)`() {
        assertCompilesClean(
            """
            package dev.gezgin.pos.d

            import dev.gezgin.core.ResultFlow
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.StartDestination

            data class Res(val v: String)

            @FlowGraph
            interface CheckoutFlow : Route, ResultFlow<Res> {
                @StartDestination
                data object Cart : CheckoutFlow

                @GoTo(Cart::class)
                data object Payment : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `positive - graph extending another annotated graph does not trip E5 on its routes`() {
        assertCompilesClean(
            """
            package dev.gezgin.pos.e

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.NavGraph

            @NavGraph
            interface AppGraph : Route

            @NavGraph
            interface OrderGraph : AppGraph {
                data object Orders : OrderGraph
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `positive - FlowGraph start with only nullable ctor params is allowed (G1)`() {
        assertCompilesClean(
            """
            package dev.gezgin.pos.f

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.StartDestination

            @FlowGraph
            interface CheckoutFlow : Route {
                @StartDestination
                data class Cart(val promoCode: String?) : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region E1 — ResultFlow entry only via @GoForResult

    @Test
    fun `E1 - GoTo into a ResultFlow container is rejected`() {
        assertViolates(
            "E1",
            """
            package dev.gezgin.e1

            import dev.gezgin.core.Route
            import dev.gezgin.core.ResultFlow
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.StartDestination

            data class Res(val v: String)

            @NavGraph
            interface HomeGraph : Route {
                @GoTo(CheckoutFlow::class)
                data object Feed : HomeGraph
            }

            @FlowGraph
            interface CheckoutFlow : Route, ResultFlow<Res> {
                @StartDestination
                data object Cart : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region E2 — @GoForResult must target a ResultRoute or a ResultFlow

    @Test
    fun `E2 - GoForResult into a plain route is rejected`() {
        assertViolates(
            "E2",
            """
            package dev.gezgin.e2

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.GoForResult
            import dev.gezgin.core.annotation.NavGraph

            @NavGraph
            interface HomeGraph : Route {
                @GoForResult(Catalog::class)
                data object Feed : HomeGraph

                data object Catalog : HomeGraph
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region E3 — flow-external direct member targeting

    @Test
    fun `E3 - GoTo into an inner (non-start) member of an unrelated flow is rejected`() {
        assertViolates(
            "E3",
            """
            package dev.gezgin.e3

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.StartDestination

            @NavGraph
            interface HomeGraph : Route {
                @GoTo(CheckoutFlow.Payment::class)
                data object Feed : HomeGraph
            }

            @FlowGraph
            interface CheckoutFlow : Route {
                @StartDestination
                data object Cart : CheckoutFlow

                data object Payment : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region E4 — clearUpTo must stay within the source's innermost flow

    @Test
    fun `E4 - ReplaceTo clearUpTo outside the source's innermost flow is rejected`() {
        assertViolates(
            "E4",
            """
            package dev.gezgin.e4

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.ReplaceTo
            import dev.gezgin.core.annotation.StartDestination

            @NavGraph
            interface HomeGraph : Route {
                data object Outside : HomeGraph
            }

            @FlowGraph
            interface CheckoutFlow : Route {
                @StartDestination
                data object Cart : CheckoutFlow

                @ReplaceTo(Cart::class, clearUpTo = HomeGraph.Outside::class)
                data object Payment : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region E5 — a route may only implement its own nesting graph

    @Test
    fun `E5 - route implementing a second graph interface is rejected`() {
        assertViolates(
            "E5",
            """
            package dev.gezgin.e5

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.NavGraph

            @NavGraph
            interface OtherGraph : Route

            @NavGraph
            interface HomeGraph : Route {
                data object Feed : HomeGraph, OtherGraph
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region G1 — a FlowGraph's start must be parameterless-constructible

    @Test
    fun `G1 - FlowGraph start with a required ctor param is rejected`() {
        assertViolates(
            "G1",
            """
            package dev.gezgin.g1

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.StartDestination

            @FlowGraph
            interface CheckoutFlow : Route {
                @StartDestination
                data class Cart(val id: String) : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region N9 — duplicate same-kind, same-target edges both unnamed

    @Test
    fun `N9 - two unnamed GoTo's to the same target are rejected`() {
        assertViolates(
            "N9",
            """
            package dev.gezgin.n9

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.NavGraph

            @NavGraph
            interface HomeGraph : Route {
                @GoTo(Catalog::class)
                @GoTo(Catalog::class)
                data object Feed : HomeGraph

                data object Catalog : HomeGraph
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region R1 — only @FlowGraph may implement ResultFlow<T>

    @Test
    fun `R1 - a NavGraph directly implementing ResultFlow is rejected`() {
        assertViolates(
            "R1",
            """
            package dev.gezgin.r1direct

            import dev.gezgin.core.Route
            import dev.gezgin.core.ResultFlow
            import dev.gezgin.core.annotation.NavGraph

            data class Res(val v: String)

            @NavGraph
            interface HomeGraph : Route, ResultFlow<Res> {
                data object Feed : HomeGraph
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `R1 - a nested NavGraph extending its enclosing ResultFlow is rejected (transitive)`() {
        assertViolates(
            "R1",
            """
            package dev.gezgin.r1transitive

            import dev.gezgin.core.Route
            import dev.gezgin.core.ResultFlow
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.StartDestination

            data class Res(val v: String)

            @FlowGraph
            interface CheckoutFlow : Route, ResultFlow<Res> {

                @StartDestination
                data object Cart : CheckoutFlow

                // Nested @NavGraph extending its own enclosing @FlowGraph — transitively inherits
                // ResultFlow<Res>, which R1 forbids for anything that isn't itself a @FlowGraph.
                @NavGraph
                interface CheckoutPages : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region NB1 — @NoBack and @StartDestination are mutually exclusive

    @Test
    fun `NB1 - NoBack together with StartDestination is rejected`() {
        assertViolates(
            "NB1",
            """
            package dev.gezgin.nb1

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.NoBack
            import dev.gezgin.core.annotation.StartDestination

            @FlowGraph
            interface CheckoutFlow : Route {
                @NoBack
                @StartDestination
                data object Cart : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region SD1 — exactly one start per FlowGraph, none in a NavGraph

    @Test
    fun `SD1 - a FlowGraph with two StartDestinations is rejected`() {
        assertViolates(
            "SD1",
            """
            package dev.gezgin.sd1two

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.StartDestination

            @FlowGraph
            interface CheckoutFlow : Route {
                @StartDestination
                data object Cart : CheckoutFlow

                @StartDestination
                data object Payment : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `SD1 - a FlowGraph with zero StartDestinations is rejected`() {
        assertViolates(
            "SD1",
            """
            package dev.gezgin.sd1zero

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph

            @FlowGraph
            interface CheckoutFlow : Route {
                data object Cart : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `SD1 - a StartDestination inside a NavGraph is rejected`() {
        assertViolates(
            "SD1",
            """
            package dev.gezgin.sd1navgraph

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.StartDestination

            @NavGraph
            interface HomeGraph : Route {
                @StartDestination
                data object Feed : HomeGraph
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region FX1 — @BackToStart/@Quit need an enclosing flow

    @Test
    fun `FX1 - Quit outside any flow is rejected`() {
        assertViolates(
            "FX1",
            """
            package dev.gezgin.fx1

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.Quit

            @NavGraph
            interface HomeGraph : Route {
                @Quit
                data object Feed : HomeGraph
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region FX2 — @QuitAndGoTo inside a ResultFlow drops the expected result

    @Test
    fun `FX2 - QuitAndGoTo from a ResultFlow member is rejected`() {
        assertViolates(
            "FX2",
            """
            package dev.gezgin.fx2

            import dev.gezgin.core.Route
            import dev.gezgin.core.ResultFlow
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.QuitAndGoTo
            import dev.gezgin.core.annotation.StartDestination

            data class Res(val v: String)

            @NavGraph
            interface HomeGraph : Route {
                data object Landing : HomeGraph
            }

            @FlowGraph
            interface CheckoutFlow : Route, ResultFlow<Res> {

                @StartDestination
                data object Cart : CheckoutFlow

                @QuitAndGoTo(HomeGraph.Landing::class)
                data object Payment : CheckoutFlow
            }
            """.trimIndent(),
        )
    }

    // endregion
}
