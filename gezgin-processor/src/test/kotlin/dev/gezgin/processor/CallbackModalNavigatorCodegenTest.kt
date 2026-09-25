package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class, GezginInternalApi::class)
class CallbackModalNavigatorCodegenTest {

  private val graphSource =
    """
    package dev.gezgin.cb

    import dev.gezgin.core.DialogContract
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.OnDismiss
    import dev.gezgin.core.annotation.Open

    @NavGraph
    sealed interface CbGraph : Route {

        @Open(ConfirmDialog::class, PickSheet::class)
        @Open(ConfirmDialog::class, name = "askConfirm")
        data object Home : CbGraph

        data class ConfirmDialog(
            val id: String,
            val onConfirm: () -> Unit,
            @OnDismiss val onDismiss: () -> Unit,
        ) : CbGraph, DialogContract

        data class PickSheet(val onSelect: (String) -> Unit) : CbGraph
    }
    """
      .trimIndent()

  private val runnerSource =
    """
    @file:OptIn(dev.gezgin.core.GezginInternalApi::class)

    package dev.gezgin.cb

    import dev.gezgin.core.RawNavigator

    val log = mutableListOf<String>()

    fun openConfirmAndClickConfirm(raw: RawNavigator) {
        raw.homeNavigator(raw.currentEntryId).openConfirmDialog(
            id = "a",
            onConfirm = { log += "confirm" },
            onDismiss = { log += "dismiss" },
        )
        (raw.current as CbGraph.ConfirmDialog).onConfirm()
    }

    fun openPickAndSelect(raw: RawNavigator) {
        raw.homeNavigator(raw.currentEntryId).openPickSheet(onSelect = { log += "picked:" + it })
        (raw.current as CbGraph.PickSheet).onSelect("x")
    }

    fun openViaNamedEdge(raw: RawNavigator) {
        raw.homeNavigator(raw.currentEntryId).askConfirm(id = "b", onConfirm = {}, onDismiss = {})
    }
    """
      .trimIndent()

  private fun compileAndStart(): Pair<ClassLoader, RawNavigator> {
    val result =
      compileGezgin(
        SourceFile.kotlin("CbGraph.kt", graphSource),
        SourceFile.kotlin("Runner.kt", runnerSource),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val loader = result.classLoader
    val topology =
      loader
        .loadClass("dev.gezgin.cb.GezginGeneratedKt")
        .getMethod("getGezginTopology")
        .invoke(null) as GezginTopology
    val home = loader.loadClass("dev.gezgin.cb.CbGraph\$Home").getField("INSTANCE").get(null)
    return loader to RawNavigator(start = home as Route, topology = topology)
  }

  @Suppress("UNCHECKED_CAST")
  private fun ClassLoader.log(): List<String> =
    loadClass("dev.gezgin.cb.RunnerKt").getMethod("getLog").invoke(null) as List<String>

  private fun ClassLoader.run(name: String, raw: RawNavigator) {
    loadClass("dev.gezgin.cb.RunnerKt").getMethod(name, RawNavigator::class.java).invoke(null, raw)
  }

  @Test
  fun `openX pushes the callback route carrying the caller lambdas`() {
    val (loader, raw) = compileAndStart()

    loader.run("openConfirmAndClickConfirm", raw)

    assertEquals(listOf("confirm"), loader.log())
    assertEquals(2, raw.backStack.value.size)
  }

  @Test
  fun `parameterized callback reaches the caller with its argument`() {
    val (loader, raw) = compileAndStart()

    loader.run("openPickAndSelect", raw)

    assertEquals(listOf("picked:x"), loader.log())
  }

  @Test
  fun `name override replaces the derived open method name`() {
    val (loader, raw) = compileAndStart()

    loader.run("openViaNamedEdge", raw)

    assertEquals("ConfirmDialog", raw.current::class.simpleName)
  }

  @Test
  fun `callback routes are listed as transient and get no serializer`() {
    val result = compileGezgin(SourceFile.kotlin("CbGraph.kt", graphSource))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val topology = result.generatedSourceFor("GezginGenerated.kt")!!.readText()
    assertContains(topology, "transientRoutes = setOf(")
    assertContains(topology, "CbGraph.ConfirmDialog::class")
    val serializers = result.generatedSourceFor("GezginSerializers.kt")!!.readText()
    assertFalse("ConfirmDialog" in serializers, serializers)
    assertFalse("PickSheet" in serializers, serializers)
  }

  // region Validation

  private fun assertViolates(code: String, body: String) {
    val source =
      """
      package dev.gezgin.cbv

      import dev.gezgin.core.ResultRoute
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.OnDismiss
      import dev.gezgin.core.annotation.Open

      @NavGraph
      sealed interface G : Route {
      $body
      }
      """
        .trimIndent()
    val result = compileGezgin(SourceFile.kotlin("Source.kt", source))
    assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "[$code]", message = result.messages)
  }

  @Test
  fun `OP1 rejects Open targeting a route without callbacks`() =
    assertViolates(
      "OP1",
      """
      @Open(Plain::class) data object Home : G
      data class Plain(val id: String) : G
      """,
    )

  @Test
  fun `OP2 rejects a forward edge other than Open into a callback route`() =
    assertViolates(
      "OP2",
      """
      @GoTo(Confirm::class) data object Home : G
      data class Confirm(val onConfirm: () -> Unit) : G
      """,
    )

  @Test
  fun `OP3 rejects a callback route that is also a ResultRoute`() =
    assertViolates(
      "OP3",
      """
      @Open(Confirm::class) data object Home : G
      data class Confirm(val onConfirm: () -> Unit) : G, ResultRoute<String>
      """,
    )

  @Test
  fun `OP4 rejects a callback returning a value`() =
    assertViolates(
      "OP4",
      """
      @Open(Confirm::class) data object Home : G
      data class Confirm(val validate: (String) -> Boolean) : G
      """,
    )

  @Test
  fun `OP4 rejects a suspend callback`() =
    assertViolates(
      "OP4",
      """
      @Open(Confirm::class) data object Home : G
      data class Confirm(val onConfirm: suspend () -> Unit) : G
      """,
    )

  @Test
  fun `OP5 rejects OnDismiss on a parameterized callback`() =
    assertViolates(
      "OP5",
      """
      @Open(Pick::class) data object Home : G
      data class Pick(@OnDismiss val onSelect: (String) -> Unit) : G
      """,
    )

  @Test
  fun `OP5 rejects two OnDismiss properties`() =
    assertViolates(
      "OP5",
      """
      @Open(Confirm::class) data object Home : G
      data class Confirm(@OnDismiss val a: () -> Unit, @OnDismiss val b: () -> Unit) : G
      """,
    )

  // endregion
}
