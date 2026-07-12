# Gezgin — V1 on-device doğrulama: gap raporu ve yürütme planı

> Tarih: 2026-07-12. Amaç: **V2'ye (multi-backstack, deeplink) geçmeden önce V1'in cihaz-üstü
> doğrulamasının tam takır olması.** Bu rapor üç bağımsız denetimin (Maestro suite kalite denetimi;
> cihaz-davranış kapsama taraması; edge-case + sample-gap denetimi) sentezidir; en iddialı bulgular
> (K1 crash adayı, script'lerde trap yokluğu, `GezginState.cutIndex` `require`'ı) orkestratör
> tarafından kaynak kodda AYRICA doğrulandı. Rapor, işlerin **başka bir session'da yaptırılması**
> için yazıldı: her kalemde dosya yolu + somut değişiklik + kabul kriteri var.
>
> Kapsam dışı: multi-backstack, deeplink (bilinçli V2), iOS/desktop davranışları (checklist 6/8).

---

## 0. Yönetici özeti

Mevcut Maestro suite'i (11 flow, 11/11 PASS) **büyük ölçüde hak edilmiş** — görsel/kapsam-dışı
kalemler dürüstçe ayrılmış, assertion string'leri kaynakla birebir doğrulandı, hiçbiri bugün
typo-vacuous değil. Ancak:

