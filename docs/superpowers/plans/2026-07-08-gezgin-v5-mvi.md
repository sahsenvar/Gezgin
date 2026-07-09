# Gezgin Faz 5 — MVI Add-on Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** `docs/gezgin-design.md` §10/§10.1/§14/§15'in tamamı — opsiyonel `:gezgin-mvi` modülü: `@ViewModel`/`@ScreenEffect` annotation'ları, `GezginMvi<out S, in I, out E>` sözleşmesi, `ObserveAsEvents`; codegen'in MVI-mode `provideXEntry` üretimi (stateless `@Screen(state, onIntent)` + `@ViewModel(Route::class)` eşleşmesi), DI-detection (Hilt/Koin + androidx fallback) ile default resolver, ve "bilinmeyen content param → ek resolver param" (Problem 2) mekanizması.

**Architecture:** Yeni `:gezgin-mvi` runtime modülü (gezgin-core'a `api` bağımlı, compose'lu — annotation+contract+ObserveAsEvents). **Codegen AYRI modül değil** — mevcut `:gezgin-processor` GENİŞLETİLİR: annotation FQN'leri string-eşleşmeli okunduğundan (mevcut desen — `gezgin-processor` `gezgin-core`'a bile compile-bağımlı DEĞİL, yalnız testte) `gezgin-mvi`'ye de compile-bağımlılık GEREKMEZ. `EntryModelReader` iki moda ayrılır: mevcut core-mode (route,nav) DEĞİŞMEZ; yeni MVI-mode (state,onIntent[,extra]) + eşleşen `@ViewModel` sınıfı okunur. S/I/E, VM'in `GezginMvi<S,I,E>` supertype'ından (Faz 2'nin `ResultRoute<T>`/`getAllSuperTypes` substitution desenini birebir tekrar eder).

**Tech Stack:** mevcut Kotlin/KSP2/KotlinPoet/kctfork zinciri. Yeni runtime bağımlılıklar (spike'ta KESİNLEŞTİRİLİR — coordinate'leri VARSAYMA): `androidx.lifecycle:lifecycle-viewmodel-compose` (JB muadili) — `viewModel()`/`viewModelFactory{}` için; `androidx.lifecycle:lifecycle-runtime-compose` (JB muadili) — `collectAsStateWithLifecycle()` için. DI-detection FQN'leri (Hilt `HiltViewModel`/`AssistedInject`/`Assisted`; Koin Annotations `KoinViewModel`/`InjectedParam`) — WebSearch ile doğrulanacak, varsayılmayacak.

## Global Constraints
- Spec kazanır; master plan Global Constraints geçerli. Faz 5 **opsiyonel add-on** — mevcut core-mode (`@Screen(route,nav)`) davranışı HİÇ değişmez; MVI-mode yalnız `@ViewModel` varlığında/composable imzasında devreye girer.
- **`@Screen` imzası seçer** (§10.1): composable'ın parametre şekli core-mode mu MVI-mode mu belirler — annotation'ın kendisi DEĞİŞMEZ (`@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal` aynı kalır).
- `@ViewModel(Route::class)` + VM'in `GezginMvi<S,I,E>` implement etmesi İKİSİ DE ZORUNLU (guardrail, yeni kod — ör. `MV1`); üçlü (`@ViewModel`/`@Screen`/`@ScreenEffect`) **aynı modülde** olmalı (Faz 3.4 §10.1 kuralının MVI-mode karşılığı).
- S/I/E VM'in `GezginMvi` supertype arg'larından okunur (content'ten türetme YOK — spec'in E-kaynağı sorununu çözen karar); content `(state,onIntent)` tipleri + `@ScreenEffect`'in `Flow<E>` tipi VM sözleşmesine karşı doğrulanır.
- DI-detection (B2/Problem 1, Faz 1 adjudication'ı geçerli): default resolver YALNIZ VM'in tüm assisted/injected ctor param'ları `{route, nav}` tipindeyse üretilir; sağlanamayan bir param varsa default YOK — `viewModel` parametresi zorunlu hâle gelir (kullanıcı override eder).
- Problem 2 (bilinmeyen content param → ek resolver param) YALNIZ MVI-mode content'inde uygulanır (spec metni `state`/`onIntent`/`sheetState` listesiyle sınırlı) — **core-mode'un SC3 hard-reject'i DEĞİŞMEZ** (kapsam kaymasını önlemek için bilinçli sınır).
- `sheetState: SheetState` param'ı (BottomSheet-kind MVI content'inde) rol-bazlı sağlanır — Problem-2 resolver'ı DEĞİL, Faz 4'ün `LocalGezginSheetState` mekanizmasıyla aynı yoldan (tutarlılık).
- `GezginEntry` wrapper tipi YOK (spec §10.1/§14 — tekrar eden ilke); `provideXEntry` yine `GezginEntryScope` extension'ı, Gezgin registry'sine kayıt.
- TDD; sık commit; her görev implementer + bağımsız reviewer + fix.

