# Gezgin — Cihaz-üstü (on-device) doğrulama checklist'i

> Faz 3 (`GezginDisplay`, Nav3 entegrasyonu) boyunca desktop `compose.uiTest` ile cihazsız doğrulanan
> davranışların üstüne, **gerçek bir Android cihaz/emülatör** gerektiren kalemler. Desktop uiTest
> Android'in `androidx.activity.compose.BackHandler`/sistem-back/predictive-back/gerçek
> `ComponentActivity` ömrünü sağlayamaz (bkz. `gezgin-core/src/androidMain/.../PlatformDisplay.android.kt`)
> — bu yüzden bu kalemler `.superpowers/sdd/progress.md`'nin Task 3.3 notunda "ON-DEVICE CHECKLIST"
> olarak ayrıldı ve kullanıcı seyahatten dönünce koşulacak. Kalemlerin **gerçek uygulaması İKİ ayrı
> sample app**'e dağılmıştır (bu dosya beş faz boyunca, her faz farklı bir implementer'la birikti; erken
> kalemler `sample:shopr`, sonraki kalemler multi-module `sample/` için yazıldı) — hangi maddeyi hangi
> app'te koşacağın hemen aşağıdaki **"İki ayrı sample uygulaması"** notunda KESİN olarak listelenmiştir;
> ayrıca her maddenin **Ön koşul** satırı hedef app'i tekrar söyler.
>
> Format: her madde **Ön koşul / Adımlar / Beklenen / İlgili spec § / Durum kutusu**.

---

## ⚠️ İki ayrı sample uygulaması — hangi maddeyi hangi app'te koşarsın

Bu checklist **iki farklı sample app**'e referans verir. Bir maddeyi koşmadan ÖNCE hangi app'i kurman
gerektiğini buradan doğrula (madde numaralarına göre kesin dağılım):

- **`:sample:shopr`** — erken, ayrı, ikincil tek-modül örnek (**Feed / Catalog / Product / CheckoutFlow /
  Payment / OrderPlaced** ekranları). Kur: `./gradlew :sample:shopr:installDebug`.
  **Kapsadığı maddeler: 1, 3, 4, 7.**
- **multi-module `sample/` (`:sample:app`)** — birincil, kapsamlı showcase (**Login / Dashboard /
  ItemDetail / ItemImageViewer / Profile / Settings / Help** ekranları). Kur: `./gradlew :sample:app:installDebug`.
  **Kapsadığı maddeler: 2, 9, 10, 11, 12, 13, 14, 15, 17.**
- **Cihaz GEREKMEYEN maddeler** (bilgilendirici / KMP-gelecek — bir app kurmaya gerek yok): **5** (madde 9'a
  yönlendirir), **6, 8, 16**.

> **Notlar:** (1) madde **2** `1–8` aralığında olmasına rağmen zaten `:sample:app`'i hedefler — Task 7.1
> denetiminin "madde 1–8 shopr, 9+ multi-module" özeti bir YAKLAŞIKLIKTI; kesin dağılım yukarıdaki gibidir.
> (2) `:sample:shopr` bu fazda bilinçli olarak DEĞİŞTİRİLMEZ/KALDIRILMAZ (yalnız hangi app'i açacağın
> netleştirildi) — shopr-hedefli maddeler multi-module sample'a TAŞINMAZ; ikisi ayrı örnek olarak kalır.

---

## 1. M5′ — `@NoBack` predictive-back / sistem-back LIFO sırası

