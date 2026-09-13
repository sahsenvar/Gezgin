package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperBinderTest {

  private fun fixture(vocabulary: String, feature: String) =
    SourceFile.kotlin(
      "Fixture.kt",
      """
      package app

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.BackTo
      import dev.gezgin.core.annotation.FilledBy
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.core.annotation.ScreenSlot
      import dev.gezgin.core.annotation.ScreenWrapper
      import kotlin.reflect.KClass
      import kotlinx.serialization.Serializable

      @NavGraph
      @Serializable
      sealed interface AppGraph : Route {
        @GoTo(DetailRoute::class) @Serializable data object ListRoute : AppGraph

        @BackTo(ListRoute::class)
        @Serializable
        data class DetailRoute(val id: String) : AppGraph
      }

      data class DetailUiState(val title: String)

      sealed interface DetailIntent

      $vocabulary

      $feature
      """
        .trimIndent(),
    )

  private val genericWrapper =
    """
    @ScreenSlot annotation class ViewModelOf(val route: KClass<out Route>)

    @ScreenWrapper
    @Composable
    fun <S, I> appRoot(
      @FilledBy(ViewModelOf::class) viewModel: @Composable () -> S,
      @FilledBy(Screen::class) content: @Composable (S, (I) -> Unit) -> Unit,
    ) = Unit
    """
      .trimIndent()

  private val detailScreen =
    """
    @Screen(AppGraph.DetailRoute::class)
    @Composable
    fun detailScreen(state: DetailUiState, onIntent: (DetailIntent) -> Unit) = Unit
    """
      .trimIndent()

  @Test
  fun `type arguments bind from the content slot and a sibling slot`() {
    compileGezgin(
      fixture(
        genericWrapper,
        """
        @ViewModelOf(AppGraph.DetailRoute::class)
        @Composable fun detailViewModel(): DetailUiState = DetailUiState("x")

        $detailScreen
        """
          .trimIndent(),
      ),
      kspArgs = mapOf("gezgin.dumpWrapper" to "true", "gezgin.emitEntries" to "false"),
    )

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "binding app.AppGraph.DetailRoute wrapper=app.appRoot")
    assertContains(dump, "typeArgs=[app.DetailUiState, app.DetailIntent]")
  }

  @Test
  fun `SW5 fires when a slot without a default has no provider`() {
    val result =
      compileGezgin(
        fixture(genericWrapper, detailScreen),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW5]")
    assertContains(result.messages, "viewModel")
  }

  @Test
  fun `SW7 fires when a type parameter is bound by no filled slot`() {
    val result =
      compileGezgin(
        fixture(
          """
          @ScreenSlot annotation class Unused(val route: KClass<out Route>)

          @ScreenWrapper
          @Composable
          fun <S, E> appRoot(
            @FilledBy(Unused::class) onEffect: (E) -> Unit = {},
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
            .trimIndent(),
          """
          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW7]")
  }

  @Test
  fun `SW6 fires when two wrappers are candidates for one route`() {
    val result =
      compileGezgin(
        fixture(
          """
          @ScreenWrapper
          @Composable
          fun firstRoot(@FilledBy(Screen::class) content: @Composable (DetailUiState) -> Unit) = Unit

          @ScreenWrapper
          @Composable
          fun secondRoot(@FilledBy(Screen::class) content: @Composable (DetailUiState) -> Unit) =
            Unit
          """
            .trimIndent(),
          """
          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW6]")
    assertContains(result.messages, "firstRoot")
    assertContains(result.messages, "secondRoot")
  }

  @Test
  fun `no wrapper in scope is not an error`() {
    val result =
      compileGezgin(
        fixture(
          "",
          """
          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertFalse(result.messages.contains("[SW6]"), result.messages)
    assertFalse(result.messages.contains("[SW5]"), result.messages)
  }

  @Test
  fun `SW10 fires when a marker no wrapper slot consumes is used`() {
    val result =
      compileGezgin(
        fixture(
          """
          @ScreenSlot annotation class Orphan(val route: KClass<out Route>)

          @ScreenWrapper
          @Composable
          fun appRoot(@FilledBy(Screen::class) content: @Composable (DetailUiState) -> Unit) = Unit
          """
            .trimIndent(),
          """
          @Orphan(AppGraph.DetailRoute::class) fun orphanProvider() = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW10]")
    assertContains(result.messages, "orphanProvider")
  }
}
