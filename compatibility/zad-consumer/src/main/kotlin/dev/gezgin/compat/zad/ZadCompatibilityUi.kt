package dev.gezgin.compat.zad

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.FilledBy
import dev.gezgin.core.annotation.Screen
import dev.gezgin.core.annotation.ScreenSlot
import dev.gezgin.core.annotation.ScreenWrapper
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The consumer's own MVI base — the published artifacts define no such type. */
abstract class ZadBaseViewModel<S, I, E> : ViewModel() {
  abstract val uiState: StateFlow<S>
  abstract val effects: Flow<E>

  abstract fun onIntent(intent: I)
}

@ScreenSlot @Repeatable annotation class ViewModelOf(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class Effects(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class BottomBar(val route: KClass<out Route>)

@ScreenWrapper
@Composable
fun <S, I, E> ZadScreenRoot(
  @FilledBy(ViewModelOf::class) viewModel: @Composable () -> ZadBaseViewModel<S, I, E>,
  @FilledBy(Effects::class) onEffect: (E) -> Unit,
  @FilledBy(TopBar::class) topBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
  @FilledBy(BottomBar::class) bottomBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
  @FilledBy(Screen::class) content: @Composable ColumnScope.(S, (I) -> Unit) -> Unit,
) {
  val vm = viewModel()
  val state by vm.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(vm) { vm.effects.collect(onEffect) }
  Column {
    topBar(state, vm::onIntent)
    content(state, vm::onIntent)
    bottomBar(state, vm::onIntent)
  }
}