`@NoBack` "Gezgin-sahipli, entry-içi `NavigationBackHandler`" olarak tanımlanır (plan §Global
Constraints, madde 3): `GezginNoBackHandler()` composable içerikten ÖNCE (OUTER) kurulur, ekranın
kendi `BackHandler`'ı (varsa) daha İÇTE/SONRA kaydolur — Android'in back-dispatcher'ı LIFO olduğu için
en son kaydolan (ekranın kendi handler'ı) önce tetiklenir. Bu sıralamayı desktop uiTest **test edemez**
(`GezginNoBackHandler` desktop actual'ı no-op) — yalnız gerçek `OnBackPressedDispatcher`/predictive-back
API'siyle gözlemlenebilir.

**Ön koşul:** `sample:shopr` cihaza/emülatöre kurulu (`assembleDebug` + install); Android 13+ (predictive
back gesture'ı için Android 14/API 34+ ideal, ama sistem geri tuşu her sürümde test edilebilir).

**Adımlar:**
1. `Feed` → `Catalog` → `Product("sku-42")` ekranına ilerle (bkz. `CatalogScreen` "Ürüne git" butonu).
2. `Catalog`'da "Checkout başlat" ile `CheckoutFlow`'a gir (`Cart` ekranı açılır).
3. `Cart`'ta "Ödemeye geç" → `Payment` ekranı.
4. `Payment`'ta "Ödemeyi tamamla" — bu `nav.quitWith(OrderId(...))` çağırır → flow kapanır, `Catalog`
   sonucu toplar (`checkoutResults`), `replaceToOrderPlaced(...)` ile **terminal** `OrderPlaced` ekranına
   geçer (`@NoBack`).
5. `OrderPlaced` ekranındayken: (a) gerçek sistem geri tuşuna / gesture'a bas — sonucu gözle. (b) eğer
   API 34+ ise, Ayarlar → Sistem → Geliştirici seçenekleri → "Predictive Back animasyonlarını
   etkinleştir" AÇIK iken de tekrarla (predictive-back preview frame'inin başlayıp başlamadığına dikkat
   et — `BackHandler(enabled=true)` bunu tüketmeli, preview görünmemeli).
6. Ardından ekrandaki "Feed'e dön" butonuna bas (`nav.backToFeed()`, declared `@BackTo`).

**Beklenen:**
- 5(a)/(b): geri tuşu/gesture **hiçbir şey yapmaz** — `OrderPlaced` ekranından çıkılmaz, önceki
  ekranlara (`Payment`/`Cart`) dönülmez (flow zaten `quitWith` ile kapandı, geri gidilecek declared bir
  şey de yok). Predictive-back preview animasyonu (API 34+) **başlamamalı** — `enabled=true` handler'ın
  gesture'ı en baştan tüketmesi gerekiyor (yarım preview + iptal DEĞİL, hiç preview yok).
- 6: `backToFeed()` çalışır, `Feed` ekranına döner (declared back — `@NoBack` yalnız implicit `back()`'i
  kapatıyor, declared `@BackTo`'yu değil, bkz. `docs/gezgin-by-example.md` §3 "Terminal ekran" notu).

**İlgili spec §:** plan Global Constraints madde 3 (M5′); `docs/gezgin-design.md` §4.2; kod:
`gezgin-core/src/androidMain/.../PlatformDisplay.android.kt` (`GezginNoBackHandler`),
`gezgin-core/src/commonMain/.../compose/GezginDisplay.kt` (`gezginOnBack` guard'ı).

**Durum:** [ ] Doğrulanmadı

---

## 2. Overlay-over-`@NoBack` etkileşimi (Faz 4 tamamlandı — sample topolojisi kısmi kapsıyor)

Faz 4 modal (dialog/bottom-sheet) SCENE wiring'i kuruldu (`DialogSceneStrategy` +
`GezginBottomSheetSceneStrategy`, `.superpowers/sdd/task-4.{1,2,3}-report.md`) ve `gezgin-core` bunu
desktop uiTest ile pinledi (dialog-üstü-dialog N8 dahil). `sample:app` artık `ForgotPasswordDialogRoute`/
`EditNameDialogRoute` (Dialog) ve `FilterBottomSheetRoute` (BottomSheet) kind'larını GERÇEK overlay
olarak render ediyor (bkz. `sample/README.md` "Faz 4 — gerçek modal overlay"). Ancak bu sample'ın
topolojisinde HİÇBİR modal, `@NoBack` bir entry'nin (`WelcomeScreenRoute`) ÜSTÜNDE açılmıyor —
`WelcomeScreenRoute`'un forward-edge'i yok, dolayısıyla "modal `@NoBack` entry'nin üstünde" senaryosu
bu topolojide DOĞRUDAN kurulamıyor. Genel modal-dismiss/geri-tuşu davranışları (tap-outside/swipe/back →
Canceled, predictive-back modal üstünde) aşağıdaki 9–12. maddelerle kapsanıyor.

**Ön koşul:** `sample:app` cihaza/emülatöre kurulu.

**Adımlar:** Login ekranından "Şifremi unuttum" ile `ForgotPasswordDialogRoute`'u aç; sistem geri
tuşuna/gesture'a bas.

**Beklenen:** Dialog kapanır (dismissOnBackPress=true varsayılan), `LoginScreenRoute` geri görünür ve
etkileşilebilir hale gelir — altındaki ekranın kendi `@NoBack`/`BackHandler` durumu YOKTUR (Login
`@NoBack` değil) — bu madde şu an yalnız "modal kapanınca alt ekran sağlıklı geri geliyor mu" genel
kontrolünü doğrular; `@NoBack`-altında-modal spesifik senaryosu hâlâ kapsam dışı (gelecekte
`sample:shopr` ya da bu sample'a yeni bir route eklenirse tekrar değerlendirilecek).

**İlgili spec §:** plan §Global Constraints madde 16 (Faz-2 final-review devri (b) top-entry-drive'ın
"modal overlay istisnası Faz 4 notu"); `docs/gezgin-design.md` §5/§7/§12 modal-kind guard'ı.

**Durum:** [ ] Doğrulanmadı (genel dismiss davranışı) / Kapsam dışı (spesifik `@NoBack`-altında-modal)

---

## 3. R2 — `ViewModelStore` decorator'ının android-only yarısı (per-entry VM store)

`rememberPlatformEntryDecorators()`'ın Android actual'ı `rememberViewModelStoreNavEntryDecorator()`
kullanıyor — bu, host `ComponentActivity`'nin gerçek `LocalViewModelStoreOwner`'ına ihtiyaç duyar; desktop
uiTest'te bu owner **garanti değil** (actual boş liste döner, bkz. `PlatformDisplay.kt` KDoc'u). Yani
"aynı route'un iki farklı `id`'li instance'ı (örn. iki kez açılan `Product(id=aynı)`) AYRI bir
`ViewModelStore`/VM alır mı" sorusunun **VM-store yarısı** yalnız gerçek Activity'de doğrulanabilir
(saved-state yarısı zaten Task 3.3'te desktop `StateRestorationTester` ile pinlendi — R2'nin TAMAMI değil,
yarısı).

**Ön koşul:** `sample:shopr` cihazda; bir `ViewModel` içeren ekran gerekiyor — sample'da hazır yok (sample
DI-agnostik/ViewModel'siz "core-mode" tasarlandı, bkz. plan deliverable 3), bu yüzden bu madde
**instrumented test** olarak (yeni bir minik androidTest, `sample:shopr`'a eklenebilir) ya da manuel
logcat/`Log.d`'li geçici bir `ViewModel.onCleared()`/`init` gözlemiyle koşulmalı.

**Adımlar (manuel, en hızlı yol):**
1. `ProductScreen`'e geçici bir `viewModel<SomeVm>()` (androidx.lifecycle) ekle, `init` bloğunda
   `Log.d("R2", "vm created for $route, hash=${this.hashCode()}")` yaz.
2. `Catalog`'dan `Product("sku-42")`'ye git → geri dön (`nav.back()`, `Product` bare route, ama demo için
   Product'a geçici bir `@GoTo(Product::class)` self-loop veya farklı id ile ikinci kez git) →
   `Product("sku-42")`'ye TEKRAR git (yeni bir `id` — Nav3 contentKey farklı çünkü GezginKey.id farklı,
   route DEĞERİ aynı olsa bile).
3. Logcat'i filtrele: `adb logcat -d -t 200 | grep R2`.

**Beklenen:** her navigasyonda **farklı** `hashCode()` loglanır — aynı route DEĞERİ (`Product("sku-42")`)
iki kez açılsa bile her `id` kendi VM store'unu (dolayısıyla kendi VM instance'ını) alır; process
death sonrası (bkz. madde 4) VM'ler kaybolur ama saved-state (Task 3.3 R2 saved-state yarısı) geri gelir.

**İlgili spec §:** `docs/gezgin-design.md` §2.1/§4.2 (R2 contentKey sözleşmesi);
`.superpowers/sdd/progress.md` Task 3.3 notu ("R2 duplicate-value testi discriminating-doğrulandı,
keysState fix load-bearing" — saved-state yarısı zaten kapandı, VM-store yarısı bu madde).

**Durum:** [ ] Doğrulanmadı

---

## 4. PD — Compose "Don't keep activities" dev-option recipe (process-death round-trip)

Desktop `StateRestorationTester` (Task 3.2/3.3) process-death'i **simüle** ediyor (aynı JVM içinde
encode/decode); gerçek bir **Activity'nin process'inin öldürülüp yeniden yaratılması** yalnız cihazda
gözlemlenebilir.

**Ön koşul:** `sample:shopr` cihazda kurulu; Geliştirici seçenekleri açık.

**Adımlar:**
1. Ayarlar → Sistem → Geliştirici seçenekleri → **"Etkinlikleri saklama"** ("Don't keep activities")
   seçeneğini **AÇIK** yap.
2. `sample:shopr`'ı aç, `Feed` → `Catalog` → "Checkout başlat" → `Cart` → "Ödemeye geç" → `Payment`
   ekranına kadar ilerle (birkaç seviye derin bir back stack + açık bir `ResultFlow` — en zorlu PD
   senaryosu: hem `keys` hem `pendingSlots` (bkz. `SavedState.kt`) restore edilmeli).
3. Home tuşuna bas (app'i arka plana at) — "Don't keep activities" açıkken Android Activity'yi HEMEN
   `onDestroy()` eder (process çoğunlukla canlı kalır ama Activity instance'ı gerçekten yok edilir,
   `rememberSaveable`'ın `SavedStateRegistry` round-trip'i gerçekten tetiklenir).
4. App switcher'dan `sample:shopr`'a geri dön.

**Beklenen:** `Payment` ekranı **aynı stack derinliğiyle** geri gelir (`Feed`→`Catalog`→`Cart`→`Payment`,
Nav3 `keysState` restore edilmiş `GezginKey.id`'lerle) — crash yok, `Feed`'e sıfırlanma yok. `Payment`'ta
"Ödemeyi tamamla"ya basınca akış hâlâ doğru tamamlanır (bekleyen `ResultBus` slotu da
`SavedSlot`'tan restore edildiği için `Catalog`'un `checkoutResults` collector'ı sonucu hâlâ alır —
`pendingSlots` PD-safe re-attach'in asıl kanıtı).

**Pas kriteri:** (a) crash/ANR yok, (b) stack derinliği/route'lar restore-öncesiyle birebir aynı, (c)
adım 4 sonrası `Payment`→tamamla→`Catalog` akışı hâlâ `NavResult.Value` ile sonuçlanıyor (Canceled değil).

**İlgili spec §:** `docs/gezgin-design.md` §1.10/§12 (PD); kod: `gezgin-core/.../compose/RememberNavigator.kt`
(`navigatorSaver`/`encodeNavigatorState`/`decodeNavigatorState`), `gezgin-core/.../SavedState.kt`.

**Durum:** [ ] Doğrulanmadı

---

## 5. N8 — artık uygulanabilir; cihaz-üstü doğrulaması madde 9'a taşındı

N8 (dialog-üstü-dialog stacked overlay) Faz 4.3'te `gezgin-core`'da desktop uiTest ile pinlendi
(`.superpowers/sdd/task-4.3-report.md`) — ilgili annotation/wiring artık codegen + runtime'da mevcut.
Bu sample'ın topolojisinde İKİ dialog'u ÜST ÜSTE açan declared bir edge yok (`ForgotPasswordDialogRoute`
ve `EditNameDialogRoute` birbirinden bağımsız, aynı anda açık değiller) — dolayısıyla N8'in gerçek
cihaz-üstü **z-order/scrim** doğrulaması aşağıdaki madde 9'da, bu sample'ın MEVCUT tek-dialog
senaryolarıyla (scrim/opacity görsel kontrolü) yapılıyor; gerçek İKİ-dialog-üst-üste senaryosu için
sample'a yeni bir route eklenmesi gerekir (kapsam dışı bırakıldı — mevcut coverage tablosu zaten
Dialog/BottomSheet contract desenlerini sergiliyor, bkz. `sample/README.md`).

**Durum:** [ ] Bkz. madde 9 (kısmi kapsam — stacked N8 senaryosu sample'da yok)

---

## 6. iOS / Desktop — geri tuşu/gesture farkları (bilgilendirici, Android-first V1)

V1 "tek-stack; Android-first" (plan §Global Constraints) — iOS/Desktop hedefleri şu an yalnız derleme
(compose plugin JB Nav3 alpha05 portu) düzeyinde var, `sample:shopr` bilinçli olarak **plain Android
application** (KMP değil, plan §3.6 deliverable 1). Yani bu madde bugün doğrulanacak bir "test" değil,
gelecekteki bir KMP sample için not:

- **iOS:** sistem "swipe-back" gesture'ı yok (iOS'un kendi edge-swipe pop davranışı `NavigationBackHandler`
  ile nasıl etkileşeceği JB Nav3 iOS portunun kendi sorumluluğu) — `@NoBack`'in iOS'ta gerçek bir
  gesture'ı yutup yutamayacağı doğrulanmamış.
- **Desktop:** ne sistem-back ne predictive-back kavramı var (`GezginNoBackHandler` desktop actual'ı
  no-op, bkz. `PlatformDisplay.kt`) — `@NoBack`'in desktop'taki TEK davranışsal karşılığı
  `gezginOnBack`'in `NavDisplay.onBack` guard'ı (klavye/mouse ile tetiklenen bir "geri" eylemi varsa o
  kapanır; işletim sistemi seviyesinde bir şey yutulmaz çünkü öyle bir şey yok).

**İlgili spec §:** plan §Tech Stack ("Android target androidx 1.1.4'ü BOM uyumuyla alır"); §Architecture
("davranış testi üç katman... (3) Android sample app derleme + on-device checklist").

**Durum:** [ ] Bilgilendirici — V2/KMP sample'a kadar aksiyon gerekmiyor

---

## 7. Uygulama güncellemesi sonrası restore — bozuk state → fresh fallback (final-review, Important 1)

`decodeNavigatorStateOrNull` (bkz. `RememberNavigator.kt`) bir uygulama güncellemesi (yeni `SavedState`
şeması/serializer değişikliği) sonrası eski cihazlarda kalmış PD-kaydedilmiş state'i decode ederken
`SerializationException`/`IllegalArgumentException` fırlatırsa `null` döner → `rememberSaveable` `start`'tan
fresh kuruluşa düşer (crash-loop DEĞİL). Bu birim testle (`RememberNavigatorSaverTest`) pinlendi ama
GERÇEK bir eski-şema `SharedPreferences`/`Bundle` state'i cihazda üretmek pratik değil — bu yüzden
cihaz-üstü doğrulaması manuel/simüle:

**Adımlar (simüle, en pratik yol):** `sample:shopr`'ı kur, birkaç ekran ilerle, "Don't keep activities"
AÇIK, arka plana at (madde 4'teki gibi PD tetikle) — ama araya (geçici, yalnız test için) bir kod
değişikliği koy: `navigatorSaver`'ın `restore` lambdasına GEÇİCİ olarak kaydedilen string'i bozan bir
satır ekle (örn. `encoded.reversed()`), rebuild + reinstall (kaydedilen `SavedStateRegistry` içeriği
eski/uyumsuz kalır), app'e geri dön.

**Beklenen:** crash/ANR YOK — `Feed`'e (fresh `start`) sessizce düşer, kullanıcı stack'i kaybeder ama
uygulama açılır durumda kalır.

**Durum:** [ ] Doğrulanmadı

---

## 8. Desktop'ta root-back'in sessiz no-op olduğu bilgisi (final-review, deferred)

Desktop'ta (`sample:shopr` yok — JVM/desktop hedefi şu an yalnız derleme düzeyinde, madde 6) kökte
(`keys.size<=1`) geri tetiklenirse `onRootBack` çağrılır; `PlatformRootBack.jvm.kt`'nin
`platformDefaultRootBack()`'i **no-op** döner (Android'in aksine `finish()`'e karşılık gelen bir "pencereyi
kapat" davranışı YOK, çağıran kendi `onRootBack`'ini vermezse hiçbir şey olmaz — uygulama açık kalır,
görünürde "geri tuşu tepkisiz" gibi görünür). Bu bilgilendirici bir madde — bugün cihaz gerektirmiyor,
gelecekteki bir desktop sample'ın `onRootBack = { exitProcess(0) }`/pencere kapatma çağırması GEREKTİĞİNİ
hatırlatmak için düşüldü.

**Durum:** [ ] Bilgilendirici — desktop sample'a kadar aksiyon gerekmiyor

---

## 9. N8 — scrim/z-order görsel katman (dialog/sheet üstündeki scrim opaklığı ve sıralaması)

`gezgin-core`'un desktop uiTest'i N8'in (stacked overlay) FONKSİYONEL doğruluğunu (hangi entry top,
back sırası) pinledi ama scrim'in GÖRSEL katman sırası (arka ekranın koyulaşması, dialog'un/sheet'in
scrim'in ÜSTÜNDE net render olması, iki ayrı scrim'in çakışmaması) yalnız gerçek cihazda/gerçek
compositor'da gözlenebilir.

**Ön koşul:** `sample:app` cihaza/emülatöre kurulu.

**Adımlar:**
1. Login ekranından "Şifremi unuttum" ile `ForgotPasswordDialogRoute`'u aç (Dialog).
2. Ekranı gözle: arkadaki `LoginScreenRoute` KARARMIŞ (scrim) ama SEÇİLEBİLİR-DEĞİL şekilde görünmeli;
   dialog kutusu scrim'in üstünde net, gölgeli render olmalı.
3. Profil → "Adı düzenle" ile `EditNameDialogRoute`'u aç; aynı görsel kontrolü tekrarla.
4. Dashboard → "Sırala" ile `FilterBottomSheetRoute`'u aç (BottomSheet); scrim + sheet'in ekranın ALT
   kenarından yukarı doğru slide-in animasyonunu gözle.

**Beklenen:** Her üç modalde de scrim tek katman, doğru opaklıkta, alttaki ekranın ÜSTÜNDE ve
modal içeriğinin ALTINDA render olur; iki modal arka arkaya açılıp kapatılırsa (örn. dialog kapat →
hemen sheet aç) eski scrim'den kalıntı/flicker olmamalı.

**İlgili spec §:** `docs/gezgin-design.md` §7 (N8); `gezgin-core` `DialogSceneStrategy`/
`GezginBottomSheetScene` (bkz. `BottomSheetScene.kt`).

**Durum:** [ ] Doğrulanmadı

---

## 10. Dialog/sheet dismiss gesture'ları → `Canceled` + doğru sonuç teslimi

**Ön koşul:** `sample:app` cihaza/emülatöre kurulu.

**Adımlar:**
1. Login → "Şifremi unuttum" aç; dışarı tıkla (`ForgotPasswordDialogRoute.dismissOnClickOutside =
   false` — KAPANMAMALI). Sonra sistem geri tuşuna bas (KAPANMALI, `dismissOnBackPress` varsayılan).
   Snackbar "Sıfırlama iptal edildi" görünmeli (`NavResult.Canceled` yolu).
2. Profil → "Adı düzenle" aç; mevcut ismi SİL (boşalt) ve dışarı tıkla — KAPANMAMALI
   (`current.isNotBlank()` artık `false`). Metni geri yaz (boş olmayan) — yeni bir örnekte dışarı tıkla,
   bu kez KAPANMALI.
3. Dashboard → "Sırala" aç (BottomSheet); aşağı swipe et — kapanmalı, `order` state'i DEĞİŞMEMELİ
   (Canceled — seçim yapılmadı). Tekrar aç, scrim'e tıkla — aynı sonuç. Tekrar aç, bir sıralama seçeneğine
   dokun — sheet ÖNCE kapanma animasyonuyla inmeli, SONRA `order` güncellenmiş görünmeli (hide-then-result).

**Beklenen:** Yukarıdaki her dismiss yolu ilgili caller'a `Canceled` teslim eder (state değişmez);
`dismissOnClickOutside=false` olan durumlarda dışarı tık HİÇBİR ŞEY yapmaz (dialog açık kalır).

**İlgili spec §:** `docs/gezgin-design.md` §7; `gezgin-core` `Contracts.kt` (`DialogContract`/
`BottomSheetContract`).

**Bilinen sınırlama (desktop — upstream Nav3 JB alpha05):** `gezginOnBack`'in `NavDisplay.onBack`
guard'ı koşulsuz `navigator.back()` çağırır. Desktop'ta bir `@Dialog` — Nav3'ün KENDİ
`DialogSceneStrategy`'si (Gezgin'in değil) — gesture/scrim ile kapatıldığında `onDismissRequest`
bu global `onBack`'e düşer; nadir bir yarışta (dismiss + hızlı ikinci back) ikinci `back()` alttaki
ekranı da pop'layabilir. Entry'ye-pinlenmiş bir düzeltme (R1'in `backWithResult(entryId, …)` deseninin
karşılığı), dismiss'i o dialog entry'sine bağlamak için Nav3'ün built-in `DialogSceneStrategy`'sini
Gezgin-özel bir stratejiyle DEĞİŞTİRMEYİ (upstream davranışı çoğaltmayı) gerektirir → kapsam dışı,
bilinen-sınırlama olarak not edildi. Android'de Compose `Dialog` penceresi back'i kendi içinde
tükettiğinden `onDismissRequest` tek sefer düşer (gözlem Android-öncelikli). Gezgin'in KENDİ sahiplendiği
`GezginBottomSheetSceneStrategy` (sheet) aynı tek-kapı `back()`→`Canceled` yolunu kullanır ve commonTest'te
(`GezginBottomSheetDismissTest`) pinlidir.

**Durum:** [ ] Doğrulanmadı

---

## 11. Predictive-back modal üstünde

**Ön koşul:** `sample:app` cihaza/emülatöre kurulu; Android 14+ (API 34+) + Ayarlar → Geliştirici
seçenekleri → "Predictive Back animasyonlarını etkinleştir" AÇIK.

**Adımlar:**
1. Login → "Şifremi unuttum" aç (dialog). Geri gesture'ını YARIM bırak (parmağı kaldırmadan geri çek) —
   preview frame'i gözle, sonra iptal et (dialog AÇIK kalmalı).
2. Aynı ekranda gesture'ı TAMAMLA — dialog kapanmalı (`dismissOnBackPress=true`).
3. Dashboard → "Sırala" (sheet) aç; aynı yarım/tam gesture çiftini sheet üzerinde tekrarla.

**Beklenen:** Predictive-back preview'ı modal'ın KENDİSİ üzerinde çalışır (arkadaki `LoginScreenRoute`/
`DashboardScreenRoute` için bir preview BAŞLAMAMALI — modal top entry olduğu için geri jesti modal'ın
`onDismissRequest`'ine gitmeli, alttaki ekranın kendi geri-animasyonuna değil).

**İlgili spec §:** `docs/gezgin-design.md` §7; madde 1'deki M5′ predictive-back altyapısıyla aynı
`OnBackPressedDispatcher` mekanizması, modal üstünde gözlem.

**Durum:** [ ] Doğrulanmadı

---

## 12. Sheet swipe-dismiss animasyonu + hide-then-result deseni (görsel doğrulama)

**Ön koşul:** `sample:app` cihaza/emülatöre kurulu.

**Adımlar:**
1. Dashboard → "Sırala" aç; bir sıralama seçeneğine dokun. Sheet'in AŞAĞI doğru kayarak (slide-down)
   kapandığını, animasyon BİTTİKTEN SONRA listenin yeni sıra ile göründüğünü gözle (`FilterSheetScreen`
   `controller.hide()` → `backWithResult(...)` sırası — bkz. `HomeScreens.kt`).
2. Karşılaştırma için: aşağı swipe ile (seçim yapmadan) kapat — animasyon aynı, ama `order`
   DEĞİŞMEMELİ.

**Beklenen:** Her iki kapanışta da (seçimli/seçimsiz) GÖRSEL animasyon aynı akıcılıkta; programatik
"seçim" yolu animasyonsuz/aniden kesilen bir kapanış GÖSTERMEMELİ (bu, `GezginBottomSheetScene`
KDoc'undaki "kalıntı risk — programatik pop animasyonsuz" notunun tam da ÖNLENMEK istenen senaryosu;
`hide()`'ı önce çağırmak bunu sample'da zaten önlüyor — bu madde bunu cihazda GÖZLE doğrular).

**İlgili spec §:** `docs/gezgin-design.md` §7; `gezgin-core/.../compose/BottomSheetScene.kt` KDoc'u.

**Durum:** [ ] Doğrulanmadı

---

## 13. `@FullscreenModal` — tam-ekran occlusion + dismiss (Faz 7.2 / GAP-1)

`gezgin-core`'un desktop uiTest'i (Task 4.3) `@FullscreenModal` render'ının FONKSİYONEL doğruluğunu
(`usePlatformDefaultWidth=false` → tam-ekran) pinledi; ama tam-ekran GÖRSEL occlusion (arka ekranın
`@Dialog`/`@BottomSheet`'in aksine GÖRÜNMEMESİ — kenar boşluğu/scrim halkası YOK) ve dismiss davranışı
yalnız gerçek cihazda/gerçek compositor'da gözlenebilir. Sample'daki route: `ItemImageViewerRoute`
(`HomeGraph.kt`) → `ItemImageViewerScreen` (`HomeScreens.kt`).

**Ön koşul:** `sample:app` cihaza/emülatöre kurulu.

**Adımlar:**
1. Dashboard → bir ürüne dokun (`ItemDetailScreenRoute`) → "Görseli tam ekran gör" ile
   `ItemImageViewerRoute`'u aç (`@FullscreenModal`).
2. Ekranı gözle: içerik (görsel yer tutucu + "Kapat") ekranın TAMAMINI kaplamalı; arkadaki
   `ItemDetailScreenRoute` `@Dialog`/`@BottomSheet`'te olduğu gibi kenardan GÖRÜNMEMELİ (scrim halkası/
   kenar boşluğu YOK — `usePlatformDefaultWidth=false`, tam-ekran render).
3. İçeriğin dışına (varsa kenar bölgesine) dokun — KAPANMAMALI (`dismissOnClickOutside = false`).
4. Sistem geri tuşuna bas (ya da predictive-back gesture'ını tamamla) — KAPANMALI
   (`dismissOnBackPress` varsayılan `true` → `onDismissRequest` → `back()`); açan `ItemDetailScreenRoute`
   geri görünmeli.
5. Tekrar aç, bu kez "Kapat" düğmesine dokun — modal kapanıp `ItemDetailScreenRoute`'a dönmeli
   (`nav.backToItemDetail()`).

**Beklenen:** Tam-ekran modal alttaki ekranı TAMAMEN örter (dialog/sheet'ten görsel olarak ayrı);
dış-tık kapatmaz, geri tuşu/gesture ve "Kapat" düğmesi kapatır ve her ikisi de açan detay ekranına döner.

**İlgili spec §:** `docs/gezgin-design.md` §7; `gezgin-core` `Contracts.kt` (`FullscreenModalContract` —
`usePlatformDefaultWidth` YOK) + `DialogSceneStrategy` (tam-ekran `DialogProperties`).

**Durum:** [ ] Doğrulanmadı

---

## 14. MVI-mode (`SettingsScreen`) — VM ömrü, tek-seferlik efekt, MVI'dan logout (Faz 5.3)

Faz 5.3, `sample:app`'in `SettingsScreen`'ini MVI-mode'a çevirdi (`@MviViewModel(SettingsScreenRoute)` +
stateless `@Screen` `SettingsContent(state, onIntent)` + `@ScreenEffect SettingsEffects`, androidx-fallback
resolver — bkz. `sample/feature/profile/src/main/.../SettingsMvi.kt` ve `sample/README.md` "Faz 5 —
MVI-mode"). Bu, MVI-mode'un ilk GERÇEK (kctfork-dışı) uçtan-uca kanıtı: `assembleDebug` yeşil ve
`GezginMviEntries.kt` üretiliyor, ama aşağıdaki üç davranış **yalnız gerçek cihazda/emülatörde** —
gerçek `ViewModelStore` + `Lifecycle` + Android runtime ile — gözlenebilir; derleme bunları KANITLAMAZ.

**Ön koşul:** `sample:app` cihaza/emülatöre kurulu (`./gradlew :sample:app:installDebug`); `adb logcat`
erişilebilir (efekt Log/Toast'unu gözlemek için).

**Adımlar:**
1. Login → giriş yap → Dashboard → Profil → "Ayarlar" ile `SettingsScreenRoute`'a git (slideIn/Out
   transition'ı da gözle — route-seviyesi override MVI-mode'da korunuyor).
2. "Koyu tema" switch'ini AÇ: (a) Switch AÇIK duruma geçmeli; (b) ekranda bir Toast ("Tema tercihi
   kaydedildi") görünmeli; (c) `adb logcat -d -t 200 | grep SettingsMvi` bir kez `effect: Tema tercihi
   kaydedildi` yazmalı.
3. Cihazı DÖNDÜR (portrait↔landscape — configuration change). Switch AÇIK kalmalı (state VM'de tutuluyor,
   `remember`'da değil → sıfırlanmaz); dönme sırasında Toast/log TEKRAR tetiklenmemeli (efekt tek-seferlik,
   `MutableSharedFlow` replay=0 + `ObserveEffects` STARTED-collect → recomposition/rotation'da replay YOK).
4. "Çıkış yap"a bas — VM'in `onIntent(Logout)`'u `nav.logout()`'u çağırır.

**Beklenen / Pas kriteri:**
- 2: Switch açılır, Toast + tek `SettingsMvi` log satırı; **toggle başına tam bir** efekt (birden fazla değil).
- 3: `darkTheme` AÇIK korunur (rotation sonrası sıfırlanmaz — VM survives config change); Toast/log rotation'da
  **tekrar oynamaz** (recomposition/config-change replay yok).
- 4: `logout()` core-mode dönüşüm-öncesiyle BİREBİR aynı davranır — stack Dashboard'a kadar (dahil) temizlenir,
  Login gelir; geri tuşu Dashboard'a/Settings'e dönmez (davranış testi
  `logoutClearUpToDashboardInclusive_stacksASecondLoginEntry`'nin pinlediği çift-Login sonucu MVI-mode'da da aynı).

**İlgili spec §:** `docs/gezgin-design.md` §10.1 (MVI-mode binding); `gezgin-mvi` `GezginMvi`/`ObserveEffects`;
kod: `sample/feature/profile/.../SettingsMvi.kt`, üretilen `.../GezginMviEntries.kt`.

**Durum:** [ ] Doğrulanmadı

---

## Faz 6 — Fragment interop (§11) kalemleri: neden yalnız on-device (Robolectric adjudication)

> Faz 6'nın `@FragmentScreen` interop'u (`AndroidFragment` host + `gezginArgs`/`gezginNav` + `route.toBundle`
> bind-registry) için PD/config-change round-trip'i V1'de **otomatik** değil, yalnız cihaz-üstüdür. Task 6.0
> spike'ı (bkz. `.superpowers/sdd/task-6.0-report.md` §2 ve "Post-review addendum") Robolectric +
> `FragmentScenario`'yu V1 için — bağımsız incelemeyle iki kez onaylanmış gerekçeyle — REDDETTİ:
> `FragmentScenario` Fragment'ı `EmptyFragmentActivity` + `ActivityScenario` ile host eder, Compose/
> `AndroidFragment`'ı ve dolayısıyla yük-taşıyan `onUpdate → bindGezgin` re-bind yolunu TAMAMEN atlar → tam
> da doğrulanması gereken şeyi doğrulayamaz. Serileştirme yarısı zaten saf-JVM `FragmentRouteSerializationTest`
> (Task 6.2, commonTest, 3/3) ile; bind-registry mekanizması `:gezgin-core:compileDebugKotlinAndroid` derleme-
> doğrulamasıyla (Task 6.2 §Deviation 4 ve Task 6.3 Part A kararı — bkz. `.superpowers/sdd/task-6.3-report.md`)
> pinlendi. Geriye kalan CANLI davranış — process-death sonrası `arguments` route decode + `onUpdate` re-bind +
> doğru stack derinliği — yalnız gerçek bir `FragmentActivity` + Android runtime'da gözlemlenebilir; aşağıdaki
> 15–17. kalemler bunu kapsar.
>
> **Ortak ön koşul (Task 6.4 tamamlandı):** 15–17, sample'a bir `@FragmentScreen`-host'lu örnek ekran
> eklenmesini gerektirir; bu ekran Task 6.4'te eklendi — `:sample:feature:home`'daki **`HelpFragment`**
> (View-tabanlı, `fragment_help.xml` inflate eder), `:sample:navigation`'daki **`HelpScreenRoute(topic)`**
> route'una `@FragmentScreen` ile bağlı; Dashboard'dan `nav.goToHelp("navigasyon")` ile açılır (`@GoTo`),
> `HelpNavigator.backToDashboard()` ile geri döner (`@BackTo`). Ayrıca `AndroidFragment` bir `FragmentActivity`/
> `AppCompatActivity` host OLMADAN fırlatır (Task 6.0 §1e.1) — sample'ın `MainActivity`'si Task 6.4'te
> `ComponentActivity`'den **`AppCompatActivity`**'ye taşındı (host-Activity ön koşulu artık karşılandı).
> Bu yüzden host-Activity gereksinimi AYRI bir kalem DEĞİL, 15–17'nin ortak ön koşulu olarak burada tutuldu.
> Kalemler artık gerçek bir cihazda koşulabilir (kod tarafı hazır; kalan iş insan-doğrulaması).

---

## 15. PD "Etkinlikleri saklama" round-trip — `@FragmentScreen` yaprağı (canlı `arguments` decode + `onUpdate` re-bind)

Madde 4 Compose entry'ler için process-death round-trip'ini cihazda pinliyor; bu madde AYNI dev-option
reçetesini bir `@FragmentScreen`-host'lu YAPRAK için tekrarlar. Task 6.0 spike'ı `AndroidFragment.onUpdate`
→ `bindGezgin` re-bind yolunu KAYNAKTA doğruladı ama CANLI olarak (Robolectric reddedildiği için)
doğrulayamadı; `route.toBundle()`'ın PD-güvenliği de `FragmentRouteSerializationTest`'te yalnız birim
düzeyinde kanıtlı. Bu madde ikisini de gerçek process-death'te gözler.

**Ön koşul:** `:sample:app` cihaza/emülatöre kurulu (`./gradlew :sample:app:installDebug`); Geliştirici
seçenekleri açık; host Activity `AppCompatActivity` (Task 6.4'te taşındı — yukarıdaki Faz 6 prefatory notu).
Örnek ekran = `HelpFragment` (`gezginArgs<HelpScreenRoute>()` + `gezginNav<HelpNavigator>()` kullanır).

**Adımlar:**
1. Ayarlar → Sistem → Geliştirici seçenekleri → **"Etkinlikleri saklama"** ("Don't keep activities") AÇIK.
2. Uygulamayı aç, Dashboard'dan **"Yardım (legacy Fragment)"** butonuyla (`nav.goToHelp("navigasyon")` →
   `@GoTo(HelpScreenRoute)`) `HelpFragment` ekranına git. Bu ekranın ALTINDA en az bir entry vardır (start
   `LoginScreenRoute` → `loginSuccess` → Dashboard → Help; birkaç seviye derin stack → doğru restore-derinliği
   de gözlenir). Ekranda `gezginArgs`'tan okunan `topic`'in ("Konu: navigasyon") `help_topic` TextView'ına
   yansıdığını doğrula.
3. Home tuşuna bas (arka plana at) — "Etkinlikleri saklama" açıkken Android Activity'yi HEMEN `onDestroy()`
   eder → `rememberSaveable`/`FragmentState` round-trip'i gerçekten tetiklenir, Gezgin backstack'i
   `SavedState`'ten restore edilir.
4. App switcher'dan uygulamaya geri dön.

**Beklenen / Pas kriteri:**
- (a) crash/ANR YOK; ekran aynı stack derinliğiyle geri gelir (başa/`Feed`'e sıfırlanma yok) — her diğer
  kind'ın PD maddesinin de kontrol ettiği invariant.
- (b) `gezginArgs<Route>()` restore sonrası HÂLÂ doğru route'u decode eder — ekrandaki argüman-türevi içerik
  (id/başlık) restore-öncesiyle birebir aynı. Bu, `route.toBundle()`'ın PD-güvenliğinin CANLI kanıtıdır
  (`FragmentRouteSerializationTest`'in birim kanıtının ötesinde). İki restore branch'i vardır, ikisi de
  PD-safe ama `arguments`'ı FARKLI kaynaktan alır: (i) İLK-YARATIM branch'inde `AndroidFragment` Fragment'ı
  yeniden örneklerken `arguments`, Gezgin'in KENDİ backstack PD'sinden taze üretilen route'la yeniden encode
  edilir (§B4/§1d) — FM'in arg persistansına bağlı değil. (ii) FragmentManager-RESTORE branch'inde (taze
  process; FM saved-Fragment'ı ilk kompozisyondan ÖNCE geri yükler) `arguments`, FM'in KENDİ parcel'lediği
  hâliyle OLDUĞU GİBİ korunur (yeniden encode YOK) → bu branch FM'in arg persistansına DAYANIR. Her iki yolda
  da decode edilen route doğrudur.
- (c) `gezginNav<HelpNavigator>()` restore sonrası bir navigasyon edge'ini ("Panoya dön" butonu →
  `nav.backToDashboard()`) HÂLÂ tetikleyebilir — "bind yok" hatası fırlatmaz. Bu,
  yeniden yaratılan Fragment instance'ı için `AndroidFragment`→`onUpdate`→`bindGezgin` re-bind'inin (Task
  6.0'ın kaynakta doğruladığı, canlı doğrulayamadığı mekanizma) çalıştığının kanıtıdır.

**İlgili spec §:** `docs/gezgin-design.md` §11.1 (`gezginArgs`/`gezginNav`/`onUpdate` bind sözleşmesi), §1.10/
§12 (PD); Task 6.0 §1c/§1d/§2 + Post-review addendum; kod: `gezgin-core/.../fragment/FragmentBinding.android.kt`
(`bindGezgin`/`gezginBoundNav`), `.../fragment/FragmentRouteBundle.android.kt` (`toBundle`/`decodeGezginRoute`);
sample: `HelpFragment.kt`, `HomeGraph.kt` (`HelpScreenRoute`).

**Durum:** [ ] Doğrulanmadı (kod hazır — Task 6.4 örneği eklendi; insan cihaz-doğrulaması bekliyor)

---

## 16. Legacy Fragment `OnBackPressedDispatcher` LIFO-bypass gözlemi (bilgilendirici, §11.1)

Spec §11.1: bir legacy Fragment'ın KENDİ `OnBackPressedDispatcher` callback'i `NavDisplay`'in LIFO
sıralamasını GEÇER (en son kaydolan callback önce tetiklenir) → migration sırasında KALDIRILMALIDIR. Bu,
Gezgin runtime'ının zorlamadığı, dokümante edilmiş bir KULLANICI sorumluluğudur (bir sınırlama, "fix"
edilecek bir bug değil) — madde 6/8 gibi bilgilendirici. Bu madde, sınırlamanın gerçekten GÖZLEMLENEBİLİR
olduğunu bir insanın nasıl doğrulayacağını tarif eder (V1'de otomatik test edilmez).

**Ön koşul:** `@FragmentScreen` ekranı mevcut — `HelpFragment` (Task 6.4). Geçici olarak, `HelpFragment`'ın
`onViewCreated`'ında kendi geri callback'ini kaydet: `requireActivity().onBackPressedDispatcher.
addCallback(viewLifecycleOwner) { android.util.Log.d("FS_BACK", "fragment kendi callback'i geri'yi yuttu");
/* isteyerek NavDisplay'e geçirme yok */ }`.

**Adımlar:**
1. Fragment-host'lu ekrana ilerle (altında en az bir entry olsun ki "geri" anlamlı olsun).
2. Sistem geri tuşuna/gesture'ına bas.
3. `adb logcat -d -t 200 | grep FS_BACK` ile callback'in tetiklenip tetiklenmediğine bak; ekranın Gezgin
   üzerinden bir önceki entry'ye DÖNÜP dönmediğini gözle.

**Beklenen:** Fragment'ın kendi callback'i geri jestini YUTAR (`FS_BACK` loglanır) ve Gezgin'in kendi geri
işlemesi (bir önceki entry'ye pop) TETİKLENMEZ — yani legacy callback LIFO'da Gezgin'in `NavDisplay.onBack`
handler'ının ÖNÜNE geçer. Bu, §11.1'in "migration'da kaldırılmalı" uyarısının somut gözlemidir: callback
kaldırılırsa (ya da hiç eklenmezse) geri jesti normal Gezgin pop'una düşer. **Aksiyon GEREKMEZ** — beklenen,
kabul edilmiş, dokümante bir davranış; yalnız migrasyon yapan geliştiriciye bir hatırlatma.

**İlgili spec §:** `docs/gezgin-design.md` §11.1 (legacy `OnBackPressedDispatcher` bypass notu); madde 1'deki
M5′ ile aynı `OnBackPressedDispatcher` LIFO mekanizması, bu kez legacy-callback perspektifinden.

**Durum:** [ ] Bilgilendirici — bilinen/kabul edilmiş sınırlama, migration'da kullanıcı kaldırır

---

## 17. Migration-swap — `@FragmentScreen` → `@Screen` (aynı navigasyon pozisyonu, sabit graph)

Spec §11.1 kapanış satırı brownfield migration'ın çekirdek vaadi: `@FragmentScreen class XFragment {…}` →
`@Screen @Composable fun XScreen(route, nav) {…}`, GRAPH/EDGE/NAVIGATOR/DEEPLINK sabit kalarak. Bu madde, bir
yaprağı Fragment-host'ludan Composable-host'luya çevirmenin — route/graph bildiriminde HİÇBİR değişiklik
olmadan — navigasyon grafiği açısından AYNI pozisyonda çalışan bir ekran ürettiğini doğrular.

**Ön koşul:** Sample'da `@FragmentScreen` interop ekranı mevcut ve derleniyor — `HelpFragment` →
`HomeGraph.HelpScreenRoute` (Task 6.4). Bu madde bir KOD DÖNÜŞÜMÜ + yeniden-derleme + davranış-karşılaştırması
içerir (saf cihaz-üstü tık-adımı değil); dönüşüm GERİ ALINABİLİR bir doğrulama egzersizidir, kalıcı bir sample
değişikliği değil (commit'lenmez).

**Adımlar:**
1. Ekranın koordinatlarını NOT ET: route `HomeGraph.HelpScreenRoute(topic)`, `HomeGraph` üyesi,
   `@BackTo(DashboardScreenRoute)` edge'i → `HelpNavigator`, deeplink yok; açan edge Dashboard'ın
   `@GoTo(HelpScreenRoute)` → `goToHelp(topic)`.
2. `HelpFragment.kt`'deki `@FragmentScreen class HelpFragment : Fragment() { val args by
   gezginArgs<HelpScreenRoute>(); val nav by gezginNav<HelpNavigator>() }`'i geçici olarak
   `@Screen @Composable fun HelpScreen(route: HelpScreenRoute, nav: HelpNavigator) {…}`'e çevir — Fragment'ın
   `onViewCreated`/XML içeriğini composable gövdeye (`Text("Konu: ${route.topic}")` + `Button(onClick =
   { nav.backToDashboard() })`), `gezginArgs`→`route` param, `gezginNav`→`nav` param olacak şekilde taşı.
   `HelpScreenRoute`/`HomeGraph`/`@BackTo`/`HelpNavigator` bildirimlerine DOKUNMA.
3. KSP + `assembleDebug` yeniden derle; aynı edge'le (`nav.goToHelp("navigasyon")`) ekrana git.

**Beklenen:** (a) Route/graph/navigator/deeplink bildirimi DEĞİŞMEDEN proje derlenir (codegen artık
`FragmentEntryCodegen` yerine core `EntryCodegen` yolundan `provideXEntry` üretir — grafik açısından fark
yok); (b) ekran AYNI navigasyon pozisyonunda açılır (aynı edge, aynı stack derinliği, aynı geri davranışı);
(c) route argümanları ve navigator edge'leri Fragment sürümüyle BİREBİR aynı davranır. Bu, "Fragment yaprağı
↔ Composable yaprağı" swap'ının navigasyon grafiğine ŞEFFAF olduğunu — brownfield migration'ın çekirdek
vaadini — kanıtlar.

**İlgili spec §:** `docs/gezgin-design.md` §11.1 (migration swap satırı: "Graph/edge/navigator/deeplink
sabit"); kod: `gezgin-processor/.../codegen/FragmentEntryCodegen.kt` vs `.../codegen/EntryCodegen.kt` (iki
entry codegen'in AYNI `register<XRoute>(SCREEN, …)` yüzeyini ürettiği — grafik-şeffaflığın codegen kanıtı).

**Durum:** [ ] Doğrulanmadı (kod hazır — Task 6.4 örneği eklendi; insan cihaz-doğrulaması bekliyor)

---

## 18. Cross-module nav-probe × incremental derleme (izleme — TEMİZ yeniden-derleme gerekir)

Cross-module nav-wiring PROBE'u (`NavigatorProbe`, `@GezginNavigatorFor` kimlik damgasıyla) `:navigation`
modülündeki bir route'un navigator'ı olup olmadığını feature modülünün KSP turunda classpath'ten okur ve
sonucu üretilen `GezginFragmentEntries.kt`/`GezginEntries.kt`/`GezginMviEntries.kt`'ye SABİTLER. Doğruluk,
route topolojisi değişince (bir route SON edge'ini kaybedince navigator sınıfı yok olur, ya da tersi) KSP'nin
classpath-ABI değişiminde feature modülünü yeniden işlemesine bağlıdır — aralıklı (incremental) KSP
izolasyonunda bu tetiklenmeyebilir.

**Adım:** `:navigation`'da `HelpScreenRoute`'un `@BackTo(DashboardScreenRoute)` edge'ini SİL (navigator artık
kazanılmaz), yalnız `:navigation`'ı yeniden derle, feature modülünü (`:sample:feature:home`) YENİDEN DERLEME
(incremental). **Beklenen sınır:** feature'ın önceki `GezginFragmentEntries.kt`'si `helpNavigator()` çağrısını
tutmaya devam edebilir → derleme kırılır ya da bayat nav bağlanır. **Aksiyon:** nav modülünde edge topolojisi
değiştiğinde feature modüllerinde TEMİZ yeniden-derleme (`clean` + `assembleDebug`) gerekir. Bu bilinçli bir
sınır (madde 17'nin "yeniden-derleme" konvansiyonuyla aynı sınıf); kalıcı çözüm probe edilen declaration'ı
KSP `Dependencies`'e kaydetmektir (KSP sürümü izin verince).

**Durum:** [ ] Bilgilendirici — bilinen sınır; nav-topoloji değişiminde clean build ile giderilir

---

## Özet tablo

| # | Kalem | Cihaz gerekli mi | Durum |
|---|---|---|---|
| 1 | M5′ `@NoBack` LIFO / predictive-back | Evet | [ ] Doğrulanmadı |
| 2 | Overlay-over-`@NoBack` | Evet (kısmi kapsam) | [ ] Doğrulanmadı / Kapsam dışı (spesifik) |
| 3 | R2 VM-store yarısı | Evet | [ ] Doğrulanmadı |
| 4 | PD "Don't keep activities" | Evet | [ ] Doğrulanmadı |
| 5 | N8 (stacked dialog) | Evet | [ ] Bkz. madde 9 (kısmi kapsam) |
| 6 | iOS/Desktop back farkları | Hayır (bilgilendirici) | [ ] Bilgilendirici |
| 7 | PD restore fallback (bozuk state → fresh) | Evet (simüle) | [ ] Doğrulanmadı |
| 8 | Desktop root-back sessiz no-op | Hayır (bilgilendirici) | [ ] Bilgilendirici |
| 9 | N8 scrim/z-order görsel katman | Evet | [ ] Doğrulanmadı |
| 10 | Dialog/sheet dismiss → Canceled + sonuç teslimi | Evet | [ ] Doğrulanmadı |
| 11 | Predictive-back modal üstünde | Evet | [ ] Doğrulanmadı |
| 12 | Sheet swipe-dismiss animasyonu + hide-then-result | Evet | [ ] Doğrulanmadı |
| 13 | `@FullscreenModal` tam-ekran occlusion + dismiss (`ItemImageViewerRoute`) | Evet | [ ] Doğrulanmadı (kod hazır — Faz 7.2 örneği eklendi) |
| 14 | MVI-mode: VM config-change ömrü + tek-seferlik efekt + MVI'dan logout | Evet | [ ] Doğrulanmadı |
| 15 | PD "Etkinlikleri saklama" — `@FragmentScreen` (`HelpFragment`; args decode + `onUpdate` re-bind) | Evet | [ ] Doğrulanmadı (kod hazır) |
| 16 | Legacy Fragment `OnBackPressedDispatcher` LIFO-bypass | Hayır (bilgilendirici) | [ ] Bilgilendirici |
| 17 | Migration-swap `@FragmentScreen` → `@Screen` (`HelpFragment`→`HelpScreen`) | Evet (kod dönüşümü) | [ ] Doğrulanmadı (kod hazır) |
| 18 | Cross-module nav-probe × incremental derleme (nav-topoloji değişince clean build) | Evet (kod dönüşümü) | [ ] Bilgilendirici (bilinen sınır) |

Kullanıcı cihazla döndüğünde, yukarıdaki **"İki ayrı sample uygulaması"** notundaki dağılıma göre koş
(her maddede kutuyu işaretle, bulguları o maddenin altına not düş):

- **`:sample:shopr`** (`./gradlew :sample:shopr:installDebug`) — maddeler **1, 3, 4, 7** (madde 7 simüle).
- **`:sample:app`** (`./gradlew :sample:app:installDebug`) — maddeler **2, 9, 10, 11, 12, 13, 14, 15, 17**;
  örnek ekranların tümü kod tarafında hazır (`ItemImageViewerRoute` Faz 7.2'de, `SettingsScreen` MVI-mode
  Faz 5.3'te, `HelpFragment` — Dashboard'daki "Yardım (legacy Fragment)" butonu — Task 6.4'te eklendi).
- **Cihaz gerektirmeyen** (bilgilendirici) maddeler: **5, 6, 8, 16** — kurulum gerektirmez; 16 istendiğinde
  geçici callback ile gözlemlenir.
