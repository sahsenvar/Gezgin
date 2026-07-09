package dev.gezgin.sample.feature.profile

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel as AndroidxViewModel
import dev.gezgin.core.annotation.Screen
import dev.gezgin.mvi.GezginMvi
import dev.gezgin.mvi.ObserveAsEvents
import dev.gezgin.mvi.annotation.ScreenEffect
import dev.gezgin.mvi.annotation.ViewModel
import dev.gezgin.sample.navigation.ProfileGraph.SettingsScreenRoute
import dev.gezgin.sample.navigation.SettingsNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * S2/Faz 5.3 — `SettingsScreenRoute`'un **MVI-mode** kanıtı (§10.1). Faz 5.1/5.2 codegen'i yalnız
 * kctfork'un plugin'siz golden-text derlemesiyle kanıtlanmıştı (gerçek Compose runtime'ını çalıştıramaz);
 * bu dosya kütüphanenin ilk GERÇEK uçtan-uca (assembleDebug + on-device) MVI kanıtıdır.
 *
 * Neden `SettingsScreen`: kendi kendine yeten tek ekran (yerel `darkTheme` toggle + tek `nav.logout()`),
 * result-flow/stream-collection taşıması gerektirmez (o desenler zaten `ProfileScreen`'de kanıtlı). MVI-mode
 * içeriğe dokunur ama `SettingsScreenRoute`'un route-seviyesi transition override'ına (slideIn/Out —
 * `ProfileGraph.kt`) dokunmaz; ekran o özelliği korurken YENİ olarak MVI-mode'u da sergiler.
 *
 * Üçlü (aynı modülde, aynı route'a eşlenir):
 *  - `@ViewModel(SettingsScreenRoute)` VM — S/I/E'yi `GezginMvi<S,I,E>` supertype'ından okur.
 *  - stateless `@Screen(SettingsScreenRoute)` `SettingsContent(state, onIntent)`.
 *  - `@ScreenEffect SettingsEffects(Flow<E>)` — `ObserveAsEvents` ile tek-seferlik efekt (Toast + Log).
 *
 * DI: `SettingsViewModel`'in tek ctor param'ı `nav: SettingsNavigator` (nav-tipli) ve Hilt/Koin
 * annotation'ı yok → codegen ANDROIDX-fallback default resolver'ı üretir
 * (`viewModel(factory = viewModelFactory { initializer { SettingsViewModel(nav) } })`). Bu sample'a
 * GERÇEK bir Hilt/Koin bağımlılığı EKLENMEZ — override yalnız örnek olarak aşağıda gösterilir.
 *
 * // Hilt override (gerçek Hilt bağımlılığı BU sample'a EKLENMEZ — yalnızca örnek/README):
 * //   provideSettingsEntry(viewModel = { nav, args ->
 * //       hiltViewModel<SettingsViewModel, SettingsViewModel.Factory>(
 * //           creationCallback = { factory -> factory.create(nav) })
 * //   })
 * // Koin override (yine yalnız örnek):
 * //   provideSettingsEntry(viewModel = { nav, args -> koinViewModel { parametersOf(nav) } })
 * // (Her iki durumda da `provideSettingsEntry`'yi ELLE yazmaya gerek yok — codegen default'u üretir,
 * //  yalnız `viewModel = { ... }` argümanı override edilir; bkz. `sample/README.md` "Faz 5 — MVI-mode".)
 */

/** UI state — yalnız gözlenen `darkTheme` toggle'ı (VM'de tutulur → config-change'te korunur). */
data class SettingsState(val darkTheme: Boolean = false)

/** Kullanıcı niyetleri — content `onIntent(...)` ile tetikler, VM `onIntent`'te işler. */
sealed interface SettingsIntent {
    data object ToggleTheme : SettingsIntent
    data object Logout : SettingsIntent
}

/** Tek-seferlik efekt (nav-olmayan yan etki) — `@ScreenEffect` `ObserveAsEvents` ile tüketir. */
sealed interface SettingsEffect {
    data class ShowMessage(val text: String) : SettingsEffect
}

@ViewModel(SettingsScreenRoute::class)
class SettingsViewModel(private val nav: SettingsNavigator) :
    AndroidxViewModel(),
    GezginMvi<SettingsState, SettingsIntent, SettingsEffect> {

    private val _uiState = MutableStateFlow(SettingsState())
    override val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    override val effects: Flow<SettingsEffect> = _effects.asSharedFlow()

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.ToggleTheme -> {
                _uiState.update { it.copy(darkTheme = !it.darkTheme) }
                // Tek-seferlik efekt: state değişimiyle birlikte bir kez emit (rotation'da tekrar oynamaz).
                _effects.tryEmit(SettingsEffect.ShowMessage("Tema tercihi kaydedildi"))
            }
            // §10 A deseni ("VM-driven, önerilen"): nav VM'e enjekte, üretilen nav metodu doğrudan çağrılır.
            // `logout()` = `replaceTo(LoginScreenRoute, clearUpTo = DashboardScreenRoute, inclusive = true)`.
            SettingsIntent.Logout -> nav.logout()
        }
    }
}

@Screen(SettingsScreenRoute::class)
@Composable
fun SettingsContent(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ayarlar")
            ThemeToggle(state.darkTheme) { onIntent(SettingsIntent.ToggleTheme) }
            Button(onClick = { onIntent(SettingsIntent.Logout) }) { Text("Çıkış yap") }
        }
    }
}

@Composable
private fun ThemeToggle(checked: Boolean, onToggle: () -> Unit) {
    Column {
        Text("Koyu tema")
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@ScreenEffect
@Composable
fun SettingsEffects(effects: Flow<SettingsEffect>) {
    val context = LocalContext.current
    ObserveAsEvents(effects) { effect ->
        when (effect) {
            // Gerçek Android yan etki — kctfork'un yapamadığı: canlı Compose/Android runtime'da bir kez çalışır.
            is SettingsEffect.ShowMessage -> {
                Log.d("SettingsMvi", "effect: ${effect.text}")
                Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
