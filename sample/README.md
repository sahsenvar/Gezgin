# Gezgin sample guide

Bu dizin iki runnable Android uygulaması içerir:

- `:sample:app`: multi-module showcase (`navigation`, `domain`, `feature:*`, `app`).
- `:sample:shopr`: küçük, tek-modül Shopr örneği.

Her ikisi de public Gezgin API'sini gerçek Compose/KSP derlemesinde kullanır. `compatibility/zad-consumer` ise sample değildir; ayrı wrapper ve repository-only çözümlemeyle ZAD toolchain uyumluluğunu kanıtlayan bağımsız consumer fixture'dır.

## Build sınırları

| Sınır | Exact sürümler |
|---|---|
| Gezgin root ve sample'lar | Gradle 9.0.0, Kotlin 2.3.21, KSP 2.3.9, AGP 8.13.2, Compose Multiplatform 1.11.0; AndroidX Navigation 3 1.0.0 + lifecycle Navigation 3 2.10.0; min SDK 24. |
| `compatibility/zad-consumer` | Kendi Gradle 9.4.1 wrapper'ı, Kotlin 2.3.21, KSP 2.3.9, AGP 9.2.1, JDK/JVM 21, compile/target SDK 37, Koin 4.2.2 + compiler plugin 1.0.1, AndroidX Navigation 3 1.0.0 + lifecycle 2.10.0. |

Consumer fixture `includeBuild`, composite substitution, `projectDir`, Maven Local veya başka source dependency kullanmaz. Yerel doğrulama exact `0.2.0` artefaktlarını geçici imzalı repository'den, release smoke ise dört `io.github.sahsenvar` koordinatını doğrudan Maven Central'dan çözer.

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

## Ekran wrapper'ı sözleşmesi

Maintained screen'lerde navigasyon yönü tam olarak şöyledir:

`Intent -> onIntent -> Effect -> route'a bağlı effect sağlayıcısı -> typed navigator`

- Stateless screen state render eder ve intent emit eder.
- ViewModel uygulamanın **kendi** temel tipini implement eder; navigator tutmaz. Gezgin bu tipi
  tanımaz.
- `onIntent` state'i günceller veya Effect emit eder.
- Route'a bağlı effect sağlayıcısı Effect'i alır ve generated typed navigator metodunu çağırır.

Uygulamanın sözlüğü ve tek ekran kökü `:sample:designsystem` içindedir; feature modülleri onu
`gezgin.wrapperPackages` ile bildirir.

```kotlin
// :sample:designsystem — bir kez
@ScreenSlot @Repeatable annotation class ViewModelOf(val route: KClass<out Route>)
@ScreenSlot @Repeatable annotation class Effects(val route: KClass<out Route>)

@ScreenWrapper
@Composable
fun <S, I, E> ShowcaseScreenRoot(
    @FilledBy(ViewModelOf::class) viewModel: @Composable () -> BaseViewModel<S, I, E>,
    @FilledBy(Effects::class)     onEffect: (E, (String) -> Unit) -> Unit,
    @FilledBy(Screen::class)      content: @Composable ColumnScope.(S, (I) -> Unit) -> Unit,
) { /* container, state toplama ve effect politikası burada */ }

// :feature:profile — ekran başına
@ViewModelOf(SettingsScreenRoute::class)
@Composable
fun settingsViewModel(): SettingsViewModel = viewModel { SettingsViewModel() }

@Effects(SettingsScreenRoute::class)
fun handleSettingsEffect(effect: SettingsEffect, show: (String) -> Unit, nav: SettingsNavigator) {
    when (effect) {
        is SettingsEffect.ShowMessage -> show(effect.text)
        SettingsEffect.Logout -> nav.logout()
    }
}
```

`show` bir Gezgin rolü değil, uygulamanın slot imzasından geçirdiği bir yetenektir: effect
sağlayıcısı düz bir fonksiyondur ve `LocalContext`'e erişemez. Typed navigator ise Gezgin'in verdiği
bir roldür ve üretilen slot lambda'sının closure'ında taşınır.

Result bekleyen route'larda collector VM'ye taşınmaz: composable bir result-collector sağlayıcısı
`nav.*Results` akışını `LaunchedEffect` içinde toplar ve her `NavResult`'ı typed bir `*Intent`
olarak wrapper'dan aldığı `onIntent`'e verir. Güncel örnekler
`sample/feature/auth/.../screen_login/LoginEffectHandler.kt`,
`sample/feature/home/.../screen_dashboard/DashboardEffectHandler.kt` ve
`sample/feature/profile/.../screen_profile/ProfileEffectHandler.kt` dosyalarındadır.

