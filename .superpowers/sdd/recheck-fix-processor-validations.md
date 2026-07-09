# Recheck-fix: gezgin-processor validation'ları — düzeltme raporu

- **Dal:** `recheck/processor-validations` (base HEAD `36b6427`).
- **Kapsam:** gezgin-processor doğrulama/codegen (Validation değil — EntryModelReader, VmDiClassifier,
  MviEntryCodegen, FragmentModelReader, ViewModelModelReader + model/fixture/test'ler). MJ4 için
  gezgin-mvi/gezgin-core runtime dokunuşu GEREKMEDİ (aşağıda gerekçe).
- **Yöntem:** her bulgu için önce kctfork testi (RED) → düzeltme (GREEN). Yeni doğrulama kodları HEAD'de
  hiç yoktu (SC1–SC6, MV1–MV10, FS1–FS4/FS6, NB1) → yeni kodun mesajda BULUNMAMASI RED durumunu kanıtlar.
- **Test sonucu:** `:gezgin-processor:test` (138) + `:gezgin-mvi:jvmTest` (2) + `:sample:navigation:test`
  (13) = **153 test, 0 failure, 0 error.** Ek olarak sample feature modülleri (profile/home/auth/shopr)
  `compileDebugKotlin` (KSP dahil) TEMİZ derlendi → yeni doğrulamalar gerçek sample koduna ateşlemiyor.

## Yeni / genişletilen doğrulama kodları

| Kod | Aile | Ne | Nerede |
|-----|------|----|--------|
| **SC7** | SC (yeni) | @NoBack × modal statik reddi | EntryModelReader (core+MVI) |
| **SC8** | SC (yeni) | kind↔contract uyumsuzluğu reddi | EntryModelReader (core+MVI) |
| **SC2** | SC (genişletildi) | core `nav:` param TİPİ doğrulaması | EntryModelReader.buildCoreEntry |
| **MV11** | MV (yeni) | @ScreenEffect imza + effect nav-tip | EntryModelReader (readScreenEffectFuns + buildMviEntry) |
| **MV12** | MV (yeni) | plain-Hilt + parametreli route reddi | EntryModelReader.buildMviEntry |
| **MV1** | MV (genişletildi) | iç içe jenerik forwarding reddi (MN3) | ViewModelModelReader |
| **FS1** | FS (genişletildi) | abstract/inner/secondary-only/private-ctor | FragmentModelReader |
| **FS2** | FS (genişletildi) | çıplak `Route::class` reddi | FragmentModelReader |
| **FS7** | FS (yeni) | @FragmentScreen + modal contract reddi | FragmentModelReader |

Davranış değişikliği (yeni kod yok): **MN1** (üretilen call-site'lar NAMED arg), **MN4** (default'lu
param'lar onurlandırılıyor).

---

## Bulgu bazında

### 1. Faz4 M1 / Integ M2 — @NoBack × modal (SC7) — **FIXED**
- **Doğrulandı:** HEAD'de yalnız `EntryAdapter` `require`'ıyla ilk navigasyonda runtime crash; KSP'de
  hiç kontrol yok. `kind` (annotation) + `noBack` (route decl) + contract implementasyonu (getAllSuperTypes)
  aynı derleme biriminde görünür → statik karar verilebilir.
- **Fix:** paylaşılan `checkKindContractAndNoBack` helper'ı (core+MVI):
  (a) `@BottomSheet + @NoBack` → koşulsuz SC7; (b) `@Dialog/@FullscreenModal + @NoBack` + route ilgili
  contract'ı implement ETMİYORSA → SC7 (default `dismissOnBackPress=true` statik biliniyor = kesin çakışma).
  Contract'ı implement eden route runtime denetimini KORUR (override değeri KSP-görünmez).
- **Test:** SC7 reject (BottomSheet), SC7 reject (Dialog contract'sız), SC7 allow (Dialog+DialogContract),
  SC7 allow (@Screen); MVI-mode SC7 smoke (BottomSheet+@NoBack). (EntryCodegenTest, MviModelReaderTest)

### 2. Faz4 M2 — kind↔contract mismatch (SC8) — **FIXED**
- **Doğrulandı:** `@FullscreenModal` route'u `DialogContract` implement edince adapter `route as?
  FullscreenModalContract`=null → tip-default → kullanıcının "kapatılamaz" modal'ı SESSİZCE kapatılabilir
  oluyordu. `@Screen` route'unda herhangi kind-contract da sessizce düşüyordu.
- **Fix:** aynı helper'da SC8 — route'un implement ettiği her kind-contract kind'ın kendi contract'ıyla
  eşleşmeli; eşleşmeyen (SCREEN'de: herhangi) → reddedilir. SC7'den ÖNCE (daha spesifik).
- **Test:** SC8 reject (FullscreenModal+DialogContract), SC8 allow (Dialog+DialogContract), SC8 allow
  (contract'sız Dialog); MVI-mode SC8 smoke.

### 3. Faz5 MJ1 — VmDiClassifier tip-önceliği — **FIXED**
- **Doğrulandı:** `roleOf`'ta `param.name == "nav"` tip çözülebilir olsa bile tipi eziyordu →
  `@InjectedParam nav: AnalyticsTracker` NAV sınıflanıp Koin runtime crash'ı + edge'siz route'ta spurious MV7.
- **Fix:** TİP karar veriyor (`typeFq == navigatorTypeFq → NAV`); `nav` adı yalnız tip ÇÖZÜLEMEZKEN
  (`isError` — aynı-modül henüz üretilmemiş navigator) fallback. `VmCtorParam.isError` (`KSType.isError`) eklendi.
- **Test:** MJ1 (About route + Koin `nav:AnalyticsTracker` → OTHER; spurious MV7 yok; default resolver yok;
  nav wiring yok). (MviEntryCodegenTest)

### 4. Faz5 MJ4 — HILT_PLAIN SavedStateHandle premisi — **FIXED (reddet seçildi)**
- **Doğrulandı:** Nav3'te route'u SavedStateHandle'a yazan mekanizma yok → plain-Hilt VM route verisini
  her zaman null okuyor; hiçbir teşhis uyarmıyordu.
- **Seçim gerekçesi (iki seçenekten REDDET):** full-fix (CreationExtras DEFAULT_ARGS plumbing) per-entry
  ViewModelStore owner'ını değiştirmeyi gerektirir — o owner JB nav3 `rememberViewModelStoreNavEntryDecorator`
  (bizim kodumuz değil) ve enjeksiyon noktası `PlatformDisplay.*` (paralel core-agent'ın dosyası,
  kapsam-dışı). Orantısız + dokunulması yasak. Onun yerine **MV12**: düz `@HiltViewModel` (assistedFactory
  YOK) + route PARAMETRELİYSE → KSP hatası, "HILT_ASSISTED kullan ya da route'u parametresiz yap".
  Parametresiz route (VM route verisi istemiyor) geçerli.
- **Fix:** MV12 (buildMviEntry). Yanlış "route via SavedStateHandle" yorumları düzeltildi (ViewModelModel
  enum KDoc + MviEntryCodegen). HILT_PLAIN golden fixture'ı parametresiz route'a (data object) çevrildi.
- **Test:** MV12 (`OrderRoute(orderId)` + plain Hilt → reddedilir); valid parametresiz path golden'la kanıtlı.

### 5. Faz5 MJ5 + Integ m4 — imza validasyonları (MV11 + SC2 nav-tip) — **FIXED**
- **Doğrulandı:** @ScreenEffect fazladan param / yanlış nav tipi → temiz [MVx] yerine üretilen
  GezginMviEntries.kt içinde kriptik hata. Core-mode `nav:` TİPİ hiç doğrulanmıyordu.
- **Fix:**
  - **MV11** (readScreenEffectFuns): binder imzası ⊆ {Flow<E>, nav} olmalı — fazladan param (ör.
    SnackbarHostState, resolver mekanizması YOK) → reddedilir.
  - **MV11** (buildMviEntry): eşleşen effect'in `nav` param TİPİ route'un `${x}Navigator`'ı değilse
    (çözülebilir yanlış tip) → reddedilir. Aynı-round navigator (error type) isError ile atlanır.
  - **SC2** (buildCoreEntry): core `nav:` param TİPİ `${x}Navigator`'a karşı doğrulanıyor; çözülebilir
    yanlış tip (ör. RawNavigator) → temiz [SC2]. Param SIRASI artık MN1 (named-arg) sayesinde tehlike değil.
- **Test:** MV11 fazladan-param, MV11 yanlış-nav-tip, SC2 nav-tip (RawNavigator).

### 6. Faz5 MN1 / MN3 / MN4 — **FIXED**
- **MN1 (named arg):** content/@ScreenEffect/androidx-VM-ctor çağrıları NAMED üretiliyor → pozisyonel-sıra
  tehlike sınıfı kalktı. Flow param adı binder'dan yakalanıyor (`fun XEffects(nav, effects)` ters sıra artık
  çalışıyor). Koin `parametersOf`/Hilt `factory.create`/`viewModel(...)` resolver çağrısı pozisyonel kalır
  (sırasıyla: tip-bazlı DI / derleyici-denetimli / fonksiyon-tipli değere named-arg Kotlin'de yasak). Golden'lar
  güncellendi.
- **MN3 (nested generics):** `Base<S> : GezginMvi<Wrapped<S>, …>` sarkan `S` bırakıyor; S/I/E TypeName
  materialize edilirken KSP transitive-substitution bug'ı (`NoSuchElementException`) processor'dan KAÇIP
  round'u opak hatayla düşürüyordu (HEAD'de bile). S/I/E TypeName üretimi tek guard'lı noktaya alındı →
  hata → temiz **MV1** reddi (task'ın "reddet" seçeneği). Somut/doğrudan-forward vakalar etkilenmez.
  (Not: ilk denenen `resolve()`-tabanlı sarkan-param tespiti de aynı bug'ı tetikledi; try/catch tek sağlam yol.)
- **MN4 (default'lar):** (a) VmDiClassifier — default'lu OTHER ctor param artık `emitDefault`'u bloklamıyor;
  androidx named-ctor çağrısı onu ATLIYOR. (b) EntryModelReader — default'lu content extra artık zorunlu
  `@Composable () -> T` resolver istemiyor (named content çağrısı atlıyor).
- **Test:** golden güncellemeleri + MN4a (default'lu OTHER onurlandırıldı), MN4b (default'lu content extra
  resolver istemiyor), MN3 (nested forwarding → MV1).

### 7. Faz6 minor'ler (FS1 / FS2 / FS7) — **FIXED**
- **FS1 (genişletildi):** HEAD yalnız primary-ctor param'ına bakıyordu; abstract / inner / yalnız-secondary-ctor /
  private-ctor Fragment'lar FS'i geçip `FragmentFactory.instantiate` içinde çöküyordu. Artık
  `Modifier.ABSTRACT/INNER` reddi + `getConstructors()` üzerinde public argsız ctor araması.
- **FS2 (genişletildi):** çıplak `@FragmentScreen(Route::class)` artık reddediliyor — @Screen'deki türet-sentinel'inin
  karşılığı yok, `register<Route>` hiçbir somut push'la eşleşmez → ölü kayıt.
- **FS7 (yeni):** modal contract (Dialog/BottomSheet/FullscreenModal) implement eden route'a @FragmentScreen
  koşulsuz SCREEN kaydında contract'ı SESSİZCE düşürüyordu. **Uyarı değil HATA** seçildi (SC8/MV8 içtihadı:
  silent-drop → hard reject; kullanıcının modal niyeti sessizce ihlal ediliyor).
- **Test:** FS1 abstract/inner/secondary-only/private-ctor, FS2 çıplak-Route, FS7 modal-contract.

---

## Disprove edilen bulgu

Yok — listelenen tüm bulgular HEAD'de gerçek doğrulandı ve düzeltildi.

## Kapsam-dışı bırakılan / dokunulmayan (rapora düşülen)

- **MJ4 full-fix (CreationExtras DEFAULT_ARGS):** per-entry ViewModelStore owner + `PlatformDisplay.*`
  değişimi gerektirir (paralel core-agent + yasak dosya). Reddet-yaklaşımı (MV12) seçildi, yukarıda gerekçe.
- Görev tanımındaki yasak dosyalara (NavigatorCodegen, GezginProcessor, FragmentEntryCodegen,
  RememberNavigator/EntryAdapter/RawNavigator/PlatformDisplay, ObserveAsEvents, docs, sample sources)
  DOKUNULMADI.

## Commit'ler (base 36b6427)

```
e94ff66 SC7/SC8 (@NoBack×modal + kind↔contract) + core nav-tip (SC2)
202b725 MJ1 (VmDiClassifier tip-önceliği) + MN3 (nested-generic MV1)
48717aa MN1 (named arg) + MN4 (default'lar)
ada232a MJ5 (@ScreenEffect imza — MV11 + effect nav-tip)
2cd3381 MJ4 (HILT_PLAIN + parametreli route — MV12)
d751444 Fragment FS1 örneklenebilirlik + FS2 çıplak-Route + FS7 modal-contract
```
