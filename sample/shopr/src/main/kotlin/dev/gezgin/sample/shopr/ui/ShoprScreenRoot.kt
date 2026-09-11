package dev.gezgin.sample.shopr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.FilledBy
import dev.gezgin.core.annotation.Screen
import dev.gezgin.core.annotation.ScreenSlot
import dev.gezgin.core.annotation.ScreenWrapper
import kotlin.reflect.KClass

@ScreenSlot @Repeatable annotation class ViewModelOf(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class Effects(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class BottomBar(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class ResultCollector(val route: KClass<out Route>)

/**
 * Shopr's screen root. The IME-aware bottom bar that used to be `@BottomBar`'s hard-coded behaviour
 * now lives here, where the app can change it.
 *
 * The `onEffect` slot carries a `(String) -> Unit` alongside the effect: an effect provider is a
 * plain function with no composition of its own, so it cannot reach `LocalContext`. Passing the
 * capability through the slot signature is how a provider gets one — the same mechanism that hands
 * it the typed navigator, only app-defined rather than Gezgin-supplied.
 */
@ScreenWrapper
@Composable
fun <S, I, E> ShoprScreenRoot(
  @FilledBy(ViewModelOf::class) viewModel: @Composable () -> BaseViewModel<S, I, E>,
  @FilledBy(Effects::class) onEffect: (E, (String) -> Unit) -> Unit,
  @FilledBy(TopBar::class) topBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
  @FilledBy(BottomBar::class) bottomBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
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
  val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
  Column(modifier = Modifier.fillMaxSize()) {
    topBar(state, vm::onIntent)
    Column(
      modifier = Modifier.fillMaxWidth().weight(1f),
      content = { content(state, vm::onIntent) },
    )
    if (!imeVisible) bottomBar(state, vm::onIntent)
  }
}
