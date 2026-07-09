package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.SavedState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * `RawNavigator`'ı platform-uygun, KİMLİK-STABİL bir holder'da kurar — PD (process death) simülasyonu
 * §1.10/§12: kaydedilen tip `String` (json-encoded [SavedState], [navigatorSaver]/[decodeSavedStateOrNull]).
 *
 * **C1 — config-change'te stable RawNavigator (spec §225):** instance edinimi [rememberRawNavigatorInstance]
 * expect/actual'ına devredilir. Android actual'ı navigator'ı host `ViewModelStoreOwner` (Activity)
 * scope'lu bir holder'da tutar → rotasyon (Activity recreation) AYNI instance'ı korur; VM ctor'unda
 * yakalanan navigator referansı rotasyondan sonra da display'in gözlemlediği state'i sürer. Process
 * death'te holder da ölür → taze instance kurulur ve `rememberSaveable`'daki serileştirilmiş snapshot
 * [RawNavigator.adoptRestored] ile BİR KEZ benimsenir. Desktop actual'ı `rememberSaveable(navigatorSaver)`
 * kullanır (CMP desktop'ta config-change YOK → kimlik composition ömrü boyunca zaten stabil; Faz-3
 * davranışı değişmedi).
 *
 * **Kuruluş guard'ı (§12):** `start`'ın flow-chain'i `isResultFlow == true` bir üye İÇEREMEZ —
 * bir ResultFlow üyesi tek başına (bekleyen bir caller'ı olmadan) kökte/ilk entry olarak açılamaz
 * (§8.1). Modal-kind guard'ı BURADA DEĞİL — kind bilgisi entry-scope'ta (registry) yaşar, bu yüzden
 * [GezginDisplay] içinde (register lookup'tan SONRA) uygulanır.
 *
 * **Stale-lambda fix (deferred, final-review):** `stableOnRootBack` yalnız İLK composition'da kurulur
 * (`remember` init-lambda'sı) — çağıranın `onRootBack`'i bir state'e kapanan (closure) yeni bir lambda
 * instance'ıysa (örn. `{ someState.value }`), holder'a SABİT (bir kez kurulan) bir sarmalayıcı lambda
 * geçilir, ama o sarmalayıcı HER ÇAĞRISINDA en GÜNCEL `onRootBack`'i çağırır ([rememberUpdatedState]).
 */
@Composable
fun rememberNavigator(
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit = platformDefaultRootBack(),
): RawNavigator {
    require(topology.flowChain(start::class).none { it.isResultFlow }) {
        "rememberNavigator: start bir ResultFlow üyesi olamaz (bekleyen caller yok, §8.1/§12) — " +
            "route: ${start::class.simpleName}"
    }
    val latestOnRootBack by rememberUpdatedState(onRootBack)
    val stableOnRootBack = remember { { latestOnRootBack() } }
    return rememberRawNavigatorInstance(start, topology, json, stableOnRootBack)
}

/**
 * C1 — [RawNavigator] instance edinimi, platform-özel kimlik-stabilitesiyle. Android actual'ı host
 * ViewModel-scope'lu bir holder'a sarar (config-change'te AYNI instance korunur) + `rememberSaveable`
 * PD snapshot'ı [RawNavigator.adoptRestored] ile benimser. Desktop actual'ı `rememberSaveable`
 * ([navigatorSaver]) kullanır (config-change yok → kimlik zaten stabil). Detay için [rememberNavigator] KDoc'u.
 */
@Composable
internal expect fun rememberRawNavigatorInstance(
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): RawNavigator

/**
 * [RawNavigator] <-> `String` `Saver`'ı — [navigatorSaver] altında [encodeNavigatorState]/
 * [decodeNavigatorState]'e delege eder (encode/decode simetrisi için TEK `json` kaynağı, bkz.
 * `RawNavigator` KDoc'u). `@Composable` DEĞİL — bilinçli: Compose runtime kurulumu olmadan doğrudan
 * birim testiyle pinlenebilir (Task 3.2 deliverable e, uiTest'siz fallback). Asıl encode/decode
 * mantığı bilinçli olarak `Saver`'ın `SaverScope`-alıcılı `save` üyesinin DIŞINA, düz fonksiyonlara
 * çıkarıldı — testler böylece Compose'un extension-member `Saver.save`'ini (Kotlin/JVM'de foreign
 * (androidx) binary metadata üzerinden çağırmanın kırılgan olduğu görüldü) hiç çağırmaz.
 */
internal fun navigatorSaver(
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): Saver<RawNavigator, String> = Saver(
    save = { nav -> encodeNavigatorState(nav, json) },
    restore = { encoded -> decodeNavigatorStateOrNull(encoded, start, topology, json, onRootBack) },
)

/** `nav.save(): SavedState` → json-encoded `String` (encode yarısı, [navigatorSaver] KDoc'u). */
internal fun encodeNavigatorState(nav: RawNavigator, json: Json): String =
    json.encodeToString(SavedState.serializer(), nav.save())

/** json-encoded `String` → yeni `RawNavigator` (`restored=` ile, decode yarısı, [navigatorSaver] KDoc'u). */
internal fun decodeNavigatorState(
    encoded: String,
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): RawNavigator {
    val restored = json.decodeFromString(SavedState.serializer(), encoded)
    return RawNavigator(start = start, topology = topology, onRootBack = onRootBack, json = json, restored = restored)
}

/**
 * PD-restore fault-tolerance (Important 1, final-review) — [navigatorSaver]'ın `restore`'unun ASIL
 * çağırdığı sarmalayıcı. Kaydedilen `String` (eski uygulama versiyonundan kalmış bozuk/uyumsuz-şema
 * bir PD state'i, örn. bir migration sonrası alan adı/serializer değişmişse) [decodeNavigatorState]'i
 * ATABİLİR — `SerializationException` (malformed/şema-uyumsuz json) veya `IllegalArgumentException`
 * (kotlinx.serialization'ın bazı decode-hatalarını bu tiple sardığı biliniyor, örn. polymorphic/enum
 * çözümü) fırlatabilir. Compose `Saver` sözleşmesi `restore`'un `null` dönmesine izin verir — `null`
 * dönünce Compose [rememberSaveable]'ın init-lambda'sına (yani `start`'tan fresh kuruluşa) düşer; bu
 * yüzden burada YAKALANAN her iki istisna da null'a eşlenir (crash-loop yerine sessiz fresh-start).
 *
 * **Loglama notu:** bu katmanda bir logging altyapısı YOK (ne bir `Logger` interface'i ne de bir
 * platform hook) — sessizce yutuluyor. Gerçek bir uygulamada bu sessizliğin bir telemetri/crash-
 * reporting hook'una bağlanması ÖNERİLİR (kullanıcı sessiz veri kaybını fark etmeyebilir); Faz 3
 * kapsamında bu altyapı yok, bu yüzden bilinçli olarak `println` de EKLENMEDİ (üretim log gürültüsü
 * yaratır) — bkz. final-review raporu, izlenecek TODO.
 */
internal fun decodeNavigatorStateOrNull(
    encoded: String,
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit,
): RawNavigator? = try {
    decodeNavigatorState(encoded, start, topology, json, onRootBack)
        .takeIf { it.keys.isNotEmpty() }   // şema-geçerli ama BOŞ stack → fresh-start (final re-review Minor 2; boş stack composition'da keys.first() ile patlardı)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

/**
 * C1 (Android PD-adopt yolu) — PD snapshot `String`'i doğrudan [SavedState]'e decode eder (navigator
 * KURMADAN; [RawNavigator.adoptRestored]'a beslenir). [decodeNavigatorStateOrNull] ile AYNI fault-
 * tolerance: bozuk/şema-uyumsuz json (eski uygulama versiyonu) `SerializationException`/
 * `IllegalArgumentException` atarsa → `null` (adopt YOK, navigator `start`'ta kalır, crash-loop yok);
 * şema-geçerli ama BOŞ stack de → `null` (composition'da `keys.first()` patlamasını önler).
 */
internal fun decodeSavedStateOrNull(encoded: String, json: Json): SavedState? = try {
    json.decodeFromString(SavedState.serializer(), encoded).takeIf { it.keys.isNotEmpty() }
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
