# Gezgin Sample Showcase — Multi-Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** Faz 0–3'te geliştirilen TÜM özellikleri kullanan, gerçekçi (login/signup/home/profile), çok-modüllü showcase sample'ı — spec §3.3'ün merkezi-nav-modülü mimarisinin İLK gerçek uçtan uca kanıtı (cross-module KSP dahil).

**Modüller (şemsiye `:sample`):**
```
:sample:navigation      → kotlin-jvm + serialization + ksp(gezgin-processor): TÜM sealed graph'lar/route'lar
                          (üretilenler: gezginTopology, gezginSerializersModule, XNavigator'lar+factory'ler)
:sample:feature:auth    → android-library + compose + ksp: Login, ForgotPasswordDialog, SignUp flow ekranları
:sample:feature:home    → android-library + compose + ksp: Dashboard, ItemDetail, FilterSheet, Welcome
:sample:feature:profile → android-library + compose + ksp: Profile, Settings, EditNameDialog, Avatar flow ekranları
:sample:app             → android app: MainActivity + rememberNavigator + GezginDisplay + NavLogger (events)
:sample:shopr           → (mevcut mini örnek — dokunulmaz)
```

## Navigasyon grafiği (kapsama matrisiyle tasarlandı — BINDING)

`dev.gezgin.sample.navigation` paketi; kök: `@Serializable sealed interface AppRoot : Route` (annotation'sız kök marker — E5 pozitif deseni).

```
@NavGraph AuthGraph : AppRoot
  LoginRoute (data object)
      @GoForResult(ForgotPasswordDialog)                       // screen-mode result (Boolean)
      @GoTo(SignUpFlow)                                        // result'suz flow'a container-giriş
      @ReplaceTo(DashboardRoute, name = "loginSuccess")        // Self-default replace (geri=login değil)
  ForgotPasswordDialog(email: String? = null) : ResultRoute<Boolean>     // @Dialog kind
  @FlowGraph SignUpFlow
      @StartDestination CredentialsRoute (data object)  @GoTo(ProfileInfoRoute)
      ProfileInfoRoute(email: String)                   @GoTo(TermsRoute)          // içeride serbest @GoTo
      TermsRoute (data object)  @BackToStart  @Quit  @QuitAndGoTo(WelcomeRoute)    // üç çıkış türü

@NavGraph HomeGraph : AppRoot
  DashboardRoute (data object — APP START, argsız/G1)
      @GoTo(ItemDetailRoute)  @GoTo(ProfileRoute)                        // cross-feature @GoTo (B1 şovu)
      @GoForResult(FilterSheetRoute, name = "pickSort")                  // named screen-mode; suspend deseniyle
  ItemDetailRoute(id: String)
      @GoTo(ItemDetailRoute, singleTop = false, name = "goToRelated")    // aynı hedefe name'li 2. edge (N9) + R2 dup-value canlı
      @BackTo(DashboardRoute::class)
  FilterSheetRoute(current: String) : ResultRoute<SortOrder>             // @BottomSheet kind
  WelcomeRoute(name: String? = null)  @NoBack  @ReplaceTo(DashboardRoute, name = "continueToDashboard")
      // @NoBack + declared-kalır şovu; @Screen composable'ı feature:home'da → CROSS-MODULE @NoBack kanıtı

@NavGraph ProfileGraph : AppRoot            // GRAPH-SEVİYESİ transition override (interface'te get())
  ProfileRoute (data object)
      @GoForResult(EditNameDialog)                                       // screen-mode (String)
      @GoTo(SettingsRoute)
      @GoForResult(AvatarFlow, name = "pickAvatar")                      // FLOW-mode result; launch+results collect deseniyle
  SettingsRoute (data object)               // ROUTE-SEVİYESİ transition override (get()) — cascade şovu
      @ReplaceTo(LoginRoute, clearUpTo = DashboardRoute::class, inclusive = true, name = "logout")
  EditNameDialog(current: String) : ResultRoute<String>                  // @Dialog kind, backWithResult
  @FlowGraph AvatarFlow : ResultFlow<AvatarChoice>
      @StartDestination PickSourceRoute (data object)  @GoTo(CropRoute)
      CropRoute(source: String)  @GoTo(ZoomFlow)       // quitWith(AvatarChoice) / quit() ekranda
      @FlowGraph ZoomFlow                              // NESTED flow (chain [AvatarFlow, ZoomFlow])
          @StartDestination ZoomRoute (data object)    // quit() ile içten çıkış
```
Değer tipleri: `SortOrder` (enum ya da @Serializable data class), `AvatarChoice(uri: String)` — @Serializable, :navigation'da.

**Kapsama matrisi (hepsi ZORUNLU — README'de tablo olarak da yer alır):** @NavGraph×3 · result'suz @FlowGraph · ResultFlow<T> · nested @FlowGraph · @StartDestination/G1 · @GoTo (+ singleTop=false + name=) · @ReplaceTo (Self-default + clearUpTo/inclusive/name=logout) · @GoForResult (screen×3: Dialog/Dialog/Sheet + flow×1, named×2) · üçlü tüketimin İKİ deseni (Dashboard: suspend `goToPickSortForResult`; Profile: `launchPickAvatar` + `pickAvatarResults` collect — VM'siz LaunchedEffect ile) · @QuitAndGoTo · @Quit · @BackToStart · @BackTo · @NoBack (cross-module!) · backWithResult · quitWith · kinds @Screen/@Dialog×2/@BottomSheet×1 (**Faz 4 notu:** kind'lar registry'de, render şimdilik plain screen — UI'da ve README'de açıkça belirtilir) · transition cascade 3 seviye (app navTransitions + ProfileGraph interface + SettingsRoute get()) · events observability (app'te NavLogger) · onRootBack=finish · PD-safe restore (README notu) · GezginTestNavigator + fromX() davranış testleri (:sample:navigation test source-set; `gezgin.emitTestAccessors` kspTest wiring — çalışmazsa raw-API testleri + rapor).

## Görevler

| # | Görev | Gate |
|---|---|---|
| S1 | Modül iskeletleri + :sample:navigation grafiği + KSP wiring; **beklenen cross-module processor keşifleri**: (a) feature modüllerinde `model.graphs.isEmpty()` iken EntryCodegen gate'inin atlaması, (b) EntryCodegen'in navigator-factory paketini cross-module çözmesi — çıkarsa processor'da düzelt (testli, ayrı commit) | :navigation derlenir + üretilenler doğru; feature'lar boş-screen'le derlenir |
| S2 | Feature ekranları (basit ama gerçekçi Material3 UI) + :sample:app (NavLogger, onRootBack, transitions) | `:sample:app:assembleDebug` yeşil |
| S3 | :sample:navigation davranış testleri (fromX + GezginTestNavigator: login→signup→quitAndGoTo, logout clearUpTo, avatar quitWith, dup-detail R2) + `sample/README.md` (kapsama tablosu + modül şeması + Faz-4 modal notu) | testler yeşil; full repo suite yeşil |

Riskler: cross-module EntryCodegen (S1'de bilinçli keşif — bu sample'ın ana değeri); kspTest accessor wiring; android-library compose'ların jvm :navigation'a bağımlılığı (sorunsuz olmalı).
