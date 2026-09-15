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

**S-7.** Jest, JB navigation3-ui'nin geri olay altyapısı
(`org.jetbrains.androidx.navigationevent:navigationevent-compose`) üzerinden alınır; Gezgin kendi
jest tanıma kodunu yazmaz. `GezginNoBackHandler`'ın iOS actual'ı bu dispatcher'a bağlanıp olayı
tüketir — `androidMain`'deki `BackHandler` kullanımının birebir karşılığı.

---

## 5. Test sözleşmesi

**S-8.** `gezgin-core/src/jvmTest/.../compose/` altındaki Compose UI testleri JUnit4
`createComposeRule()` kullanır; iOS'ta JUnit4 yoktur. Bu testler `nonAndroidTest`'e taşınır ve
`runComposeUiTest {}` (`org.jetbrains.compose.ui:ui-test`, kotlin.test tabanlı) biçimine çevrilir →
**aynı test gövdesi** hem desktop hem iOS simülatöründe koşar.

Taşınanlar: `GezginDisplayTest`, `GezginDisplaySceneTest`, `GezginDisplayR2Test`,
`GezginBottomSheetSceneTest`, `GezginStackedOverlaySceneTest`, `GezginLocalsTest`.
Desktop'a özgü oldukları için `jvmTest`'te kalanlar: `DesktopViewModelStoreDecoratorTest`,
`RememberNavigatorJvmSaveableRegistryTest`.

**S-8.1.** Kotlin/Native, backtick'li bildirim adlarında `(`, `)`, `@`, `,` ve `§` karakterlerine izin
vermez ("Name contains illegal characters"). JVM'de yaygın olan `` `back() pops the top entry` ``
biçimi bu yüzden `commonTest`'te kullanılamaz; parantezler cümle sonundaysa tire'ye, cümle
içindeyse satır içine iner. `-`, `'`, `_`, `=`, `+` ve Türkçe harfler serbesttir.

Buna bağlı bir gözlem: Apple **klib** derlemesi Linux host'ta da yapılır — yalnız Apple *binary*
link'i (framework, test çalıştırılabiliri) macOS ister. Dolayısıyla `kotlin.native.ignoreDisabledTargets`
bu hedefleri tamamen kapatmaz ve Linux `check` job'ı da iOS kaynaklarını derler. Bu istenen bir
durumdur: derleme hataları PR'da macOS job'ını beklemeden yakalanır.

**S-9.** "iOS testleri yeşil" ölçütü yetersizdir — sıfır test koşan bir hedef de yeşil görünür.
Kabul ölçütü: `iosSimulatorArm64Test`'in **koşan test sayısı > 0** ve taşınan sınıfların her
birinin iOS raporunda göründüğü doğrulanır.

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

**S-14.** `sample/hello` KMP'ye çevrilir (`androidTarget` + iosArm64 + iosSimulatorArm64);
`androidx.compose.*` koordinatları `org.jetbrains.compose.*`'a, `androidx.lifecycle.*` ise
catalog'daki `jb-lifecycle-*` alias'larına geçer. `MainActivity` androidMain'e, `HelloApp`
composable'ı commonMain'e, `MainViewController` iosMain'e gider. `sample/iosApp` altında
checked-in bir Xcode projesi framework'ü tüketir.

`sample/app`, `sample/shopr` ve `sample/feature/*` **Android-only kalır** — `sample/app` Fragment +
AppCompat kullanır, KMP'ye çevrilmesi bu spec'in kapsamı değildir.

**S-15.** KMP'de `ksp(project(":gezgin-processor"))` tek satırı yeterli değildir;
`kspCommonMainMetadata` kullanılır, üretilen kaynak commonMain'e `srcDir` olarak eklenir ve compile
task'ları `kspCommonMainKotlinMetadata`'ya bağlanır. `hello`'da `@FragmentScreen` bulunmadığından
processor'ın Android'e özel ürettiği tek yol (`AndroidFragment` çağrısı) devrede değildir.

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
