# Gezgin Sample Showcase

Faz 0–3'te geliştirilen Gezgin API'sinin **tam kapsamlı**, çok-modüllü, gerçekçi bir kanıtı:
login/sign-up, bir dashboard + ürün detayı, ve profil/ayarlar/avatar akışlarından oluşan bir
uygulama iskeleti. Amaç bir "gerçek app" üretmek değil — spec'in her navigasyon deseninin
(graph/flow/result/transition/observability/test-API) **cross-module** bir kurulumda gerçekten
çalıştığını göstermek (bkz. `docs/superpowers/plans/2026-07-08-gezgin-sample-showcase.md`).

## Modül şeması

```
:gezgin-core         ── framework çekirdeği (RawNavigator, GezginState, ResultBus, ...)
:gezgin-processor    ── KSP: @NavGraph/@FlowGraph/@GoTo/... → topology + navigator + entry codegen
:gezgin-test         ── GezginTestNavigator (UI'siz test API çekirdeği)
      │
      │  api(...)              ksp(gezgin-processor)
      ▼
:sample:navigation   ── kotlin-jvm modülü — TÜM sealed route/graph ağacı burada yaşar (spec §3.3:
                         "sealed subtypes must co-reside"). Üretilenler: gezginTopology,
                         gezginSerializersModule, her route için XNavigator + xNavigator(id) factory.
      │
      │  implementation(":sample:navigation") + ksp(gezgin-processor)  (her feature kendi @Screen'lerini kaydeder)
      ├──────────────┬──────────────────┐
      ▼              ▼                  ▼
:sample:feature:auth :sample:feature:home :sample:feature:profile
 Login, ForgotPassword   Dashboard, ItemDetail   Profile, Settings,
 Dialog, SignUp flow     FilterBottomSheet, Welcome    EditNameDialogRoute, Avatar flow
      │              │                  │
      └──────────────┴──────────────────┘
                     │
                     ▼
              :sample:app  ── MainActivity: rememberNavigator + GezginDisplay + NavLogger + onRootBack

:sample:shopr — ayrı, dokunulmamış mini örnek (bu showcase'in bir parçası değil).
```

Her feature modülü `:sample:navigation`'a bağımlı ve KENDİ `ksp(gezgin-processor)`'ını çalıştırır —
`EntryCodegen` (Task 3.4) route'un **kendi** paketine değil, route'un tanımlandığı `:navigation`
paketine karşı navigator-factory'sini çözer; bu yüzden bir feature modülünün kendi `@NavGraph`'ı
olmasa bile (`model.graphs.isEmpty()`) `@Screen`/`@Dialog`/`@BottomSheet` kayıtları ve
`provideXEntry()` codegen'i sorunsuz çalışır (S1'in ana keşfi — cross-module entry codegen).

## Navigasyon grafiği

Tam grafik `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/{AuthGraph,HomeGraph,ProfileGraph}.kt` dosyalarında;
üç `@NavGraph` (`AuthGraph`, `HomeGraph`, `ProfileGraph`) doğrudan `Route` altında,
artı iç içe `AvatarFlow` → `ZoomFlow` (`@FlowGraph` içinde `@FlowGraph`).

## Kapsama tablosu

