# Changelog

Bu projenin tüm kayda değer değişiklikleri bu dosyada belgelenir.

Biçim [Keep a Changelog](https://keepachangelog.com/tr/1.1.0/)'e,
sürümleme [Semantic Versioning](https://semver.org/lang/tr/)'e dayanır.

## [Unreleased]

### Added

- **iOS desteği.** `gezgin-core` ve `gezgin-test` artık `iosArm64` ve `iosSimulatorArm64`
  hedeflerini yayınlıyor (`.klib`). `iosX64` **yoktur**: üstüne kurulduğumuz JetBrains
  Navigation 3 artefaktları onu yayınlamıyor, dolayısıyla Intel-Mac simülatörleri desteklenmez.
- Platforma bağımlı beş `expect` bildirimi için ortak bir `nonAndroidMain` kaynak kümesi: desktop
  ve iOS aynı `actual`'ları paylaşır, davranış sürüklenmesi mimari olarak imkânsız hale gelir.
- iOS'ta geri, `@NoBack` ve kök muafiyeti dahil Android ile aynı sözleşmeyi verir; kökte geri
  no-op'tur (Apple bir uygulamanın kendini sonlandırmasını yasaklar). Uygulama içi geri hem
  simülatör UI testleriyle hem de üç e2e akışıyla doğrulanmıştır.
- Compose UI testleri `nonAndroidTest`'e taşındı ve her PR'da iOS simülatöründe de koşuyor
  (`iosSimulatorArm64Test`).
- `sample/hello` paylaşılan bir KMP kütüphanesi (`sample/hello-shared`) ve ince bir Android
  host'u olarak ikiye bölündü; iOS host'u `sample/iosApp` olarak eklendi ve CI'da `xcodebuild`
  ile derleniyor.
- `maestro/run-ios-all.sh` ve dört iOS akışı (push/back, kenar-çekme, kökte geri, arka plan turu).
  Üçü her PR'da CI'da koşar: macOS runner'ında simülatör boot edilir, örnek uygulama kurulur ve
  akışlar sürülür. Maestro dağıtımı tam sürüme ve sha256'sına sabitlenmiştir.

### Known issues

- **iOS kenar-çekme geri jesti henüz çalışmıyor.** Jest bu yapılandırmada Compose'a geri olayı
  ulaştırmıyor: aynı jestle Apple'ın kendi uygulaması geri giderken, Compose hiyerarşisinin
  köküne konan her zaman açık bir `BackHandler` hiç tetiklenmiyor. Uygulama içi geri (üst bar,
  programatik) etkilenmiyor. Ayrıntı ve kanıt: `docs/gezgin-ios-support-spec.md` S-7.2.

### Changed

- Yayın (`release`/`snapshot`) ve imzalı yerel yayın doğrulaması artık macOS runner'da koşuyor —
  Apple hedefleri yalnız orada derlenir; Linux'ta bu job'lar başarılı görünüp iOS artefaktlarını
  sessizce atlardı.

## [0.3.0] - 2026-09-12

Kırıcı sürüm. Gezgin artık bir MVI tarzı dayatmıyor: ekranın container'ı, ViewModel'i, state
akışı ve yan-etki politikası uygulamaya geri verildi.

### Removed

- **`gezgin-mvi` artifact'i tamamen kaldırıldı.** Yayınlanan 4 modül 3'e indi
  (`gezgin-core`, `gezgin-processor`, `gezgin-test`).
- `GezginMvi<S, I, E>`, `GezginEffects`, `ObserveEffects`, `@MviViewModel`, `@EffectHandler`,
  `@TopBar`, `@BottomBar`, `@ExperimentalGezginMigrationApi`.
- DI algılama alt sistemi (`VmDiClassifier`) ve yalnız onu denetleyen `MV*` validasyonları.
  Gezgin artık Hilt/Koin/androidx ayrımı yapmıyor; VM'i uygulama kendi sağlayıcısında yaratıyor.
- Üretilen entry'lerdeki koşulsuz `Column { Column(Modifier.fillMaxWidth().weight(1f)) { … } }`
  sarmalayıcısı.
- `provideXEntry`'nin uygulama tarafından doldurulan resolver parametreleri.

- Route'larda (`@NavGraph` içindeki graph üyelerinde) `@Serializable` zorunluluğu kaldırıldı.
  Yalnızca route parametresi veya result türü olarak kullanılan enum'lar da annotation gerektirmez;
  kendi `@Serializable` türünü tanımlamayan graph modülü serialization Gradle plugin'ine ihtiyaç duymaz.

### Added

- `@ScreenWrapper` — content slot'u olan bir composable'ı ekran kökü yapar.
- `@ScreenSlot` — uygulamanın kendi annotation'ını slot marker'ı yapan meta-annotation. İşaretlenen
  annotation tam olarak bir `KClass<out Route>` parametresi bildirmelidir; diğerleri yok sayılır.
- `@FilledBy(marker)` — bir wrapper parametresini o marker'ın sağlayıcılarına bağlar.
- `gezgin.wrapperPackages` KSP seçeneği — bir bağımlılığa derlenmiş wrapper ve marker'ların
  paketlerini bildirir. KSP classpath'teki bildirimleri annotation'la sayamadığı için çok-modüllü
  kurulumda gereklidir; tek modüllü uygulamada gerekmez.
- `SW1`–`SW11` hata kataloğu.
- `SZ1`, serializer'ı bulunamayan route parametreleri ve result türleri için açık processor hatası.

### Migration

Ekran başına:

1. `GezginMvi` üst tipini kaldır; ViewModel ve state/intent/effect tiplerin olduğu gibi kalır.
2. Uygulamanın kendi temel tiplerini ve **bir** `@ScreenWrapper`'ı yaz — ekran başına değil, bir kez.
3. `@MviViewModel(R::class)` yerine DI'yı doğrudan çağıran bir `@ViewModelOf(R::class)` sağlayıcısı.
4. `@EffectHandler(R::class)` yerine `@Effects(R::class)` sağlayıcısı; gövde aynen kalır, typed
   navigator parametresi dahil.
5. `@TopBar`/`@BottomBar` yerine uygulamanın kendi marker'ları ya da doğrudan wrapper'ın `Scaffold`'u.

Effect sink'ini `MutableSharedFlow` ile değiştirme: `replay = 0`, abone yokken yayılan effect'i
düşürür ve örtülen bir Navigation 3 entry'si composition'dan tamamen çıkar. `Channel(UNLIMITED)`
tabanlı bir sink kullan (`sample/*/ui/*Mvi.kt` içindeki `EffectSink` örnektir).

Tasarım: `docs/superpowers/specs/2026-09-11-gezgin-screen-wrapper-design.md`.

## [0.2.0] - 2026-07-24

### Changed

- Kotlin 2.3 hattıyla uyumlu 12 Dependabot güncellemesi tek pakette uygulandı: KSP, Compose
  Multiplatform, Coroutines, Navigation3, lifecycle-navigation3, Compose UI testleri, Kover ve
  pinned GitHub Actions sürümleri yükseltildi.
- Desktop `NavDisplay`, Navigation3 1.2'nin liste tabanlı `sceneStrategies` API'sine geçirildi.
- CodeQL `init` ve `analyze` aynı immutable `v4.37.3` commit'ine sabitlendi.

### Deferred

- Kotlin 2.4.10, KSP'nin Kotlin 2.4 metadata desteği yayınlanana kadar ertelendi.
- AndroidX Lifecycle 2.11.0, compileSdk 37 ve AGP 9.1 KMP geçişiyle birlikte ele alınmak üzere
  ertelendi.

## [0.1.0] - 2026-07-22

### Added

- Route-explicit `@EffectHandler` artık opsiyonel `onIntent: (I) -> Unit` parametresi alabilir.
  Codegen bunu aynı route'un owner ViewModel'ındaki `vm::onIntent` ile bağlar; yanlış Intent tipi
  `MV23` ile derleme zamanında reddedilir. Böylece navigation result akışları Navigator'ı ViewModel'a
  vermeden strict MVI `result -> Intent -> effect` zincirine dönebilir.
- `io.github.sahsenvar` altında `gezgin-core`, `gezgin-processor`, `gezgin-mvi` ve
  `gezgin-test` için imzalı Maven Central yayınları, sources ve Dokka javadoc artefaktları.
- Eksik public API belgesini, Dokka uyarısını, API uyumsuzluğunu, format ve coverage
  gerilemesini durduran CI kapıları; tag tabanlı Central smoke ve GitHub Release akışı.

### Changed

- Tek MVI effect API'si route-explicit `@EffectHandler(route)` olarak sabitlendi.
- Geçici `TopBar`, `BottomBar` ve `BottomSheetDragHandleMode` API'leri
  `@ExperimentalGezginMigrationApi` ile açık opt-in gerektirir.
- Yayın toolchain'i Gradle 9.0.0 ve AGP 8.13.2'ye yükseltildi.

## [0.1.0-alpha03] - 2026-07-17

### Fixed

- Her `@NoBack` olmayan route için, başka declared edge bulunmasa bile tek-adımlık typed `back()`
  navigator'ı üretilir. `@NoBack` açık opt-out'tur; başka operation tanımlamayan `@NoBack` route
  navigator üretmez.

## [0.1.0-alpha02] - 2026-07-17

### Added

- ZAD geçişi için migration-only `BottomSheetDragHandleMode.Default/None`; `None` Material 3 host'a
  `dragHandle = null` iletir, özel handle consumer içeriğinde kalır. Kalıcı route-bound presentation/slot
  API'si V2'ye bırakılmıştır ve bu enum ileride değiştirilebilir veya kaldırılabilir.

## [0.1.0-alpha01] - 2026-07-17

İlk alpha. Compose Multiplatform için type-safe, annotation + KSP-codegen, state-as-data
navigasyon katmanı (Navigation 3 üzerinde). İlk önizleme core, processor ve MVI
artefaktlarını içeriyordu.

### Eklenenler

- **Type-safe navigasyon** — navigasyon grafiği bir `sealed interface` ağacı; her kaynak için
  tipli per-source navigator üretilir → tanımlanmayan hedefe gidiş **derlenmez**.
- **İleri-gidiş sözlüğü** — `@GoTo` / `@ReplaceTo` (push / replace).
- **Flow navigasyonu** — `@FlowGraph` + `@StartDestination` / `@BackToStart` / `@Quit` /
  `@QuitAndGoTo`; geri sözlüğü `@BackTo` / `@NoBack` / implicit `back()`.
- **Result passing** — `ResultRoute<T>` / `ResultFlow<T>` + `@GoForResult`; üçlü API
  (`launchX` tetik · `xResults` re-attach stream · suspend `goToXForResult` sugar) + tipli
  `backWithResult`. **PD-safety:** `xResults` stream re-attach yüzeyidir — VM recreate'te
  (config-change VE gerçek process-death) yeniden collect edilip kalıcı slot'tan teslim eder
  (PD-safe taşıyıcı). suspend `goToXForResult` yalnız process-ömrü içi await'tir (gerçek
  process-death'te continuation ölür → sonuç düşer); PD sınırını aşacaksan `launchX`+`xResults`
  kullan (cihazda `am kill` ile doğrulandı; sample tümüyle bu deseni kullanır).
- **4 entry kind** — `@Screen`, `@Dialog`, `@BottomSheet`, `@FullscreenModal` (modal = render
  varyantı olan normal back-stack entry'si).
- **MVI add-on** (`gezgin-mvi`, opsiyonel) — `@MviViewModel` / route-explicit `@EffectHandler` +
  `GezginMvi<S, I, E>` + codegen binder (`provideXEntry`) + DI-detection (Hilt/Koin, androidx
  fallback).
- **Fragment interop** — `@FragmentScreen` ile brownfield Fragment yaprakları
  (host `FragmentActivity`/`AppCompatActivity` OLMALI). Gerçek process-death'te `gezginArgs`'ın
  çalışması için `Application.onCreate()`'te bir kez `Gezgin.initFragmentInterop(gezginJson)`
  çağrılmalıdır (app-Json'u FragmentManager restore'undan önce kaydeder; cihazda doğrulandı).
- **State-as-data** — gözlemlenebilir `backStack: StateFlow` + `events: Flow`; process-death
  restore (`@Serializable` route ağacı); UI'sız test (`GezginTestNavigator`, `gezgin-test`).
- **KMP** — Android (stable) + desktop; iOS/Web JetBrains Nav3 portu alpha (§2.2).
- **KSP seçenekleri** — `gezgin.emitSerializers` (opt-out), `gezgin.emitTestAccessors` (opt-in).

### Bilinen sınırlamalar

- **Sekmeler / multi-backstack** ve **deep-link** V1'de YOK — bilinçli **V2** kalemleri
  (`@TabGraph` / `@DeepLink` bu sürümde yer almaz).
- **UI'sız test, çok-modül** — tipli `GezginTestNavigator.fromX()` erişimcileri yalnız
  `@NavGraph`'lar ile testler **aynı KSP round'unda** (tek-modül) üretilir. Kanonik çok-modül
  düzeninde (graph'lar `main`, testler ayrı modülün `test` source-set'inde) erişimciler üretilmez;
  o düzende `GezginTestNavigator.raw` (`@GezginInternalApi`) + `entryIdOf(route)` →
  `raw.xNavigator(entryId)` yolu kullanılır.
- **Cihaz doğrulaması** — riskli runtime davranışları (per-entry VM-store, process-death
  round-trip, predictive-back, modal iptal) gerçek cihaz/emülatörde henüz doğrulanmadı;
  bkz. [docs/gezgin-on-device-checklist.md](docs/gezgin-on-device-checklist.md).

[0.2.0]: https://github.com/sahsenvar/Gezgin/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/sahsenvar/Gezgin/releases/tag/v0.1.0
[0.1.0-alpha03]: https://github.com/sahsenvar/Gezgin/compare/v0.1.0-alpha02...v0.1.0-alpha03
[0.1.0-alpha02]: https://github.com/sahsenvar/Gezgin/compare/v0.1.0-alpha01...v0.1.0-alpha02
[0.1.0-alpha01]: https://github.com/sahsenvar/Gezgin/releases/tag/v0.1.0-alpha01
