package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntryDecorator
import dev.gezgin.core.Route

/**
 * Platform'a bağlı `NavEntryDecorator` alt-kümesi (Task 3.3, deliverable 1). [GezginDisplay] ORTAK
 * `rememberSaveableStateHolderNavEntryDecorator()`'i her iki platformda commonMain'de ekler
 * (saveable state = zorunlu, non-optional; `LocalViewModelStoreOwner` gerektirmez, desktop dahil
 * çalışır) ve BUNA bu expect'in döndürdüğü platform-özel decorator'ları EKLER.
 *
 * **Platform ayrımı (bilinçli, kısayol değil):** `rememberViewModelStoreNavEntryDecorator()`
 * (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3`) commonMain'de tanımlı AMA
 * çağrı anında `checkNotNull(LocalViewModelStoreOwner.current)` yapar. Android'de host
 * `ComponentActivity` bunu sağlar → gerçek per-entry `ViewModelStore` (eşit-değerli iki route ayrı
 * VM store alır, R2'nin VM tarafı). CMP desktop host'unda `LocalViewModelStoreOwner` garanti DEĞİL
 * (özellikle `runComposeUiTest`/`setContent`) → çağrı `IllegalStateException` atardı. Bu yüzden
 * desktop actual'ı **boş liste** (no-op) döndürür; saveable-state-holder decorator'ı desktop'ta R2'nin
 * saved-state tarafını zaten karşılar (bkz. GezginDisplayR2Test). Kararın gerekçesi task-3.3-report.md.
 */
@Composable
internal expect fun rememberPlatformEntryDecorators(): List<NavEntryDecorator<Route>>

/**
 * `@NoBack` entry-scoped Gezgin-sahipli geri-handler'ı (M5′, §4.2). [toNavEntry] `noBack==true`
 * (ve kök DEĞİL) entry'lerde bunu ekran içeriğinden ÖNCE (OUTER) çağırır → dispatcher LIFO'sunda
 * ekranın kendi `BackHandler`'ı (varsa, sonra/İÇ kaydolur) kazanır; yoksa Gezgin'inki back'i yutar.
 *
 * **Platform ayrımı:** Android actual'ı gerçek `androidx.activity.compose.BackHandler(enabled = true)`
 * kurar (sistem-back'i entry düzeyinde tüketir; predictive preview o entry'de başlamaz). Desktop'ta
 * sistem-back/predictive-back kavramı YOK (task 3.2'de doğrulandı) → desktop actual'ı no-op; desktop'ta
 * geri-yutma davranışı [gezginOnBack] guard'ıyla (her iki platformda test edilebilir) sağlanır.
 */
@Composable
internal expect fun GezginNoBackHandler()
