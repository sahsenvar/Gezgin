package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.compileGezginModule
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperScopeCodegenTest {

  private fun String.flat() = replace(Regex("\\s+"), " ")

  private val header =
    """
    package app

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.FilledBy
    import dev.gezgin.core.annotation.FlowGraph
    import dev.gezgin.core.annotation.GoTo
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.NoBack
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.core.annotation.StartDestination
    import dev.gezgin.core.compose.GezginWrapperScope
    import kotlin.reflect.KClass
    """
      .trimIndent()

  private fun source(body: String) =
    SourceFile.kotlin("Scope.kt", header + "\n\n" + body.trimIndent())

  private val wrapperAndGraph =
    """
    import dev.gezgin.core.annotation.ScreenWrapper

    @NavGraph
    sealed interface AppGraph : Route {
      @GoTo(DetailRoute::class) data object ListRoute : AppGraph
      data class DetailRoute(val id: String) : AppGraph
    }

    @ScreenWrapper
    @Composable
    fun <S> GezginWrapperScope.appRoot(
      @FilledBy(Screen::class) content: @Composable (S) -> Unit,
    ) = Unit

    @Screen(AppGraph.DetailRoute::class)
    @Composable
    fun detailScreen(state: String) = Unit
    """

  // region Validation

  @Test
  fun `a wrapper without the scope receiver is rejected with SW13`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          @ScreenWrapper
          @Composable
          fun <S> plainRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
        )
      )

    assertContains(result.messages, "[SW13]")
    assertContains(result.messages, "GezginWrapperScope")
  }

  @Test
  fun `a wrapper with a different receiver type is also rejected with SW13`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          @ScreenWrapper
          @Composable
          fun <S> String.otherRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
        )
      )

    assertContains(result.messages, "[SW13]")
  }

  @Test
  fun `a parameter that is neither a slot nor defaulted is rejected with SW14`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.titledRoot(
            title: String,
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
        )
      )

    assertContains(result.messages, "[SW14]")
    assertContains(result.messages, "'title'")
    assertFalse(result.messages.contains("[SW13]"), result.messages)
  }

  @Test
  fun `a defaulted non-slot parameter stays legal and the wrapper still binds`() {
    val result =
      compileGezgin(
        source(
          wrapperAndGraph.replace(
            "fun <S> GezginWrapperScope.appRoot(",
            "fun <S> GezginWrapperScope.appRoot(\n  title: String = \"\",",
          )
        )
      )

    assertFalse(result.messages.contains("[SW14]"), result.messages)
    assertFalse(result.messages.contains("[SW13]"), result.messages)
    assertNotNull(result.generatedSourceFor("GezginWrapperEntries.kt"), result.messages)
  }

  // endregion

  // region Scope call and route metadata

  @Test
  fun `the wrapper is called on a scope built from the route's compile-time metadata`() {
    val result = compileGezgin(source(wrapperAndGraph))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "val scope = rememberGezginWrapperScope(")
    assertContains(text, "route = route,")
    assertContains(text, "routeName = \"DetailRoute\",")
    assertContains(text, "noBack = false,")
    assertContains(text, "scope.appRoot<String>(")
    assertContains(text, "@file:OptIn(GezginInternalApi::class)")
  }

  @Test
  fun `a route with no annotations of interest gets an empty annotation list`() {
    val result = compileGezgin(source(wrapperAndGraph.replace("@GoTo(DetailRoute::class) ", "")))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "routeAnnotations = emptyList(),")
  }

  @Test
  fun `custom and gezgin annotations are reproduced as constructor calls and plugin ones are not`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper
          import kotlinx.serialization.Serializable

          enum class Level { LOW, HIGH }

          annotation class Inner(val value: Int)

          annotation class Tracked(
            val name: String,
            val level: Level = Level.LOW,
            val tags: Array<String> = [],
            val target: KClass<*> = Any::class,
            val inner: Inner = Inner(0),
            val weight: Long = 0L,
          )

          @NavGraph
          sealed interface AppGraph : Route {
            @GoTo(DetailRoute::class) data object ListRoute : AppGraph

            @NoBack
            @Serializable
            @Tracked("detail", level = Level.HIGH, tags = ["a", "b"], target = String::class, inner = Inner(5), weight = 7L)
            data class DetailRoute(val id: String) : AppGraph
          }

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.appRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable
          fun detailScreen(state: String) = Unit
          """
        )
      )
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "NoBack()")
    assertContains(text, "Tracked(")
    assertContains(text, "name = \"detail\"")
    assertContains(text, "level = Level.HIGH")
    assertContains(text, "tags = arrayOf<String>(\"a\", \"b\")")
    assertContains(text, "target = String::class")
    assertContains(text, "inner = Inner(value = 5)")
    assertContains(text, "weight = 7L")
    assertFalse(text.contains("Serializable("), "plugin annotation leaked:\n$text")
    assertContains(text, "noBack = true,")
  }

  @Test
  fun `a vararg KClass argument such as GoTo target is reproduced with a typed array`() {
    val result =
      compileGezgin(
        source(
          wrapperAndGraph.replace(
            "@Screen(AppGraph.DetailRoute::class)",
            "@Screen(AppGraph.ListRoute::class)",
          )
        )
      )
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "GoTo(")
    assertContains(text, "*arrayOf<KClass<out Route>>(AppGraph.DetailRoute::class)")
  }

  @Test
  fun `an annotation the generated file cannot name is skipped with SW15 and the build goes on`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          private annotation class Hidden

          @NavGraph
          sealed interface AppGraph : Route {
            @Hidden data class DetailRoute(val id: String) : AppGraph
          }

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.appRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable
          fun detailScreen(state: String) = Unit
          """
        )
      )
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText()

    assertSw15IsOnlyAWarning(result.messages, count = 1)
    assertContains(result.messages, "Hidden")
    assertFalse(text.contains("Hidden"), "skipped annotation must not be emitted:\n$text")
    assertFalse(result.messages.contains("[SW13]") || result.messages.contains("[SW14]"))
  }

  @Test
  fun `an annotation naming a type the generated file cannot see is skipped with SW15`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          private class Secret

          private object Holder {
            annotation class Nested
          }

          annotation class Tracked(val target: KClass<*>)

          @NavGraph
          sealed interface AppGraph : Route {
            @GoTo(OtherRoute::class) @Tracked(target = Secret::class) data object DetailRoute : AppGraph

            @Holder.Nested data object OtherRoute : AppGraph
          }

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.appRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable
          fun detailScreen(state: String) = Unit

          @Screen(AppGraph.OtherRoute::class)
          @Composable
          fun otherScreen(state: String) = Unit
          """
        )
      )
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText()

    assertSw15IsOnlyAWarning(result.messages, count = 2)
    assertFalse(text.contains("Secret"), "private KClass argument leaked:\n$text")
    assertFalse(text.contains("Nested"), "annotation inside a private object leaked:\n$text")
    assertContains(text, "GoTo(")
  }

  private fun assertSw15IsOnlyAWarning(messages: String, count: Int) {
    val lines = messages.lines().filter { "[SW15]" in it }
    assertTrue(lines.size == count, "expected $count [SW15] lines:\n$messages")
    assertTrue(lines.all { it.startsWith("w: ") }, "[SW15] must be a warning:\n$messages")
  }

  // endregion

  // region Graph chain

  private val nestedGraphs =
    """
    import dev.gezgin.core.annotation.ScreenWrapper

    annotation class Area(val name: String)

    @Area("profile")
    @NavGraph
    sealed interface ProfileGraph : Route {
      @GoTo(SettingsFlow::class) data object Profile : ProfileGraph

      @FlowGraph
      sealed interface SettingsFlow : ProfileGraph {
        @StartDestination data object Landing : SettingsFlow
        data object Other : SettingsFlow
      }
    }

    @ScreenWrapper
    @Composable
    fun <S> GezginWrapperScope.appRoot(
      @FilledBy(Screen::class) content: @Composable (S) -> Unit,
    ) = Unit

    @Screen(ProfileGraph.SettingsFlow.Landing::class)
    @Composable
    fun landingScreen(state: String) = Unit

    @Screen(ProfileGraph.SettingsFlow.Other::class)
    @Composable
    fun otherScreen(state: String) = Unit
    """

  @Test
  fun `a route in a flow nested in a nav graph gets the chain with the right kinds`() {
    val result = compileGezgin(source(nestedGraphs))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "graph = gezginGraph_app_ProfileGraph_SettingsFlow,")
    assertContains(text, "GezginGraph( name = \"SettingsFlow\", kind = GraphKind.Flow,")
    assertContains(text, "parent = gezginGraph_app_ProfileGraph")
    assertContains(text, "GezginGraph( name = \"ProfileGraph\", kind = GraphKind.Nav,")
    assertContains(text, "Area(name = \"profile\")")
    assertContains(text, "parent = null")
    assertTrue(
      text.indexOf("val gezginGraph_app_ProfileGraph:") <
        text.indexOf("val gezginGraph_app_ProfileGraph_SettingsFlow:"),
      "a parent constant must be declared before the constant that references it:\n$text",
    )
  }

  @Test
  fun `two routes sharing a graph emit its constant once`() {
    val result = compileGezgin(source(nestedGraphs))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertTrue(
      Regex("val gezginGraph_app_ProfileGraph_SettingsFlow:").findAll(text).count() == 1,
      "the shared graph constant must be emitted exactly once:\n$text",
    )
  }

  @Test
  fun `a route outside any annotated graph references one shared fallback graph constant`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          data object LoneRoute : Route

          data object OtherLoneRoute : Route

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.appRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit

          @Screen(LoneRoute::class)
          @Composable
          fun loneScreen(state: String) = Unit

          @Screen(OtherLoneRoute::class)
          @Composable
          fun otherLoneScreen(state: String) = Unit
          """
        )
      )
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertTrue(
      Regex("graph = gezginGraph_none,").findAll(text).count() == 2,
      "both routes must reference the fallback constant:\n$text",
    )
    assertTrue(
      Regex("val gezginGraph_none:").findAll(text).count() == 1,
      "the fallback graph must be declared exactly once:\n$text",
    )
    assertFalse(text.contains("graph = GezginGraph("), "graph must never be built inline:\n$text")
  }

  // endregion

  // region Cross-module

  @Test
  fun `a route compiled into a dependency still gets its name annotations and graph`() {
    val navigation =
      compileGezginModule(
        SourceFile.kotlin(
          "Nav.kt",
          """
          package navigation

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.GoTo
          import dev.gezgin.core.annotation.NavGraph

          annotation class Tracked(val name: String)

          @NavGraph
          sealed interface AppGraph : Route {
            @GoTo(DetailRoute::class) data object ListRoute : AppGraph

            @Tracked("detail") data class DetailRoute(val id: String) : AppGraph
          }
          """
            .trimIndent(),
        )
      )
    val feature =
      compileGezginModule(
        SourceFile.kotlin(
          "Feature.kt",
          """
          package feature

          import androidx.compose.runtime.Composable
          import dev.gezgin.core.annotation.FilledBy
          import dev.gezgin.core.annotation.Screen
          import dev.gezgin.core.annotation.ScreenWrapper
          import dev.gezgin.core.compose.GezginWrapperScope
          import navigation.AppGraph

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.featureRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable
          fun detailScreen(state: String) = Unit
          """
            .trimIndent(),
        ),
        extraClasspath = listOf(navigation.outputDirectory),
      )
    val text = feature.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "routeName = \"DetailRoute\",")
    assertContains(text, "Tracked(name = \"detail\")")
    assertContains(text, "GezginGraph( name = \"AppGraph\", kind = GraphKind.Nav,")
  }

  // endregion
}
