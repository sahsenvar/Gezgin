package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Android actual (C1 — spec §225 "stable RawNavigator") — iki katman:
 * 1. **Config-change (rotasyon) dayanıklılığı:** navigator, host `ViewModelStoreOwner` (Activity —
 *    `rememberNavigator` çağrısı setContent'te, GezginDisplay'in ÜSTÜNDE) scope'lu bir
 *    [NavigatorHolder] ViewModel'inde tutulur. Activity `ViewModelStore` retained-instance ile
 *    rotasyondan sağ çıktığından holder — ve içindeki AYNI `RawNavigator` — de sağ çıkar. Böylece
 *    per-entry ViewModel'in (aynı mekanizmayla yaşayan) ctor'unda yakaladığı navigator referansı
 *    rotasyondan sonra ÖLÜ olmaz; display'in gözlemlediği `keysState`'i sürmeye devam eder.
 *    (HEAD'de `rememberSaveable` restore'da YENİ bir navigator kuruyordu → VM eskisini, display
 *    yenisini tutuyordu = C1.)
 * 2. **Process death:** holder ViewModel'i de ölür → taze navigator `start`'ta kurulur.
 *    `rememberSaveable` (Bundle → PD'yi atlar) serileştirilmiş [dev.gezgin.core.SavedState]
 *    snapshot'ını taşır; taze instance onu BİR KEZ [RawNavigator.adoptRestored] ile benimser. Adopt
 *    tetikleyicisi [NavigatorHolder.adoptChecked] bayrağıdır: holder'a bağlı, TAZE kurulduğu
 *    composition'da `false` → adopt (snapshot varsa) → `true`. Config-change'te holder (dolayısıyla
 *    bayrak) retained → rotasyonda re-adopt YOK (MN-1: bayrak snapshot boş olsa DA ilk
 *    composition'da set edilir; canlı navigator zaten doğru state'i taşıdığından snapshot'la
 *    üzerine yazmak hem gereksiz hem de teslim edilmiş bir slotu diriltme (çift-teslim) hazard'ı
 *    taşırdı). PD'de taze holder → bayrak `false` → adopt tam bir kez. `save` daima CANLI
 *    navigator'dan encode eder (held değer yok sayılır) → snapshot güncel. Bozuk/şema-uyumsuz
 *    snapshot [decodeSavedStateOrNull] ile `null` → sessiz fresh-start.
 *
 * **Çoklu-navigator (MJ-A):** holder `viewModel<NavigatorHolder>(key = …)` ile call-site-stabil bir
 * key altında edinilir. Key `rememberSaveable`'da tutulan benzersiz bir token'dır; iki bağımsız
 * `rememberNavigator` çağrısı (master/detail, adaptif iki-pane, overlay navigator) AYRI
 * rememberSaveable slot'una (composition konumundan türetilir) düşer → AYRI token → AYRI holder.
 * Token config-change'te Bundle'dan aynen restore edilir (retained holder'ı geri bulur), PD'de de
 * korunur → determinist-stabil. Key'SİZ `viewModel<T>()` sınıf-adı tabanlı SABİT default key
 * kullanırdı → aynı Activity'de (aynı `ViewModelStoreOwner`) iki navigator TEK holder'a sessizce
 * çakışır, ikinci çağrı birincinin instance'ını (yanlış start/topology) alırdı. Token, base'in
 * `rememberSaveable{RawNavigator}` call-site ayrımının birebir eşleniğidir.
 */
@Composable
internal actual fun rememberRawNavigatorInstance(
  start: Route,
  topology: GezginTopology,
  json: Json,
  restoreKey: String,
  onRootBack: () -> Unit,
): RawNavigator =
  key(restoreKey) {
    // The token remains call-site-specific so two navigators in one owner can share a business
    // namespace
    // without sharing a holder. Both the Compose group and input invalidate it when restoreKey
    // changes.
    val callSiteToken =
      rememberSaveable(
        restoreKey,
        saver =
          Saver<String, String>(
            save = { token -> encodeNamespacedNavigatorPayload(restoreKey, token) },
            restore = { encoded -> decodeNamespacedNavigatorPayloadOrNull(encoded, restoreKey) },
          ),
      ) {
        UUID.randomUUID().toString()
      }
    val holderKey = "dev.gezgin.core.NavigatorHolder#$restoreKey#$callSiteToken"
    val holder =
      viewModel<NavigatorHolder>(
        key = holderKey,
        factory =
          viewModelFactory {
            initializer {
              NavigatorHolder(
                RawNavigator(
                  start = start,
                  topology = topology,
                  onRootBack = onRootBack,
                  json = json,
                )
              )
            }
          },
      )
    val navigator = holder.navigator
    val pdSnapshot =
      rememberSaveable(
        restoreKey,
        saver =
          Saver<String, String>(
            save = {
              encodeNamespacedNavigatorPayload(
                restoreKey = restoreKey,
                value = encodeNavigatorState(navigator, json),
              )
            }, // held değer değil, CANLI navigator encode edilir
            restore = { encoded -> decodeNamespacedNavigatorPayloadOrNull(encoded, restoreKey) },
          ),
      ) {
        ""
      }
    if (!holder.adoptChecked) {
      holder.adoptChecked = true
      if (pdSnapshot.isNotEmpty()) {
        // MJ-B — Android adopt yolunda slot-payload decode HATA-TOLERANSI (desktop pariteyi kur):
        // `decodeSavedStateOrNull` yalnız YAPISAL decode yapar (payload opak string → edge silinse/
        // yeniden-adlandırılsa bile geçer). Gerçek slot-decode `adoptRestored`→`decodeSlot` içinde
        // geç
        // çalışır; migration sonrası bir edge/şema uyumsuzluğunda `IllegalArgumentException`/
        // `SerializationException` fırlatır. Guard'sız bu, açılışta CRASH/crash-loop olurdu
        // (desktop
        // `decodeNavigatorStateOrNull` bunu zaten yakalayıp fresh-start yapar → asimetri). Burada
        // yakala
        // → navigator `start`'ta kalır (adoptRestored atomik: fırlarsa state dokunulmadı), sessiz
        // fresh-start (RawNavigator KDoc'unun vaat ettiği dayanıklılık).
        decodeSavedStateOrNull(pdSnapshot, json)?.let { restored ->
          try {
            navigator.adoptRestored(restored)
          } catch (e: SerializationException) {
            // şema-uyumsuz payload → graceful fresh-start (desktop ile aynı semantik)
          } catch (e: IllegalArgumentException) {
            // edge silinmiş/yeniden-adlandırılmış (resultSerializer yok) → graceful fresh-start
          }
        }
      }
    }
    navigator
  }

/**
 * C1 — config-change'i atlayan host-scope'lu kap. Activity `ViewModelStore`'unda yaşar →
 * rotasyondan sağ çıkar; [adoptChecked] snapshot-adopt'un holder ömrü başına EN FAZLA BİR KEZ —
 * yalnız holder TAZE kurulduğu (PD ya da ilk açılış) composition'da — çalışmasını garanti eder
 * (MN-1: config-change'te re-adopt olmaz).
 */
private class NavigatorHolder(val navigator: RawNavigator) : ViewModel() {
  var adoptChecked: Boolean = false
}
