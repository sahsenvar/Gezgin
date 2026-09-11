package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperAnnotationsTest {

  @Test
  fun `an application can declare a slot marker and a wrapper`() {
    val result =
      compileGezgin(
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

          @ScreenSlot
          @Repeatable
          annotation class TopBar(val route: KClass<out Route>)

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
      )

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }
}
