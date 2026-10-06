package dev.gezgin.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class CallbackModalEntryCodegenTest {

  private val graphSource =
    """
    package dev.gezgin.cb

    import dev.gezgin.core.BottomSheetContract
    import dev.gezgin.core.DialogContract
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.OnDismiss
    import dev.gezgin.core.annotation.Open

    @NavGraph
    sealed interface CbGraph : Route {

        @Open(ConfirmDialog::class, PickSheet::class)
        data object Home : CbGraph

        data class ConfirmDialog(
            val id: String,
            val onConfirm: () -> Unit,
            @OnDismiss val onDismiss: () -> Unit,
        ) : CbGraph, DialogContract

        data class PickSheet(val title: String, val onSelect: (String) -> Unit) : CbGraph, BottomSheetContract
    }
    """
      .trimIndent()

  private val contentSource =
    """
    package dev.gezgin.cbui

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.annotation.BottomSheet
    import dev.gezgin.core.annotation.Dialog
    import dev.gezgin.cb.CbGraph

    @Dialog(CbGraph.ConfirmDialog::class)
    @Composable
    fun ConfirmDialogContent(id: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    }

    @BottomSheet(CbGraph.PickSheet::class)
    @Composable
    fun PickSheetContent(onSelect: (String) -> Unit) {
    }
    """
      .trimIndent()

  private fun assertCallbackEntries(result: JvmCompilationResult) {
    assertFalse(result.messages.contains("[CB"), result.messages)
    assertFalse(result.messages.contains("[SC"), result.messages)
    assertFalse(
      result.messages.contains("unresolved reference", ignoreCase = true),
      result.messages,
    )
    val text =
      assertNotNull(result.generatedSourceFor("GezginEntries.kt"), result.messages).readText()

    assertContains(text, "onDismiss = { it.onDismiss() }")
    assertContains(text, "val raw = LocalGezginRawNavigator.current")
    assertContains(text, "val entryId = LocalGezginEntryId.current")
    assertContains(text, "id = route.id")
    assertContains(text, "onConfirm = { if (raw.isOnStack(entryId)) route.onConfirm() }")
    assertContains(text, "onDismiss = { if (raw.isOnStack(entryId)) route.onDismiss() }")
    assertContains(text, "onSelect = { p0 -> if (raw.isOnStack(entryId)) route.onSelect(p0) }")
    assertFalse("title = route.title" in text, text)
  }

  @Test
  fun `callback modal composable receives route fields and guarded callbacks`() {
    val result =
      compileGezgin(
        SourceFile.kotlin("CbGraph.kt", graphSource),
        SourceFile.kotlin("CbContent.kt", contentSource),
      )

    assertCallbackEntries(result)
  }

  @Test
  fun `repeated BottomSheet binds one callback composable to several routes`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Repeated.kt",
          """
          package dev.gezgin.cbrep

          import androidx.compose.runtime.Composable
          import dev.gezgin.core.BottomSheetContract
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.BottomSheet
          import dev.gezgin.core.annotation.NavGraph
          import dev.gezgin.core.annotation.Open

          @NavGraph
          sealed interface TradeGraph : Route {
              @Open(TradeFundingSheet::class)
              data object Trade : TradeGraph
              data class TradeFundingSheet(val onSelect: (String) -> Unit) : TradeGraph, BottomSheetContract
          }

          @NavGraph
          sealed interface ExerciseGraph : Route {
              @Open(ExerciseFundingSheet::class)
              data object Exercise : ExerciseGraph
              data class ExerciseFundingSheet(val onSelect: (String) -> Unit) : ExerciseGraph, BottomSheetContract
          }

          @BottomSheet(TradeGraph.TradeFundingSheet::class)
          @BottomSheet(ExerciseGraph.ExerciseFundingSheet::class)
          @Composable
          fun FundingSheet(onSelect: (String) -> Unit) {}
          """
            .trimIndent(),
        )
      )
    assertFalse(result.messages.contains("[CB"), result.messages)
    assertFalse(result.messages.contains("[SC"), result.messages)
    assertFalse(
      result.messages.contains("unresolved reference", ignoreCase = true),
      result.messages,
    )
    val text =
      assertNotNull(result.generatedSourceFor("GezginEntries.kt"), result.messages).readText()
    assertContains(text, "TradeFundingSheet")
    assertContains(text, "ExerciseFundingSheet")
  }

  @Test
  fun `repeated Dialog binds one composable to several plain routes`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "RepeatedDialog.kt",
          """
          package dev.gezgin.dlgrep

          import androidx.compose.runtime.Composable
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.Dialog
          import dev.gezgin.core.annotation.NavGraph

          @NavGraph
          sealed interface G : Route {
              data object A : G
              data object B : G
          }

          @Dialog(G.A::class)
          @Dialog(G.B::class)
          @Composable
          fun SharedDialog() {}
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false", "gezgin.emitSerializers" to "false"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }

  @Test
  fun `CB2 names the route whose field set a repeated composable fails to match`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "RepeatedMismatch.kt",
          """
          package dev.gezgin.cbmismatch

          import androidx.compose.runtime.Composable
          import dev.gezgin.core.BottomSheetContract
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.BottomSheet
          import dev.gezgin.core.annotation.NavGraph
          import dev.gezgin.core.annotation.Open

          @NavGraph
          sealed interface G : Route {
              @Open(Good::class, Bad::class)
              data object Home : G
              data class Good(val onSelect: (String) -> Unit) : G, BottomSheetContract
              data class Bad(val onPick: (String) -> Unit) : G, BottomSheetContract
          }

          @BottomSheet(G.Good::class)
          @BottomSheet(G.Bad::class)
          @Composable
          fun Sheet(onSelect: (String) -> Unit) {}
          """
            .trimIndent(),
        )
      )
    assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "[CB2]", message = result.messages)
    assertContains(result.messages, "Bad", message = result.messages)
  }

  @Test
  fun `cross-module feature reads the callback route from the classpath`() {
    val navModule = CompileHarness.compileGezginModule(SourceFile.kotlin("CbGraph.kt", graphSource))
    assertEquals(KotlinCompilation.ExitCode.OK, navModule.exitCode, navModule.messages)

    val feature =
      CompileHarness.compileGezginModule(
        SourceFile.kotlin("CbContent.kt", contentSource),
        extraClasspath = listOf(navModule.outputDirectory),
      )

    assertCallbackEntries(feature)
  }

  @Test
  fun `a screen wrapper with a modal content slot never claims a callback modal`() {
    val wrapperSource =
      """
      package dev.gezgin.cbui
      import dev.gezgin.core.compose.GezginWrapperScope

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.Dialog
      import dev.gezgin.core.annotation.FilledBy
      import dev.gezgin.core.annotation.ScreenSlot
      import dev.gezgin.core.annotation.ScreenWrapper
      import kotlin.reflect.KClass

      @ScreenSlot annotation class ViewModelOf(val route: KClass<out Route>)

      @ScreenWrapper
      @Composable
      fun <S, I> GezginWrapperScope.dialogRoot(
        @FilledBy(ViewModelOf::class) viewModel: @Composable () -> S,
        @FilledBy(Dialog::class) content: @Composable (S, (I) -> Unit) -> Unit,
      ) = Unit
      """
        .trimIndent()

    val result =
      compileGezgin(
        SourceFile.kotlin("CbGraph.kt", graphSource),
        SourceFile.kotlin("CbContent.kt", contentSource),
        SourceFile.kotlin("Wrapper.kt", wrapperSource),
        kspArgs = mapOf("gezgin.wrapperPackages" to "dev.gezgin.cbui"),
      )

    assertFalse(result.messages.contains("[SW6]"), result.messages)
    assertCallbackEntries(result)
  }

  private fun assertViolates(code: String, content: String) {
    val source =
      """
      package dev.gezgin.cbui

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.annotation.Dialog
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.cb.CbGraph

      $content
      """
        .trimIndent()
    val result =
      compileGezgin(
        SourceFile.kotlin("CbGraph.kt", graphSource),
        SourceFile.kotlin("Content.kt", source),
      )
    assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "[$code]", message = result.messages)
  }

  @Test
  fun `CB1 rejects a callback route bound to a Screen`() =
    assertViolates(
      "CB1",
      """
      @Screen(CbGraph.ConfirmDialog::class)
      @Composable
      fun ConfirmScreen(id: String) {}
      """,
    )

  @Test
  fun `CB2 rejects a parameter that names no route field`() =
    assertViolates(
      "CB2",
      """
      @Dialog(CbGraph.ConfirmDialog::class)
      @Composable
      fun ConfirmDialogContent(onOk: () -> Unit) {}
      """,
    )

  @Test
  fun `CB2 rejects a parameter whose type differs from the route field`() =
    assertViolates(
      "CB2",
      """
      @Dialog(CbGraph.ConfirmDialog::class)
      @Composable
      fun ConfirmDialogContent(id: Int) {}
      """,
    )
}
