package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import kotlin.test.Test
import kotlin.test.assertContains
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class SlotProviderReaderTest {

  private fun fixture(extra: String) =
    SourceFile.kotlin(
      "Fixture.kt",
      """
      package app

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

      @ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

      @ScreenWrapper
      fun appRoot(
        @FilledBy(TopBar::class) topBar: () -> Unit = {},
        @FilledBy(Screen::class) content: () -> Unit,
      ) {
        topBar()
        content()
      }

      $extra
      """
        .trimIndent(),
    )

  @Test
  fun `a provider is bound to its route and its role parameters are classified`() {
    val result =
      compileGezgin(
        fixture(
          """
          @TopBar(AppGraph.DetailRoute::class)
          fun detailTopBar(route: AppGraph.DetailRoute, nav: DetailNavigator) = Unit
          """
            .trimIndent()
        ),
        kspArgs = mapOf("gezgin.dumpWrapper" to "true", "gezgin.emitEntries" to "false"),
      )

    val dump =
      findGeneratedResource("GezginWrapperDump.txt")?.readText()
        ?: error("no wrapper dump; compilation said: ${result.messages}")
    assertContains(
      dump,
      "provider app.detailTopBar marker=app.TopBar route=app.AppGraph.DetailRoute",
    )
    assertContains(dump, "roles=[route:ROUTE, nav:NAVIGATOR]")
    assertContains(dump, "slotParams=[]")
  }

  @Test
  fun `a non-role parameter stays a slot parameter`() {
    compileGezgin(
      fixture(
        """
        @TopBar(AppGraph.DetailRoute::class)
        fun detailTopBar(title: String) = Unit
        """
          .trimIndent()
      ),
      kspArgs = mapOf("gezgin.dumpWrapper" to "true", "gezgin.emitEntries" to "false"),
    )

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "slotParams=[title]")
    assertContains(dump, "roles=[]")
  }

  @Test
  fun `SW4 fires when two providers claim the same slot for one route`() {
    val result =
      compileGezgin(
        fixture(
          """
          @TopBar(AppGraph.DetailRoute::class) fun firstTopBar() = Unit

          @TopBar(AppGraph.DetailRoute::class) fun secondTopBar() = Unit
          """
            .trimIndent()
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW4]")
    assertContains(result.messages, "firstTopBar")
    assertContains(result.messages, "secondTopBar")
  }

  @Test
  fun `a repeatable marker binds one provider to several routes`() {
    compileGezgin(
      fixture(
        """
        @TopBar(AppGraph.ListRoute::class)
        @TopBar(AppGraph.DetailRoute::class)
        fun sharedTopBar() = Unit
        """
          .trimIndent()
      ),
      kspArgs = mapOf("gezgin.dumpWrapper" to "true", "gezgin.emitEntries" to "false"),
    )

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "route=app.AppGraph.ListRoute")
    assertContains(dump, "route=app.AppGraph.DetailRoute")
  }
}
