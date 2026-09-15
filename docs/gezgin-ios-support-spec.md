# Gezgin — iOS Desteği Spec'i

> Durum: **uygulanıyor**. Bu belge `gezgin-core` ve `gezgin-test`'in iosArm64 + iosSimulatorArm64
> hedeflerini kazanmasının sözleşmesini tanımlar.
> İlgili: [gezgin-design.md](gezgin-design.md) (§2.2 Nav3 adapter sınırı, §8.1 root back) ·
> [gezgin-on-device-checklist.md](gezgin-on-device-checklist.md) (davranışsal kabul maddeleri).

---

## 1. Bağlam

`docs/gezgin-design.md` iOS'u baştan öngörür: §8.1 kökte `back()` için "iOS no-op", §"transition"
bölümü predictive back için "iOS edge-swipe" der. Buna karşılık bugün hiçbir iOS hedefi tanımlı
değil; README platform tablosu iOS'u `◑ (compile-level)` olarak işaretler. Bu spec o boşluğu
kapatır.

Kapsam **yalnız** `gezgin-core` + `gezgin-test` çalışma-zamanıdır. `gezgin-processor` JVM'de koşan
bir KSP2 işlemcisidir ve hedef platformdan bağımsızdır — değişmez.

---

## 2. Hedef kümesi

| Hedef | Durum | Gerekçe |
|---|---|---|
| `iosArm64` | ✅ | Gerçek cihaz |
| `iosSimulatorArm64` | ✅ | Apple Silicon simülatörü; CI'da testlerin koştuğu hedef |
| `iosX64` | ❌ **desteklenmez** | `org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0-alpha02` bu hedefi yayınlamıyor |
| `macosArm64`, `watchos*`, `tvos*` | ❌ kapsam dışı | Bu spec'in hedefi değil |

**S-1.** `iosX64` dışlaması bizim tercihimiz değil, upstream'in yayın kümesinin sonucudur. JB
navigation3-ui iosX64 yayınlamaya başlarsa bu karar yeniden değerlendirilir; o güne kadar Intel Mac
simülatörü desteklenmez ve README bunu açıkça belirtir.

**S-2.** `androidx.navigation3:navigation3-runtime` catalog pin'i **1.0.0'da kalır**. JB
navigation3-ui daha yükseğini (1.2.0-alpha04) talep ettiği için Gradle çakışma çözümü non-Android
sınıf yolunda zaten yükseltiyor — `ReleasePublicationVerifier`'ın `gezgin-core-jvm` POM beklentisi
bunu bugün de doğruluyor. Pin'i elle yükseltmek Android POM'unu gereksizce değiştirirdi.

---

## 3. Source set hiyerarşisi

```
commonMain
├── androidMain                 (değişmez)
└── nonAndroidMain              (YENİ)
    ├── jvmMain                 (mevcut jvm actual'ları buraya taşınır)
    └── iosMain
        ├── iosArm64Main
        └── iosSimulatorArm64Main
```

**S-3.** Platforma bağımlı yüzey 5 `expect`'ten ibarettir. Bunların davranışı iOS ve desktop'ta
aynıdır; tek kopya `nonAndroidMain`'de tutulur:

| `expect` | `androidMain` | `nonAndroidMain` | `iosMain` override |
|---|---|---|---|
| `rememberRawNavigatorInstance` | ViewModel holder + PD adopt | `rememberSaveable` + saver | — |
| `rememberPlatformEntryDecorators` | AndroidX nav3 decorator | JB nav3 decorator | — |
| `GezginNavDisplay` | AndroidX `NavDisplay` | JB `NavDisplay` | — |
| `platformDefaultRootBack` | no-op | no-op | — |
| `GezginNoBackHandler` | `BackHandler` | no-op (desktop) | **evet** (§4) |

**S-3.1.** `nonAndroidMain` JetBrains ailesini `compileOnly` olarak alır; `api` olarak dışa vermeyi
`jvmMain` ve `iosMain` üstlenir. Bir **ara** kaynak kümesinin `api` bağımlılığı ortak metadata
POM'una çıkar — bu da JB `navigation3-ui`/`lifecycle` artefaktlarını, Maven ile çözen bir Android
tüketicisinin grafiğine sızdırırdı ve §2.2'nin adapter sınırını delerdi. `commonMain` aynı deseni
aynı sebeple zaten kullanır. Yayınlanan bağımlılık yüzeyi bu sayede iOS öncesiyle birebir aynı kalır
ve `ReleasePublicationVerifier` bunu her koşumda kanıtlar.

