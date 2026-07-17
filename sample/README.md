# Gezgin sample guide

Bu dizin iki runnable Android uygulaması içerir:

- `:sample:app`: multi-module showcase (`navigation`, `domain`, `feature:*`, `app`).
- `:sample:shopr`: küçük, tek-modül Shopr örneği.

Her ikisi de public Gezgin API'sini gerçek Compose/KSP derlemesinde kullanır. `compatibility/zad-consumer` ise sample değildir; ayrı wrapper ve yalnız Maven Local artefaktlarıyla ZAD toolchain uyumluluğunu kanıtlayan bağımsız consumer fixture'dır.

## Build sınırları

| Sınır | Exact sürümler |
|---|---|
| Gezgin root ve sample'lar | Gradle 8.14, Kotlin 2.3.21, KSP 2.3.9, AGP 8.11.0, Compose Multiplatform 1.11.0; AndroidX Navigation 3 1.0.0 + lifecycle Navigation 3 2.10.0; min SDK 24. |
| `compatibility/zad-consumer` | Kendi Gradle 9.4.1 wrapper'ı, Kotlin 2.3.21, KSP 2.3.9, AGP 9.2.1, JDK/JVM 21, compile/target SDK 37, Koin 4.2.2 + compiler plugin 1.0.1, AndroidX Navigation 3 1.0.0 + lifecycle 2.10.0. |

Consumer fixture `includeBuild`, composite substitution, `projectDir` veya başka source dependency kullanmaz. Gezgin root exact `0.1.0-alpha01` artefaktını önce Maven Local'a publish eder; fixture yalnız bu pinli koordinatı çözer.

## Multi-module yerleşim

```text
:sample:domain
       |
       v
:sample:navigation   sealed graph/route ağacı + generated topology/navigators
       |
       +----------------+----------------+
       v                v                v
:sample:feature:auth  :sample:feature:home  :sample:feature:profile
       \                |                /
        +---------------+---------------+
                        v
                   :sample:app
```

Graph ve route deklarasyonları `:sample:navigation` içinde aynı Kotlin paketindedir. Feature modülleri kendi `@Screen`, modal ve MVI binding'lerini taşır; generated `provideXEntry` fonksiyonlarını feature-owned `*GraphEntries.kt` bundle'larında kaydeder. App yalnız host, root navigation state ve bu bundle'ların montajına sahiptir.

## Strict MVI sözleşmesi

Maintained screen'lerde navigasyon yönü tam olarak şöyledir:

`Intent -> onIntent -> Effect -> @EffectHandler(route) -> typed navigator`

- Stateless screen state render eder ve intent emit eder.
- ViewModel `GezginMvi<State, Intent, Effect>` implement eder; navigator tutmaz.
- `onIntent` state'i günceller veya Effect emit eder.
- Route-bound handler Effect'i gözler ve generated typed navigator metodunu çağırır.

```kotlin
@MviViewModel(SettingsRoute::class)
class SettingsViewModel : ViewModel(), GezginMvi<SettingsState, SettingsIntent, SettingsEffect> {
    private val effectSink = GezginEffects<SettingsEffect>()
    override val effects: Flow<SettingsEffect> = effectSink.flow

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Logout -> effectSink.send(SettingsEffect.Logout)
            is SettingsIntent.ToggleTheme -> updateTheme(intent.enabled)
        }
    }
}

@EffectHandler(SettingsRoute::class)
@Composable
fun SettingsEffectHandler(
    effects: Flow<SettingsEffect>,
    nav: SettingsNavigator,
) {
    ObserveEffects(effects) { effect ->
        if (effect == SettingsEffect.Logout) nav.logout()
    }
}
```

Result bekleyen akışlarda gerçek process-death güvenli pattern `launchX()` + VM recreation'da yeniden kurulan `xResults.collect { ... }` collector'ıdır. Suspend `goToXForResult()` yalnız process ömrü içi convenience yüzeyidir.

## Repeatable screen ve route-specific binding

`@Screen` repeatable'dır. Aynı `ColumnScope` content, ortak State/Intent sözleşmesiyle birden çok route'a bağlanabilir:

```kotlin
@Screen(HomeRoute::class)
@Screen(FeaturedRoute::class)
@Composable
fun ColumnScope.SharedFeed(
    state: FeedState,
    onIntent: (FeedIntent) -> Unit,
) { /* ... */ }
```