1. **Üç sistemik zayıflık** suite'in en büyük yanlış-PASS vektörleri (Bölüm 1 / P0):
   - **S1 — Perturbation'lar doğrulanmıyor:** rotation ve "Don't keep activities" (DKA) uygulandı
     VARSAYILIYOR; sessizce no-op olurlarsa (yanlış cihaz, emülatör quirk'ü) tüm "survival"
     testleri boş geçer.
   - **S2 — DKA ≠ gerçek process death:** `always_finish_activities` Activity'yi yok eder ama
     **process yaşar** → statikler (`gezginFragmentJson` dahil) hayatta kalır. "PD-safe kanıtlandı"
     iddiaları bugün yalnız "activity-recreation-safe kanıtlandı" düzeyinde; Fragment'ın
     fresh-process restore branch'i **hiç koşulmadı**.
   - **S3 — Eşleşmemiş negatif assertion'lar + trap'siz script'ler:** üç runner'da da `trap` yok
     (0/3 — doğrulandı); yarıda kesilen bir koşu cihazda `always_finish_activities=1` /
     auto-rotate kapalı bırakır. Birkaç `assertNotVisible`, pozitif çifti olmadığı için bir
     string-rename ile kalıcı-yeşil vacuous'a döner.
2. **İki gerçek kod-tarafı aday** (Bölüm 2 / P1) — cihazda repro + tasarım kararı gerekiyor:
   - **K1 (crash adayı):** `@ReplaceTo(clearUpTo=…)` butonuna çıkış animasyonu penceresinde çift
     tık → ikinci çağrıda hedef stack'te yok → `GezginState.cutIndex`'in
     `require(i >= 0)` fırlatır (kaynakta doğrulandı) → main-thread `IllegalArgumentException`.
   - **K2 (stuck adayı):** `@QuitAndGoTo` çift tık → duplicate `@NoBack` Welcome entry.
3. **En büyük kapsama delikleri** (Bölüm 3 / P2): **SignUpFlow + Welcome SIFIR Maestro kapsamı**
   (`@BackToStart`, `@Quit`, `@QuitAndGoTo`, `@NoBack` Welcome, OnAppear one-shot efekt — en büyük
   test-edilmemiş annotation yüzeyi); **modal açıkken PD**; **nested ResultFlow (AvatarFlow→ZoomFlow)
   cihazda hiç koşulmadı** (PD'li/PD'siz); **root-back → `finish()` kanıtsız**; **fragment
   yaprağından sistem-back test edilmedi**; rotation yalnız Settings'te.
4. **Sample eklemeleri** (Bölüm 4 / P3): 6 kalem (a–f) hazır taslaklı; en değerlisi (c) —
   checklist madde 7'yi kalıcı-manuel olmaktan çıkarıp **rebuild'siz otomatikleştiren** debug
   corrupt-state kancası.
5. Görsel kalemler (Bölüm 5) insan gözünde kalıyor; screenshot-arşiv önerisiyle.

Önerilen sıra: **P0 → P1 → P2 → P3 → P4 (insan-gözü seansı + checklist kapanışı)**. P0+P1 bitmeden
"V1 on-device tam takır" DENMEMELİ: bugünkü yeşilin bir kısmı doğrulanmamış perturbation üstünde
duruyor ve K1 gerçek bir kullanıcı çift-tıkıyla tetiklenebilir.

---

## 1. P0 — Mevcut suite'i dürüstleştir (yalnız `maestro/` + `docs/`; kütüphane/sample kodu değişmez)

Sıralı iş listesi (ilk 4'ü yanlış-PASS sınıfını öldürür):

| # | İş | Dosya | Somut değişiklik |
|---|---|---|---|
| P0.1 | **Perturbation'ları doğrula** | `maestro/run-04-process-death.sh`, `run-14-settings-mvi.sh`, `run-15-fragment-pd.sh` | DKA sonrası `[ "$(adb shell settings get global always_finish_activities)" = "1" ] \|\| fail=1`; rotation sonrası `adb shell dumpsys window displays \| grep -q 'rotation=1' \|\| fail=1` (geri dönüşte `rotation=0`). |
| P0.2 | **Gerçek PD varyantı** (`am kill`) | `run-04`, `run-15` | HOME sonrası `pid1=$(adb shell pidof -s $PKG)`; `adb shell am kill $PKG`; relaunch sonrası `pid2` al, `[ "$pid1" != "$pid2" ]` şartı. DKA reçetesi ikinci (ucuz) case olarak kalsın. Bu, run-15'te Fragment'ın **fresh-process** restore branch'ini (bkz. `gezgin-core/.../fragment/FragmentBinding.android.kt:24-31`; statik `gezginFragmentJson` `FragmentRouteBundle.android.kt:37`) İLK KEZ gerçekten koşturur. |
| P0.3 | **`trap` cleanup** | 3 runner | `trap 'adb shell settings put global always_finish_activities 0; adb shell settings put system user_rotation 0; …' EXIT` — önceki değerleri yakala ve geri yükle (`accelerometer_rotation` dahil; bugün kalıcı 0 bırakılıyor). |
| P0.4 | **Checklist Durum düzeltmeleri** | `docs/gezgin-on-device-checklist.md` | (i) Madde 4 + 15(b): "PD-safe kanıtlandı" → "activity-recreation-safe kanıtlandı; gerçek cold-process PD am-kill varyantıyla koşulacak" (P0.2 bitince geri güncelle). (ii) **Madde 12 overclaim:** final-state assertion'ları hide→result SIRASINI gözleyemez — "sırası doğrulandı" ifadesini "result-delivery + closed-state doğrulandı; sıra assertion'la ölçülemedi" yap (ya da runner'da `Log.d` timestamp probe ekle). (iii) Madde 2'nin "WelcomeScreenRoute'un forward-edge'i yok" cümlesi BAYAT — `continueToDashboard` `@ReplaceTo` var (`sample/navigation/.../HomeGraph.kt:50`). (iv) Madde 14'ün "MutableSharedFlow replay=0" mekanizma metni bayat — sample VM'lerinin tümü Channel-tabanlı `GezginEffects` kullanıyor (davranış aynı; metni güncelle). |
| P0.5 | **10a dış-tık kalibrasyonu** | `maestro/app-10a-forgot-dialog-canceled.yaml` | `50%,6%` noktası scrim'e ulaşmıyorsa test vacuous. 10b'nin kanıtlı noktasını (`50%,4%`) kullan → 10b pozitif kontrol olur; yoruma kalibrasyon bağımlılığını yaz; opsiyonel ikinci nokta `50%,90%`. |
| P0.6 | **14c back-guard deliği** | `maestro/app-14c-logout.yaml` | Back sonrası yalnız 2 NotVisible var; bozuk `clearUpTo` Profil'e düşürse yine PASS. Ekle: `assertNotVisible "Profil: Gezgin Kullanıcı"` + `"Koyu tema"`; sonra pozitif çapa: `launchApp` (clearState YOK) + `assertVisible "Giriş yap"` + `assertNotVisible "Çıkış yap"` → kalıcı stack `[Login]` VE root-back-finish kanıtına yaklaşım. |
| P0.7 | **Negatifleri eşle** | `app-13-fullscreen-modal.yaml`, `app-10c-filter-sheet.yaml` | 13: modal açılmadan ÖNCE `assertVisible "Panoya dön"` (occlusion proxy'sinin silahlanması). 10c: sheet AÇIKKEN `assertVisible "Panel — sıralama: RELEVANCE"` (kısmi-overlay altında Maestro'nun alt içeriği raporladığının pozitif kontrolü → 13'ün proxy'sini de sağlamlaştırır). |
| P0.8 | **app-03 transition yarışı** | `maestro/app-03-r2-vmstore.yaml` | Tap sonrası `waitForAnimationToEnd` + `assertNotVisible "…sayacı: 2"`; Back sonrası `assertNotVisible "…sayacı: 3"`. (Bugün ayırt etme yükü sessizce yalnız 3. adımda.) |
| P0.9 | **run-all preflight + cihaz pinleme** | `maestro/run-all.sh` | `adb get-state`, iki paket için `pm path` kontrolü, birden çok cihazda `ANDROID_SERIAL` zorunluluğu + maestro `--device`. |
| P0.10 | **Jest-nav önkoşulu (app-11)** | `run-all.sh` ya da yeni runner | `adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural` (sonra eski overlay'i geri yükle); README'ye "gesture nav zorunlu" notu. 10c swipe'ını element-çapalı yap: `swipe: {from: {text: "Sırala (şu an: RELEVANCE)"}, direction: DOWN}`. |
| P0.11 | Küçükler | çeşitli | shopr-01: her Back sonrası `assertNotVisible "Ödemeyi tamamla"` + Back#2 sonrası `"Order placed:.*"` tekrarı. 10b: Toast assert'ünü (`"Ad güncellendi"`) `tapOn "Kaydet"`in HEMEN arkasına al (2 sn'lik LENGTH_SHORT penceresi). 04b: restore edilen stack'in ORTA katmanlarını da kanıtla — OrderPlaced'te `"Feed'e dön"` tıkla → `assertVisible "Kataloğa git"` (`@BackTo(Feed)` restore'lu stack'te Feed'i gerektirir). |

**Kabul kriteri (P0):** run-all yeşil; herhangi bir runner yarıda kesilince cihaz ayarları eski
haline dönüyor; `am kill` varyantında pid değişimi asserted; checklist Durum satırları yeni
gerçekle birebir.

---

## 2. P1 — Kod-tarafı adaylar: cihazda repro + karar

### K1 — Çift-tık `@ReplaceTo(clearUpTo=…)` → crash adayı  **[EN YÜKSEK ÖNCELİK]**
- **Mekanizma (kaynakta doğrulandı):** `logout = @ReplaceTo(Login, clearUpTo=Dashboard, inclusive)`
  (`sample/navigation/.../ProfileGraph.kt:35-40`). İlk çağrı Dashboard'ı stack'ten kaldırır; çıkış
  animasyonu (~300 ms; Settings'te özel slideOut, `ProfileGraph.kt:43-47`, pencereyi büyütür)
  sırasında ikinci tık → `GezginState.cutIndex`
  (`gezgin-core/src/commonMain/kotlin/dev/gezgin/core/GezginState.kt:41-44`)
  `require(i >= 0) { "clearUpTo target is not on the stack" }` fırlatır
  (`RawNavigator.kt:208`'deki `resultingRootAfterReplace` üzerinden mutasyon-öncesi bile) →
  yakalanmamış `IllegalArgumentException`, uygulama çöker. `@GoTo` (singleTop dedupe,
  `GezginState.kt:9-11`) ve `@GoForResult` (slot dedupe, `RawNavigator.kt:331-342`) korumalı;
  **`@ReplaceTo`/`@QuitAndGoTo`'da hiçbir dedupe yok.**
- **Repro planı:** Settings'te "Çıkış yap"a `tapOn` ×2 ardışık (gerekirse `adb shell input tap`
  ×2 ~100 ms arayla, koordinatı `dumpsys`/maestro hierarchy'den al). Crash'i
  `adb logcat -d -b crash -t 50` ile yakala. Pencere tutturulamazsa `monkey` smoke (P2.9) da
  büyük olasılıkla bulur.
- **Karar seçenekleri (repro sonrası):** (1) kütüphane toleransı — `replaceTo`/`quitAndGoTo`
  çağrısında hedef yoksa `backTo`'nun zarif `BackToTargetMissing` (`RawNavigator.kt:227-229`)
  desenine benzer no-op/event; (2) navigasyon-sürerken-yoksay (transition-aware debounce);
  (3) yalnız dokümante et (fail-loud bilinçli). Öneri: (1) — kullanıcı çift-tıkı crash
  nedeni olmamalı; `BackToTargetMissing` emsali zaten var.

### K2 — Çift-tık `@QuitAndGoTo` → duplicate `@NoBack` Welcome (stuck adayı)
- **Mekanizma:** `SignUpFlow.kt:26` `@QuitAndGoTo(WelcomeScreenRoute)`. İkinci çağrıda top=Welcome,
  `flowPath` boş → teardown atlanır, sonra `navigate(route, singleTop=false)`
  (`RawNavigator.kt:391`) → **ikinci `@NoBack` Welcome entry**; back stale entry'de yutulur
  (`GezginDisplay.kt:150-160`) → kullanıcı `continueToDashboard` sonrası artık Welcome'a
  sıkışabilir.
- **Repro:** SignUp akışında Terms kabul butonuna çift tık (app-16 flow'una varyant olarak ekle).
- **Karar:** K1 ile aynı ailede tek çözüm (quit-family çağrılarına idempotens) muhtemelen ikisini
  birden kapatır.

### K3 — `singleTop=false` çift push (bilinçli ama görünür davranış)
`goToRelated` (`sample/navigation/.../HomeGraph.kt:25`, `@GoTo(singleTop=false)`) çift tıkta iki
entry push'lar — tasarım gereği, crash değil; cihazda gözle + README/by-example'a bir cümle
"singleTop=false edge'lerde çift-tık iki entry üretir; istemiyorsan singleTop bırak" notu yeterli.

**Kabul kriteri (P1):** K1/K2 cihazda repro edildi ya da edilemediği kanıtlandı; karar verildi ve
uygulandıysa regresyon testi (unit + maestro çift-tık flow'u) eklendi.

---

## 3. P2 — Yeni kapsama flow'ları (sample kodu DEĞİŞMEDEN yazılabilir)

Öncelik sırasıyla; hepsi mevcut sample UI'sıyla erişilebilir (erişilebilirlik kod düzeyinde
doğrulandı):

| # | Flow | Kapatır | Adım özeti |
|---|---|---|---|
| P2.1 | `app-16-signup-flow.yaml` | **SignUpFlow + Welcome sıfır kapsamı**: `@GoTo`(flow'a giriş), `@BackToStart` (`SignUpFlow.kt:24`), `@QuitAndGoTo` (`:26`), Welcome `@NoBack`, `continueToDashboard` `@ReplaceTo`, `WelcomeViewModel.kt:28-32` OnAppear one-shot efekt | Login → "Kayıt ol" → Credentials → ProfileInfo → `@BackToStart` ile başa dön (assert Credentials) → tekrar ilerle → Terms kabul (`@QuitAndGoTo`) → Welcome (assert; Back ×2 no-op — `@NoBack`) → "Devam" (`@ReplaceTo`) → Dashboard; Back → Dashboard'da kal (Welcome'a dönmez). K2 çift-tık varyantı ayrı flow. |
| P2.2 | `app-17-avatar-flow.yaml` + `run-17-avatar-pd.sh` | **Nested ResultFlow cihazda ilk kez**: AvatarFlow→Crop→Zoom (`sample/navigation/.../AvatarFlow.kt:12-31`), `quitWith` sahiplik (ZoomFlow değil AvatarFlow biter — TopologyCodegen ownership fix'inin cihaz kanıtı); PD-varyantında Zoom'da PD → relaunch → "kareyi kullan" → Profile `Avatar: zoomed://frame` assert (flowPath + slot restore, `GezginKey.kt:12`, `RawNavigator.kt:253-271`) | Profil → avatar seç → PickSource → Crop → Zoom; PD reçetesi (P0.2'nin am-kill'li hali) ortada uygulanır. |
| P2.3 | `run-18-modal-pd.sh` (+ 2 part-flow) | **Modal açıkken PD**: modal entry'nin kendisi + bekleyen `@GoForResult` slotu restore (bugün run-04 yalnız düz ekran) | sample:app — EditNameDialog AÇIKKEN PD → relaunch → dialog yeniden görünür assert → "Kaydet" → Value teslim ("Profil: Yeni Ad"). İkinci case: FilterSheet ya da ItemImageViewer ile tekrar. |
| P2.4 | `app-19-fragment-back.yaml` | **Fragment yaprağından sistem-back** (bugün yalnız `@BackTo` butonu test edildi — app-15b) | Dashboard → Help → `pressKey: back` → assert Dashboard (Gezgin pop; FM pop değil). |
| P2.5 | Rotation genişletmesi (`run-20-rotation-matrix.sh`) | Rotation yalnız Settings'te; **dialog/sheet/fullscreen-modal/fragment açıkken rotation** + `backWithResult` hemen ardından rotation (slot-across-recreation, `ResultBus.kt:29-37`) | Her modal kind'da: aç → rotate (P0.1'in doğrulamalı reçetesi) → hâlâ açık assert → sonuçla kapat → teslim assert. Fragment: Help'te rotate → "Konu: navigasyon" + "Panoya dön" çalışır. |
| P2.6 | `app-21-double-tap.yaml` | `@GoForResult` slot-dedupe cihaz kanıtı (çift tık → TEK dialog); `@GoTo` singleTop dedupe (çift tık → tek entry: 1 Back yeter); K3 gözlemi | "Şifremi unuttum" ×2 → tek Back ile Login'e dön; ItemDetail "İlgili ürün" ×2 (singleTop=false) → 2 Back gerekir (bilinçli davranışın pini). |
| P2.7 | `app-22-ime-dialog-back.yaml` | IME açıkken dialog'da sistem-back (1. back IME'yi kapatır, 2. back dialog'u dismiss eder; Canceled TEK teslim — `RawNavigator.kt:437-453` settleRemoved) | ForgotPassword → text field'a tap (IME açık) → Back → dialog hâlâ açık assert → Back → "Sıfırlama iptal edildi". |
| P2.8 | `shopr-05-forcestop-fresh.sh` | `am force-stop` → cold start FRESH olmalı (bayat restore yok) — 1 satırlık sanity | Derin stack → `am force-stop` → launch → assert Feed (Payment DEĞİL). |
| P2.9 | `run-23-monkey-smoke.sh` | Rastgele-girdi stabilite smoke (K1'i de büyük olasılıkla yakalar) | `adb shell monkey -p dev.gezgin.sample.app --pct-syskeys 0 -s 42 500`; PASS = `logcat -b crash` boş. İki app için de. |
| P2.10 | `app-24-notifications-sheet.yaml` (S, düşük öncelik) | `NotificationsBottomSheetRoute` (`ProfileGraph.kt:59`, `ResultRoute<NotificationLevel>`) — hiçbir flow'da yok; sonuç-tipi matrisinin son deliği | Profil → bildirim tercihi sheet'i → seç → teslim assert; swipe → Canceled. |

Sonuç-tipi kapsamı bilgisi: `Boolean` (10a), `String` (10b), `SortOrder` (10c), `OrderId` (04b)
cihazda kanıtlı; `NotificationLevel` P2.10 ile kapanır; sample'da `Int` builtin edge yok (kctfork
testlerinde pinli — cihaz kalemi açmaya değmez).

**Kabul kriteri (P2):** P2.1–P2.5 zorunlu (release-blocker sınıfı restore/annotation kapsamı);
P2.6–P2.10 güçlü-tavsiye. Hepsi run-all'a eklenir (P2.2/2.3/2.5/2.8/2.9 runner olarak).

---

## 4. P3 — Sample eklemeleri + flow'ları (kütüphane kodu değişmez)

| # | Kalem | Boyut | Özet |
|---|---|---|---|
| P3.a | **N8 stacked dialog-over-dialog** (checklist 5/9'un fonksiyonel yarısı) | S | `ProfileGraph.kt`'ye `ConfirmResetDialogRoute : ProfileGraph, ResultRoute<Boolean>, DialogContract`; `EditNameDialogRoute`'a `@GoForResult(…, name="confirmReset")`; yeni `sample/feature/profile/.../dialog_confirm_reset/ConfirmResetDialog.kt`; EditName'e "Adı sıfırla" butonu. **"Vazgeç"/"Kaydet" metinlerine DOKUNMA** (app-10b bağımlı). Flow: dialog-üstü-dialog aç → Back LIFO (üstteki kapanır, alttaki açık) → Evet → alan temizlenir. |
| P3.b | **Modal-over-`@NoBack`** (checklist 2'nin açık deliği) | S | shopr `ShopGraph.kt`'ye `OrderDetailsDialogRoute(orderId)` + OrderPlaced'e edge; `dialog_order_details/` + buton. Flow (`shopr-02-noback-modal.yaml`): OrderPlaced → dialog aç → Back → dialog kapanır (dismissOnBackPress), OrderPlaced kalır → Back ×2 yutulur (hâlâ OrderPlaced) → "Feed'e dön". |
| P3.c | **Kalıcı debug corrupt-state kancası** (checklist 7'yi otomatikleştirir — REBUILD'SİZ) | M | Yalnız sample-side: `sample/shopr/.../debug/CorruptingSaveableStateRegistry.kt` — delegating `SaveableStateRegistry`, `consumeRestored` dönüşünde `{"keys"` ile başlayan String'i `"{corrupted"` yapar; `MainActivity`'de `BuildConfig.DEBUG && intent.getBooleanExtra("corrupt_state", false)` ise `CompositionLocalProvider(LocalSaveableStateRegistry provides …)`. Runner `run-07-corrupt-state.sh`: derin stack → PD → `am start … --ez corrupt_state true` → assert **Feed (fresh)** + `logcat -b crash` boş. Opsiyonel mod-2: yalnız `payloadJson`'ı boz → `adoptRestored` slot-decode catch'ini (`RememberNavigator.android.kt:84-91`) hedefler. |
| P3.d | **B2 — contract KDoc + checklist reword** | S | Canlı `dismissOnClickOutside` KÜTÜPHANECE İMKANSIZ (yapısal): props entry-build anında BİR KEZ çözülür (`EntryAdapter.kt:143-151`, `remember(keys, transitions)` içinde `GezginDisplay.kt:100-102`) → open-time sabiti. `gezgin-core/.../Contracts.kt`'ye tek cümle KDoc ("dismiss/layout props entry kurulurken bir kez okunur; canlı state değildir"); checklist 10 not'unu buna göre sadeleştir. V2-notu: canlı props istenirse `resolveDialogProperties`'i scene content lambda'sına taşımak — bilinçli V1-dışı. |
| P3.e | **B1 — AppNavBehaviorTest gerçek yolu yansıtsın** | S | `sample/navigation/src/test/.../AppNavBehaviorTest.kt:57-70`: mevcut testi `logoutFromRawPushedStack_stacksASecondLoginEntry` diye yeniden adlandır (raw-push semantiği pini olarak KALSIN) + kardeş test `logoutAfterRealLoginSuccess_leavesSingleLoginEntry`: `fromLogin().loginSuccess()` → … → `fromSettings().logout()` → `assertEquals(listOf(LoginScreenRoute), backStack)` + güvenlik invariant'ı. Cihaz yarısı zaten app-14c'de. |
| P3.f | **Madde 17 tekrarlanabilir migration-swap** | M | `docs/patches/item17-help-fragment-to-screen.patch` (git-apply'lık: `HelpFragment` → `screen_help/HelpScreen.kt`, AYNI görünür string'ler; route/graph/`@BackTo`/`HelpNavigator` sabit) + `run-17-migration-swap.sh` (opt-in, run-all'a EKLENMEZ — suite'in gradle-çalıştırmama sözleşmesi): temiz-çalışma-ağacı guard'ı → apply → `:sample:app:installDebug` → **değişmemiş** `app-15a` + `run-15`'i koştur (aynı flow'ların geçmesi = eşdeğerlik kanıtı) → `git apply -R` + baseline reinstall. |
| P3.g | **testTag sertleştirmesi** | S-M | İki sample'da SIFIR `testTag`/`semantics` — tüm selector'lar görünür Türkçe metin + koordinat; her copy edit suite'i sessizce kırar. Kök `Modifier.semantics { testTagsAsResourceId = true }` + ~10 `testTag` (dialog yüzeyleri, tema switch'i, OrderPlaced konteyneri, ziyaret sayacı) → Maestro `id:` selector'ları. Özellikle dinamik-metinli ekranlar (ItemDetail, OrderPlaced regex'i, Welcome). |

**Kabul kriteri (P3):** her kalemin flow'u run-all'da (f hariç) yeşil; P3.a/b sonrası checklist
2/5/9-fonksiyonel kutuları güncellenir; P3.c sonrası madde 7 `[x] otomatik` olur.

---

## 5. P4 — Otomatikleştirilemeyenler: insan-gözü seansı (tek oturum, ~30 dk)

Kalan görsel kalemler — assertion'la ölçülemez, checklist'te dürüstçe ayrılmış:

1. **Madde 9:** scrim opaklığı / tek-katman z-order / iki-modal-arası flicker (P3.a stacked-dialog
   eklendikten SONRA bakılırsa N8'in görsel yarısı da aynı seansta kapanır).
2. **Madde 12 (animasyon yarısı):** sheet slide-down akıcılığı; programatik kapanışın animasyonsuz
   KESİLMEMESİ.
3. **Madde 1 + 11 (preview yarısı):** predictive-back preview frame'leri — `@NoBack`'te hiç
   başlamaması; modal üstünde alttaki ekran için başlamaması (API 34+, "Predictive Back
   animasyonları" dev-option AÇIK).
4. **Madde 16:** legacy Fragment `OnBackPressedDispatcher` bypass gözlemi (bilgilendirici; geçici
   callback ile istenirse).

Kolaylaştırıcı öneri: P2/P3 flow'larının kritik karelerinde `takeScreenshot` toplayan bir
`run-visual-evidence.sh` — insan tek klasörü gözden geçirir, karar kutucuklarını işaretler.

---

## 6. Sıralı yürütme planı (özet)

| Faz | İçerik | Büyüklük | Çıkış kriteri |
|---|---|---|---|
| **P0** | Suite dürüstleştirme (11 kalem) + checklist Durum düzeltmeleri | S-M (yalnız maestro/+docs) | run-all yeşil; perturbation'lar asserted; trap'ler var; iddialar gerçekle birebir |
| **P1** | K1/K2 cihaz repro + karar (+K3 doc-notu) | M (repro S; fix M) | crash adayları kapatıldı ya da bilinçli-dokümante; regresyon testli |
| **P2** | 10 yeni flow/runner (P2.1–P2.5 zorunlu) | M-L | SignUpFlow/nested-flow/modal-PD/fragment-back/rotation-matrisi cihaz-kanıtlı |
| **P3** | Sample eklemeleri a–g + flow'ları | M | checklist 2/5/7/9-fonk kapandı; B1/B2 düzeltildi; testTag sertleşmesi |
| **P4** | İnsan-gözü seansı + checklist final işaretleme | S (insan) | Görsel kalemler işaretli; "V1 on-device tam takır" beyanı |

Bağımlılıklar: P0.2 (am-kill reçetesi) → P2.2/P2.3 onu kullanır; P3.a → P4.1'in stacked-scrim
gözlemi; P1 kararı kütüphane değişikliği doğurursa P2.6 çift-tık flow'u regresyon testi olur.

## 7. Kaynakça / künye

- Denetimler: (A) Maestro suite kalite denetimi — 20 dosyanın tamamı satır satır okundu, tüm
  assertion string'leri `sample/` kaynağına greplendi, 10 "[x] Maestro" checklist satırı çapraz
  kontrol edildi; (B) cihaz-davranış kapsama taraması (kısmi — session limitine takıldı; ara
  bulguları bu rapora işlendi ve (C) ile çapraz doğrulandı); (C) edge-case + sample-gap denetimi —
  17 senaryo kod-vetli, 6 sample-ekleme taslağı. Orkestratör ayrıca şunları kaynaktan bağımsız
  doğruladı: `GezginState.cutIndex` `require`'ı (K1), üç runner'da `trap` yokluğu (0/3),
  `run-04`'ün perturbation-doğrulamasızlığı, sample'ların `GezginEffects` kullanımı,
  `SignUpFlow.kt`'de `@BackToStart`/`@QuitAndGoTo`, sonuç-tipi envanteri.
- İlgili dosyalar: `maestro/*`, `docs/gezgin-on-device-checklist.md`,
  `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/{GezginState,RawNavigator,ResultBus}.kt`,
  `gezgin-core/.../compose/{GezginDisplay,RememberNavigator,EntryAdapter,DialogScene,BottomSheetScene}.kt`,
  `gezgin-core/.../fragment/*.android.kt`, `sample/navigation/.../{AuthGraph,HomeGraph,ProfileGraph,SignUpFlow,AvatarFlow}.kt`,
  `sample/shopr/.../nav/ShopGraph.kt`.
