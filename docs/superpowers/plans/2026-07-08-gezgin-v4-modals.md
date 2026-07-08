# Gezgin Faz 4 — Modal / Scene'ler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** `docs/gezgin-design.md` §7'nin tamamı — `@Dialog`/`@BottomSheet`/`@FullscreenModal` kind'larının GERÇEK overlay render'ı (Nav3 SceneStrategy): `DialogContract`/`BottomSheetContract` opsiyonel property interface'leri, `DialogSceneStrategy` + BottomSheet scene wiring, dismiss→Canceled, modal-root/dismissOnBackPress guard'ları, N8 dialog-üstü-dialog. Faz 3'te kind'lar registry'de metadata olarak duruyordu ama plain screen render ediliyordu — Faz 4 bunları overlay'e çevirir.

**Architecture:** Contract interface'leri gezgin-core commonMain'de (opsiyonel — route implement eder, `ResultRoute<T>` gibi); adapter (`toNavEntry`) route instance'ından contract'ı okuyup `NavEntry.metadata`'ya scene bilgisi yazar; `GezginDisplay` `NavDisplay`'e `sceneStrategy` geçirir. **NAMED RISK (Task 3.0 bulgusu):** `NavDisplay`'in `sceneStrategy`/`sceneStrategies` param'ı android(1.1.4: tekil? liste?)/desktop(alpha05) FARKLI imzalı — ortak alt-kümede değil → platform-özel `expect/actual` sarmalayıcı gerekebilir. 4.0 spike bunu çözer.

**Tech Stack:** mevcut JB Nav3 alpha05 (desktop) / androidx 1.1.4 (android) — `DialogSceneStrategy` (androidx.navigation3.scene, earlier-verified), material3 `ModalBottomSheet`/`SheetState`, `DialogProperties`. Desktop uiTest (compose.uiTest) + Android sample assembleDebug + on-device checklist.

## Global Constraints
- Spec §7 kazanır; master plan Global Constraints geçerli. V1 tek-stack, deep-link yok.
- Contract'lar **runtime değer** (§2.4): route instance'ından okunur (adapter), KSP'den DEĞİL. SABİT prop = interface property override (default'lu); KOŞULLU prop = route ctor param (serialize → PD-safe, zaten @Serializable).
- `dismissOnBackPress=true + @NoBack` çelişkisi: spec "derleme hatası" der ama `dismissOnBackPress` runtime değer → KSP okuyamaz. **Karar (4.1'de uygulanır + spec touch-up):** RUNTIME guard — adapter/scene kurulumunda `require(!(noBack && dismissOnBackPress))` açıklayıcı hata; §7 buna göre "kuruluş-zamanı runtime guard" olarak düzeltilir. (İmplementer bunu adjudicate eder; reviewer onaylar.)
- Modal-root guard KALIR (OverlayScene `require(overlaidEntries.isNotEmpty())` — modal tek entry olamaz gerçeği); GezginDisplay'deki mevcut guard + KDoc'taki "Faz 4'te gevşetilecek" notu **kaldırılır/düzeltilir** (gevşetilmiyor — modal genuinely root olamaz).
- dismiss (tap-outside/swipe/back-when-allowed) = pop + `ResultRoute` ise `Canceled` (mevcut `back()` yolu Canceled üretir — dismiss onu tetiklemeli).
- BottomSheet scene strategy core'a **bundle** (kütüphane re-ship eder); `sheetState: SheetState` opsiyonel content param'ı (register/codegen content imzası — Faz 5 MVI content-param mekanizması YOK, ama `sheetState` Gezgin-sağladığı rol-param; core-mode `@Screen`/`@BottomSheet` content'i `sheetState` alabilmeli — 4.2 çözer).
- Faz-3 davranışı (screen render, @NoBack, transition, R2) BOZULMAZ — SCREEN kind aynen kalır.
- TDD; sık commit; her faz görev implementer + bağımsız reviewer + fix.

## Görev haritası

| # | Görev | Gate |
|---|---|---|
| 4.0 | Scene API spike: NavDisplay sceneStrategy platform-imzaları (android/desktop), DialogSceneStrategy API, material3 ModalBottomSheet scene yaklaşımı — expect/actual gereksinimi netleşir | derlenen minimal scene-wire iskeleti (iki platform) + API notları |
| 4.1 | `DialogContract` + `FullscreenModalContract` interface'leri + adapter contract okuma → DialogSceneStrategy metadata; dismiss→Canceled; dismissOnBackPress+@NoBack runtime guard; modal-root guard düzeltme | desktop uiTest: dialog overlay render (arka görünür) + dismiss→pop; @NoBack+dismissOnBackPress guard testi |
| 4.2 | `BottomSheetContract` + BottomSheet scene strategy (core bundle) + `sheetState` content-param enjeksiyonu + swipe-dismiss→Canceled | uiTest: sheet render + sheetState erişimi + dismiss=Canceled |
| 4.3 | N8 dialog-üstü-dialog (stacked overlay) davranış testi + processor guardrail'leri (varsa KSP-okunabilir olanlar: `@QuitAndGoTo(modal)` uyarısı) + `FullscreenModal` | uiTest: [.., dialogB, dialogC] stack, back tek tek kapatır |
| 4.4 | Sample: ForgotPasswordDialog/EditNameDialog → gerçek `@Dialog`+DialogContract; FilterBottomSheet → `@BottomSheet`+sheetState; README güncelleme; on-device checklist (N8 scrim/predictive) ; final review + merge | sample assembleDebug + full repo yeşil; final review Yes |

## Riskler
1. **sceneStrategy platform-imza farkı** (4.0 spike — en büyük risk; expect/actual fallback).
2. Material3 BottomSheet'in Nav3 scene modeline oturması (JB `BottomSheetSceneStrategy` var mı yoksa el-yazımı mı — 4.0/4.2 keşfi).
3. dismiss→Canceled'ın result slot'una doğru akması (dismiss = programatik pop mu, scene-callback mı — mevcut `back()`/`backWithResult` yollarıyla hizalanmalı).
4. `sheetState` content-param enjeksiyonu (register imzası `@Composable (R) -> Unit` — sheetState ek param nasıl geçer; core-mode'da content imza esnekliği 3.4'te SC3 ile sınırlanmıştı → sheetState özel-rol param olarak eklenmeli).
5. Kctfork compose ICE (Faz 3'ten bilinen) → scene codegen'i yoksa contract okuma runtime → golden-text yerine uiTest/sample kanıtı.