Her route ayrı `@MviViewModel(route)` ve `@EffectHandler(route)` alır. State ve Intent content ile uyumlu olmalıdır; Home ve Featured route'larının Effect ve typed Navigator tipleri farklı olabilir. Duplicate, eksik veya type-mismatch binding processor tarafından fail-loud reddedilir.

Deprecated `@ScreenEffect` yalnız source-compatibility bridge'idir. Exact `Flow<E>` tipi, explicit handler tarafından işgal edilmemiş tam bir route göstermiyorsa inference yapılmaz; sıfır/birden fazla aday, duplicate legacy handler ve explicit overlap derleme hatasıdır. Maintained sample kodu `@EffectHandler(Route::class)` kullanır.

## Migration-only top/bottom chrome

`@TopBar(route)` ve `@BottomBar(route)` repeatable `gezgin-mvi` API'leridir. Bunlar yalnız ZAD'ın mevcut `ColumnScope` ekran şeklini migration sırasında korur:

```kotlin
Column {
    TopBar(state, onIntent)
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Screen(state, onIntent)
    }
    if (!imeVisible) {
        BottomBar(state, onIntent)
    }
}
```

Provider eksik olabilir; aynı route için duplicate provider ve State/Intent mismatch compile error'dür. Bu annotation'lar kalıcı app chrome çözümü değildir ve migration app-owned container'a geçtiğinde kaldırılır.

## Host ve restore namespace

```kotlin
val navigator = rememberNavigator(
    start = LoginRoute,
    topology = gezginTopology,
    json = gezginJson,
    restoreKey = "$sessionGeneration:$appMode",
    onRootBack = onRootBack,
)

GezginDisplay(navigator = navigator) {
    authGraphEntries()
    homeGraphEntries()
    profileGraphEntries()
}
```

`restoreKey` boş olmayan, persistent bir namespace'dir. Aynı key recreation/process restore'da aynı stack ve Android holder kimliğini kullanır; key değişirse supplied `start` ile fresh navigator kurulur. Startup route ve key hazır olmadan placeholder navigator oluşturulmaz.

## Bottom-sheet dismissal

`BottomSheetContract.sheetGesturesEnabled` varsayılan olarak `true`'dur. Bir sheet kullanıcı tarafından hiçbir yolla dismiss edilmemeliyse üç anahtar birlikte kapatılır:

```kotlin
@Serializable
data object LockedSheetRoute : AppGraph, BottomSheetContract {
    override val dismissOnBackPress: Boolean get() = false
    override val dismissOnClickOutside: Boolean get() = false
    override val sheetGesturesEnabled: Boolean get() = false
}
```

`dismissOnBackPress`, `dismissOnClickOutside` ve drag/swipe gesture kontrolü birbirinden bağımsızdır. `@NoBack` sheet için back dismissal ve gestures mutlaka `false` olmalıdır. Programatik seçim/result kapanışında sample önce `GezginSheetController.hide()` ile animasyonu bitirir, sonra typed result/back çağrısını yapar.

## Fragment interop

`@FragmentScreen` yalnız View-based **screen leaf** interop'udur. Host `FragmentActivity`/`AppCompatActivity` olmalı; app `Application.onCreate()` içinde Fragment restore'dan önce şunu çağırmalıdır:

```kotlin
Gezgin.initFragmentInterop(gezginJson)
```

Route `gezginArgs<Route>()` ile Bundle'dan, typed navigator `gezginNav<XNavigator>()` ile canlı binding'den gelir. Gerçek process-death restore desteği mevcut ve tamamlanmıştır. `DialogFragment`/`BottomSheetDialogFragment` interop'u yoktur; bu modallar native `@Dialog`/`@BottomSheet` route'larına taşınır.

## Bu artefaktta olmayanlar

- Multiple back stack;
- deep-link route dispatch;
- generic `Throwable` serialization;
- permanent chrome/container API;
- Fragment modal interop.

Deep-link handling mevcut bir Gezgin API'si gibi gösterilmez; V2 debt'idir.

## Çalıştırma

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
./gradlew :sample:shopr:check
./gradlew :sample:app:assembleDebug
./gradlew :sample:navigation:test
```

Bağımsız consumer fixture root build'e composite olarak dahil değildir; kendi wrapper'ıyla ayrıca doğrulanır.
