package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.SavedState
import kotlinx.serialization.json.Json

/**
 * `RawNavigator`'ı `rememberSaveable` ile kurar — PD (process death) simülasyonu §1.10/§12:
 * kaydedilen tip `String` (json-encoded [SavedState], [navigatorSaver]); restore'da ctor'un
 * `restored` parametresine geçer, ilk kuruluşta yok sayılır ve [start] kullanılır.
 *
 * **Kuruluş guard'ı (§12):** `start`'ın flow-chain'i `isResultFlow == true` bir üye İÇEREMEZ —
 * bir ResultFlow üyesi tek başına (bekleyen bir caller'ı olmadan) kökte/ilk entry olarak açılamaz
 * (§8.1). Modal-kind guard'ı BURADA DEĞİL — kind bilgisi entry-scope'ta (registry) yaşar, bu yüzden
 * [GezginDisplay] içinde (register lookup'tan SONRA) uygulanır.
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
    val saver = remember { navigatorSaver(start, topology, json, onRootBack) }
    return rememberSaveable(saver = saver) {
        RawNavigator(start = start, topology = topology, onRootBack = onRootBack, json = json)
    }
}

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
    restore = { encoded -> decodeNavigatorState(encoded, start, topology, json, onRootBack) },
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
