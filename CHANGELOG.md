# Changelog

Bu projenin tüm kayda değer değişiklikleri bu dosyada belgelenir.

Biçim [Keep a Changelog](https://keepachangelog.com/tr/1.1.0/)'e,
sürümleme [Semantic Versioning](https://semver.org/lang/tr/)'e dayanır.

## [0.1.0-alpha03] - Unreleased

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
navigasyon katmanı (Navigation 3 üzerinde). Yayınlanan artefaktlar: `dev.gezgin:gezgin-core`,
`dev.gezgin:gezgin-processor`, `dev.gezgin:gezgin-mvi`.

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
- **MVI add-on** (`gezgin-mvi`, opsiyonel) — `@MviViewModel` / `@ScreenEffect` +
  `GezginMvi<S, I, E>` + codegen binder (`provideXEntry`) + DI-detection (Hilt/Koin, androidx
  fallback).
- **Fragment interop** — `@FragmentScreen` ile brownfield Fragment yaprakları
  (host `FragmentActivity`/`AppCompatActivity` OLMALI). Gerçek process-death'te `gezginArgs`'ın
  çalışması için `Application.onCreate()`'te bir kez `Gezgin.initFragmentInterop(gezginJson)`
  çağrılmalıdır (app-Json'u FragmentManager restore'undan önce kaydeder; cihazda doğrulandı).
- **State-as-data** — gözlemlenebilir `backStack: StateFlow` + `events: Flow`; process-death
  restore (`@Serializable` route ağacı); UI'sız test (`GezginTestNavigator`, `dev.gezgin:gezgin-test`).
- **KMP** — Android (stable) + desktop; iOS/Web JetBrains Nav3 portu alpha (§2.2).
- **KSP seçenekleri** — `gezgin.emitSerializers` (opt-out), `gezgin.emitTestAccessors` (opt-in).

### Bilinen sınırlamalar

- **Sekmeler / multi-backstack** ve **deep-link** V1'de YOK — bilinçli **V2** kalemleri
  (`@TabGraph` / `@DeepLink` bu sürümde yer almaz).
- **UI'sız test, çok-modül** — tipli `GezginTestNavigator.fromX()` erişimcileri yalnız
  `@NavGraph`'lar ile testler **aynı KSP round'unda** (tek-modül) üretilir. Kanonik çok-modül
  düzeninde (graph'lar `main`, testler ayrı modülün `test` source-set'inde) erişimciler üretilmez;
  o düzende `GezginTestNavigator.raw` (`@GezginInternalApi`) + `entryIdOf(route)` →
  `raw.xNavigator(entryId)` yolu kullanılır. Ayrıca `dev.gezgin:gezgin-test` şu an yayınlanmaz.
- **Yayın** — henüz Maven Central'da değil; `maven-publish` yalnız iskelet (uzak repository /
  signing YOK) → şimdilik `publishToMavenLocal` + kaynaktan derleme.
- **Cihaz doğrulaması** — riskli runtime davranışları (per-entry VM-store, process-death
  round-trip, predictive-back, modal iptal) gerçek cihaz/emülatörde henüz doğrulanmadı;
  bkz. [docs/gezgin-on-device-checklist.md](docs/gezgin-on-device-checklist.md).

[0.1.0-alpha03]: https://github.com/sahsenvar/Gezgin/compare/v0.1.0-alpha02...v0.1.0-alpha03
[0.1.0-alpha02]: https://github.com/sahsenvar/Gezgin/compare/v0.1.0-alpha01...v0.1.0-alpha02
[0.1.0-alpha01]: https://github.com/sahsenvar/Gezgin/releases/tag/v0.1.0-alpha01
