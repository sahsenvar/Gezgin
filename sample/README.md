# Gezgin Sample Showcase

Faz 0–3'te geliştirilen Gezgin API'sinin **tam kapsamlı**, çok-modüllü, gerçekçi bir kanıtı:
login/sign-up, bir dashboard + ürün detayı, ve profil/ayarlar/avatar akışlarından oluşan bir
uygulama iskeleti. Amaç bir "gerçek app" üretmek değil — spec'in her navigasyon deseninin
(graph/flow/result/transition/observability/test-API) **cross-module** bir kurulumda gerçekten
çalıştığını göstermek (bkz. `docs/superpowers/plans/2026-07-08-gezgin-sample-showcase.md`).

## Modül şeması

```
:gezgin-core         ── framework çekirdeği (RawNavigator, GezginState, ResultBus, ...)
:gezgin-processor    ── KSP: @NavGraph/@FlowGraph/@GoTo/... → topology + navigator + entry codegen
:gezgin-test         ── GezginTestNavigator (UI'siz test API çekirdeği)
:sample:domain       ── kotlin-jvm — paylaşılan alan modelleri (`AvatarChoice`/`NotificationLevel`/
                         `SortOrder`, `dev.gezgin.sample.domain.model`); navigation + feature'lar bağımlı
      │
      │  api(...)              ksp(gezgin-processor)
      ▼
:sample:navigation   ── kotlin-jvm modülü — TÜM sealed route/graph ağacı burada yaşar (spec §3.3:
                         "sealed subtypes must co-reside"). Üretilenler: gezginTopology,
                         gezginSerializersModule, her route için XNavigator + xNavigator(id) factory.
      │
      │  implementation(":sample:navigation") + ksp(gezgin-processor)  (her feature kendi @Screen'lerini kaydeder)
      ├──────────────┬──────────────────┐
      ▼              ▼                  ▼
:sample:feature:auth :sample:feature:home :sample:feature:profile
 Login, ForgotPassword   Dashboard, ItemDetail   Profile, Settings, EditName
 Dialog, SignUp flow     FilterBottomSheet, Welcome    Dialog, Notifications sheet, Avatar flow
      │              │                  │
      └──────────────┴──────────────────┘
                     │
                     ▼
              :sample:app  ── MainActivity: rememberNavigator + GezginDisplay + NavLogger + onRootBack

:sample:shopr — ayrı, dokunulmamış mini örnek (bu showcase'in bir parçası değil).
```

Her feature modülü `:sample:navigation`'a bağımlı ve KENDİ `ksp(gezgin-processor)`'ını çalıştırır —
`EntryCodegen` (Task 3.4) route'un **kendi** paketine değil, route'un tanımlandığı `:navigation`
paketine karşı navigator-factory'sini çözer; bu yüzden bir feature modülünün kendi `@NavGraph`'ı
olmasa bile (`model.graphs.isEmpty()`) `@Screen`/`@Dialog`/`@BottomSheet` kayıtları ve
`provideXEntry()` codegen'i sorunsuz çalışır (S1'in ana keşfi — cross-module entry codegen).

## Dosya düzeni (per-file)

**Her `@Screen` route'u MVI-mode'dur** ve kendi `screen_<snake_name>/` alt-paketinde **6 dosyaya**
bölünür: `<Name>UiState.kt` / `<Name>Intent.kt` / `<Name>Effect.kt` / `<Name>ViewModel.kt` /
`<Name>Screen.kt` (stateless `@Screen`) / `<Name>EffectHandler.kt` (`@ScreenEffect`). Örn.
`screen_settings/{SettingsUiState,SettingsIntent,SettingsEffect,SettingsViewModel,SettingsScreen,SettingsEffectHandler}.kt`.

Dialog / bottom-sheet / fullscreen-modal içerikleri `dialog_<name>/` · `sheet_<name>/` ·
`modal_<name>/` alt-paketlerinde yaşar; **VM opsiyoneldir** (core-mode da desteklenir). Bilinçli olarak
core-mode tutulanlar (VM'siz `fun X(route, nav)` — core-mode'un hâlâ sergilendiği yer):
`dialog_edit_name/EditNameDialog.kt`, `dialog_forgot_password/ForgotPasswordDialog.kt`,
`sheet_filter/FilterBottomSheet.kt`, `modal_image_viewer/ItemImageViewerModal.kt`. MVI tutulan tek
overlay: `sheet_notification/NotificationsBottomSheet.kt` (MVI + `@BottomSheet` + `controller` +
result — "MVI-sheet" örneği).

Suffix kuralları: `@Screen`→`*Screen`, `@Dialog`→`*Dialog`, `@BottomSheet`→`*BottomSheet`,
`@FullscreenModal`→`*Modal`, `@ScreenEffect`→`*EffectHandler`; UI state'leri `*UiState`. Ekran-yerel
Compose yardımcıları `screen_<name>/ui/` altında (örn. `screen_settings/ui/ThemeToggle.kt`).
Paylaşılan alan modelleri `:sample:domain`'de (`AvatarChoice`/`NotificationLevel`/`SortOrder`).