**S-4.** Tek kopya tutmanın üç ölçülebilir sonucu vardır ve bunlar kabul ölçütüdür:
kök `koverVerify` gate'i (%94 satır) gevşemez, çünkü kod jvmTest'te ölçülmeye devam eder;
`checkPublicApiKDoc` envanteri yalnız gerçekten yeni bildirimler kadar büyür;
desktop ve iOS davranışının birbirinden sürüklenmesi yapısal olarak imkânsız hale gelir.

---

## 4. Geri jesti sözleşmesi

**S-5.** iOS'ta ekran kenarından içeri kaydırma (edge-swipe), Android'deki sistem/predictive back
ile **aynı** Gezgin sözleşmesini taşır:

- Stack'te birden fazla entry varsa → üstteki entry pop edilir.
- Üstteki entry `@NoBack` ise → jest yutulur, stack değişmez.
- Üstteki entry bir modal (`@Dialog` / `@BottomSheet` / `@FullscreenModal`) ise → önce modal
  kapanır; bekleyen bir `@GoForResult` çağrısı varsa `NavResult.Canceled` teslim edilir.
- Stack tek entry'deyse (kök) → `onRootBack` çağrılır.

**S-6.** `platformDefaultRootBack()` iOS'ta **no-op**'tur. Apple, uygulamanın kendini programatik
olarak sonlandırmasını yasaklar; Android'deki `finish()` karşılığı yoktur. Kök davranışını isteyen
uygulama `rememberNavigator(onRootBack = …)` ile kendi politikasını verir.

**S-7.** Zincir baştan sona hazır bileşenlerden kuruludur; Gezgin kendi jest tanıma kodunu yazmaz:

```
UIScreenEdgePanGestureRecognizer        (Compose Multiplatform, UIKitNavigationEventInput)
  → androidx.navigationevent dispatcher
  → NavDisplay'in kendi NavigationBackHandler'ı   (JB navigation3-ui)
  → onBack = gezginOnBack                          (§4'ün tüm kurallarını uygulayan yer)
```

Yani `GezginNavDisplay`'in mevcut actual'ı jesti zaten taşır; `gezginOnBack` de `@NoBack`'in ve kök
muafiyetinin **davranışsal taşıyıcısıdır** (`GezginDisplay.kt`). iOS bu ikisiyle §4'ü eksiksiz
karşılar ve `GezginNoBackHandler` desktop'taki gibi no-op kalır.

**S-7.1 — bilinen boşluk.** Android'de `GezginNoBackHandler` ayrıca entry-kapsamlı bir
`BackHandler` kurar; bunun tek ek kazancı, `@NoBack` bir ekranda predictive-back **önizleme
animasyonunun hiç başlamamasıdır**. iOS'ta bu önizleme başlar ve guard pop'u reddedince geri döner —
stack doğru kalır, yalnız görsel bir pürüz oluşur. Kapatmak `androidx.navigationevent.compose`'un
entry-kapsamlı handler API'sini gerektirir; bu artefaktın sınıfları Google Maven'da olduğundan
imzası çevrimdışı doğrulanamadı ve tahminle yazılmadı. Ayrı bir iş olarak ele alınır.

---

## 5. Test sözleşmesi

**S-8.** `gezgin-core/src/jvmTest/.../compose/` altındaki Compose UI testleri zaten
`runComposeUiTest {}` (`org.jetbrains.compose.ui:ui-test`, kotlin.test tabanlı) kullanır; hiçbirinde
JUnit4, `java.*` veya `compose.desktop` kullanımı yoktur. Dolayısıyla bir dönüştürme gerekmez —
sekiz dosyanın tamamı olduğu gibi `nonAndroidTest`'e taşınır ve **aynı test gövdeleri** hem desktop
hem iOS simülatöründe koşar. `jvmTest` yalnız desktop host runtime'ını (`compose.desktop.currentOs`)
sağlayan bağımlılık kümesi olarak kalır.

