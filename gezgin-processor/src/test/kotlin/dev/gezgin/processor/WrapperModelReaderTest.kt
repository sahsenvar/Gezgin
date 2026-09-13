package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.compileGezginModule
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperModelReaderTest {

  private val vocabulary =
    SourceFile.kotlin(
      "AppVocabulary.kt",
      """
      package app

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FilledBy
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.core.annotation.ScreenSlot
      import dev.gezgin.core.annotation.ScreenWrapper
      import kotlin.reflect.KClass

      @ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

      @ScreenWrapper
      fun appRoot(
        @FilledBy(TopBar::class) topBar: () -> Unit = {},
        @FilledBy(Screen::class) content: () -> Unit,
      ) {
        topBar()
        content()
      }
      """
        .trimIndent(),
    )

  @Test
  fun `in-module wrapper and marker are discovered`() {
    compileGezgin(vocabulary, kspArgs = mapOf("gezgin.dumpWrapper" to "true"))

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "wrapper app.appRoot")
    assertContains(dump, "slot topBar marker=app.TopBar default=true")
    assertContains(dump, "slot content marker=dev.gezgin.core.annotation.Screen default=false")
    assertContains(dump, "marker app.TopBar route=route")
  }

  @Test
  fun `a wrapper on the classpath is discovered through gezgin wrapperPackages`() {
    val lib = compileGezginModule(vocabulary)
    assertEquals(KotlinCompilation.ExitCode.OK, lib.exitCode, lib.messages)

    val feature =
      compileGezginModule(
        SourceFile.kotlin(
          "Feature.kt",
          """
          package feature

          object Placeholder
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.dumpWrapper" to "true", "gezgin.wrapperPackages" to "app"),
        extraClasspath = listOf(lib.outputDirectory),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, feature.exitCode, feature.messages)

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "wrapper app.appRoot")
    assertContains(dump, "marker app.TopBar route=route")
    assertContains(dump, "slot topBar marker=app.TopBar default=true")
  }

  @Test
  fun `SW9 fires when a configured package yields nothing`() {
    val result =
      compileGezgin(vocabulary, kspArgs = mapOf("gezgin.wrapperPackages" to "does.not.exist"))

    assertContains(result.messages, "[SW9]")
    assertContains(result.messages, "does.not.exist")
  }

  @Test
  fun `SW3 fires when a slot marker has no route parameter`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "BadMarker.kt",
          """
          package app

          import dev.gezgin.core.annotation.ScreenSlot

          @ScreenSlot annotation class Broken(val name: String)
          """
            .trimIndent(),
        )
      )

    assertContains(result.messages, "[SW3]")
    assertContains(result.messages, "app.Broken")
  }

  @Test
  fun `SW1 fires when a wrapper has no content slot`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "NoContent.kt",
          """
          package app

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.FilledBy
          import dev.gezgin.core.annotation.ScreenSlot
          import dev.gezgin.core.annotation.ScreenWrapper
          import kotlin.reflect.KClass

          @ScreenSlot annotation class TopBar(val route: KClass<out Route>)

          @ScreenWrapper
          fun appRoot(@FilledBy(TopBar::class) topBar: () -> Unit) {
            topBar()
          }
          """
            .trimIndent(),
        )
      )

    assertContains(result.messages, "[SW1]")
    assertContains(result.messages, "app.appRoot")
  }

  @Test
  fun `SW2 fires when a FilledBy parameter is not a function type`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "BadSlot.kt",
          """
          package app

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.FilledBy
          import dev.gezgin.core.annotation.Screen
          import dev.gezgin.core.annotation.ScreenSlot
          import dev.gezgin.core.annotation.ScreenWrapper
          import kotlin.reflect.KClass

          @ScreenSlot annotation class TopBar(val route: KClass<out Route>)

          @ScreenWrapper
          fun appRoot(
            @FilledBy(TopBar::class) topBar: String,
            @FilledBy(Screen::class) content: () -> Unit,
          ) {
            content()
          }
          """
            .trimIndent(),
        )
      )

    assertContains(result.messages, "[SW2]")
    assertContains(result.messages, "topBar")
  }
}
