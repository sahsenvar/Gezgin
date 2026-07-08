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
 Dialog, SignUp flow     FilterSheet, Welcome    EditNameDialog, Avatar flow
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

Tam grafik `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/AppNav.kt`'ta;
üç `@NavGraph` (`AuthGraph`, `HomeGraph`, `ProfileGraph`), sealed `AppRoot : Route` kökü altında
(kök annotation'sız — E5 pozitif deseni), artı iç içe `AvatarFlow` → `ZoomFlow` (`@FlowGraph` içinde
`@FlowGraph`).

## Kapsama tablosu

| Özellik | Route(lar) | Ekran / dosya |
|---|---|---|
| `@NavGraph` ×3 | `AuthGraph`, `HomeGraph`, `ProfileGraph` | `AppNav.kt` |
| result'suz `@FlowGraph` | `AuthGraph.SignUpFlow` | `AppNav.kt` |
| `ResultFlow<T>` + nested `@FlowGraph` | `ProfileGraph.AvatarFlow` → `AvatarFlow.ZoomFlow` | `AppNav.kt` |
| `@StartDestination` / G1 (app start) | `SignUpFlow.CredentialsRoute`, `AvatarFlow.PickSourceRoute`, `ZoomFlow.ZoomRoute`; gerçek app start = `LoginRoute` | `AppNav.kt`, `MainActivity.kt` |
| `@GoTo` (+ `singleTop=false` + `name=`) | `DashboardRoute→ItemDetailRoute`; `ItemDetailRoute→ItemDetailRoute` (`goToRelated`, R2 dup) | `HomeScreens.kt` |
| `@ReplaceTo` (Self-default) | `LoginRoute→DashboardRoute` (`loginSuccess`), `WelcomeRoute→DashboardRoute` (`continueToDashboard`) | `AuthScreens.kt`, `HomeScreens.kt` |
| `@ReplaceTo` (`clearUpTo`/`inclusive`/`name=logout`) | `SettingsRoute→LoginRoute` | `ProfileScreens.kt` |
| `@GoForResult` — screen×3 (Dialog/Dialog/Sheet) | `ForgotPasswordDialog`, `EditNameDialog`, `FilterSheetRoute` | `AuthScreens.kt`, `ProfileScreens.kt`, `HomeScreens.kt` |
| `@GoForResult` — flow×1, named×2 | `AvatarFlow` (`pickAvatar`), `FilterSheetRoute` (`pickSort`) | `ProfileScreens.kt`, `HomeScreens.kt` |
| Üçlü tüketimin İKİ deseni | suspend `goToPickSortForResult` (Dashboard) **vs.** `launchPickAvatar()` + `pickAvatarResults.collect` VM'siz `LaunchedEffect` (Profile) | `HomeScreens.kt` / `ProfileScreens.kt` |
| `@QuitAndGoTo` | `TermsRoute` → `WelcomeRoute` | `AuthScreens.kt` |
| `@Quit` | `TermsRoute` | `AuthScreens.kt` |
| `@BackToStart` | `TermsRoute` → `CredentialsRoute` | `AuthScreens.kt` |
| `@BackTo` | `ItemDetailRoute` → `DashboardRoute` | `HomeScreens.kt` |
| `@NoBack` (cross-module) | `WelcomeRoute` (declared in `:navigation`, `@Screen` in `:feature:home`) | `AppNav.kt` / `HomeScreens.kt` |
| `backWithResult` | `ForgotPasswordDialog`, `EditNameDialog`, `FilterSheetRoute` | ilgili dosyalar |
| `quitWith` | `CropRoute` (AvatarFlow'a ulaşır), `ZoomRoute` (yalnız kendi ZoomFlow'unu kapatır — bkz. "Tasarım notları") | `ProfileScreens.kt` |
| Kind'lar `@Screen` / `@Dialog`×2 / `@BottomSheet`×1 | bkz. aşağıdaki "Faz 4 notu" | tüm feature dosyaları |
| Transition cascade (3 seviye) | app `navTransitions{default{...}}` → `ProfileGraph` arayüz override (`fadeIn/fadeOut`) → `SettingsRoute` getter override (`slideIn/slideOut`) | `MainActivity.kt`, `AppNav.kt` |
| Events observability | `NavLogger` (`navigator.events.collect { Log.d(...) }`) | `MainActivity.kt` |
| `onRootBack = finish()` | — | `MainActivity.kt` |
| PD-safe restore | `rememberNavigator` PD-safe `Saver`; bkz. `gezgin-core` (`GezginDisplay`) dokümantasyonu — bu sample ek bir test EKLEMEZ, framework'ün kendi restore testleri kapsar | — |
| `GezginTestNavigator` + davranış testleri | bkz. "Davranış testleri" altında | `sample/navigation/src/test/.../AppNavBehaviorTest.kt` |