`*GraphEntries.kt` her feature-modülünün KÖKÜNDE durur (`AuthGraphEntries.kt`,
`HomeGraphEntries.kt`, `ProfileGraphEntries.kt`; shopr'da `ShopGraphEntries.kt`); flow/graph route
ağacı `:sample:navigation`'da. KSP `provideXEntry`'yi içerik fonksiyonunun paketine ürettiğinden
`*GraphEntries.kt` (ve shopr'da `MainActivity.kt`) import'ları bu alt-paketleri izler.

## Navigasyon grafiği

Tam grafik `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/` altında;
üç `@NavGraph` (`AuthGraph`, `HomeGraph`, `ProfileGraph`) doğrudan `Route` altında.
**Flat-file kanıtı (Faz 8.2):** `SignUpFlow` ve `AvatarFlow` kendi dosyalarında (`SignUpFlow.kt`,
`AvatarFlow.kt`) top-level `@FlowGraph` olarak durur — üyelik nesting'den değil deklare edilen
supertype'tan gelir (`: AuthGraph` / `: ProfileGraph`, aynı paket). `AvatarFlow` içinde nested
`ZoomFlow` kalır (`@FlowGraph` içinde `@FlowGraph`) → hibrit: top-level flow + nested alt-flow.

## Kapsama tablosu

| Özellik | Route(lar) | Ekran / dosya |
|---|---|---|
| `@NavGraph` ×3 | `AuthGraph`, `HomeGraph`, `ProfileGraph` | `AuthGraph.kt`, `HomeGraph.kt`, `ProfileGraph.kt` |
| result'suz `@FlowGraph` (flat-file, top-level `: AuthGraph`) | `SignUpFlow` | `SignUpFlow.kt` |
| `ResultFlow<T>` + nested `@FlowGraph` (flat-file, top-level `: ProfileGraph`) | `AvatarFlow` → `AvatarFlow.ZoomFlow` | `AvatarFlow.kt` |
| `@StartDestination` / G1 (app start) | `SignUpFlow.CredentialsScreenRoute`, `AvatarFlow.PickSourceScreenRoute`, `ZoomFlow.ZoomScreenRoute`; gerçek app start = `LoginScreenRoute` | graph dosyaları, `MainActivity.kt` |
| `@GoTo` (+ `singleTop=false` + `name=`) | `DashboardScreenRoute→ItemDetailScreenRoute`; `ItemDetailScreenRoute→ItemDetailScreenRoute` (`goToRelated`, R2 dup) | `screen_dashboard/DashboardViewModel.kt`, `screen_item_detail/ItemDetailViewModel.kt` |
| `@ReplaceTo` (Self-default) | `LoginScreenRoute→DashboardScreenRoute` (`loginSuccess`), `WelcomeScreenRoute→DashboardScreenRoute` (`continueToDashboard`) | `screen_login/LoginViewModel.kt`, `screen_welcome/WelcomeViewModel.kt` |
| `@ReplaceTo` (`clearUpTo`/`inclusive`/`name=logout`) | `SettingsScreenRoute→LoginScreenRoute` | `screen_settings/SettingsViewModel.kt` (VM `onIntent`'ten çağrılır — MVI-mode) |
| `@GoForResult` — screen×4 (Dialog/Dialog/Sheet/Sheet) | `ForgotPasswordDialogRoute`, `EditNameDialogRoute`, `FilterBottomSheetRoute`, `NotificationsSheetRoute` | `dialog_forgot_password/ForgotPasswordDialog.kt`, `dialog_edit_name/EditNameDialog.kt`, `sheet_filter/FilterBottomSheet.kt`, `sheet_notification/NotificationsBottomSheet.kt` |
| `@GoForResult` — flow×1, named×3 | `AvatarFlow` (`pickAvatar`), `FilterBottomSheetRoute` (`pickSort`), `NotificationsSheetRoute` (`pickNotifications`) | `screen_profile/ProfileViewModel.kt`, `screen_dashboard/DashboardViewModel.kt` |
| Üçlü tüketimin İKİ deseni (ikisi de VM `viewModelScope`'ta) | suspend `goToPickSortForResult` (Dashboard) **vs.** `launchPickAvatar()` + `pickAvatarResults.collect` (Profile VM `init`) | `screen_dashboard/DashboardViewModel.kt` / `screen_profile/ProfileViewModel.kt` |
| `@QuitAndGoTo` | `TermsScreenRoute` → `WelcomeScreenRoute` | `screen_terms/TermsViewModel.kt` |
| `@Quit` | `TermsScreenRoute` | `screen_terms/TermsViewModel.kt` |
| `@BackToStart` | `TermsScreenRoute` → `CredentialsScreenRoute` | `screen_terms/TermsViewModel.kt` |
| `@BackTo` | `ItemDetailScreenRoute` → `DashboardScreenRoute`; `ItemImageViewerRoute` → `ItemDetailScreenRoute` (fullscreen modal'ın tipli çıkışı) | `screen_item_detail/ItemDetailViewModel.kt`, `modal_image_viewer/ItemImageViewerModal.kt` |
| `@NoBack` (cross-module) | `WelcomeScreenRoute` (declared in `:navigation`, `@Screen` in `:feature:home`) | `HomeGraph.kt` / `screen_welcome/WelcomeScreen.kt` |
| `backWithResult` | `ForgotPasswordDialogRoute`, `EditNameDialogRoute`, `FilterBottomSheetRoute` (core-mode composable'lardan); `NotificationsSheetRoute` (MVI VM'inden) | ilgili dosyalar |
| `quitWith` | `CropScreenRoute`, `ZoomScreenRoute` (nested içinden de en yakın sözleşme-sahibi AvatarFlow'u bitirir) | `screen_crop/CropViewModel.kt`, `screen_zoom/ZoomViewModel.kt` |
| Kind'lar `@Screen` (her ekran) / `@Dialog`×2 / `@BottomSheet`×2 / `@FullscreenModal`×1 | gerçek overlay render — bkz. aşağıdaki "Faz 4 — gerçek modal overlay" | tüm feature dosyaları |
| `DialogContract` — SABİT desen | `ForgotPasswordDialogRoute.dismissOnClickOutside = false` | `AuthGraph.kt`, `dialog_forgot_password/ForgotPasswordDialog.kt` |
| `DialogContract` — KOŞULLU desen | `EditNameDialogRoute.dismissOnClickOutside` ← `current.isNotBlank()` | `ProfileGraph.kt`, `dialog_edit_name/EditNameDialog.kt` |
| `BottomSheetContract` + `LocalGezginSheetController` + hide-then-result | `FilterBottomSheetRoute.skipPartiallyExpanded = true`; `FilterBottomSheet` `controller.hide()` → `backWithResult(...)` | `HomeGraph.kt`, `sheet_filter/FilterBottomSheet.kt` |
| **`@FullscreenModal` + `FullscreenModalContract`** (tam-ekran modal, `usePlatformDefaultWidth` YOK → `DialogContract`'tan AYRI render kontratı) | `ItemImageViewerRoute.dismissOnClickOutside = false` (dış-tık kapatmaz; `dismissOnBackPress` varsayılan `true` → geri/gesture kapatır); giriş `@GoTo(ItemImageViewerRoute)` `ItemDetailScreenRoute`'tan | `HomeGraph.kt`, `modal_image_viewer/ItemImageViewerModal.kt` |
| Transition cascade (3 seviye) | app `navTransitions{forward{...}backward{...}}` → `ProfileGraph` arayüz override (`fadeIn/fadeOut`) → `SettingsScreenRoute` getter override (`slideIn/slideOut`) | `MainActivity.kt`, `ProfileGraph.kt` |
| **MVI-mode add-on** (`@MviViewModel`/stateless `@Screen`/`@ScreenEffect`, androidx-fallback resolver) — artık TÜM `@Screen`'ler | her `screen_<name>/`; detaylı örnek `SettingsScreenRoute` | `screen_settings/SettingsViewModel.kt` + `screen_settings/Settings*.kt` (her `screen_*/` paketi aynı 6-dosya desenini izler) — bkz. aşağıdaki "Faz 5 — MVI-mode" |
| **MVI-sheet** (`@BottomSheet` MVI-mode + `controller` rol-param'ı + `ResultRoute` + hide-then-result) | `NotificationsSheetRoute` (`ResultRoute<NotificationLevel>`) | `sheet_notification/` (6 dosya: `Notifications*` + `NotificationsBottomSheet.kt`) |
| **MVI Problem 2** (rol-DIŞI content param → ZORUNLU `@Composable () -> T` resolver param) | `SettingsScreen(..., buildInfo: BuildInfo)` → `provideSettingsEntry(buildInfo = { BuildInfo("1.0.0") })` | `screen_settings/SettingsScreen.kt`, `ProfileGraphEntries.kt` |
| **`@FragmentScreen`** (brownfield Fragment interop, View-tabanlı) | `HelpScreenRoute` | `HelpFragment.kt` + `fragment_help.xml` — bkz. aşağıdaki "Faz 6 — Fragment interop" |
| Events observability | `NavLogger` (`navigator.events.collect { Log.d(...) }`) | `MainActivity.kt` |
| `onRootBack = finish()` | — | `MainActivity.kt` |
| PD-safe restore | `rememberNavigator` PD-safe `Saver`; bkz. `gezgin-core` (`GezginDisplay`) dokümantasyonu — bu sample ek bir test EKLEMEZ, framework'ün kendi restore testleri kapsar | — |
| `GezginTestNavigator` + davranış testleri | bkz. "Davranış testleri" altında | `sample/navigation/src/test/.../AppNavBehaviorTest.kt` |

## İki tüketim deseni — karşılaştırma

Aynı "@GoForResult sonucunu bekle" ihtiyacının iki farklı, İKİSİ DE meşru çözümü. Tüm ekranlar
MVI olduğundan İKİSİ DE artık `viewModelScope`'ta yaşar (VM'siz composable'da DEĞİL) — bu ikisini de
config-change/PD'ye karşı doğal olarak güvenli kılar (VM sürdürüldükçe scope da yaşar):

**1. Suspend çağrı (`DashboardViewModel` → FilterBottomSheetRoute, `pickSort`)** —
`viewModelScope.launch { val r = nav.goToPickSortForResult(...) }`. Kısa, doğrudan; sonuç tek-seferlik
beklenir. VM scope'unda çalıştığı için eski `rememberCoroutineScope()` config-change hazard'ı ARTIK
YOK (bkz. `DashboardViewModel.kt`'deki kod-içi yorum — hazard'ın MVI karşılığı çözülür).

**2. Launch + collect / stream (`ProfileViewModel` → AvatarFlow, `pickAvatar`)** —
`onIntent(PickAvatar)` içinde `nav.launchPickAvatar()` (fire-and-forget push) + VM `init`'inde ayrı bir
`viewModelScope.launch { nav.pickAvatarResults.collect { ... } }`. Kalıcı bir abone; bekleyen bir sonuç
varsa collector'a teslim edilir. VM ömrü boyunca dinlemeye devam eder — birden çok akıştan (avatar,
bildirim) gelen sonuçları tek bir yerde toplamak için doğal desen (§6).

## Faz 4 — gerçek modal overlay

Faz 3'te `@Screen` / `@Dialog` / `@BottomSheet` annotation'ları `EntryCodegen`/registry seviyesinde
işleniyordu ama tüm kind'lar aynı şekilde, plain full-screen composable olarak render ediliyordu.
**Faz 4 bunu gerçek overlay'e çevirdi** (`gezgin-core`: `DialogSceneStrategy` + el-yazımı
`GezginBottomSheetSceneStrategy`, bkz. `.superpowers/sdd/task-4.{1,2,3}-report.md`) — bu sample artık
kütüphane kullanıcısına REFERANS teşkil edecek şekilde bu API'leri gerçekten kullanıyor:

- **`ForgotPasswordDialogRoute`** (`AuthGraph.kt`) ve **`EditNameDialogRoute`** (`ProfileGraph.kt`) —
  gerçek `androidx.compose.ui.window.Dialog` overlay'i (`DialogSceneStrategy`), arkadaki ekran
  (`LoginScreenRoute`/`ProfileScreenRoute`) görünür kalır. `DialogContract`'ın İKİ besleme deseni
  yan yana gösterilir (bkz. Contracts.kt KDoc'u §7):
  - **SABİT** — `ForgotPasswordDialogRoute.dismissOnClickOutside = false`: her zaman dışarı-tık
    kapatmaz (kullanıcı yanlışlıkla şifre-sıfırlama akışını kaybetmesin); `dismissOnBackPress`
    varsayılan (`true`) hâlâ çalışır.
  - **KOŞULLU** — `EditNameDialogRoute.dismissOnClickOutside` route'un `current` ctor param'ından
    hesaplanır (`current.isNotBlank()`): isim boşsa (ilk-kayıt varsayımı) dışarı-tık kapatmaz, mevcut
    ismi düzenlerken rahatça vazgeçilebilir.
  - Her iki route'da da dismiss (izin verilen yollarla: tap-outside/Esc/back) →
    `onDismissRequest = onBack` → `navigator.back()` → `ResultRoute` caller'ı `NavResult.Canceled`
    alır (mevcut `back()` yolu, ek kod gerekmez).
- **`FilterBottomSheetRoute`** (`HomeGraph.kt`) — gerçek `ModalBottomSheet` overlay'i (el-yazımı
  `GezginBottomSheetSceneStrategy`, arkadaki `DashboardScreenRoute` görünür kalır).
  `BottomSheetContract.skipPartiallyExpanded = true` (kısa liste, ara durak gereksiz).
  `FilterBottomSheet` (`sheet_filter/FilterBottomSheet.kt`) `LocalGezginSheetController.current` ile sheet'in `GezginSheetController`'ını
  okur; bir sıralama seçildiğinde spec §7 deseni izlenir: ÖNCE `controller.hide()` (kapanma
  animasyonu tamamlanır), SONRA `nav.backWithResult(candidate)` (programatik pop + sonuç) — düz
  `backWithResult` çağrısı sheet'i animasyonsuz kaybettirirdi (bkz. `GezginBottomSheetScene`
  KDoc'undaki "kalıntı risk" notu). Kullanıcı swipe-down/scrim-tap/geri-tuşu ile kapatırsa
  (`BottomSheetContract` varsayılanları) yine `Canceled`.

- **`@FullscreenModal` + `FullscreenModalContract` (Faz 7.2 / GAP-1)** — `ItemImageViewerRoute`
  (`HomeGraph.kt`): `ItemDetailScreenRoute`'tan `@GoTo(ItemImageViewerRoute)` ile açılan tam-ekran
  ürün görseli önizleyici (`ItemImageViewerModal`, `modal_image_viewer/ItemImageViewerModal.kt`). `FullscreenModalContract`,
  `DialogContract`'ın bir kopyası DEĞİL: `usePlatformDefaultWidth` **yok** — tam-ekran tanımı gereği
  adapter'da SABİT `false` → `DialogSceneStrategy` bunu scrim'siz/kenar-boşluksuz tam-ekran render eder;
  `DialogContract`'tan AYRI bir render kontratı. Route yalnız dismiss davranışını taşır:
  `dismissOnClickOutside = false` (yanlış dış-tık tam-ekran önizleyiciyi kapatmasın),
  `dismissOnBackPress` varsayılan `true` (geri tuşu/predictive-gesture kapatır → `onDismissRequest` →
  `back()`). Explicit "Kapat" düğmesi `nav.backToItemDetail()` ile açan detay ekranına döner.

  > **Not (kapsam kararı güncellemesi):** Faz 4.4, bu route'u bilinçle atlamıştı ("`FullscreenModal`,
  > `DialogContract`'ın basit bir paraleli; core'da uiTest var, üçüncü modal yeni API deseni öğretmez").
  > O gerekçe ESKİ bar altında ("desenlerin cross-module çalıştığını kanıtla") geçerliydi. Kullanıcının
  > yeni **"tüm özellikleri kullanan örnek"** barı altında ise public bir annotation (`@FullscreenModal`)
  > + public bir tip (`FullscreenModalContract`, `DialogContract`'tan farklı render kontratı) için
  > sample'da SIFIR satır olması gerçek bir kapsama boşluğuydu; Faz 7.2 (GAP-1) bunu kapatır. (Tam-ekran
  > modal route'u codegen'de bir navigator KAZANması için — bare bir leaf-modal kazanmaz — `@BackTo`
  > tipli çıkışı taşır; giriş kenarı düz `@GoTo`, result taşımaz.)

## Faz 5 — MVI-mode (opsiyonel `:gezgin-mvi` add-on)

Faz 5, kütüphaneye **opsiyonel** bir MVI binder'ı ekledi (`:gezgin-mvi` modülü: `GezginMvi<S,I,E>`
sözleşmesi, `@MviViewModel`/`@ScreenEffect` annotation'ları) + tam KSP codegen'i (`gezgin-processor`'ın
`MviEntryCodegen`'i: Hilt/Koin/androidx-fallback için DI-detection default resolver'ı). O ana kadar
MVI-mode yalnız kctfork'un **plugin'siz golden-text** derleme testleriyle kanıtlanmıştı — bunlar gerçek
bir Compose/Android runtime'ını **çalıştıramaz** (kctfork backend'i gerçek composable çağrı bölgelerinde
ICE veriyor). Faz 5.3 bu boşluğu ilk kez `SettingsScreen`'i gerçek AGP derlemesi
(`:sample:app:assembleDebug`, gerçek compose-compiler plugin'i) + on-device koşusuyla MVI'a çevirerek
kapatmıştı — kütüphanenin ilk GERÇEK uçtan-uca MVI kanıtı.

**Artık MVI, bu sample'daki TÜM `@Screen`'lerin standart modudur** (Faz 10 yapı refactor'ü): her ekran
kendi `screen_<name>/` paketinde 6-dosya üçlüsüyle (`*UiState`/`*Intent`/`*Effect`/`*ViewModel`/
`*Screen`/`*EffectHandler`) yazılır; nav-edge çağrıları (`nav.goToX()`/`replaceTo`/`quitWith`/...) artık
ilgili `*ViewModel.onIntent`'inden gelir. `SettingsScreen` aşağıda **detaylı örnek** olarak kalır çünkü
en zenginidir: hem route-seviyesi transition override'ını (slideIn/Out — MVI-mode İÇERİĞE dokunur,
`Route.transition`'a DOKUNMAZ) taşır hem de Problem 2'yi (`buildInfo` resolver-param'ı) sergiler.
Core-mode (VM'siz `fun X(route, nav)`) hâlâ sergilensin diye bazı overlay'ler bilinçle core tutuldu
(bkz. "Dosya düzeni"); MVI-mode overlay örneği ise `NotificationsBottomSheet`'tir (aşağıda).

Üçlü (`screen_settings/{SettingsViewModel,SettingsScreen,SettingsEffectHandler}.kt`, hepsi aynı modülde, aynı route'a eşlenir):

- **`@MviViewModel(SettingsScreenRoute) class SettingsViewModel(nav: SettingsNavigator) : ViewModel(),
  GezginMvi<SettingsUiState, SettingsIntent, SettingsEffect>`** — gerçek androidx `ViewModel`.
  `onIntent(ToggleTheme)` state'teki `darkTheme`'i çevirir + bir efekt emit eder; `onIntent(Logout)`
  enjekte edilmiş `nav.logout()`'u **doğrudan** çağırır (spec §10 A deseni: "VM-driven, önerilen" —
  nav VM'e enjekte, üretilen nav metodu VM içinden çağrılır).
  <br>**İsim kuralı:** navigator ctor-param'ı mutlaka `nav` adlı olmalı — DI-detection onu **ada göre**
  tanır (aynı-modül `SettingsNavigator` tipi henüz üretilmediğinden). `navigator` gibi başka bir ad
  default `viewModel` resolver'ının onu tanımamasına yol açar (`viewModel` param'ı zorunlu olur). Route
  param'ı tipe göre eşlenir, bu kısıt yalnız nav için geçerlidir.
- stateless **`@Screen(SettingsScreenRoute) fun SettingsScreen(state, onIntent, buildInfo)`** —
  suffix kuralı gereği `*Screen` adlı (core-mode `fun XScreen(route, nav)`'in aksine stateless); UI
  yalnız `state` okur + `onIntent` tetikler (`buildInfo` = Problem 2 param'ı, aşağıda).
- **`@ScreenEffect fun SettingsEffectHandler(effects: Flow<SettingsEffect>)`** — `gezgin-mvi`'nin
  `ObserveEffects`'iyle tek-seferlik efekt (bir `Toast` + `Log.d`); `ToggleTheme`'de bir kez tetiklenir.
  Bu, `@ScreenEffect`/`ObserveEffects`'in canlı Compose/Android runtime'da GERÇEKTEN çalıştığının ilk
  kctfork-dışı kanıtı.

**androidx-fallback resolver (gerçek Hilt/Koin bağımlılığı EKLENMEDİ).** `SettingsViewModel`'in tek ctor
param'ı nav-tipli ve `@HiltViewModel`/`@KoinViewModel` taşımadığı için `MviEntryCodegen`'in DI-detection'ı
otomatik olarak ANDROIDX default resolver'ı üretir — `provideSettingsEntry()`'yi ELLE yazmaya gerek yok
(`ProfileGraphEntries.kt`'deki mevcut `provideSettingsEntry()` çağrısı, core-mode'dan MVI-mode'a geçince
DEĞİŞMEDEN çözülür; codegen artık `GezginMviEntries.kt`'ye üretir, `GezginEntries.kt`'ye değil):

```kotlin
// sample/feature/profile/build/generated/ksp/debug/.../GezginMviEntries.kt (üretilen)
public fun GezginEntryScope.provideSettingsEntry(
  viewModel: @Composable (nav: SettingsNavigator, args: ProfileGraph.SettingsScreenRoute) -> SettingsViewModel =
    { nav, args -> viewModel(factory = viewModelFactory { initializer { SettingsViewModel(nav) } }) },
  buildInfo: @Composable () -> BuildInfo,                 // ← Problem 2 resolver'ı (default'suz/ZORUNLU), aşağıya bkz.
) {
  register<ProfileGraph.SettingsScreenRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->
    val nav = LocalGezginRawNavigator.current.settingsNavigator(LocalGezginEntryId.current)
    val vm = viewModel(nav, route)
    val state by vm.uiState.collectAsStateWithLifecycle()
    SettingsEffectHandler(vm.effects)
    SettingsScreen(state, vm::onIntent, buildInfo = buildInfo())
  }
}
```

**Problem 2 (Faz 7.2 / GAP-2) — rol-DIŞI content param → EK resolver param (§10.1).** MVI-mode bir
`@Screen`/`@Dialog`/`@BottomSheet` content'i rol-bazlı sağlananların (`state` / `onIntent` /
`controller`) DIŞINDA bir param alırsa, `MviEntryCodegen` onu `provideXEntry`'ye **kullanıcı-sağlamalı,
default'suz (ZORUNLU)** bir `@Composable () -> T` resolver param'ına dönüştürür ve content'e NAMED arg
olarak geçer. Burada `SettingsScreen(..., buildInfo: BuildInfo)` (`screen_settings/SettingsScreen.kt`) → üretilen
`provideSettingsEntry`'ye `buildInfo: @Composable () -> BuildInfo` eklenir → kurulumda AÇIKÇA verilmek
ZORUNDA: `ProfileGraphEntries.kt`'de `provideSettingsEntry(buildInfo = { BuildInfo(version = "1.0.0") })`.
Bu explicit, zorunlu çağrı-yeri Problem 2'nin **tüketici-tarafı** kanıtıdır (mekanizma artık yalnız
processor fixture'ında değil, gerçek sample'da). `buildInfo` gerçekçi bir bağımlılık gibi okunur —
bir ayarlar ekranı doğal olarak sürüm satırı gösterir; content onu yalnız salt-okunur tüketir.

**Hilt/Koin override — yalnız örnek, wire EDİLMEZ (sample'ı yalın tutmak için).** Bu sample'a bilinçli
olarak gerçek bir Hilt/Koin Gradle bağımlılığı EKLENMEZ; `viewModel` resolver'ı override etmek için
`provideSettingsEntry`'yi elle yazmaya gerek yoktur — yalnızca `viewModel = { ... }` argümanı geçilir
(codegen default'u zaten üretir):

```kotlin
// Hilt (gerçek Hilt bağımlılığı BU sample'a EKLENMEZ — yalnız örnek):
//   provideSettingsEntry(viewModel = { nav, args ->
//       hiltViewModel<SettingsViewModel, SettingsViewModel.Factory>(
//           creationCallback = { factory -> factory.create(nav) }) })
// Koin (yine yalnız örnek):
//   provideSettingsEntry(viewModel = { nav, args -> koinViewModel { parametersOf(nav) } })
```

**MVI-sheet örneği — `NotificationsBottomSheet` (`sheet_notification/`).** MVI-mode yalnız tam ekranlarda
değil, overlay'lerde de çalışır: `NotificationsSheetRoute` bir `@BottomSheet` + `ResultRoute<NotificationLevel>`'dir
ve tam MVI üçlüsüyle yazılır (`Notifications{UiState,Intent,Effect,ViewModel,EffectHandler}.kt` +
`NotificationsBottomSheet.kt`). Farkları Settings'ten: (a) `@BottomSheet` content'i `state`/`onIntent`
yanında bir rol-param'ı olan `controller: GezginSheetController` alır (MVI + controller bir arada); (b)
VM route verisini enjekte alır (`NotificationsViewModel(route, nav)` → başlangıç `UiState` `route.current`'tan);
(c) sonuç teslimi hâlâ hide-then-result: composable önce `controller.hide()` (kapanma animasyonu), sonra
`onIntent(Confirm)` → VM `nav.backWithResult(selected)`. Profile'dan `@GoForResult(..., name = "pickNotifications")`
ile açılır; sonuç `ProfileViewModel`'de suspend `goToPickNotificationsForResult(...)` ile toplanır.

**Cihaz-üstü doğrulama:** MVI-mode'un yalnız derlemeden görülemeyen davranışları (VM'in config-change'te
hayatta kalması, efektin rotation'da tekrar oynamaması, logout'un stack'i doğru temizlemesi)
`docs/gezgin-on-device-checklist.md` madde 14'te insan-doğrulaması olarak listelendi.

## Faz 6 — Fragment interop (brownfield, §11/§11.1)

Faz 6, kütüphaneye **brownfield migration** köprüsünü ekledi: elde zaten var olan, **View-tabanlı** bir
`androidx.fragment.app.Fragment`'ı yeniden yazmadan Gezgin'in Nav3 back-stack'ine **leaf** olarak sokan
`@FragmentScreen` annotation'ı + `FragmentEntryCodegen` (üretilen `provideXEntry` bir
`androidx.fragment.compose.AndroidFragment<XFragment>` host eder) + `gezgin-core/androidMain`'in
`gezginArgs`/`gezginNav` delege'leri. MVI-mode gibi bu makine de o ana dek yalnız kctfork'un **plugin'siz
golden-text** derlemesiyle (Task 6.1/6.2) kanıtlanmıştı — gerçek AGP + compose-compiler + gerçek
`AndroidFragment` derlemesi HİÇ çalıştırılmamıştı. **Faz 6.4 bu boşluğu kapatır:** aşağıdaki `HelpFragment`,
gerçek `:sample:app:assembleDebug` (gerçek `AndroidFragment` + Fragment derlemesi) ile Fragment interop'un
ilk uçtan-uca kanıtıdır (MVI-mode'un Faz 5.3'teki `SettingsScreen` emsali gibi).

### Neden bu route/Fragment seçildi (kapsam adjudication)

MVI-mode **ileriye dönük** bir yazım biçimi olduğu için Faz 5.3 MEVCUT bir ekranı (`SettingsScreen`) MVI'a
çevirmişti. Fragment interop ise **geriye dönük** bir köprüdür — "henüz yeniden yazmadığın" ekranlar içindir.
Bu yüzden mevcut bir Compose ekranını Fragment'a GERİ çevirmek (bir regresyon gibi okunurdu, ve
`AppNavBehaviorTest.kt`/README iddialarıyla çakışabilirdi) yerine, hikâyeyi dürüstçe anlatan **yeni, küçük,
adanmış bir route** eklendi: `HomeGraph.HelpScreenRoute(topic)` — "henüz Compose'a taşımadığımız yardım
ekranı". `HomeGraph`'a bağlandı çünkü Dashboard uygulamanın hub'ı; oradan bir "Yardım" butonu doğal.

- **Route (`:sample:navigation`), Fragment (`:feature:home`) ayrı modüllerde** → codegen route paketini
  Fragment'ın kendi paketinden DEĞİL, route declaration'ından okur (cross-module doğrulaması).
- **Navigator'LI yol canlı çalıştırılır:** `HelpScreenRoute` `@BackTo(DashboardScreenRoute)` deklare eder →
  bir `HelpNavigator` KAZANIR. Böylece `gezginNav`'ın registry-tabanlı yolu GERÇEKTEN egzersiz edilir
  (edge'siz-yaprak/FS5 yolu DEĞİL — o Task 6.2'nin codegen testlerinde kapsanır; buranın işi navigator'LI
  yolun canlı çalıştığını kanıtlamak). Dashboard'a `@GoTo(HelpScreenRoute)` giriş kenarı → `goToHelp(topic)`.

### Sözleşmenin tamamı (`HelpFragment.kt` + `fragment_help.xml`)

```kotlin
@FragmentScreen(HelpScreenRoute::class)
class HelpFragment : Fragment() {                          // hiçbir Gezgin arayüzü YOK; parametreli ctor YOK
    private val args by gezginArgs<HelpScreenRoute>()      // route'u kendi `arguments` Bundle'ından decode → PD-safe
    private val nav  by gezginNav<HelpNavigator>()         // bind sonrası canlı navigator (instance-registry)

    override fun onCreateView(...) = inflater.inflate(R.layout.fragment_help, container, false)   // GERÇEK View, Compose YOK

    override fun onViewCreated(view, ...) {
        view.findViewById<TextView>(R.id.help_topic).text = "Konu: ${args.topic}"     // gezginArgs → görünür içerik
        view.findViewById<Button>(R.id.help_back).setOnClickListener { nav.backToDashboard() }  // gezginNav → gerçek nav
    }
}
```

> **ZORUNLU kurulum — `Gezgin.initFragmentInterop` (gerçek process-death için):** `@FragmentScreen` kullanan
> uygulama, `Application.onCreate()`'te BİR KEZ `Gezgin.initFragmentInterop(gezginJson)` çağırmalıdır (bkz.
> `sample/app/.../ShowcaseApp.kt` + manifest `android:name`). Neden: gerçek process-death sonrası FragmentManager,
> Fragment'ı `Activity.onCreate`'te — `setContent` kompozisyonundan ÖNCE — `onViewCreated`'a kadar restore eder;
> o an `gezginArgs`'ın decode edeceği app-Json henüz kaydedilmemiş olur ve fırlatır. Bu çağrı Json'u process
> açılışında kaydedip pencereyi kapatır (config-change/DKA'da process yaşadığı için zaten çalışırdı — bu yalnız
> gerçek PD için gerekir). Cihazda `am kill` ile doğrulandı (on-device checklist madde 15).

- **`gezginArgs<HelpScreenRoute>()`** — route Fragment'ın `arguments` Bundle'ından decode edilir (`onUpdate`
  zamanlamasından bağımsız; `arguments` örnekleme anında kurulur). `onViewCreated`'da güvenle okunur — **yukarıdaki
  `Gezgin.initFragmentInterop` kurulumu yapıldıysa** (gerçek process-death için; bkz. not).
- **`gezginNav<HelpNavigator>()`** — bind (= `AndroidFragment.onUpdate`'in ilk çalışması) sonrası canlı
  navigator'ı instance-anahtarlı registry'den okur. `nav` erişimi buton tık lambdasına ERTELENİR — o an bind
  kesinlikle tamamlanmıştır (delege lazy olduğu için erken okunmaz).
- **Üretilen `provideHelpEntry()` elle yazılmaz** — `FragmentEntryCodegen` onu core-mode `GezginEntries.kt`'den
  AYRI bir `GezginFragmentEntries.kt`'ye üretir; `homeGraphEntries()` bundle'ı onu çağırır. Gözlenen şekil:

  ```kotlin
  public fun GezginEntryScope.provideHelpEntry() {
    register<HomeGraph.HelpScreenRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->
      val raw = LocalGezginRawNavigator.current
      val nav = raw.helpNavigator(LocalGezginEntryId.current)
      AndroidFragment<HelpFragment>(
        arguments = route.toBundle(raw),
        onUpdate = { fragment -> bindGezgin(fragment, route, nav) },
      )
    }
  }
  ```

### ZORUNLU precondition — host `FragmentActivity`/`AppCompatActivity` olmalı

Fragment interop'u kullanan HER tüketicinin sağlaması gereken **tek precondition**: `AndroidFragment`,
görünüm ağacında bir `FragmentActivity`/`AppCompatActivity` host'u (`FragmentManager.findFragmentManager(view)`)
YOKSA runtime'da fırlatır (Task 6.0 §1e.1 — bir düz `ComponentActivity` host ilk `AndroidFragment`'ta
crash eder). Bu yüzden `MainActivity` `ComponentActivity`'den **`AppCompatActivity`**'ye taşındı ve
`sample:app` kendi `androidx.appcompat:appcompat`'ını AÇIKÇA getirir (gezgin-core `fragment-compose`'u
`implementation` tuttuğundan tüketiciye SIZMAZ — host'unu tüketici yönetir). **Not:** `AppCompatActivity`
ayrıca bir `Theme.AppCompat` (ya da türevi) tema gerektirir → `AndroidManifest.xml` teması
`Theme.AppCompat.Light.NoActionBar`'a çekildi (`res/values/themes.xml`); UI'ı yine Compose'un `MaterialTheme`'i
çizer. (Alternatif: `FragmentActivity` — tema gerektirmez; sample real-world brownfield emsali için
`AppCompatActivity`'yi tercih etti.)

### Legacy `OnBackPressedDispatcher` — kullanıcı sorumluluğu

Bir legacy Fragment'ın KENDİ `OnBackPressedDispatcher` callback'i Nav3'ün tek-otorite back-stack'ini LIFO'da
geçebilir; migration sırasında KALDIRILMALIDIR (§11.1 — Gezgin bunu otomatik sessizleştirmez). `HelpFragment`
böyle bir callback deklare ETMEZ (geri, `GezginDisplay`'in normal back yoluyla çalışır), ama tüketicilerin
bilmesi gereken bir nottur. Dialog/BottomSheet Fragment varyantları interop kapsamı DIŞIDIR (§11.2 — çift
`Window`/çift-otorite; içeriği native `@Dialog`/`@BottomSheet`'e taşınır).

**Cihaz-üstü doğrulama:** Fragment interop'un yalnız derlemeden görülemeyen davranışları (PD/config-change
sonrası `gezginArgs` route + `gezginNav` re-bind, doğru stack derinliğinde restore) `docs/gezgin-on-device-checklist.md`
madde 15-17'de insan-doğrulaması olarak listelendi.

## Davranış testleri

`sample/navigation/src/test/kotlin/dev/gezgin/sample/navigation/AppNavBehaviorTest.kt` — 18 test,
UI katmanına hiç dokunmadan `GezginTestNavigator` + üretilen tipli `fromX()` erişimcileri üzerinden
kurgulanmış senaryolar (kapsama matrisinin DAVRANIŞ kanıtı; tablo çekirdek senaryoları gösterir):

| Test | Kanıtladığı |
|---|---|
| `loginSignUpQuitAndGoToWelcome_leavesLoginThenWelcome` | `@QuitAndGoTo` bütün SignUpFlow segmentini yıkar, Welcome Login'in üstüne düz push edilir |
| `welcomeContinueToDashboard_replacesWelcomeKeepsLoginBelow` | Self-default `@ReplaceTo` (`clearUpTo=null`) yalnız TOP'u keser |
| `logoutClearUpToDashboardInclusive_stacksASecondLoginEntry` | **Tasarım notu** — aşağıya bakın |
| `avatarFlowQuitWith_deliversValueToProfilePickAvatarResults` | `quitWith` en yakın kapsayan `ResultFlow`'u hedefler, Value flow-entry'nin caller'ına gider |
| `nestedZoomFlowBack_popsOnlyZoomLeavesCropOnTop` | nested `@FlowGraph`'ta flow-entry `back()` yalnız kendi segmentini kapatır |
| `nestedZoomFlowQuitWith_deliversValueToProfileTearsDownWholeAvatarFlow` | `quitWith` NESTED sub-flow içinden çağrılınca da en yakın SÖZLEŞME SAHİBİ flow'u (AvatarFlow) hedefler — HEM ZoomFlow HEM AvatarFlow segmenti yıkılır, Value yine Profile'a gider (spec §6 ownership) |
| `goToRelatedTwiceSameId_createsThreeDistinctStackEntries` | `singleTop=false` aynı route-değerine tekrar tekrar YENİ (id'si farklı) entry basar (R2) |
| `forgotPasswordSuspendResult_deliversValue` | suspend `goToXForResult` + `deliverResult` (raw `backWithResult`) round-trip'i |
| `forgotPasswordBack_deliversCanceled` | pending bir `ResultRoute` üzerinde düz `back()` → `NavResult.Canceled` (dismiss without answer) |
| `termsBackToStart_landsOnCredentialsKeepsLoginBelow` | `@BackToStart` flow'un kendi START'ına döner, flow HAYATTA kalır, Login altta dokunulmaz |
| `termsQuit_tearsDownSignUpFlowLeavesLoginOnTop` | `@Quit` bütün SignUpFlow segmentini yıkar, Login üstte kalır |
| `backToDashboardThenCrossGraphGoToProfile_bothViaGeneratedNavigators` | `@BackTo` (ItemDetail→Dashboard) + cross-graph B1 edge'i (`goToProfile`) TAMAMEN üretilen navigator'larla (raw.navigate YOK) pinler |

### Tipli `fromX()` erişimcileri — çok-modül düzeninde çalışıyor (F-MAJOR-2)

Manşet "UI'sız test" özelliği kanonik çok-modül düzeninde (graph'lar `main`'de, testler `test`
kaynak kümesinde) uçtan uca çalışır: yukarıdaki 18 test `nav.fromLogin().goToSignUp()` gibi
**üretilmiş** tipli erişimcilerle sürülür — hiçbir `raw.xNavigator(entryId)` fabrika plumbing'i yok.

Doğru bağlama noktası, flag'i graph'ların bulunduğu **`main` KSP round'unda** açmaktır. Önceki
`kspTest`-scoped deneme yapısal olarak yanlıştı: `kspTestKotlin` round'u yalnız `test` kaynak
kümesinin `.kt`'lerini görür; `@NavGraph`'lar (`AuthGraph.kt`/`HomeGraph.kt`/`ProfileGraph.kt`) ise
`main`'de zaten `.class`'a derlenmiştir ve `getSymbolsWithAnnotation` binary classpath'ten
annotation'ları yeniden keşfetmez → boş model → hiç erişimci üretilmez. Çözüm
`sample/navigation/build.gradle.kts` içinde:

- `ksp { arg("gezgin.emitTestAccessors", "true") }` — GLOBAL `ksp{}` bloğu `kspKotlin` (main round)
  üzerinde çalışır; `TestApiCodegen` `GezginTestAccessors.kt`'yi `main`'e, topology/navigator ile
  aynı yere üretir. (`kspTestKotlin` round'u flag'i alır ama orada graph olmadığı için no-op'tur —
  çift üretim yok.)
- `compileOnly(project(":gezgin-test"))` — üretilen erişimciler `GezginTestNavigator`'a dokunur, bu
  yüzden `compileKotlin`'in (main) sınıf yolunda görünür olmalı; `compileOnly` bu test-yardımcı
  artefaktı app'in RUNTIME sınıf yolundan uzak tutar. `test` kaynak kümesi için
  `testImplementation(project(":gezgin-test"))` onu compile + runtime'a yeniden ekler.

`test` kaynak kümesi `main`'i gördüğü için üretilmiş `nav.fromX()`'i doğrudan çağırır (opt-in
gerektirmez). `nav.raw` yalnızca `fromX()` kapsamı dışındaki birkaç düşük-seviye `raw.navigate` /
`raw.currentEntryId` kurulum/inceleme çağrısı için (`@GezginInternalApi` opt-in ile) kalır — sample'da
hâlâ `kotlinx-coroutines-test` bağımlılığı vardır. Processor'ın kctfork tabanlı kendi testleri de bu
çok-modül senaryosunu doğrular (`TestApiCodegenTest`: main round üretir, ayrı bir round classpath'ten
tüketir ama yeniden üretmez).

## Tasarım notları (S2/S3 bulguları)

- **`SettingsScreenRoute.logout()` çift-Login bulgusu** — `@ReplaceTo(LoginScreenRoute, clearUpTo =
  DashboardScreenRoute::class, inclusive = true)`, `[Login, Dashboard, Profile, Settings]` stack'inden
  başlarsa Dashboard'a kadar (dahil) her şeyi temizler ve GERİYE KALAN `Login`'in ÜSTÜNE yeni bir
  Login entry'si push eder (`replaceTo` her zaman `singleTop=false` ile push eder — hedef zaten
  top'un altındaki bir entry ile aynı DEĞERDE olsa bile dedup yapmaz). Runtime sonucu:
  `[Login, Login']` — çift Login. Bu, grafiği "düzeltmek" yerine BİLİNÇLİ OLARAK olduğu gibi
  bırakılan bir örnek-grafik tasarım bulgusudur (S1'in dosyası değiştirilmedi); davranış testi
  (`logoutClearUpToDashboardInclusive_stacksASecondLoginEntry`) bu GERÇEK sonucu pinler. Gerçek bir
  üründe muhtemel düzeltme: `clearUpTo` hedefini `LoginScreenRoute::class` yapmak (o zaman zaten dip
  Login'in KENDİSİ temizlenir) ya da `SettingsScreenRoute`'u her zaman Dashboard'un ÜSTÜNDE bir yerden
  çağrılacağını varsaymamak.
- **`quitWith` sahiplik (ownership) çözünürlüğü — bulunan ve DÜZELTİLEN kütüphane bug'ı** — Bu
  showcase, `ZoomFlow : AvatarFlow` şeklinde (nested flow'un, kapsayan `ResultFlow`'u SUBTYPE
  ettiği) bir zincirde `quitWith`'in sessizce değer düşürdüğünü ortaya çıkardı: `TopologyCodegen`
  üretilen `FlowType.isResultFlow`'u TRANSİTİF bayraktan yazıyordu, runtime'ın
  `chain.indexOfLast { it.isResultFlow }` hedef seçimi de bu yüzden AvatarFlow yerine ZoomFlow'u
  buluyordu. Düzeltme (spec §6 ownership): üretilen bayrak artık DOĞRUDAN `ResultFlow<T>`
  deklarasyonundan (`declaresResultFlowDirectly`) yazılır — nested result'suz sub-flow `false`,
  sözleşme sahibi `true`. `NavigatorCodegen`'in `quitWith` parametre-tipi seçimi de aynı sahiplik
  kuralına hizalandı. Processor regresyon testi:
  `TopologyCodegenTest."FlowType isResultFlow is OWNERSHIP (direct declaration) ..."`; davranış
  kanıtı: `nestedZoomFlowQuitWith_deliversValueToProfileTearsDownWholeAvatarFlow`. (Eski el-yazımı
  core fixture'ları zaten ownership semantiğindeydi — `PayAuthFlow : Route` kapsayanı subtype
  etmediği için bug'ı hiç tetiklememişti.)
- **Dashboard'ın suspend `goToPickSortForResult` çağrısı** — artık `DashboardViewModel.onIntent`'te
  `viewModelScope.launch` ile toplanır; VM scope'unda çalıştığı için config-change/process-death'te
  SESSİZCE DÜŞMEZ (eski `rememberCoroutineScope()` composable-scope hazard'ının MVI karşılığı çözülür;
  bkz. `DashboardViewModel.kt`'deki kod-içi yorum). Birden çok akıştan gelen kalıcı sonuç isteniyorsa
  `ProfileViewModel`'in `init`'teki launch+collect (stream) deseni tercih edilebilir — yukarıdaki
  "İki tüketim deseni" karşılaştırmasına bakın.
- **Üretilen API isimleri plan metniyle birebir eşleşmiyor** — bkz.
  `.superpowers/sdd/sample-s2-report.md` "Escalations" bölümü (örn. plandaki varsayılan
  `goToForgotPasswordForResult` yerine gerçek üretilen ad `goToForgotPasswordDialogForResult`).
- **Entry kaydı runtime-checked'tir, derleme-zamanı değil** — bir route için `provideXEntry`
  unutulursa (feature modülü `@Screen`/`@Dialog`/`@BottomSheet` composable'ını sağlamazsa) derleme
  YEŞİL kalır; hata yalnızca o route'un İLK gösterilmeye çalışıldığı anda, `GezginDisplay`'in
  `toNavEntry` eager lookup'ında açıklayıcı bir exception olarak ortaya çıkar.

## Çalıştırma

```
./gradlew :sample:app:installDebug   # cihaz/emülatörde showcase'i kurar
./gradlew :sample:navigation:test    # davranış testleri
./gradlew build                      # tüm repo (gezgin-core/processor/test + sample:* + shopr)
```