| Özellik | Route(lar) | Ekran / dosya |
|---|---|---|
| `@NavGraph` ×3 | `AuthGraph`, `HomeGraph`, `ProfileGraph` | `AuthGraph.kt`, `HomeGraph.kt`, `ProfileGraph.kt` |
| result'suz `@FlowGraph` | `AuthGraph.SignUpFlow` | `AuthGraph.kt` |
| `ResultFlow<T>` + nested `@FlowGraph` | `ProfileGraph.AvatarFlow` → `AvatarFlow.ZoomFlow` | `ProfileGraph.kt` |
| `@StartDestination` / G1 (app start) | `SignUpFlow.CredentialsScreenRoute`, `AvatarFlow.PickSourceScreenRoute`, `ZoomFlow.ZoomScreenRoute`; gerçek app start = `LoginScreenRoute` | graph dosyaları, `MainActivity.kt` |
| `@GoTo` (+ `singleTop=false` + `name=`) | `DashboardScreenRoute→ItemDetailScreenRoute`; `ItemDetailScreenRoute→ItemDetailScreenRoute` (`goToRelated`, R2 dup) | `HomeScreens.kt` |
| `@ReplaceTo` (Self-default) | `LoginScreenRoute→DashboardScreenRoute` (`loginSuccess`), `WelcomeScreenRoute→DashboardScreenRoute` (`continueToDashboard`) | `AuthScreens.kt`, `HomeScreens.kt` |
| `@ReplaceTo` (`clearUpTo`/`inclusive`/`name=logout`) | `SettingsScreenRoute→LoginScreenRoute` | `ProfileScreens.kt` |
| `@GoForResult` — screen×3 (Dialog/Dialog/Sheet) | `ForgotPasswordDialogRoute`, `EditNameDialogRoute`, `FilterBottomSheetRoute` | `AuthScreens.kt`, `ProfileScreens.kt`, `HomeScreens.kt` |
| `@GoForResult` — flow×1, named×2 | `AvatarFlow` (`pickAvatar`), `FilterBottomSheetRoute` (`pickSort`) | `ProfileScreens.kt`, `HomeScreens.kt` |
| Üçlü tüketimin İKİ deseni | suspend `goToPickSortForResult` (Dashboard) **vs.** `launchPickAvatar()` + `pickAvatarResults.collect` VM'siz `LaunchedEffect` (Profile) | `HomeScreens.kt` / `ProfileScreens.kt` |
| `@QuitAndGoTo` | `TermsScreenRoute` → `WelcomeScreenRoute` | `AuthScreens.kt` |
| `@Quit` | `TermsScreenRoute` | `AuthScreens.kt` |
| `@BackToStart` | `TermsScreenRoute` → `CredentialsScreenRoute` | `AuthScreens.kt` |
| `@BackTo` | `ItemDetailScreenRoute` → `DashboardScreenRoute` | `HomeScreens.kt` |
| `@NoBack` (cross-module) | `WelcomeScreenRoute` (declared in `:navigation`, `@Screen` in `:feature:home`) | `HomeGraph.kt` / `HomeScreens.kt` |
| `backWithResult` | `ForgotPasswordDialogRoute`, `EditNameDialogRoute`, `FilterBottomSheetRoute` | ilgili dosyalar |
| `quitWith` | `CropScreenRoute`, `ZoomScreenRoute` (nested içinden de en yakın sözleşme-sahibi AvatarFlow'u bitirir) | `ProfileScreens.kt` |
| Kind'lar `@Screen` / `@Dialog`×2 / `@BottomSheet`×1 | bkz. aşağıdaki "Faz 4 notu" | tüm feature dosyaları |
| Transition cascade (3 seviye) | app `navTransitions{forward{...}backward{...}}` → `ProfileGraph` arayüz override (`fadeIn/fadeOut`) → `SettingsScreenRoute` getter override (`slideIn/slideOut`) | `MainActivity.kt`, `ProfileGraph.kt` |
| Events observability | `NavLogger` (`navigator.events.collect { Log.d(...) }`) | `MainActivity.kt` |
| `onRootBack = finish()` | — | `MainActivity.kt` |
| PD-safe restore | `rememberNavigator` PD-safe `Saver`; bkz. `gezgin-core` (`GezginDisplay`) dokümantasyonu — bu sample ek bir test EKLEMEZ, framework'ün kendi restore testleri kapsar | — |
| `GezginTestNavigator` + davranış testleri | bkz. "Davranış testleri" altında | `sample/navigation/src/test/.../AppNavBehaviorTest.kt` |

## İki tüketim deseni — karşılaştırma

Aynı "@GoForResult sonucunu bekle" ihtiyacının iki farklı, İKİSİ DE meşru çözümü:

**1. Suspend çağrı (Dashboard → FilterBottomSheetRoute, `pickSort`)** — `scope.launch { val r =
nav.goToPickSortForResult(...) }`. Kısa, doğrudan; ama `scope`
(`rememberCoroutineScope()`) composable'ın YAŞAM SÜRESİNE bağlıdır — bkz. aşağıdaki "Tasarım
notları" içindeki config-change caveat'ı.

**2. Launch + collect / stream (Profile → AvatarFlow, `pickAvatar`)** — `nav.launchPickAvatar()`
(fire-and-forget push) + ayrı bir `LaunchedEffect(Unit) { nav.pickAvatarResults.collect { ... } }`.
Her (re)composition'da yeniden abone olur; bekleyen bir sonuç varsa collector kurulur kurulmaz
teslim edilir. VM'siz bir composable'da PD/config-change sonrası kalıcı sonuç isteniyorsa BU desen
tercih edilmeli (§6).

## Faz 4 notu — kind render

`@Screen` / `@Dialog` / `@BottomSheet` annotation'ları `EntryCodegen`/registry seviyesinde tam
işlenir (kind bilgisi kaydedilir), ama **Faz 4'e kadar tüm kind'lar aynı şekilde, plain
full-screen composable olarak render edilir** — `GezginDisplay` henüz `Dialog`/`ModalBottomSheet`
gibi platform-native konteynerlere ayrım yapmıyor. Bu sample'da `ForgotPasswordDialogRoute`,
`EditNameDialogRoute` (Dialog) ve `FilterBottomSheetRoute` (BottomSheet) UI'da sıradan bir ekran gibi görünür;
kind farkı yalnızca kodda (annotation) ve derlenmiş registride vardır.

## Davranış testleri

`sample/navigation/src/test/kotlin/dev/gezgin/sample/navigation/AppNavBehaviorTest.kt` — 12 test,
UI katmanına hiç dokunmadan `GezginTestNavigator` + üretilen navigator sınıfları üzerinden
kurgulanmış senaryolar (kapsama matrisinin DAVRANIŞ kanıtı):

| Test | Kanıtladığı |
|---|---|
| `loginSignUpQuitAndGoToWelcome_leavesLoginThenWelcome` | `@QuitAndGoTo` bütün SignUpFlow segmentini yıkar, Welcome Login'in üstüne düz push edilir |
| `welcomeContinueToDashboard_replacesWelcomeKeepsLoginBelow` | Self-default `@ReplaceTo` (`clearUpTo=null`) yalnız TOP'u keser |
| `logoutClearUpToDashboardInclusive_stacksASecondLoginEntry` | **Tasarım notu** — aşağıya bakın |
| `avatarFlowQuitWith_deliversValueToProfilePickAvatarResults` | `quitWith` en yakın kapsayan `ResultFlow`'u hedefler, Value flow-entry'nin caller'ına gider |
| `nestedZoomFlowBack_popsOnlyZoomLeavesCropOnTop` | nested `@FlowGraph`'ta flow-entry `back()` yalnız kendi segmentini kapatır |
| `nestedZoomFlowQuitWith_deliversValueToProfileTearsDownWholeAvatarFlow` | `quitWith` NESTED sub-flow içinden çağrılınca da en yakın SÖZLEŞME SAHİBİ flow'u (AvatarFlow) hedefler — HEM ZoomFlow HEM AvatarFlow segmenti yıkılır, Value yine Profile'a gider (spec §6 ownership) |
| `goToRelatedTwiceSameId_createsThreeDistinctStackEntries` | `singleTop=false` aynı route-değerine tekrar tekrar YENİ (id'si farklı) entry basar (R2) |
| `forgotPasswordSuspendResult_deliversValue` | suspend `goToXForResult` + `deliverResult` (raw `backWithResult`) round-trip'i |
| `forgotPasswordBack_deliversCanceled` | pending bir `ResultRoute` üzerinde düz `back()` → `NavResult.Canceled` (dismiss without answer) |
| `termsBackToStart_landsOnCredentialsKeepsLoginBelow` | `@BackToStart` flow'un kendi START'ına döner, flow HAYATTA kalır, Login altta dokunulmaz |
| `termsQuit_tearsDownSignUpFlowLeavesLoginOnTop` | `@Quit` bütün SignUpFlow segmentini yıkar, Login üstte kalır |
| `backToDashboardThenCrossGraphGoToProfile_bothViaGeneratedNavigators` | `@BackTo` (ItemDetail→Dashboard) + cross-graph B1 edge'i (`goToProfile`) TAMAMEN üretilen navigator'larla (raw.navigate YOK) pinler |

### kspTest wiring — denendi, yapısal nedenle vazgeçildi

Plan, `gezgin.emitTestAccessors=true`'u `:sample:navigation`'ın `kspTest` kaynak kümesinde açıp
üretilen tipli `GezginTestNavigator.fromX()` erişimcileriyle test yazmayı öngörüyordu. Denendi:

1. Paylaşılan `ksp { arg(...) }` bloğu GLOBAL'dir (`KspExtension.apOptions` tek bir map, hem `main`
   hem `test` KSP task'larınca okunur) — orada açmak `GezginTestAccessors.kt`'yi ana kaynak kümesine
   de (`:gezgin-test`'e bağımlı olmayan) sızdırıp `compileKotlin`'i kırardı.
2. Bunun için `kspTestKotlin` TASK'ının kendi `commandLineArgumentProviders`'ına (KSP Gradle
   plugin'inin `KspAATask.kt`'sinde task-seviyesinde, paylaşılan listenin ÜSTÜNE eklenen bir liste)
   doğrudan argüman eklemek mümkün — bu kısım ÇALIŞIYOR, flag yalnız test derlemesinde açılabiliyor.
3. Ama gerçek engel YAPISAL: `TestApiCodegen`, [GraphModel] gerektirir; `kspTestKotlin`'in KSP
   round'u yalnızca `test` kaynak kümesinin `.kt` dosyalarını görür. `AuthGraph.kt`/`HomeGraph.kt`/`ProfileGraph.kt`'nin `@NavGraph`'ları
   `main`'de yaşar ve o noktada zaten `.class`'a derlenmiştir — `getSymbolsWithAnnotation` binary
   classpath girdilerinden annotation'ları asla yeniden keşfetmez. Sonuç: `model.graphs`/`routes`
   HER ZAMAN boş → `GezginTestAccessors.kt` hiç üretilmez. Ampirik doğrulama: `kspTestKotlin`'i tek
   başına çalıştırınca ÇIKTI YOK (topology bile değil) — sadece test-accessor'lar değil, tüm codegen
   sıfır.

Bu, "graph'lar ve testler ayrı Gradle kaynak kümelerinde yaşayan" HER modül için geçerli yapısal bir
sınır (processor'ın kctfork-tabanlı kendi testleri bunu görmez çünkü orada graph+test kaynağı AYNI
KSP round'unda derlenir). **Fallback (meşru, plan'ın öngördüğü gibi):** testler
`GezginTestNavigator.raw` üzerinden, ana round'da zaten üretilmiş `raw.xNavigator(entryId)`
factory'lerini `entryIdOf(...)` ile birlikte doğrudan çağırır — `GezginDisplay`'in de kullandığı
AYNI üretilmiş kod, yalnızca `fromX()` sarmalayıcısı olmadan. `sample/navigation/build.gradle.kts`
içinde `testImplementation(project(":gezgin-test"))` + `kotlinx-coroutines-test` bağımlılığı bunun
için eklendi; `kspTest(...)` bağımlılığı EKLENMEDİ (yukarıdaki nedenle işe yaramıyor).

## Tasarım notları (S2/S3 bulguları)

- **`SettingsScreenRoute.logout()` çift-Login bulgusu** — `@ReplaceTo(LoginScreenRoute, clearUpTo =
  DashboardScreenRoute::class, inclusive = true)`, `[Login, Dashboard, Profile, Settings]` stack'inden
  başlarsa Dashboard'a kadar (dahil) her şeyi temizler ve GERİYE KALAN `Login`'in ÜSTÜNE yeni bir
  Login entry'si push eder (`replaceTo` her zaman `singleTop=false` ile push eder — hedef zaten
  top'un altındaki bir entry ile aynı DEĞERDE olsa bile dedup yapmaz). Runtime sonucu:
  `[Login, Login']` — çift Login. Bu, grafiği "düzeltmek" yerine BİLİNÇLİ OLARAK olduğu gibi
  bırakılan bir örnek-grafik tasarım bulgusudur (S1'in dosyası değiştirilmedi); davranış testi
  (`logoutClearUpToDashboardInclusive_stacksASecondLoginEntry`) bu GERÇEK sonucu pinler. Gerçek bir
  üründe muhtemel düzeltme: `clearUpTo` hedefini `LoginScreenRoute::class` yapmak (o zaman zaten dip
  Login'in KENDİSİ temizlenir) ya da `SettingsScreenRoute`'u her zaman Dashboard'un ÜSTÜNDE bir yerden
  çağrılacağını varsaymamak.
- **`quitWith` sahiplik (ownership) çözünürlüğü — bulunan ve DÜZELTİLEN kütüphane bug'ı** — Bu
  showcase, `ZoomFlow : AvatarFlow` şeklinde (nested flow'un, kapsayan `ResultFlow`'u SUBTYPE
  ettiği) bir zincirde `quitWith`'in sessizce değer düşürdüğünü ortaya çıkardı: `TopologyCodegen`
  üretilen `FlowType.isResultFlow`'u TRANSİTİF bayraktan yazıyordu, runtime'ın
  `chain.indexOfLast { it.isResultFlow }` hedef seçimi de bu yüzden AvatarFlow yerine ZoomFlow'u
  buluyordu. Düzeltme (spec §6 ownership): üretilen bayrak artık DOĞRUDAN `ResultFlow<T>`
  deklarasyonundan (`declaresResultFlowDirectly`) yazılır — nested result'suz sub-flow `false`,
  sözleşme sahibi `true`. `NavigatorCodegen`'in `quitWith` parametre-tipi seçimi de aynı sahiplik
  kuralına hizalandı. Processor regresyon testi:
  `TopologyCodegenTest."FlowType isResultFlow is OWNERSHIP (direct declaration) ..."`; davranış
  kanıtı: `nestedZoomFlowQuitWith_deliversValueToProfileTearsDownWholeAvatarFlow`. (Eski el-yazımı
  core fixture'ları zaten ownership semantiğindeydi — `PayAuthFlow : Route` kapsayanı subtype
  etmediği için bug'ı hiç tetiklememişti.)
- **Dashboard'ın suspend `goToPickSortForResult` çağrısı** — `rememberCoroutineScope()`'a bağlı
  `scope.launch`; VM ömrü İÇİNDE güvenlidir ama VM'siz composable'da bir config-change/process-death
  sonucu SESSİZCE düşürür (bkz. `HomeScreens.kt`'deki kod-içi yorum). Kalıcı sonuç isteniyorsa
  `ProfileScreen`'in launch+collect (stream) deseni tercih edilmelidir — yukarıdaki "İki tüketim
  deseni" karşılaştırmasına bakın.
- **Üretilen API isimleri plan metniyle birebir eşleşmiyor** — bkz.
  `.superpowers/sdd/sample-s2-report.md` "Escalations" bölümü (örn. plandaki varsayılan
  `goToForgotPasswordForResult` yerine gerçek üretilen ad `goToForgotPasswordDialogForResult`).
- **Entry kaydı runtime-checked'tir, derleme-zamanı değil** — bir route için `provideXEntry`
  unutulursa (feature modülü `@Screen`/`@Dialog`/`@BottomSheet` composable'ını sağlamazsa) derleme
  YEŞİL kalır; hata yalnızca o route'un İLK gösterilmeye çalışıldığı anda, `GezginDisplay`'in
  `toNavEntry` eager lookup'ında açıklayıcı bir exception olarak ortaya çıkar.

## Çalıştırma

```
./gradlew :sample:app:installDebug   # cihaz/emülatörde showcase'i kurar
./gradlew :sample:navigation:test    # davranış testleri
./gradlew build                      # tüm repo (gezgin-core/processor/test + sample:* + shopr)
```
