package dev.gezgin.mvi.annotation

import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * MVI-mode bağlama (§10.1) — **VM class'ının kendisi** üzerinde durur (CLASS target), route'a bağlar:
 * `@ViewModel(OrderChainRoute::class) class OrderChainViewModel(...) : GezginMvi<S,I,E>`.
 *
 * `@Screen(Route::class)`'in aksine burada [route] sentinel'i YOK (argsız türetme yok) — VM'in
 * hangi route'a ait olduğu doğrudan verilir; codegen üçlüyü (`@ViewModel`/`@Screen`/`@ScreenEffect`)
 * route'a göre eşler ve üçünün **aynı modülde** olmasını ister (per-module KSP eşleşmesi, §10.1).
 *
 * Guardrail (5.1): `@ViewModel` işaretli sınıf [dev.gezgin.mvi.GezginMvi] implement ETMELİDİR; etmezse
 * derleme hatası. Codegen ayrıca eşleşen `@Screen(Route)` content'inin `(state, onIntent)` + varsa
 * `@ScreenEffect`'in `Flow<E>` tiplerini VM'in `GezginMvi<S,I,E>` supertype arg'larına karşı doğrular.
 *
 * NOT: gezgin-mvi RUNTIME'da DI-agnostiktir; DI-detection (§10.1) VM'in `@HiltViewModel`/`@KoinViewModel`
 * annotation'larını + ctor `@Assisted`/`@InjectedParam`'ını **string-FQN ile** okuyan codegen'de yapılır
 * (bu annotation'a bir DI bağımlılığı eklemez).
 *
 * NOT (isim kuralı): Aynı-modül bir VM'in **navigator ctor-param'ı `nav` adlı OLMALIDIR** ki default
 * `viewModel` resolver'ının DI-detection'ı onu tanısın — çünkü aynı-modül `nav: XNavigator` tipi bu KSP
 * round'unda henüz üretilmemiştir (tip çözülemez), bu yüzden navigator param'ı **ada göre** (`nav`)
 * eşlenir. Başka bir ad (örn. `SettingsViewModel(navigator: SettingsNavigator)`) verirsen default
 * resolver onu tanımaz ve `viewModel` param'ı zorunlu hale gelir (güvenli ama sessiz bir degradasyon).
 * Route ctor-param'ı ise TİPE göre eşlendiğinden (route tipi her zaman çözülür) onun için böyle bir isim
 * kısıtı yoktur.
 */
@Target(AnnotationTarget.CLASS)
public annotation class ViewModel(val route: KClass<out Route>)