## Görev haritası

| # | Görev | Gate |
|---|---|---|
| 5.0 | `:gezgin-mvi` modül iskeleti (annotation'lar+`GezginMvi`+`ObserveAsEvents`) + **DI-detection & lifecycle-artefakt spike** (NAMED RISK: Hilt/Koin FQN'leri + `hiltViewModel()`'in Nav3 [Navigation-Compose değil] ile uyumu; `collectAsStateWithLifecycle`/`viewModelFactory` JB koordinat'ları) | iki platformda derlenen iskelet + API notları |
| 5.1 | `EntryModelReader` MVI-mode okuma: `@ViewModel` sınıfı + S/I/E extraction (ResultRoute deseni) + `MV1` guardrail + aynı-modül kuralı + content/effect tip doğrulama | model-dump/golden testleri (Faz 2/3 deseninde) |
| 5.2 | MVI-mode entry codegen: `provideXEntry(viewModel = <DI-detected default>)` + DI-detection default resolver (Hilt/Koin/androidx-fallback) + Problem 2 ek-resolver-param mekanizması + `@ScreenEffect` wiring | golden-text + (mümkünse) derleme testi; sample'da canlı kanıt |
| 5.3 | Sample: bir feature'ı (örn. `feature:profile` ya da yeni küçük bir ekran) MVI-mode'a çevir — androidx-fallback resolver ile (DI framework eklemeden; Hilt/Koin override'ı YORUM/README'de gösterilir, gerçek dependency eklenmez — kapsam kararı, implementer adjudicate edip raporlar) | sample assembleDebug + full repo yeşil |
| 5.4 | README/checklist güncelleme + final review + `main`'e merge | fable review Yes |

## Riskler
1. **`hiltViewModel()`/Hilt entry-point'lerinin Nav3 (androidx.navigation DEĞİL) ile uyumu** — spike'ın en kritik keşfi; uyumsuzsa Hilt default resolver'ı `viewModel(factory=...)` + Hilt'in ürettiği factory sınıfına (varsa) düz erişimle kurulur, ya da Hilt için "override zorunlu" (default yok) kararı alınır — dürüstçe raporlanır.
2. Koin Annotations'ın (`KoinViewModel`/`InjectedParam`) KSP-zamanı okunabilirliği (bunlar KENDİLERİ birer KSP-üretici annotation — Gezgin yalnız OKUR, üretmez; çakışma riski yok ama FQN'ler doğrulanmalı).
3. Dual-mode `EntryModelReader` ayrımının (core vs MVI) mevcut SC1–SC6 testleriyle çakışmaması (regresyon riski — Faz 3/4 testleri bozulmamalı).
4. `collectAsStateWithLifecycle` JB coordinate'i CMP sürüm ailesiyle (1.11.0) uyumlu mu — 3.0/4.0 spike'larındaki gibi sürüm-uyum sürprizi olabilir.
