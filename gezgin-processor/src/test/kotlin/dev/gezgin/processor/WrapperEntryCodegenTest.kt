package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperEntryCodegenTest {

  private val fixture =
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

      sealed interface DetailEffect

      @ScreenSlot annotation class ViewModelOf(val route: KClass<out Route>)

      @ScreenSlot annotation class Effects(val route: KClass<out Route>)

      @ScreenSlot annotation class TopBar(val route: KClass<out Route>)

      @ScreenWrapper
      @Composable
      fun <S, I, E> appRoot(
        @FilledBy(ViewModelOf::class) viewModel: @Composable () -> S,
        @FilledBy(Effects::class) onEffect: (E) -> Unit,
        @FilledBy(TopBar::class) topBar: @Composable (S) -> Unit = {},
        @FilledBy(Screen::class) content: @Composable (S, (I) -> Unit) -> Unit,
      ) = Unit

      @ViewModelOf(AppGraph.DetailRoute::class)
      @Composable
      fun detailViewModel(route: AppGraph.DetailRoute): DetailUiState = DetailUiState(route.id)

      @Effects(AppGraph.DetailRoute::class)
      fun handleDetailEffect(effect: DetailEffect, nav: DetailNavigator) = Unit

      @Screen(AppGraph.DetailRoute::class)
      @Composable
      fun detailScreen(state: DetailUiState, onIntent: (DetailIntent) -> Unit) = Unit
      """
        .trimIndent(),
    )

  @Test
  fun `the generated entry calls the wrapper with explicit type arguments and role wiring`() {
    val result = compileGezgin(fixture)
    val generated = result.generatedSourceFor("GezginWrapperEntries.kt")

    assertNotNull(generated, "GezginWrapperEntries.kt was not generated: ${result.messages}")
    val text = generated.readText()
    assertContains(text, "public fun GezginEntryScope.provideDetailEntry()")
    assertContains(text, "val nav = LocalGezginRawNavigator.current.detailNavigator(")
    assertContains(text, "appRoot<DetailUiState, DetailIntent, DetailEffect>(")
    assertContains(text, "viewModel = { detailViewModel(route = route) }")
    assertContains(text, "onEffect = { effect -> handleDetailEffect(effect = effect, nav = nav) }")
    assertContains(
      text,
      "content = { state, onIntent -> detailScreen(state = state, onIntent = onIntent) }",
    )
  }

  @Test
  fun `an omitted optional slot does not appear in the call`() {
    val result = compileGezgin(fixture)
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText()

    assertFalse(text.contains("topBar ="), "an unfilled optional slot must be omitted:\n$text")
  }

  @Test
  fun `the route registers once - no core-mode entry is emitted alongside`() {
    val result = compileGezgin(fixture)

    assertFalse(
      result.generatedSourceFor("GezginEntries.kt")?.readText()?.contains("provideDetailEntry") ==
        true,
      "a wrapped route must not also be registered by core-mode codegen",
    )
  }
}
