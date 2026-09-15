package dev.gezgin.sample.hello.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.FilledBy
import dev.gezgin.core.annotation.Screen
import dev.gezgin.core.annotation.ScreenSlot
import dev.gezgin.core.annotation.ScreenWrapper
import kotlin.reflect.KClass

@ScreenSlot @Repeatable annotation class ViewModelOf(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class EffectHandler(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

/**
 * The application's single screen root. It owns the container, the ViewModel, state collection and
 * effect-collection policy; Gezgin fills the slots and supplies the typed route and navigator to
 * whatever declarations carry the marker annotations above.
 */
@ScreenWrapper
@Composable
fun <S : UiState, I : UiIntent, E : UiEvent> AppScreenRoot(
  @FilledBy(ViewModelOf::class) viewModel: @Composable () -> BaseViewModel<S, I, E>,
  @FilledBy(EffectHandler::class) onEffect: (E) -> Unit,
  @FilledBy(TopBar::class) topBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
  @FilledBy(Screen::class) screen: @Composable ColumnScope.(S, (I) -> Unit) -> Unit,
) {
  val vm = viewModel()
  val state by vm.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(vm) { vm.effects.collect(onEffect) }

  Scaffold(modifier = Modifier.fillMaxSize(), topBar = { topBar(state, vm::onIntent) }) { padding ->
    Column(
      modifier = Modifier.padding(padding).fillMaxSize(),
      content = { screen(state, vm::onIntent) },
    )
  }
}
