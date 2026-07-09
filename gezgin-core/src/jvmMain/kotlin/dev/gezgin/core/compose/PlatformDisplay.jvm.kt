package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.Route

/**
 * Desktop (JVM): per-entry `ViewModelStore` decorator'ı — Android [rememberPlatformEntryDecorators]
 * actual'ının DAVRANIŞSAL AYNADAŞI (Faz 5 recheck / C1·MJ3). Üretilen MVI entry'leri VM'i
 * `viewModel()`/`koinViewModel()`/`hiltViewModel()` ile çözer; üçü de `LocalViewModelStoreOwner`
 * ister → decorator olmadan desktop'ta ya ilk render'da `IllegalStateException` (owner yok) ya da
 * VM pencere-scoped olurdu (aynı route'un iki stack instance'ı TEK VM paylaşır, pop'ta `onCleared`
 * hiç gelmez — §222'nin "entry-scoped, pop'ta onCleared" vaadinin sessiz ihlali; yalnız Android'de doğruydu).
 *
 * JB `rememberViewModelStoreNavEntryDecorator` (org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-
 * navigation3, commonMain — Android actual'ı da AYNI artefaktı kullanır) her entry için
 * (`contentKey = GezginKey.id`) TAZE bir child `ViewModelStore` kurar ve entry stack'ten düşünce
 * (`removeViewModelStoreOnPop` non-android default'u `true`) o store'u `clear()`'lar → `onCleared`.
 * Böylece Android'deki "entry-scoped VM, pop'ta temizlik, cover/recompose'da korunur" yaşam döngüsü
 * desktop'ta da birebir sağlanır.
 *
 * **Neden host owner değil, Gezgin'in KENDİ pencere-scoped owner'ı:** decorator'ın varsayılan owner'ı
 * `checkNotNull(LocalViewModelStoreOwner.current)` yapar — CMP desktop host'unda (özellikle
 * `runComposeUiTest`) bu owner garanti DEĞİL → varsayılan patlardı. Desktop'ta config-change (recreation)
 * yok, dolayısıyla owner'ın recreation-survival'a ihtiyacı yok: `remember`'lı stabil bir owner yeter ve
 * owner'sız host'larda da çalışır. `DisposableEffect` ile display teardown'ında owner'ın store'u
 * `clear()`'lanır (aksi halde en son görünür entry'nin VM'i sızardı — child store'ları pop'ta temizlenir
 * ama görünür-kalan entry'ninki teardown'a kadar canlıdır).
 *
 * Sıra [GezginDisplay]'de `[saveable] + platform` (saveable OUTER; bu decorator saveable'ın sağladığı
 * yaşam döngüsü kancasına dayanır) — Android ile aynı.
 */
@Composable
internal actual fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>> {
    val storeOwner = remember { GezginWindowViewModelStoreOwner() }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    return listOf(rememberViewModelStoreNavEntryDecorator(viewModelStoreOwner = storeOwner))
}

/**
 * Desktop pencere-scoped [ViewModelStoreOwner] — [rememberPlatformEntryDecorators]'ın per-entry child
 * store'ları barındırdığı stabil kök (Android'in `ComponentActivity` owner'ının desktop karşılığı).
 * `remember`'lanır (composition boyunca tek instance); GezginDisplay teardown'ında store `clear()`'lanır.
 */
private class GezginWindowViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * Desktop (JVM): sistem-back/predictive-back kavramı yok → no-op. `@NoBack` geri-yutma davranışı
 * [gezginOnBack] guard'ıyla sağlanır (top `noBack` entry iken `onBack` pop yapmaz).
 */
@Composable
internal actual fun GezginNoBackHandler() { /* no-op — bkz. KDoc */ }

/**
 * Desktop (JB alpha05): `NavDisplay` scene-strategy'si **TEKİL** `sceneStrategy: SceneStrategy<T>`.
 * `DialogSceneStrategy() then GezginBottomSheetSceneStrategy() then SinglePaneSceneStrategy()` — dialog-
 * ve sheet-metadata'lı entry'ler overlay olarak (her strateji kendi metadata key'ini okur, ayrık), kalanı
 * tek-pane (SceneStrategy.then overlay-önce sözleşmesi; iki overlay stratejisi karşılıklı-dışlayan, sıra
 * önemsiz — SinglePane en sonda fallback).
 */
@Composable
internal actual fun GezginNavDisplay(
    entries: List<NavEntry<Route>>,
    modifier: Modifier,
    onBack: () -> Unit,
) {
    // m3 — strateji zinciri stateless ama HER recomposition'da yeniden kurulmasın (Android actual'la
    // aynı kimlik-stabilizasyonu; GezginDisplay'in decorator/onBack `remember`'ıyla tutarlı).
    val sceneStrategy = remember {
        DialogSceneStrategy<Route>() then GezginBottomSheetSceneStrategy() then SinglePaneSceneStrategy()
    }
    NavDisplay(
        entries = entries,
        modifier = modifier,
        sceneStrategy = sceneStrategy,
        onBack = onBack,
    )
}
