package dev.gezgin.sample.designsystem

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.BottomSheet
import dev.gezgin.core.annotation.FilledBy
import dev.gezgin.core.annotation.Screen
import dev.gezgin.core.annotation.ScreenSlot
import dev.gezgin.core.annotation.ScreenWrapper
import kotlin.reflect.KClass

@ScreenSlot @Repeatable annotation class ViewModelOf(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class Effects(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class ResultCollector(val route: KClass<out Route>)

/**
 * The showcase's screen root. It lives here, in a module the feature modules only depend on, so no
 * feature's KSP round can discover it by annotation — each feature names this package through the
 * `gezgin.wrapperPackages` option instead.
 */
@ScreenWrapper
@Composable
fun <S, I, E> ShowcaseScreenRoot(
  @FilledBy(ViewModelOf::class) viewModel: @Composable () -> BaseViewModel<S, I, E>,
  @FilledBy(Effects::class) onEffect: (E, (String) -> Unit) -> Unit,
  @FilledBy(TopBar::class) topBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
  @FilledBy(ResultCollector::class) resultCollector: @Composable ((I) -> Unit) -> Unit = { _ -> },
  @FilledBy(Screen::class) content: @Composable ColumnScope.(S, (I) -> Unit) -> Unit,
) {
  val context = LocalContext.current
  val vm = viewModel()
  val state by vm.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(vm) {
    vm.effects.collect { effect ->
      onEffect(effect) { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
  }
  resultCollector(vm::onIntent)
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      topBar(state, vm::onIntent)
      Column(Modifier.fillMaxWidth().weight(1f)) { content(state, vm::onIntent) }
    }
  }
}

/**
 * The sheet counterpart. Its content slot names `@BottomSheet`, so only bottom-sheet routes are
 * candidates for it and only `@Screen` routes are candidates for [ShowcaseScreenRoot] — the kind
 * annotation is part of the match, with no special-casing in the processor.
 *
 * There is no outer container here: a sheet body brings its own padding, and the sheet frame is
 * Gezgin's.
 */
@ScreenWrapper
@Composable
fun <S, I, E> ShowcaseSheetRoot(
  @FilledBy(ViewModelOf::class) viewModel: @Composable () -> BaseViewModel<S, I, E>,
  @FilledBy(Effects::class) onEffect: (E, (String) -> Unit) -> Unit,
  @FilledBy(BottomSheet::class) content: @Composable (S, (I) -> Unit) -> Unit,
) {
  val context = LocalContext.current
  val vm = viewModel()
  val state by vm.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(vm) {
    vm.effects.collect { effect ->
      onEffect(effect) { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
  }
  content(state, vm::onIntent)
}