Process-death sonrası re-attach, VM'in navigator tutmasına değil, restore edilen caller route
entry'sinin result-collector'ı yeniden composition'a sokmasına bağlıdır. Generated navigator aynı
caller entry kimliğine bağlıdır; mevcut navigator snapshot'ı in-flight veya teslim edilmiş ama
tüketilmemiş `ResultBus` slotunu korur ve yeniden kurulan collector sonucu tüketir. Navigator
sağlayıcıda kalır, VM yalnız Intent görür.

Effect'leri `MutableSharedFlow` ile taşıma: `replay = 0`, abone yokken yayılan effect'i düşürür ve
örtülen bir Navigation 3 entry'si composition'dan tamamen çıkar. Sample'lar `Channel(UNLIMITED)`
tabanlı `EffectSink` kullanır.


## Maintained capability matrix

| Capability | Güncel kanıt |
|---|---|
| Nested / result flows | `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/AvatarFlow.kt`: `AvatarFlow : ResultFlow<AvatarChoice>` ve nested `AvatarFlow.ZoomFlow`; `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/SignUpFlow.kt`: result'suz sibling `SignUpFlow`. |
| Typed result edges | `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/ProfileGraph.kt`: `ProfileScreenRoute` üzerindeki `@GoForResult(AvatarFlow::class, name = "pickAvatar")` generated `launchPickAvatar()` + `pickAvatarResults` üretir; `sample/feature/profile/src/main/kotlin/dev/gezgin/sample/feature/profile/screen_profile/ProfileEffectHandler.kt` sonucu `ProfileIntent.AvatarResult` olarak VM'e iletir. |
| Quit / back | `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/SignUpFlow.kt`: `TermsScreenRoute` üzerindeki `@BackToStart`, `@Quit`, `@QuitAndGoTo`; `sample/feature/auth/src/main/kotlin/dev/gezgin/sample/feature/auth/screen_terms/TermsEffectHandler.kt`: `nav.backToStart()`, `nav.quit()`, `nav.quitAndGoToWelcome(...)`. `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/HomeGraph.kt` + `sample/feature/home/src/main/kotlin/dev/gezgin/sample/feature/home/screen_item_detail/ItemDetailEffectHandler.kt`: typed `backToDashboard()`. |
| Fullscreen modals | `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/HomeGraph.kt`: `ItemImageViewerRoute : FullscreenModalContract`; `sample/feature/home/src/main/kotlin/dev/gezgin/sample/feature/home/modal_image_viewer/ItemImageViewerModal.kt`: `@FullscreenModal(ItemImageViewerRoute::class)` ve typed `backToItemDetail()`. |
| Transition cascading | `sample/app/src/main/kotlin/dev/gezgin/sample/app/MainActivity.kt`: host `navTransitions`; `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/ProfileGraph.kt`: graph-level `ProfileGraph.transition` ve route-level `SettingsScreenRoute.transition` override'ları. |
| Observability | `sample/app/src/main/kotlin/dev/gezgin/sample/app/MainActivity.kt`: `navigator.events.collect { event -> Log.d("GezginNav", event.toString()) }`. |
| UI'sız typed test API | `sample/navigation/src/test/kotlin/dev/gezgin/sample/navigation/AppNavBehaviorTest.kt`: `GezginTestNavigator` ile generated `nav.fromLogin()`, `nav.fromTerms()`, `nav.fromZoom()` ve diğer `fromX()` accessors. |

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

Her route ayrı `@ViewModelOf(route)` ve `@Effects(route)` sağlayıcısı alır. State ve Intent content ile uyumlu olmalıdır; Home ve Featured route'larının Effect ve typed Navigator tipleri farklı olabilir. Duplicate, eksik veya type-mismatch binding processor tarafından fail-loud reddedilir.

Effect binding yalnız route-explicit bir slot marker'ı ile yapılır; maintained sample kodu her sağlayıcının route ownership'ini açıkça bildirir.

## Uygulamaya ait top/bottom chrome

`@TopBar`/`@BottomBar` artık Gezgin'in kavramı değil: `sample/shopr` kendi marker'larını tanımlar
ve `ShoprScreenRoot` bunları kendi slot'larından doldurur. IME görünürken bottom bar'ı gizleme
davranışı da wrapper'ın içindedir — yani uygulamanın değiştirebileceği bir yerde.

Bkz. `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/ui/ShoprScreenRoot.kt` ve
`sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedChrome.kt`.

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

Geçici ZAD uyumluluğu için `BottomSheetDragHandleMode.None`, Material host'a `dragHandle = null` iletir; özel handle consumer içeriğinde kalır. Varsayılan `Default` mevcut davranışı korur. Enum ve `BottomSheetContract.dragHandleMode`, `@OptIn(ExperimentalGezginMigrationApi::class)` gerektirir; bunlar V2 route-bound presentation/slot tasarımı değildir ve ileride kaldırılabilir.

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