Adları artık yanıltıcı olan ikisi yeniden adlandırılır: `DesktopViewModelStoreDecoratorTest` →
`PlatformViewModelStoreDecoratorTest`, `RememberNavigatorJvmSaveableRegistryTest` →
`RememberNavigatorSaveableRegistryTest`. İkisi de `nonAndroidMain`'deki paylaşılan kodu sınar;
platforma özgü değildirler.

**S-8.1.** Kotlin/Native, backtick'li bildirim adlarında `(`, `)`, `@`, `,` ve `§` karakterlerine izin
vermez ("Name contains illegal characters"). JVM'de yaygın olan `` `back() pops the top entry` ``
biçimi bu yüzden `commonTest`'te kullanılamaz; parantezler cümle sonundaysa tire'ye, cümle
içindeyse satır içine iner. `-`, `'`, `_`, `=`, `+` ve Türkçe harfler serbesttir.

Buna bağlı bir gözlem: Apple **klib** derlemesi Linux host'ta da yapılır — yalnız Apple *binary*
link'i (framework, test çalıştırılabiliri) macOS ister. Dolayısıyla `kotlin.native.ignoreDisabledTargets`
bu hedefleri tamamen kapatmaz ve Linux `check` job'ı da iOS kaynaklarını derler. Bu istenen bir
durumdur: derleme hataları PR'da macOS job'ını beklemeden yakalanır.

**S-9.** "iOS testleri yeşil" ölçütü yetersizdir — sıfır test koşan bir hedef de yeşil görünür.
Kabul ölçütü: `iosSimulatorArm64Test`'in **koşan test sayısı > 0** ve taşınan sınıfların adlarının
iOS test raporunda göründüğü doğrulanır. Bu, macOS job'ında ayrı bir adım olarak uygulanır ve
sözleşme testiyle kilitlenir — yoksa boş bir koşum "yeşil iOS desteği" gibi görünürdü.

---

## 6. CI sözleşmesi

**S-10.** Kotlin/Native Apple hedefleri yalnız macOS host'ta derlenir. Bunun üç zorunlu sonucu var:

1. `ci.yml`'e `macos-latest` job'ı eklenir; her PR'da iOS derlemesi + simülatör testleri koşar.
2. `release.yml` ve `snapshot.yml`'in **publish** job'ları `macos-latest`'e taşınır. Aksi halde iOS
   klib'leri Maven Central'a hiç gitmez ve bu sessizce olur — yayın başarılı görünür, artefakt eksik
   çıkar. Aynı gerekçeyle `./gradle/verify-release-publications.sh` de macOS'ta koşar.
3. `gradle.properties`'e `kotlin.native.ignoreDisabledTargets=true` eklenir. Bu olmadan, iOS
   hedefleri tanımlanır tanımlanmaz **mevcut** ubuntu `check` job'ı "desteklenmeyen host" hatasıyla
   kırılır.

**S-11.** `codeql.yml`'e iOS derleme adımı eklenmez: CodeQL `java-kotlin` çıkarıcısı Kotlin/Native
çıktısını analiz etmez, eklenen adım yalnız süre maliyeti olurdu.

**S-12.** macOS job'ı `~/.konan` ve Gradle cache'ini saklar. Cache'siz ilk koşum ~15-20 dk, cache'li
koşum ~3-5 dk sürer.

**S-13.** Yeni yayınlanan koordinatlar `ReleasePublicationVerifier`'ın beklenen artefakt listesine
ve Central smoke script'lerine eklenir:
`gezgin-core-iosarm64`, `gezgin-core-iossimulatorarm64`,
`gezgin-test-iosarm64`, `gezgin-test-iossimulatorarm64` — hepsi `.klib`.

---

## 7. Örnek uygulama

**S-14.** Örnek İKİ modüldür. `sample/hello-shared` saf bir KMP kütüphanesidir (`androidTarget`
+ iosArm64 + iosSimulatorArm64): graph, ekranlar, MVI tabanı, paylaşılan `HelloApp` ve iOS
`MainViewController` oradadır; Apple hedefleri `HelloShared` adlı statik framework üretir.
`sample/hello` ise yalnız Android host'udur — `MainActivity`, manifest ve `activity-compose`.

