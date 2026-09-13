package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import dev.gezgin.processor.CompileHarness.compileGezginModule
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Locks the discovery path the design depends on. KSP cannot enumerate classpath declarations by
 * annotation, so a wrapper compiled into a dependency is reached by package enumeration instead. If
 * a KSP upgrade breaks that, this fails here rather than silently stripping every screen's chrome.
 *
 * These assert on the GENERATED SOURCE and on the absence of KSP errors, not on a successful
 * compilation: kctfork runs without the Compose compiler plugin, and the generated
 * `register<R>(...) { }` body cannot be inlined by the bare JVM backend (see [CompileHarness]).
 * Whether the generated code compiles is covered by the sample modules, which build for real.
 */
@OptIn(ExperimentalCompilerApi::class)
class WrapperCrossModuleTest {

  private val designSystem =
    SourceFile.kotlin(
      "DesignSystem.kt",
      """
      package design

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FilledBy
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.core.annotation.ScreenSlot
      import dev.gezgin.core.annotation.ScreenWrapper
      import kotlin.reflect.KClass

      @ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

      @ScreenWrapper
      @Composable
      fun <S> designRoot(
        @FilledBy(TopBar::class) topBar: @Composable (S) -> Unit = {},
        @FilledBy(Screen::class) content: @Composable (S) -> Unit,
      ) = Unit
      """
        .trimIndent(),
    )

  private val navigation =
    SourceFile.kotlin(
      "Nav.kt",
      """
      package navigation

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.BackTo
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import kotlinx.serialization.Serializable

      @NavGraph
      @Serializable
      sealed interface AppGraph : Route {
        @GoTo(DetailRoute::class) @Serializable data object ListRoute : AppGraph

        @BackTo(ListRoute::class)
        @Serializable
        data class DetailRoute(val id: String) : AppGraph
      }
      """
        .trimIndent(),
    )

  private val feature =
    SourceFile.kotlin(
      "Feature.kt",
      """
      package feature

      import androidx.compose.runtime.Composable
      import design.TopBar
      import dev.gezgin.core.annotation.Screen
      import navigation.AppGraph

      data class DetailUiState(val title: String)

      @TopBar(AppGraph.DetailRoute::class)
      @Composable
      fun detailTopBar(state: DetailUiState) = Unit

      @Screen(AppGraph.DetailRoute::class)
      @Composable
      fun detailScreen(state: DetailUiState) = Unit
      """
        .trimIndent(),
    )

  @Test
  fun `a wrapper compiled into a dependency binds a feature module's screen`() {
    val designSystemResult =
      compileGezginModule(designSystem, kspArgs = mapOf("gezgin.emitEntries" to "false"))
    assertEquals(
      KotlinCompilation.ExitCode.OK,
      designSystemResult.exitCode,
      designSystemResult.messages,
    )

    val navigationResult = compileGezginModule(navigation)
    assertEquals(
      KotlinCompilation.ExitCode.OK,
      navigationResult.exitCode,
      navigationResult.messages,
    )

    val featureResult =
      compileGezginModule(
        feature,
        kspArgs = mapOf("gezgin.wrapperPackages" to "design", "gezgin.dumpWrapper" to "true"),
        extraClasspath =
          listOf(designSystemResult.outputDirectory, navigationResult.outputDirectory),
      )

    assertFalse(
      featureResult.messages.contains("[SW"),
      "wrapper discovery reported an error:\n${featureResult.messages}",
    )
    val dump = CompileHarness.findGeneratedResource("GezginWrapperDump.txt")?.readText().orEmpty()
    assertContains(dump, "wrapper design.designRoot")
    assertContains(dump, "binding navigation.AppGraph.DetailRoute wrapper=design.designRoot")

    val text =
      featureResult.sourcesGeneratedBySymbolProcessor
        .firstOrNull { it.name == "GezginWrapperEntries.kt" }
        ?.readText() ?: error("no wrapper entries generated; dump was:\n$dump")
    assertContains(text, "designRoot<DetailUiState>(")
    assertContains(text, "topBar = { state -> detailTopBar(state = state) }")
    assertContains(text, "content = { state -> detailScreen(state = state) }")
  }

  @Test
  fun `without the option the classpath wrapper is invisible and the entry stays unwrapped`() {
    val designSystemResult =
      compileGezginModule(designSystem, kspArgs = mapOf("gezgin.emitEntries" to "false"))
    val navigationResult = compileGezginModule(navigation)

    val featureResult =
      compileGezginModule(
        feature,
        extraClasspath =
          listOf(designSystemResult.outputDirectory, navigationResult.outputDirectory),
      )

    assertEquals(
      null,
      featureResult.sourcesGeneratedBySymbolProcessor.firstOrNull {
        it.name == "GezginWrapperEntries.kt"
      },
      "a wrapper must not be discovered without gezgin.wrapperPackages",
    )
  }
}
