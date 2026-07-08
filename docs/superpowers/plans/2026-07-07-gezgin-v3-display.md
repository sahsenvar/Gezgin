# Gezgin Faz 3 — GezginDisplay (Nav3 Entegrasyonu) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** `docs/gezgin-design.md` §2.2/§4.2/§9/§12/§14'ün Faz-3 dilimi: Compose katmanı — `GezginDisplay` (Nav3 `NavDisplay` adapter'ı, contentKey=`GezginKey.id`), `rememberNavigator`, kind annotation'ları + `GezginEntryScope`/registry + `provideXEntry` codegen'i (Faz 2'den devir), `@NoBack` entry-içi handler, transition cascade ve Shopr mini-sample.

**Architecture:** `gezgin-core` CMP compose modülü olur (JB Nav3 alpha05 commonMain'de; Android target androidx 1.1.4'ü BOM uyumuyla alır). Davranış testi üç katman: (1) saf-JVM registry/adapter birimleri, (2) **desktop compose uiTest** (NavDisplay JB portu desktop'ta çalışır → cihazsız gerçek render/back döngüsü), (3) Android sample app derleme + on-device checklist (kullanıcıya işaretli bırakılır — kullanıcı seyahatte).

**Tech Stack:** Compose Multiplatform 1.11.0 (compose plugin) · `org.jetbrains.androidx.navigation3:navigation3-ui:1.0.0-alpha05` · `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0-alpha05` · navigationevent (NavigationBackHandler) · desktop `compose.uiTest` · sample: androidApp (minimal).

## Global Constraints
- Spec kazanır; master plan Global Constraints geçerli. **V1 tek-stack; deep-link yok.**
- `contentKey = GezginKey.id` (R2) — Nav3 `NavEntry` adapter'ında; kullanıcı `GezginKey`'i görmez.
- `@NoBack` = **entry-içi Gezgin-sahipli `NavigationBackHandler`** (M5′; GezginDisplay-flag DEĞİL); ekranın kendi BackHandler'ı daha içte kalır.
- Kuruluş guard'ları (§12): start ResultFlow üyesi olamaz; modal kind olamaz (kind'lar bu fazda geliyor); root flow'da quit → onRootBack.
- Faz-2 devri: `provideXEntry` = `GezginEntryScope` extension (Gezgin registry'sine; Nav3 `entry{}` DSL değil); kind annotation'ları (`@Screen(route=Route::class)` default-sentinel, `@Dialog/@BottomSheet/@FullscreenModal` — modal SCENE wiring Faz 4'te, annotation+registry alanı bu fazda).
- Faz-2 final-review devirleri: (a) GezginDisplay gezgin-core İÇİNDE (internal `keys` yeter); (b) **top-entry-drive**: entry content'i yalnız kendi `entryId`'siyle kurulmuş navigator alır; Nav3 yalnız top'u compose eder (modal overlay istisnası Faz 4 notu); (c) N10 name=-reserved-member diagnostiği; (d) NavigatorCodegen BackTo KDoc düzeltmesi; (e) serializers-ON derlemesi sample'da doğrulanır.
- **On-device kalemler** (M5′ LIFO, predictive, PD): instrumented/manuel checklist olarak hazırlanır, `docs/gezgin-on-device-checklist.md`'ye yazılır — kullanıcı dönünce koşar. Cihaz gerektirmeyen her şey desktop uiTest'le kapatılır.

## Görev haritası

| # | Görev | Gate |
|---|---|---|
| 3.0 | gezgin-core'a compose plugin + JB Nav3 deps; boş `GezginDisplay` iskeleti derlenir (android+jvm/desktop) | compile yeşil (NAMED RISK: alpha çözülmesi) |
| 3.1 | Kind annotation'ları + `GezginEntryScope`/registry + `GezginKey→NavEntry` adapter (contentKey=id) | saf-JVM registry testleri |
| 3.2 | `rememberNavigator` (rememberSaveable+SavedState+guard'lar) + `GezginDisplay` gövdesi (NavDisplay, decorators, onBack) | desktop uiTest: push/back/render döngüsü |
| 3.3 | `@NoBack` entry-içi handler + duplicate-value ayrı-VM-store testi (desktop uiTest) | @NoBack back-yutma + iki Detail(42) ayrı state testi |
| 3.4 | Entry codegen (`provideXEntry` — @Screen okuma, EntryCodegen) + N10/KDoc devirleri | golden-text + sample'da canlı |
| 3.5 | Transition cascade (§9: screen>graph>app → NavDisplay metadata) | desktop uiTest/golden |
| 3.6 | Shopr sample (androidApp; serializers-ON) + on-device checklist dokümanı + final review + merge | sample derlenir; checklist yazıldı; fable review Yes |

Detaylar dispatch'lerde (Faz-2 deseni). Riskler: JB alpha çözülme/churn (3.0 spike); desktop uiTest'te NavDisplay davranış farkları (bulguları checklist'e yaz); kctfork'ta compose YOK → entry codegen testleri golden-text + sample-canlı.
