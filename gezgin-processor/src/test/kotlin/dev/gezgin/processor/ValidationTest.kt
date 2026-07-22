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

  @Test
  fun `repeatable Screen binds one composable to two routes`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Source.kt",
          """
          package dev.gezgin.repeatablescreen

          import androidx.compose.runtime.Composable
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.NavGraph
          import dev.gezgin.core.annotation.Screen

          @NavGraph
          sealed interface G : Route {
              data object A : G
              data object B : G
          }

          @Screen(G.A::class)
          @Screen(G.B::class)
          @Composable
          fun SharedContent() {}
              """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false", "gezgin.emitSerializers" to "false"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }

  @Test
  fun `SC7 rejects no-back bottom sheet when structural contract is missing`() {
    assertViolates(
      "SC7",
      """
      package dev.gezgin.validation.sheetmissing

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.BottomSheet
      import dev.gezgin.core.annotation.NoBack

      @NoBack
      data object LockedSheet : Route

      @BottomSheet(LockedSheet::class)
      @Composable
      fun LockedSheetContent() {}
      """
        .trimIndent(),
    )
  }

  @Test
  fun `SC7 accepts no-back bottom sheet with runtime-valued structural contract`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Source.kt",
          """
          package dev.gezgin.validation.sheetcontract

          import androidx.compose.runtime.Composable
          import dev.gezgin.core.BottomSheetContract
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.BottomSheet
          import dev.gezgin.core.annotation.NoBack

          @NoBack
          data object LockedSheet : Route, BottomSheetContract {
              override val dismissOnBackPress get() = false
              override val sheetGesturesEnabled get() = false
          }

          @BottomSheet(LockedSheet::class)
          @Composable
          fun LockedSheetContent() {}
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitSerializers" to "false"),
      )
    assertEquals(false, result.messages.contains("[SC7]"), result.messages)
    assertEquals(false, result.messages.contains("[SC8]"), result.messages)
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
      sealed interface HomeGraph : Route {
          @GoTo(OnboardingFlow::class)
          @ReplaceTo(OnboardingFlow::class)
          data object Feed : HomeGraph
      }

      @FlowGraph
      sealed interface OnboardingFlow : Route {
          @StartDestination
          data object Welcome : OnboardingFlow
      }
      """
        .trimIndent()
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
      sealed interface HomeGraph : Route {
          data object Picker : HomeGraph, ResultRoute<Picked>
      }

      @FlowGraph
      sealed interface CheckoutFlow : Route {
          @StartDestination
          @GoForResult(HomeGraph.Picker::class)
          data object Cart : CheckoutFlow
      }
      """
        .trimIndent()
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
      sealed interface HomeGraph : Route {
          data object Landing : HomeGraph
      }

      @FlowGraph
      sealed interface OnboardingFlow : Route {
          @StartDestination
          data object Welcome : OnboardingFlow

          @QuitAndGoTo(HomeGraph.Landing::class)
          data object Done : OnboardingFlow
      }
      """
        .trimIndent()
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
      sealed interface CheckoutFlow : Route, ResultFlow<Res> {
          @StartDestination
          data object Cart : CheckoutFlow

          @GoTo(Cart::class)
          data object Payment : CheckoutFlow
      }
      """
        .trimIndent()
    )
  }

  @Test
  fun `positive - GoTo into a result-less nested sub-flow from within its enclosing ResultFlow is allowed`() {
    // AvatarFlow OWNS the result contract (ResultFlow<Res>); ZoomFlow is nested inside it and
    // only INHERITS the marker transitively (declares none of its own). Crop, itself inside
    // AvatarFlow, @GoTo-ing ZoomFlow crosses no *result* boundary — E1 must key on the DIRECT
    // declaration, not the transitive `isResultFlow` (which would wrongly reject this). E3 must
    // also stay quiet because AvatarFlow is in Crop's flow chain.
    assertCompilesClean(
      """
      package dev.gezgin.pos.nestedflow

      import dev.gezgin.core.ResultFlow
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FlowGraph
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.StartDestination

      data class Res(val v: String)

      @FlowGraph
      sealed interface AvatarFlow : Route, ResultFlow<Res> {
          @StartDestination
          @GoTo(Crop::class)
          data object PickSource : AvatarFlow

          @GoTo(ZoomFlow::class)
          data class Crop(val source: String) : AvatarFlow

          @FlowGraph
          sealed interface ZoomFlow : AvatarFlow {
              @StartDestination
              data object Zoom : ZoomFlow
          }
      }
      """
        .trimIndent()
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
      sealed interface AppGraph : Route

      @NavGraph
      sealed interface OrderGraph : AppGraph {
          data object Orders : OrderGraph
      }
      """
        .trimIndent()
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
      sealed interface CheckoutFlow : Route {
          @StartDestination
          data class Cart(val promoCode: String?) : CheckoutFlow
      }
      """
        .trimIndent()
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
      sealed interface HomeGraph : Route {
          @GoTo(CheckoutFlow::class)
          data object Feed : HomeGraph
      }

      @FlowGraph
      sealed interface CheckoutFlow : Route, ResultFlow<Res> {
          @StartDestination
          data object Cart : CheckoutFlow
      }
      """
        .trimIndent(),
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
      sealed interface HomeGraph : Route {
          @GoForResult(Catalog::class)
          data object Feed : HomeGraph

          data object Catalog : HomeGraph
      }
      """
        .trimIndent(),
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
      sealed interface HomeGraph : Route {
          @GoTo(CheckoutFlow.Payment::class)
          data object Feed : HomeGraph
      }

      @FlowGraph
      sealed interface CheckoutFlow : Route {
          @StartDestination
          data object Cart : CheckoutFlow

          data object Payment : CheckoutFlow
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `E3 - GoTo into a NESTED flow container from outside the enclosing flow is rejected`() {
    // ZoomFlow (a flow container) is itself an interior member of AvatarFlow — targeting it
    // from HomeGraph (outside AvatarFlow) jumps over AvatarFlow's boundary. The ancestor-chain
    // walk must flag AvatarFlow even though the DIRECT target is a legal-looking flow container.
    assertViolates(
      "E3",
      """
      package dev.gezgin.e3.nestedcontainer

      import dev.gezgin.core.ResultFlow
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FlowGraph
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.StartDestination

      data class Res(val v: String)

      @NavGraph
      sealed interface HomeGraph : Route {
          @GoTo(AvatarFlow.ZoomFlow::class)
          data object Feed : HomeGraph
      }

      @FlowGraph
      sealed interface AvatarFlow : Route, ResultFlow<Res> {
          @StartDestination
          data object PickSource : AvatarFlow

          @FlowGraph
          sealed interface ZoomFlow : AvatarFlow {
              @StartDestination
              data object Zoom : ZoomFlow
          }
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `E3 - GoTo into a NESTED flow's start route from outside the enclosing flow is rejected`() {
    // The grandchild-start hole (discriminator for the ancestor-chain walk — compiled clean
    // before the fix): Zoom is ZoomFlow's @StartDestination, so the innermost-container start
    // exemption applies to ZoomFlow — but AvatarFlow (the enclosing flow) is NOT in Feed's flow
    // chain, so the edge still crosses AvatarFlow's boundary, silently bypassing its result
    // contract. Neither the old single-level E3 nor E1 (ZoomFlow doesn't DIRECTLY declare
    // ResultFlow) caught this.
    assertViolates(
      "E3",
      """
      package dev.gezgin.e3.nestedstart

      import dev.gezgin.core.ResultFlow
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FlowGraph
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.StartDestination

      data class Res(val v: String)

      @NavGraph
      sealed interface HomeGraph : Route {
          @GoTo(AvatarFlow.ZoomFlow.Zoom::class)
          data object Feed : HomeGraph
      }

      @FlowGraph
      sealed interface AvatarFlow : Route, ResultFlow<Res> {
          @StartDestination
          data object PickSource : AvatarFlow

          @FlowGraph
          sealed interface ZoomFlow : AvatarFlow {
              @StartDestination
              data object Zoom : ZoomFlow
          }
      }
      """
        .trimIndent(),
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
      sealed interface HomeGraph : Route {
          data object Outside : HomeGraph
      }

      @FlowGraph
      sealed interface CheckoutFlow : Route {
          @StartDestination
          data object Cart : CheckoutFlow

          @ReplaceTo(Cart::class, clearUpTo = HomeGraph.Outside::class)
          data object Payment : CheckoutFlow
      }
      """
        .trimIndent(),
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
      sealed interface OtherGraph : Route

      @NavGraph
      sealed interface HomeGraph : Route {
          data object Feed : HomeGraph, OtherGraph
      }
      """
        .trimIndent(),
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
      sealed interface CheckoutFlow : Route {
          @StartDestination
          data class Cart(val id: String) : CheckoutFlow
      }
      """
        .trimIndent(),
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
      sealed interface HomeGraph : Route {
          @GoTo(Catalog::class)
          @GoTo(Catalog::class)
          data object Feed : HomeGraph

          data object Catalog : HomeGraph
      }
      """
        .trimIndent(),
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
      sealed interface HomeGraph : Route, ResultFlow<Res> {
          data object Feed : HomeGraph
      }
      """
        .trimIndent(),
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
      sealed interface CheckoutFlow : Route, ResultFlow<Res> {

          @StartDestination
          data object Cart : CheckoutFlow

          // Nested @NavGraph extending its own enclosing @FlowGraph — transitively inherits
          // ResultFlow<Res>, which R1 forbids for anything that isn't itself a @FlowGraph.
          @NavGraph
          sealed interface CheckoutPages : CheckoutFlow
      }
      """
        .trimIndent(),
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
      sealed interface CheckoutFlow : Route {
          @NoBack
          @StartDestination
          data object Cart : CheckoutFlow
      }
      """
        .trimIndent(),
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
      sealed interface CheckoutFlow : Route {
          @StartDestination
          data object Cart : CheckoutFlow

          @StartDestination
          data object Payment : CheckoutFlow
      }
      """
        .trimIndent(),
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
      sealed interface CheckoutFlow : Route {
          data object Cart : CheckoutFlow
      }
      """
        .trimIndent(),
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
      sealed interface HomeGraph : Route {
          @StartDestination
          data object Feed : HomeGraph
      }
      """
        .trimIndent(),
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
      sealed interface HomeGraph : Route {
          @Quit
          data object Feed : HomeGraph
      }
      """
        .trimIndent(),
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
      sealed interface HomeGraph : Route {
          data object Landing : HomeGraph
      }

      @FlowGraph
      sealed interface CheckoutFlow : Route, ResultFlow<Res> {

          @StartDestination
          data object Cart : CheckoutFlow

          @QuitAndGoTo(HomeGraph.Landing::class)
          data object Payment : CheckoutFlow
      }
      """
        .trimIndent(),
    )
  }

  // endregion

  // region E6 — forward-edge/@BackTo targets must resolve to a route or @FlowGraph

  @Test
  fun `E6 - GoTo into a NavGraph (which has no start) is rejected`() {
    assertViolates(
      "E6",
      """
      package dev.gezgin.e6navgraph

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph

      @NavGraph
      sealed interface HomeGraph : Route {
          @GoTo(OtherGraph::class)
          data object Feed : HomeGraph
      }

      @NavGraph
      sealed interface OtherGraph : Route {
          data object Landing : OtherGraph
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `E6 - GoTo to a Route class outside every graph is rejected`() {
    assertViolates(
      "E6",
      """
      package dev.gezgin.e6external

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph

      // A Route subtype (so the annotation type-checks) but NOT nested in any graph — the
      // model has no such route, so codegen could never resolve it.
      data object Orphan : Route

      @NavGraph
      sealed interface HomeGraph : Route {
          @GoTo(Orphan::class)
          data object Feed : HomeGraph
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `E6 - BackTo targeting a graph (not a route) is rejected`() {
    assertViolates(
      "E6",
      """
      package dev.gezgin.e6backto

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.BackTo
      import dev.gezgin.core.annotation.NavGraph

      @NavGraph
      sealed interface HomeGraph : Route {
          @BackTo(OtherGraph::class)
          data object Feed : HomeGraph
      }

      @NavGraph
      sealed interface OtherGraph : Route {
          data object Landing : OtherGraph
      }
      """
        .trimIndent(),
    )
  }

  // endregion

  // region N10 — generated navigator name collisions

  @Test
  fun `N10 - two routes collapsing to the same navigator class name are rejected`() {
    assertViolates(
      "N10",
      """
      package dev.gezgin.n10class

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph

      @NavGraph
      sealed interface AGraph : Route {
          @GoTo(AFoo::class)
          data object Detail : AGraph

          data object AFoo : AGraph
      }

      @NavGraph
      sealed interface BGraph : Route {
          @GoTo(BFoo::class)
          data object Detail : BGraph

          data object BFoo : BGraph
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `N10 - two edges producing the same member name (Detail + DetailRoute) are rejected`() {
    assertViolates(
      "N10",
      """
      package dev.gezgin.n10member

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph

      @NavGraph
      sealed interface HomeGraph : Route {
          // goToDetail (from Detail) and goToDetail (from DetailRoute, 'Route' stripped) clash.
          @GoTo(Detail::class)
          @GoTo(DetailRoute::class)
          data object Feed : HomeGraph

          data object Detail : HomeGraph

          data object DetailRoute : HomeGraph
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `N10 - a name= override spelling a reserved navigator member is rejected`() {
    // Task 3.4 devir: `name = "back"` would emit a goTo method literally named `back`, silently
    // colliding with the navigator's own unconditional back() member.
    assertViolates(
      "N10",
      """
      package dev.gezgin.n10reserved

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph

      @NavGraph
      sealed interface HomeGraph : Route {
          @GoTo(Detail::class, name = "back")
          data object Feed : HomeGraph

          data object Detail : HomeGraph
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `N10 - name=quitWith on a ResultFlow member collides with the flow-earned quitWith member`() {
    // A ResultFlow member's navigator ALWAYS carries quitWith(result) — a name= override
    // spelling it out would emit a second, edge-shaped quitWith into the same class.
    assertViolates(
      "N10",
      """
      package dev.gezgin.n10quitwith

      import dev.gezgin.core.ResultFlow
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FlowGraph
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.StartDestination

      @FlowGraph
      sealed interface CheckoutFlow : Route, ResultFlow<String> {
          @StartDestination
          @GoTo(Payment::class, name = "quitWith")
          data object Cart : CheckoutFlow

          data object Payment : CheckoutFlow
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `N10 - a second edge colliding with a @GoForResult triple sibling (goToXForResult) is rejected`() {
    // The named @GoForResult emits launchPick/pickResults/goToPickForResult — ALL THREE are
    // recorded, so a @GoTo whose name= spells out a SIBLING member (not just launchX) clashes.
    assertViolates(
      "N10",
      """
      package dev.gezgin.n10triple

      import dev.gezgin.core.ResultRoute
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoForResult
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph

      @NavGraph
      sealed interface HomeGraph : Route {
          @GoForResult(Picker::class, name = "pick")
          @GoTo(Detail::class, name = "goToPickForResult")
          data object Feed : HomeGraph

          data object Picker : HomeGraph, ResultRoute<String>

          data object Detail : HomeGraph
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `N10 - name=entryId collides with the navigator's unconditional private entryId property`() {
    // Every navigator carries `private val entryId: Long` (NavigatorCodegen.kt:154). A name=
    // override spelling `entryId` emits `fun entryId(...)` alongside it → conflicting declarations.
    assertViolates(
      "N10",
      """
      package dev.gezgin.n10entryid

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph

      @NavGraph
      sealed interface HomeGraph : Route {
          @GoTo(Detail::class, name = "entryId")
          data object Feed : HomeGraph

          data object Detail : HomeGraph
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `N10 - name=backToStart override collides with the @BackToStart fixed member`() {
    // Crop carries a fixed backToStart() from @BackToStart AND a goTo named "backToStart" — two
    // backToStart() members in one navigator (uncompilable) → N10 via the size>=2 collision path.
    assertViolates(
      "N10",
      """
      package dev.gezgin.n10backtostart

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.BackToStart
      import dev.gezgin.core.annotation.FlowGraph
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.StartDestination

      @FlowGraph
      sealed interface AvatarFlow : Route {
          @StartDestination
          data object Pick : AvatarFlow

          @BackToStart
          @GoTo(Preview::class, name = "backToStart")
          data object Crop : AvatarFlow

          data object Preview : AvatarFlow
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `N10 - @BackToStart plus @BackTo(StartRoute) both derive backToStart (no name= needed)`() {
    // Refactor leftover: Crop keeps @BackTo(StartRoute) after gaining @BackToStart. StartRoute
    // strips
    // to "Start" → backTo+"Start" = backToStart, duplicating the @BackToStart member → N10.
    assertViolates(
      "N10",
      """
      package dev.gezgin.n10backtostart2

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.BackTo
      import dev.gezgin.core.annotation.BackToStart
      import dev.gezgin.core.annotation.FlowGraph
      import dev.gezgin.core.annotation.StartDestination

      @FlowGraph
      sealed interface AvatarFlow : Route {
          @StartDestination
          data object StartRoute : AvatarFlow

          @BackToStart
          @BackTo(StartRoute::class)
          data object Crop : AvatarFlow
      }
      """
        .trimIndent(),
    )
  }

  @Test
  fun `N10 positive - a lone @BackToStart (no collision) compiles clean`() {
    // Guards against over-broadness: backToStart is NOT in RESERVED_MEMBER_NAMES, so a single
    // @BackToStart member must NOT trip N10.
    assertCompilesClean(
      """
      package dev.gezgin.n10backtostartok

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.BackToStart
      import dev.gezgin.core.annotation.FlowGraph
      import dev.gezgin.core.annotation.StartDestination

      @FlowGraph
      sealed interface AvatarFlow : Route {
          @StartDestination
          data object Pick : AvatarFlow

          @BackToStart
          data object Crop : AvatarFlow
      }
      """
        .trimIndent()
    )
  }

  // endregion

  // region PKG — generated code needs a single common target package

  @Test
  fun `PKG - routes split across unrelated top-level packages are rejected`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Alpha.kt",
          """
          package com.alpha

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.NavGraph

          @NavGraph
          sealed interface AGraph : Route {
              data object A : AGraph
          }
          """
            .trimIndent(),
        ),
        SourceFile.kotlin(
          "Beta.kt",
          """
          package org.beta

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.NavGraph

          @NavGraph
          sealed interface BGraph : Route {
              data object B : BGraph
          }
          """
            .trimIndent(),
        ),
      )
    assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "[PKG]", message = result.messages)
  }

  @Test
  fun `PKG - graphs in different sub-packages of a common prefix are rejected (M2)`() {
    // Nav module with graphs under com.app.nav.home + com.app.nav.auth → common prefix com.app.nav
    // (NON-empty). The OLD [PKG] rejected only an EMPTY prefix, so this was ACCEPTED and navigators
    // were emitted into com.app.nav while the routes live in the sub-packages — a cross-module
    // fragment/core/MVI probe, which looks in the route's OWN package, then silently MISSED (M2
    // false
    // negative). The tightened [PKG] requires every graph/route package == targetPackage.
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Home.kt",
          """
          package com.app.nav.home

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.NavGraph

          @NavGraph
          sealed interface HomeGraph : Route {
              data object H : HomeGraph
          }
          """
            .trimIndent(),
        ),
        SourceFile.kotlin(
          "Auth.kt",
          """
          package com.app.nav.auth

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.NavGraph

          @NavGraph
          sealed interface AuthGraph : Route {
              data object A : AuthGraph
          }
          """
            .trimIndent(),
        ),
      )
    assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "[PKG]", message = result.messages)
  }

  @Test
  fun `PKG allow - all graphs and routes in ONE package is accepted (regression, mirrors the sample)`() {
    // The canonical §3.3 nav module (like the sample's single `dev.gezgin.sample.navigation`):
    // every
    // graph/route in one package → targetPackage == every package → the tightened [PKG] must NOT
    // fire.
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Nav.kt",
          """
          package com.app.nav

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.NavGraph

          @NavGraph
          sealed interface HomeGraph : Route {
              data object H : HomeGraph
          }

          @NavGraph
          sealed interface AuthGraph : Route {
              data object A : AuthGraph
          }
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitSerializers" to "false"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }

  // endregion
}