Bölmenin sebebi mimari değil, çözümlemedir: tek modülde ortak kaynak kümesine konan lifecycle
bağımlılığı — hangi scope'ta olursa olsun — Android derleme sınıf yoluna düşer ve orada üç sürüm
çarpışır (bkz. S-15.1). İki modül, Compose ve lifecycle ailesini `api` ile tek yerden yayarak bu
çakışmayı ortadan kaldırır.

`sample/app`, `sample/shopr` ve `sample/feature/*` **Android-only kalır** — `sample/app` Fragment +
AppCompat kullanır, KMP'ye çevrilmesi bu spec'in kapsamı değildir.

**S-15.** KMP'de `ksp(project(...))` tek satırı hedeflere ulaşmaz; `kspCommonMainMetadata`
kullanılır, üretilen kaynak `commonMain`'e `srcDir` olarak eklenir ve derleme görevleri
`kspCommonMainKotlinMetadata`'ya bağlanır. `hello`'da `@FragmentScreen` bulunmadığından
processor'ın Android'e özel ürettiği tek yol (`AndroidFragment` çağrısı) devrede değildir.

**S-15.1.** Lifecycle sürümü **2.10.0**'dır ve bu bir tercih değil, tek geçerli seçenektir:
JetBrains bu artefaktı Compose Multiplatform 1.11.1'in Android'de sabitlediği **2.9.6** olarak
yayınlamaz (en düşük 2.10.0), **2.11.0** hattının Android varyantı ise AGP 9.1 ve compileSdk 37
ister. `compileOnly` de çözüm değildir: runtime sınıf yolunda görünmediğinden AGP'nin tutarlı
çözümlemesi derleme tarafını 2.9.6'ya sabitler ve uyuşmazlık çıkar.

**S-15.2.** `sample/iosApp` checked-in bir Xcode projesidir ve `HelloShared` framework'ünü
Compose Multiplatform'un kanonik kurulumuyla tüketir: hedefin bir build fazı
`:sample:hello-shared:embedAndSignAppleFrameworkForXcode`'u çağırır, `FRAMEWORK_SEARCH_PATHS` ise
`hello-shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`'i gösterir. Framework bu yüzden
**dinamiktir** (varsayılan); statik olsaydı bu görev kullanılamazdı.

**S-15.3.** Xcode projesi macOS job'ında `xcodebuild` ile derlenir. Gerekçe: proje dosyası elle
yazılır ve boru hattının başka hiçbir yerinde derlenmez — doğrulanmadan bir gözden geçirene ulaşırdı.
Ölçülen süreler bunun bedelsiz olduğunu gösteriyor: kritik yol `build` → `release-artifacts`
zinciri (~20 dk), iOS job'ı ise ~10,6 dk sürüyor ve paralel koşuyor.

**S-16 — bilinçli kapsam sınırı.** `hello` graph'ında `@NoBack`, `@Dialog`, `@BottomSheet` ve
`@GoForResult` yoktur. Dolayısıyla iOS Maestro akışları liste→detay push/back, edge-swipe pop,
kökte root-back ve process-death restore ile sınırlıdır. §4'teki modal ve `@NoBack` davranışları
iOS'ta **§5'in simülatör UI testleriyle** kanıtlanır, e2e düzeyinde değil. Bu bilinen bir boşluktur;
kapatmak için `sample/shopr`'un KMP'ye çevrilmesi gerekir (ayrı iş).

**S-17.** iOS Maestro akışları, mevcut Android suite'i gibi **CI'da koşmaz**; `maestro/run-ios-all.sh`
ile elle sürülür ve `maestro/README.md`'de belgelenir.

---

## 8. Geriye dönük uyumluluk

**S-18.** Bu çalışma hiçbir mevcut gate'i gevşetmez ve hiçbir public API'yi değiştirmez. Android ve
jvm hedeflerinin ABI'si (`.api` dump'ları), POM'ları ve davranışı aynı kalır; iOS yalnız **eklenir**.
Değişiklik `[Unreleased] / Added` altında yayınlanır.