## İki tüketim deseni — karşılaştırma

Aynı "@GoForResult sonucunu bekle" ihtiyacının iki farklı, İKİSİ DE meşru çözümü:

**1. Suspend çağrı (Dashboard → FilterSheet, `pickSort`)** — `scope.launch { val r =
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
gibi platform-native konteynerlere ayrım yapmıyor. Bu sample'da `ForgotPasswordDialog`,
`EditNameDialog` (Dialog) ve `FilterSheetRoute` (BottomSheet) UI'da sıradan bir ekran gibi görünür;
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
| `nestedZoomFlowQuitWith_onlyQuitsZoomFlowSegmentAvatarFlowContractStaysOpen` | **Tasarım notu** — aşağıya bakın |
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
   round'u yalnızca `test` kaynak kümesinin `.kt` dosyalarını görür. `AppNav.kt`'nin `@NavGraph`'ları
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

- **`SettingsRoute.logout()` çift-Login bulgusu** — `@ReplaceTo(LoginRoute, clearUpTo =
  DashboardRoute::class, inclusive = true)`, `[Login, Dashboard, Profile, Settings]` stack'inden
  başlarsa Dashboard'a kadar (dahil) her şeyi temizler ve GERİYE KALAN `Login`'in ÜSTÜNE yeni bir
  Login entry'si push eder (`replaceTo` her zaman `singleTop=false` ile push eder — hedef zaten
  top'un altındaki bir entry ile aynı DEĞERDE olsa bile dedup yapmaz). Runtime sonucu:
  `[Login, Login']` — çift Login. Bu, grafiği "düzeltmek" yerine BİLİNÇLİ OLARAK olduğu gibi
  bırakılan bir örnek-grafik tasarım bulgusudur (S1'in dosyası değiştirilmedi); davranış testi
  (`logoutClearUpToDashboardInclusive_stacksASecondLoginEntry`) bu GERÇEK sonucu pinler. Gerçek bir
  üründe muhtemel düzeltme: `clearUpTo` hedefini `LoginRoute::class` yapmak (o zaman zaten dip
  Login'in KENDİSİ temizlenir) ya da `SettingsRoute`'u her zaman Dashboard'un ÜSTÜNDE bir yerden
  çağrılacağını varsaymamak.
- **`ZoomFlow` içinden `quitWith` AvatarFlow'a ULAŞMAZ bulgusu** — `ZoomFlow` kendi
  `ResultFlow<T>`'unu deklare etmez (`declaresResultFlowDirectly=false`) ama transitif olarak biridir
  (`isResultFlow=true`, AvatarFlow'dan miras). Hem `RawNavigator.quitWith`'in runtime çözümü
  (`chain.indexOfLast { it.isResultFlow }`) hem `NavigatorCodegen`'in ürettiği statik parametre tipi
  zincirdeki EN İÇTEKİ `isResultFlow` üyesini hedefler — bu, `ZoomRoute` için AvatarFlow DEĞİL,
  ZoomFlow'un KENDİSİDİR. Sonuç: `ZoomScreen`'deki `quitWith` düğmesi yalnız ZoomFlow'un tek
  entry'sini (`back()` ile AYNI stack etkisi) kapatır; teslim edilen `AvatarChoice` değerinin
  dinleyeni yoktur (AvatarFlow'un `pickAvatarResults` beklemesi `PickSourceRoute`'un entry'sine
  bağlıdır, `ZoomRoute`'unkine değil) ve sessizce düşer — AvatarFlow'un sözleşmesi AÇIK kalır.
  Sözleşmeyi gerçekten kapatmak zincirinin tek `isResultFlow` üyesi AvatarFlow'un kendisi olan bir
  üyeden (`CropRoute`, `PickSourceRoute`) `quitWith` çağırmayı gerektirir. Bu, "grafiği düzeltmek"
  yerine BİLİNÇLİ OLARAK olduğu gibi bırakılan bir framework-davranışı bulgusudur (çözüm gerektirirse:
  `NavigatorCodegen`/`RawNavigator.quitWith`'in `isResultFlow` yerine `declaresResultFlowDirectly`
  bazlı bir "en yakın SÖZLEŞME SAHİBİ" çözünürlüğüne geçmesi düşünülebilir — bu sample'ın kapsamı
  dışında); davranış testi
  (`nestedZoomFlowQuitWith_onlyQuitsZoomFlowSegmentAvatarFlowContractStaysOpen`) bu GERÇEK sonucu
  pinler.
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
