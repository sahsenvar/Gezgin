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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Sekme yığını: `@Tab` / `@Switch` doğrulama kodları (`TB1`–`TB4`, `SC12`) ve üretilen
 * `switchToX()` + `tabRoots` topolojisi.
 */
@OptIn(ExperimentalCompilerApi::class, GezginInternalApi::class)
class TabCodegenTest {
  private val kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitEntries" to "false")

  private fun assertViolates(code: String, source: String) {
    val result = compileGezgin(SourceFile.kotlin("Source.kt", source), kspArgs = kspArgs)
    assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "[$code]", message = result.messages)
  }

  private val header =
    """
    package dev.gezgin.tabs

    import dev.gezgin.core.ResultFlow
    import dev.gezgin.core.ResultRoute
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.FlowGraph
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.StartDestination
    import dev.gezgin.core.annotation.Switch
    import dev.gezgin.core.annotation.Tab
    """
      .trimIndent()

  private val tabsSource =
    header +
      """

      @NavGraph
      sealed interface TabsGraph : Route {
        @Tab @Switch(Search::class, Profile::class) data object Home : TabsGraph
        @Tab @Switch(Home::class) data object Search : TabsGraph
        @Tab data object Profile : TabsGraph
        data class Detail(val id: String) : TabsGraph
      }
      """
        .trimIndent()

  @Test
  fun `Switch typed switchToX uretir ve topology tabRoots tasir`() {
    val result = compileGezgin(SourceFile.kotlin("Source.kt", tabsSource), kspArgs = kspArgs)
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val navigator = assertNotNull(result.generatedSourceFor("HomeNavigator.kt")).readText()
    assertContains(navigator, "public fun switchToSearch()")
    assertContains(navigator, "public fun switchToProfile()")
    assertContains(navigator, "raw.switchTo(TabsGraph.Search)")
    val topology = assertNotNull(result.generatedSourceFor("GezginGenerated.kt")).readText()
    assertContains(topology, "tabRoots = setOf(")
    assertContains(topology, "TabsGraph.Home::class")
  }

  @Test
  fun `uretilen switchToX gercek RawNavigator ile sekme degistirir`() {
    val result = compileGezgin(SourceFile.kotlin("Source.kt", tabsSource), kspArgs = kspArgs)
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val loader = result.classLoader
    val topology =
      loader
        .loadClass("dev.gezgin.tabs.GezginGeneratedKt")
        .getMethod("getGezginTopology")
        .invoke(null) as GezginTopology
    val home = loader.loadClass("dev.gezgin.tabs.TabsGraph\$Home").getField("INSTANCE").get(null)
    val raw = RawNavigator(start = home as Route, topology = topology)

    val navigator =
      loader
        .loadClass("dev.gezgin.tabs.HomeNavigatorKt")
        .getMethod("homeNavigator", RawNavigator::class.java, Long::class.javaPrimitiveType)
        .invoke(null, raw, raw.currentEntryId)
    navigator.javaClass.getMethod("switchToSearch").invoke(navigator)

    assertEquals("Search", raw.current::class.simpleName)
    raw.switchTo(home)
    assertEquals(home, raw.current, "Home segmenti pasif olarak saklanmış olmalı")
  }

  @Test
  fun `TB1 Switch hedefi Tab degilse reddedilir`() {
    assertViolates(
      "TB1",
      header +
        """

        @NavGraph
        sealed interface G : Route {
          @Tab @Switch(Plain::class) data object Home : G
          data object Plain : G
        }
        """
          .trimIndent(),
    )
  }

  @Test
  fun `TB2 Switch kaynagi Tab degilse reddedilir`() {
    assertViolates(
      "TB2",
      header +
        """

        @NavGraph
        sealed interface G : Route {
          @Tab data object Home : G
          @Switch(Home::class) data object Plain : G
        }
        """
          .trimIndent(),
    )
  }

  @Test
  fun `TB3 Tab flow uyesi olamaz`() {
    assertViolates(
      "TB3",
      header +
        """

        @NavGraph
        sealed interface G : Route {
          data object Entry : G
        }

        @FlowGraph
        sealed interface F : G {
          @StartDestination @Tab data object Start : F
        }
        """
          .trimIndent(),
    )
  }

  @Test
  fun `TB3 Tab ResultRoute olamaz`() {
    assertViolates(
      "TB3",
      header +
        """

        @NavGraph
        sealed interface G : Route {
          @Tab data object Pick : G, ResultRoute<String>
        }
        """
          .trimIndent(),
    )
  }

  @Test
  fun `TB4 Tab argumansiz kurulamiyorsa reddedilir`() {
    assertViolates(
      "TB4",
      header +
        """

        @NavGraph
        sealed interface G : Route {
          @Tab data class Home(val userId: String) : G
        }
        """
          .trimIndent(),
    )
  }

  @Test
  fun `SC12 Tab modal kind ile kaydedilemez`() {
    assertViolates(
      "SC12",
      """
      package dev.gezgin.tabs.modal

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.DialogContract
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.Dialog
      import dev.gezgin.core.annotation.Tab

      @Tab
      data object ModalTab : Route, DialogContract

      @Dialog(ModalTab::class)
      @Composable
      fun ModalTabContent() {}
      """
        .trimIndent(),
    )
  }
}
