# Gezgin — Cihaz-üstü (on-device) doğrulama checklist'i

> Faz 3 (`GezginDisplay`, Nav3 entegrasyonu) boyunca desktop `compose.uiTest` ile cihazsız doğrulanan
> davranışların üstüne, **gerçek bir Android cihaz/emülatör** gerektiren kalemler. Desktop uiTest
> Android'in `androidx.activity.compose.BackHandler`/sistem-back/predictive-back/gerçek
> `ComponentActivity` ömrünü sağlayamaz (bkz. `gezgin-core/src/androidMain/.../PlatformDisplay.android.kt`)
> — bu yüzden bu kalemler `.superpowers/sdd/progress.md`'nin Task 3.3 notunda "ON-DEVICE CHECKLIST"
> olarak ayrıldı ve kullanıcı seyahatten dönünce koşulacak. `sample:shopr` (Task 3.6, bkz.
> `sample/shopr/`) bu koşuların **gerçek uygulaması** — her madde altta o sample'daki hangi ekranı
> kullanacağını söylüyor.
>
> Format: her madde **Ön koşul / Adımlar / Beklenen / İlgili spec § / Durum kutusu**.

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

## 2. Overlay-over-`@NoBack` etkileşimi (bilgilendirici — Faz 4'e kadar kapsam dışı)

Faz 4'te modal (dialog/bottom-sheet/fullscreen) SCENE wiring gelince, bir modal'ın `@NoBack` bir entry'nin
**üstünde** açık olduğu durumda geri tuşunun hangi katmanı (modal'ın kendi dismiss'i mi, yoksa alttaki
`@NoBack` entry'nin yutması mı) önceleyeceği ayrıca doğrulanmalı. `sample:shopr` (Task 3.6) hiç modal kind
kullanmıyor (`@Dialog`/`@BottomSheet`/`@FullscreenModal` yok) — bu kalem bugün test EDİLEMEZ, yalnız not
düşülüyor ki Faz 4 sample'ı genişletirken unutulmasın.

**İlgili spec §:** plan §Global Constraints madde 16 (Faz-2 final-review devri (b) top-entry-drive'ın
"modal overlay istisnası Faz 4 notu"); `docs/gezgin-design.md` §5/§12 modal-kind guard'ı.

**Durum:** [ ] Kapsam dışı (Faz 4'te tekrar değerlendirilecek)

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

## 5. N8 — Faz 4'e ertelendi, henüz uygulanabilir değil (yalnız not)

N8 kalemi (spec'te referans verilen, Faz 4 kapsamına planlanmış bir madde) bu fazda (Faz 3, `GezginDisplay`)
işlenmiyor — ilgili annotation/wiring henüz codegen'de yok, dolayısıyla `sample:shopr` üzerinden bugün
test edilecek bir davranış YOK. Faz 4 başladığında bu checklist'e gerçek bir madde olarak eklenecek.

**Durum:** [ ] Henüz uygulanabilir değil (Faz 4)

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

## Özet tablo

| # | Kalem | Cihaz gerekli mi | Durum |
|---|---|---|---|
| 1 | M5′ `@NoBack` LIFO / predictive-back | Evet | [ ] Doğrulanmadı |
| 2 | Overlay-over-`@NoBack` | Evet (Faz 4) | [ ] Kapsam dışı |
| 3 | R2 VM-store yarısı | Evet | [ ] Doğrulanmadı |
| 4 | PD "Don't keep activities" | Evet | [ ] Doğrulanmadı |
| 5 | N8 | — | [ ] Henüz uygulanabilir değil |
| 6 | iOS/Desktop back farkları | Hayır (bilgilendirici) | [ ] Bilgilendirici |

Kullanıcı cihazla döndüğünde: `sample:shopr`'ı bir Android cihaza/emülatöre kur
(`./gradlew :sample:shopr:installDebug`), yukarıdaki 1/3/4'ü sırayla koş, kutuları işaretle, bulguları bu
dosyaya (ilgili maddenin altına) not düş.
