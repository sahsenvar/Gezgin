package dev.gezgin.processor

import com.squareup.kotlinpoet.ClassName
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.core.FlowType
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.Route
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 2.4 gate for [dev.gezgin.processor.codegen.TopologyCodegen]: compiles [SHOP_SOURCE], loads
 * the generated `dev.gezgin.shop.GezginGeneratedKt` facade via reflection off the compilation's own
 * [ClassLoader], and asserts against the real [GezginTopology] runtime API (`flowChain`/`startOf`/
 * `edges`) — `inheritClassPath` puts `:gezgin-core`'s classes on the same classloader lineage as
 * the generated code, so the loaded `GezginTopology` instance casts directly to the compile-time
 * type without any field-by-field reflection fallback being necessary (verified below: the cast
 * succeeds).
 *
 * `emitSerializers=false` is passed everywhere except
 * [golden text for GezginSerializers contains expected shape], which never live-loads the result —
 * it only inspects the generated *source text* (`gezgin.emitSerializers` gates
 * `GezginSerializers.kt` specifically because `subclass(X::class)` would need a real
 * `.serializer()`-bearing `X` to ever be *used* at runtime by a consumer, which the kctfork test
 * fixture can't fully provide).
 */
@OptIn(ExperimentalCompilerApi::class)
class TopologyCodegenTest {

  /** JVM binary name for a KSP-style dotted fqName (`Outer.Inner` nesting → `Outer$Inner`). */
  private fun binaryNameOf(fqName: String): String {
    val className = ClassName.bestGuess(fqName)
    val prefix = className.packageName.takeIf { it.isNotEmpty() }?.plus(".").orEmpty()
    return prefix + className.simpleNames.joinToString("$")
  }

  private fun ClassLoader.routeClassOf(fqName: String): Class<*> = loadClass(binaryNameOf(fqName))

  @Suppress("UNCHECKED_CAST")
  private fun ClassLoader.kClassOf(fqName: String): KClass<out Route> =
    routeClassOf(fqName).kotlin as KClass<out Route>

  // M2 — FlowType is no longer a data class (no structural equals), so compare flow chains by their
  // public (id, isResultFlow) fields instead of whole-object equality.
  private fun List<FlowType>.ids(): List<Pair<String, Boolean>> = map { it.id to it.isResultFlow }

  @Test
  fun `SHOP_SOURCE compiles and GezginTopology loads via reflection off the compiled classloader`() {
    val result =
      compileGezgin(
        SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
        kspArgs = mapOf("gezgin.emitSerializers" to "false"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    // Pins the emitSerializers=false direction of the gate: the serializers file must NOT be
    // generated (the =true direction is pinned by the golden-text test's assertNotNull below).
    assertNull(
      result.generatedSourceFor("GezginSerializers.kt"),
      "gezgin.emitSerializers=false must suppress GezginSerializers.kt",
    )
    // The M1 GezginRememberNavigator.kt shares the emitSerializers gate (it references
    // gezginSerializersModule), so =false must suppress it too.
    assertNull(
      result.generatedSourceFor("GezginRememberNavigator.kt"),
      "gezgin.emitSerializers=false must suppress GezginRememberNavigator.kt",
    )

    val loader = result.classLoader
    val generatedFacade = loader.loadClass("dev.gezgin.shop.GezginGeneratedKt")
    val raw = generatedFacade.getMethod("getGezginTopology").invoke(null)

    // Binding decision (see class KDoc): this cast is expected to succeed because
    // inheritClassPath shares :gezgin-core's GezginTopology class with the compiled result.
    val topology = raw as GezginTopology

    val cart = loader.kClassOf("dev.gezgin.shop.CheckoutFlow.Cart")
    val otp = loader.kClassOf("dev.gezgin.shop.CheckoutFlow.PayAuthFlow.Otp")
    val giftPick = loader.kClassOf("dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow.GiftPick")

    assertEquals(listOf("dev.gezgin.shop.CheckoutFlow" to true), topology.flowChain(cart).ids())
    assertEquals(
      listOf(
        "dev.gezgin.shop.CheckoutFlow" to true,
        "dev.gezgin.shop.CheckoutFlow.PayAuthFlow" to false,
      ),
      topology.flowChain(otp).ids(),
    )
    assertEquals(
      listOf(
        "dev.gezgin.shop.CheckoutFlow" to true,
        "dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow" to false,
      ),
      topology.flowChain(giftPick).ids(),
    )

    assertEquals(cart.java, topology.startOf("dev.gezgin.shop.CheckoutFlow").java)
    assertEquals(otp.java, topology.startOf("dev.gezgin.shop.CheckoutFlow.PayAuthFlow").java)
    assertEquals(
      giftPick.java,
      topology.startOf("dev.gezgin.shop.CheckoutFlow.CheckoutPages.GiftFlow").java,
    )

    // M2 — `edges` is now internal (read only by the same-module runtime); the Feed→CheckoutFlow
    // edge's presence + serialization is proven end-to-end by NavigatorCodegenTest's live result
    // round-trip.
  }

  @Test
  fun `FlowType isResultFlow is OWNERSHIP (direct declaration) — a nested sub-flow SUBTYPING the declaring ResultFlow stays false`() {
    // Regression for the sample showcase's AvatarFlow/ZoomFlow shape: unlike SHOP_SOURCE's
    // `PayAuthFlow : Route`, here the nested flow SUBTYPES its enclosing ResultFlow
    // (`ZoomFlow : AvatarFlow`), so it is TRANSITIVELY a ResultFlow (inherits `ResultFlow<T>`
    // through the supertype chain) while owning no contract of its own. Emitting the transitive
    // flag into the topology made `RawNavigator.quitWith`'s `chain.indexOfLast { it.isResultFlow }`
    // resolve to the sub-flow — quitting only its own segment and silently DROPPING the value
    // (no slot listens at the sub-flow's entry). Ownership semantics (spec §6 + the S1 E1
    // adjudication): only the DIRECTLY-declaring flow carries `isResultFlow = true`.
    val source =
      """
          package dev.gezgin.nestedresult

          import dev.gezgin.core.ResultFlow
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.FlowGraph
          import dev.gezgin.core.annotation.GoForResult
          import dev.gezgin.core.annotation.GoTo
          import dev.gezgin.core.annotation.NavGraph
          import dev.gezgin.core.annotation.StartDestination
          import kotlinx.serialization.KSerializer
          import kotlinx.serialization.descriptors.SerialDescriptor
          import kotlinx.serialization.descriptors.buildClassSerialDescriptor
          import kotlinx.serialization.encoding.Decoder
          import kotlinx.serialization.encoding.Encoder

          data class AvatarChoice(val uri: String) {
              // Test-only stub (same pattern as SHOP_SOURCE's OrderId) — kctfork has no
              // kotlinx-serialization compiler plugin, but the generated topology's <clinit>
              // eagerly calls AvatarChoice.serializer() for the pickAvatar edge.
              companion object {
                  fun serializer(): KSerializer<AvatarChoice> = object : KSerializer<AvatarChoice> {
                      override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AvatarChoice")
                      override fun serialize(encoder: Encoder, value: AvatarChoice): Unit =
                          throw UnsupportedOperationException("test stub")
                      override fun deserialize(decoder: Decoder): AvatarChoice =
                          throw UnsupportedOperationException("test stub")
                  }
              }
          }

          @NavGraph
          sealed interface ProfileGraph : Route {
              @GoForResult(AvatarFlow::class, name = "pickAvatar")
              data object Profile : ProfileGraph

              @FlowGraph
              sealed interface AvatarFlow : ProfileGraph, ResultFlow<AvatarChoice> {
                  @StartDestination
                  @GoTo(Crop::class)
                  data object PickSource : AvatarFlow

                  @GoTo(ZoomFlow::class)
                  data object Crop : AvatarFlow

                  @FlowGraph
                  sealed interface ZoomFlow : AvatarFlow {
                      @StartDestination
                      data object Zoom : ZoomFlow
                  }
              }
          }
      """
        .trimIndent()

    val result =
      compileGezgin(
        SourceFile.kotlin("NestedResult.kt", source),
        kspArgs = mapOf("gezgin.emitSerializers" to "false"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val loader = result.classLoader
    val topology =
      loader
        .loadClass("dev.gezgin.nestedresult.GezginGeneratedKt")
        .getMethod("getGezginTopology")
        .invoke(null) as GezginTopology

    val zoom = loader.kClassOf("dev.gezgin.nestedresult.ProfileGraph.AvatarFlow.ZoomFlow.Zoom")
    val crop = loader.kClassOf("dev.gezgin.nestedresult.ProfileGraph.AvatarFlow.Crop")

    // The declaring flow OWNS the contract (true); the transitively-ResultFlow sub-flow does NOT.
    assertEquals(
      listOf(
        "dev.gezgin.nestedresult.ProfileGraph.AvatarFlow" to true,
        "dev.gezgin.nestedresult.ProfileGraph.AvatarFlow.ZoomFlow" to false,
      ),
      topology.flowChain(zoom).ids(),
    )
    assertEquals(
      listOf("dev.gezgin.nestedresult.ProfileGraph.AvatarFlow" to true),
      topology.flowChain(crop).ids(),
    )

    // NavigatorCodegen alignment: the sub-flow member's generated `quitWith` param type is the
    // DECLARING flow's T (AvatarChoice) — resolved via `declaresResultFlowDirectly`, mirroring
    // the runtime's (now ownership-based) target selection.
    val zoomNavigatorText = result.generatedSourceFor("ZoomNavigator.kt")?.readText()
    assertNotNull(
      zoomNavigatorText,
      "ZoomNavigator.kt must be emitted (Zoom is inside AvatarFlow's chain)",
    )
    assertTrue("public fun quitWith(result: AvatarChoice)" in zoomNavigatorText, zoomNavigatorText)
  }

  @Test
  fun `topology compiles when a GoForResult result type is a kotlinx builtin (Boolean)`() {
    // ForgotPasswordDialog : ResultRoute<Boolean> — a BUILTIN result type has no companion
    // `Boolean.serializer()`, so the topology must reach it through the reified
    // `kotlinx.serialization.serializer<Boolean>()` helper. Regression for the sample showcase's
    // ResultRoute<Boolean>/<String> screen-mode results (spec §6).
    val source =
      """
          package dev.gezgin.builtinresult

          import dev.gezgin.core.ResultRoute
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.GoForResult
          import dev.gezgin.core.annotation.NavGraph

          @NavGraph
          sealed interface AuthGraph : Route {
              @GoForResult(ForgotPasswordDialog::class)
              data object LoginRoute : AuthGraph

              data class ForgotPasswordDialog(val email: String? = null) : AuthGraph, ResultRoute<Boolean>
          }
      """
        .trimIndent()

    val result =
      compileGezgin(
        SourceFile.kotlin("BuiltinResult.kt", source),
        kspArgs = mapOf("gezgin.emitSerializers" to "false"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val topologyText = result.generatedSourceFor("GezginGenerated.kt")?.readText()
    assertNotNull(topologyText, "GezginGenerated.kt must be emitted")
    assertTrue("serializer<Boolean>()" in topologyText, topologyText)
  }

  @Test
  fun `golden text for GezginSerializers contains expected shape`() {
    val result =
      compileGezgin(
        SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
        kspArgs = mapOf("gezgin.emitSerializers" to "true"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    // Pins the emitSerializers=true (default-equivalent) direction of the gate: the
    // serializers file MUST be generated (=false direction is pinned by the live-load test).
    val serializersSource = result.generatedSourceFor("GezginSerializers.kt")
    assertNotNull(
      serializersSource,
      "gezgin.emitSerializers=true must generate GezginSerializers.kt",
    )
    val text = serializersSource.readText()

    assertTrue("polymorphic(Route::class)" in text, text)
    // KotlinPoet only auto-imports top-level types, so a nested route like `Feed` (nested in
    // `HomeGraph`) is emitted qualified by its enclosing simple name(s) — `HomeGraph.Feed`, not
    // a bare `Feed` — which is also the only form that would actually resolve/compile here.
    assertTrue("subclass(HomeGraph.Feed::class)" in text, text)
    assertFalse("BasePicker" in text, text)
  }

  @Test
  fun `M1 GezginRememberNavigator emits ONLY the stable gezginJson val — never a @Composable rememberGezginNavigator (crash regression guard)`() {
    // Regression guard for the on-device NoSuchMethodError crash (fix 1e28f89).
    // GezginRememberNavigator.kt
    // is emitted into the graph module (§3.3) which — in the canonical layout — is a PLAIN
    // kotlin.jvm
    // module WITHOUT the Compose compiler plugin. A `@Composable rememberGezginNavigator` compiled
    // there
    // gets a NON-lowered bytecode signature (no Composer/$changed/$default) → a Compose consumer
    // calling it
    // crashes at runtime (compiles + unit + assembleDebug all green; only fails when the app RUNS).
    // Only the
    // plain `gezginJson` val is crash-safe here; call sites use core rememberNavigator(start,
    // gezginTopology,
    // gezginJson, onRootBack).
    val result =
      compileGezgin(
        SourceFile.kotlin("ShopSource.kt", SHOP_SOURCE),
        kspArgs = mapOf("gezgin.emitSerializers" to "true"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val rememberSource = result.generatedSourceFor("GezginRememberNavigator.kt")
    assertNotNull(
      rememberSource,
      "gezgin.emitSerializers=true must generate GezginRememberNavigator.kt",
    )
    val text = rememberSource.readText()

    // The stable, process-wide Json IS emitted (the encode/decode symmetry the PD-restore Saver
    // needs).
    assertTrue("val gezginJson" in text, text)
    assertTrue("serializersModule = gezginSerializersModule" in text, text)

    // …and the crash-prone @Composable convenience is NOT — this is the exact bug the fix removed.
    assertFalse(
      "@Composable" in text,
      "generated remember file must not declare a @Composable: $text",
    )
    assertFalse(
      "rememberGezginNavigator" in text,
      "the @Composable rememberGezginNavigator must never be regenerated: $text",
    )
  }
}
