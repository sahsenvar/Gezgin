# Gezgin — Tasarım Notları (çalışan taslak)

Durum: beyin fırtınası sürüyor. Bu dosya kilitlenen kararları, parkedilenleri ve sıradaki konuları tutar.
Yöntem: **tümden gelim** — önce developer-facing yüzey ("nasıl görünür"), sonra çekirdek.

## Kilitlenen kararlar

### 1. Çekirdek model — state-as-data, Gezgin sahibi
- Back stack = gözlemlenebilir + serializable bir `Route` listesi (StateFlow / SnapshotStateList); Gezgin tutar.
- `navigate` / `back` = ergonomik cephe; altta saf liste dönüşümü.
- Test = listeyi assert et (UI yok). Restore = listeyi serialize et. Log = listeyi dinle.
- MVI: mümkün kılınır, **dayatılmaz**.

### 2. Deklarasyon + codegen
- Route = `@Serializable` veri tipi, developer yazar (first-class; MVI'da `when` ile exhaustive).
- `@Screen(RouteType::class)` composable'a konur; `route` param olarak enjekte edilir (ihtiyaç varsa).
- Codegen üretir: graph registry, `NavHost` wiring, navigate plumbing.
- Yüzey: `navigator.navigate(route)`, `navigator.back()`; `App()` içinde `rememberNavigator()` + `NavHost(navigator)`.
- Açık uç: `navigator` param mı, `CompositionLocal` mı (şimdilik param'a meyilli).

### 3. Graph = sealed tip hiyerarşisi
- Bir graph'ın route'ları, o graph'ın `sealed interface`'inin **içinde** nested (aynı dosya, namespaced).
- Alt-graph'lar **ayrı dosyada**, `: ParentGraph` ile bağlanır (subtyping = nesting).
- `@StartDestination` start üyeyi işaretler (annotation forward-reference derdi yok).
- sealed tip ağacı **==** nav ağacı; codegen sadece bunu okur.

### 4. Deep link (KAPATILDI — temel hâli)
- `@DeepLink("uri/{arg}")` route üstünde; bir route'a birden çok olabilir; query/optional → nullable prop.
- Yer tutucular **derleme zamanında** route property'leriyle eşleştirilir (typo / eksik / decode-edilemez tip = build hatası). ← ana compile-time güvenlik kazancı.
- Parent reconstruction: codegen tip zincirini (Route → Graph → … → root) yürür, `[ata start'ları…, hedef]` kurar. Guardrail 1 sayesinde güvenli.
- Giriş noktası: `navigator.handleDeepLink(uri): Boolean` (Android intent / web URL).

### 5. Guardrail 1 (ANA KAZANIM) — self-constructible start
- `@StartDestination`, codegen'in dışarıdan argüman vermeden kurabileceği bir şey olmalı:
  `data object`, ya da **her** parametre default'lu **veya** nullable (codegen null geçer).
- Derleme zamanında zorlanır. Hiyerarşik reconstruction'ın "arg yok" duvarına çarpmamasını garantiler.

### 6. Altyapı — Gezgin, Nav3 ÜZERİNE kurulu (DOĞRULANDI, kilitli)
- Compose Destinations Nav2'nin üstünde; Gezgin **Nav3'ün** üstünde. Nav3 = sahiplenilen state-as-data back stack → çekirdek modelimizle birebir, sıfır empedans. Nav3 bilinçli olarak düşük-seviye/uzatılabilir bir temel olarak tasarlandı.
- İş bölümü: **Nav3** = render (`NavDisplay`), back stack = `List<NavKey>`, transition + predictive back, per-entry lifecycle & state-save, adaptive Scene (two-pane). **Gezgin** = codegen, type-safe route/graph (sealed → `NavKey`), deep-link reconstruction, typed result, multiple back stack, MVI scope, transition DSL.
- **Adapter sınırı**: kullanıcı Nav3'ü doğrudan görmesin; Nav3 değişince tek yerde adapte edelim.
- **Platform olgunluğu (CMP 1.10+, 2026-06)**: Nav3 Android / iOS / desktop'ta **Stable**, web/wasm **Beta**. JetBrains artifact'leri: `org.jetbrains.androidx.navigation3:navigation3-ui` (+ `-common` transitively), ViewModel ve Material3 adaptive add-on'ları. iOS edge-pan geri jesti var; web'de Esc = geri; tarayıcı geçmişi base lib 1.1.0'da geliyor.
- **Cross-platform gotcha → tasarımımız zaten çözüyor**: iOS/web reflection-tabanlı serialization yapamaz; `NavKey`'ler **polymorphic serialization** ister. Bizim **sealed graph ağacımız** kotlinx.serialization'a kapalı polimorfizmi bedavaya verir → her target'ta otomatik. Codegen, sealed-olmayan/çok-graph durumlar için gerekli polymorphic kaydı da üretir. (Ham Nav3 kullanıcısının elle yaptığı işi Gezgin halleder = net değer katkısı.)

### 7. Transition & meta — Nav3 metadata'sına tip-güvenli geçit
- Transition derleme-zamanı kontrol gerektirmez → annotation değil, **`meta` property**'sinde düz değer. (Compile-time check gereken = annotation; gerekmeyen runtime davranış = değer.)
- `Route.meta: Meta` (interface default = boş). `meta { transition(X); put(key, value) }` DSL'i → Nav3 `NavEntry.metadata`'sına iner.
- `meta` = Nav3 meta'sının **genel, tip-güvenli geçidi**: transition + (ileride) dialog/bottomsheet/two-pane scene + ham `put(key, value)` escape hatch. **Sabit `kind` enum YOK** — kind/dialog ileride birer meta helper'ı olur (Nav3 scene-strategy mantığıyla).
- Transition spec yön bazlı (Nav3'e sadık): `forward` / `back` / `predictive` (predictive yazılmazsa = back; iOS edge-pan'e de bağlanır). İsimli `object X : NavTransition`.
- Seviyeler = interface zinciri: `Route`(lib) ← `AppGraph`(app) ← `Graph`(graph) ← route(screen) + per-call `navigate(route, transition = X)`. Cascade = ağaç boyunca **anahtar-bazında merge**, en içteki kazanır.
- Config taşıyan `@Screen` KALKTI. Annotation yüzeyi inceldi: `@Graph`, `@StartDestination`/`@Graph(start=)`, `@DeepLink`, `@Serializable`.
- ✅ Binding ÇÖZÜLDÜ → madde 8.

### 8. Yüzey ayrımı + binding (kilitli — "A")
- **Kural**: navigasyon grafiği & davranışı → **sealed ağaç (route)**; pikseller & sunum → **composable**.
- **Sealed ağaç (route)** taşır: kimlik + arg'lar, `: Graph` üyeliği/nesting, `@Serializable`, `@StartDestination`, `@DeepLink`, `meta { transition(...) ; put(...) }`.
- **Composable** taşır: UI + **kind annotation** (`@Screen` / `@Dialog` / `@BottomSheet` / `@FullscreenModal`). Kind annotation üç işi birden yapar: (1) "bu bir destination" = binding, (2) sunum kind'ı, (3) `route:` param tipinden route'a bağ. **Ayrı `@Content` YOK.**
- Binding kuralı: `@Screen` route'u `route:` param tipinden çıkarır; argsız route'ta açıkça `@Screen(OrdersRoute::class)`.
- Sunum UI tarafında: aynı route'u composable'da `@BottomSheet` yapınca sheet olarak açılır — route değişmez.

### 9. MVI scoping / per-entry lifecycle (kilitli)
- **Gezgin state-holder dünyasına GİRMEZ**: store/intent/uiEvent/DI tanımlamaz, MVI dayatmaz, convenience (`rememberScoped`/`rememberStore`) sunmaz. (Bu icatlar geri çekildi.)
- **MVI sözlüğü (kullanıcının terimleri)**: `Intent` = girdi, UiState'i değiştirir. `UiState` = state. `UiEvent` = çıktı, one-time, state değiştirmez (navigate/toast/snackbar). ("event" lehçelere göre çelişir; bu terimleri kullanıyoruz.)
- **Scoping = Nav3'ten**: Gezgin `NavDisplay`'e `rememberViewModelStoreNavEntryDecorator` (+ saveable + savedState) decorator'larını **varsayılan** ekler → kullanıcının sıradan `koinViewModel()`'i otomatik entry-scoped olur, pop'ta `onCleared`, config/process-death'e dayanır. Route arg: `koinViewModel { parametersOf(route.x) }`.
- **Statefulness**: `@Screen` ince bir *stateful binder*'a konur (VM resolve eder, state collect eder, UiEvent'i kenarda navigasyona çevirir) → *stateless* `XContent(state, onIntent)`'e devreder (preview/test edilebilir).
- **Gezgin'in ürettiği**: `entryProvider` (`entry<Route> { key -> Screen(route=key, navigator=...) }`), tipli route çıkarımı, navigator injection, decorator kurulumu, transition cascade. Kullanıcı bunların hiçbirini elle yazmaz.
- **State takibi**: `navigator.events: Flow<NavEvent>` + `navigator.backStack: StateFlow<List<Route>>` (Redux-vari log/analytics/devtools).

### 10. Usage model — DI-agnostik + GezginDisplay sarmalayıcı (kilitli)
- **DI-agnostik (Ktorfit modeli)**: Gezgin ekran başına entry + modül başına `xFeatureEntries(): List<GezginEntry>` üretir. DI'a koymak/toplamak KULLANICININ işi (Koin/Hilt/manuel). Çekirdek hiçbir DI'a bağlı değil; `koin.getAll` magic'i YOK.
- **Entry = tek katkı birimi**: route bağı + (stateful) content + deeplink pattern'leri + meta/transition. Multi-module: her modül kendi listesini üretir, kullanıcı birleştirir → çoklu modül bedava.
- **MVI = opt-in add-on** (`gezgin-mvi`), core değil. Core = manuel mod (`@Screen` kendi binder'ında). MVI modunda stateful binder ÜRETİLİR: `@Screen` *stateless* content'e konur, VM `MviModel<S,I,E>` uygular, `@UiEventHandler` UiEvent→nav verir. (Tasarımda şimdilik MVI üzerinden düşünüyoruz.)
- **Host = `GezginDisplay(...)`** — ham `NavDisplay`'i kullanıcı yazmaz. GİZLİ iç plumbing: decorators (scoping/saved-state), entryProvider assembly, predictive-back. AÇIK parametreler: `navigator`, `entries`, app-geneli `transitions`. (Bu erken notun `middleware` parametresi FİKRİ elendi — şipping API'de `GezginDisplay`'in kendi param'ı yok; gözlemlenebilirlik `navigator.events: Flow<NavEvent>`'i kullanıcının kendi `LaunchedEffect`'inde collect etmesiyle sağlanır, madde 10.1/§12'deki gibi — bkz. DOC-1 düzeltmesi, Faz 7.) Aynı zamanda **Nav3 adapter sınırı** (churn'ü gizler).

### 11. Graph nav metadata = ANNOTATION-only (kilitli) — madde 7'yi günceller
- **Karar**: tüm route nav metadata'sı annotation (KSP-okunur) → compile-time enforcement + generation baştan sona çalışır. Runtime `nav { }` / `meta { }` blokları DÜŞTÜ (KSP lambda gövdesi okuyamaz — `nav{}`, `OrderGraph({...})`, `conf:()->Unit`, `init{}` hepsi runtime, hiçbiri çözmez).
- **Ayrı annotation'lar (consolidated `@Nav` İPTAL)**: `@GoTo(vararg KClass)`, `@BackTo(vararg KClass)?` (gerekliliği AÇIK), `@StartDestination`, `@DeepLink`. Hepsi compile-time (KSP okur). vararg + transition'ın annotation'dan çıkması farm'ı küçültür.
- **Transition = RUNTIME değer** (annotation DEĞİL): `override val transition = transition { forward; back; predictive }`. Compile-time iş yok → runtime + parametreli serbest. (Madde 7 büyük ölçüde geri geldi; madde 11'in eski "transition = annotation spec-class" kısmı İPTAL.)
- **İlke (net)**: compile-time gereken (edges, start, deeplink) → annotation; runtime davranış (transition, navigate options) → değer.
- **Navigate options** (replace / popUpTo / singleTop / clearStack) = runtime per-call, edge DEĞİL → `navigate(route) { … }` option DSL (tasarlanacak).
- AÇIK sorular: `@BackTo` gerekli mi (sistem geri-jesti dışında?), cross-graph edge syntax + enforcement, navigate-options DSL.
- **Enforcement opt-in**: `@Graph(enforceEdges = true)` → KSP tipli navigator üretir, off-edge `navigate` DERLENMEZ. Kapalıyken `@Nav.goTo` sadece üretilen nav-haritasını + lint'i besler.
- **Üretilen nav-haritası**: `@Nav.goTo` kenarlarından Gezgin akış diyagramı çıkarır (her iki modda).
- **PARK**: keyfi runtime Nav3 metadata'sı (custom scene strategy `put(key,value)`) — annotation'a sığmaz; ileride küçük runtime escape hatch.

### 12. Kenar tipleri (edge kinds) + @NoBack + görsel kodlama
- Kenarlar tek tip değil; her biri ayrı annotation (mode param DEĞİL — her tipin farklı param'ı var):
  - `@GoTo(vararg)` — push; geri dönülür. Görsel: düz **çift baş** ok.
  - `@Replace(vararg)` — git + kaynağı (ve isteğe bağlı yukarısını) sil; geri yok. Görsel: düz **tek baş** + kesik (cut) işareti.
  - `@Switch(vararg)` — tab / paralel back-stack geçişi; iki stack korunur. Görsel: **kesikli çift baş** yatay ok. → multiple back stack topic'i.
  - `@GoForResult(target, result = T)` — git + tipli sonuçla dön. Görsel: düz ileri + **kesik dönüş** oku. → result passing topic'i.
- **Kenar OLMAYANLAR**: modal = hedefin kind'i (`@Dialog`/`@BottomSheet`, composable'da), harita modal node'u kesik-kenarlı çizer. Deeplink = dış giriş (`@DeepLink`), harita "dışarıdan" ok ile.
- **`@NoBack`** — node (route) özelliği: o ekranda geri tuşu/jesti yok. `@Replace`'ten farkı: Replace kenar (geçmişi siler), NoBack node (arkada stack olsa bile geri'yi bastırır). Bir node `@NoBack` ise ona giden her kenar haritada tek-baş çizilir (node ezer). Sistem-back semantiği AÇIK: varsayılan no-op, `@NoBack(onSystemBack = ExitApp)` opt-in.
- **Görsel kodlama ilkesi**: ok başı = geri-dönülebilirlik; kesik çizgi = farklı tür (switch/result/deeplink); node dekorasyonu = kind & NoBack.
- AÇIK: isimler (`@Replace`/`@Redirect`, `@Switch`/`@SwitchTo`), `@Replace` temizleme kapsamı, `@NoBack` sistem-back varsayılanı.

### 13. Navigasyon operasyon modeli + forward seti KİLİT (madde 12'yi günceller)
**Temel (library-agnostik)**: nav = back stack dönüşümü (`stack → stack'`, state-as-data). Forward = push + modifier → topoloji *yaratır* (annotation). Back = pop + modifier → geçmişi *tüketir* (runtime, annotation YOK). Asimetri: ileri deklare edilir, geri türetilir. "No back" = çoğunlukla temizlenmiş stack.

**Forward annotation seti (kilitli — isimler onaylandı)**:
- `@GoTo(vararg targets, singleTop = true)` → push. singleTop default TRUE, dedup **değere göre** (`equals`) — `Detail(1)`/`Detail(2)` ikisi de push'lanır, yalnız birebir-aynı tepe çoğalmaz.
- `@Replace(vararg targets, upTo = Self::class, inclusive = true)` → clear + push. `upTo=Self` (sentinel) = current'i değiştir; `upTo=X` = X'e kadar temizle. (eski @X = `@Replace(D)`; @Y(D,A) = `@Replace(D, upTo=A)`.)
- `@Switch(vararg targets)` → tab / paralel stack geçişi (→ multiple back stack topic).
- `@GoForResult(target, result = T)` → push + tipli dönüş; result tipi compile-time (→ result passing topic).

**Back = annotation YOK**: `back()`, `back { to(X, inclusive) }`, `back(result)` runtime. Result tipi `@GoForResult` kontratından gelir. `@BackTo` İPTAL; `@NoBack` marjinal/opsiyonel node bayrağı (core değil).

**Sıradaki tasarım**: runtime `navigate(target) { popUpTo(...); launchSingleTop() }` + `back...` DSL'i; annotation'lar sabit-edge için yapısal şeker.

### 14. Tipli per-source navigator = ANA navigasyon API'si (kilitli — A & B onaylı)
- Codegen her source ekran için tipli bir navigator üretir; her deklare edilen kenar = bir metot. `@GoTo(OrderDetail)` → `fun goToOrderDetail(orderId, highlight = false)` (hedefin constructor param'ları metoda döner). Eklenmeyen hedefe metot YOK → derlenmez. Annotation'lar artık nav API'sini ÜRETİR (yük taşıyan).
- **Metot adlandırma**: `@GoTo→goToX`, `@Replace→replaceWithX`, `@Switch→switchToX`, `@GoForResult→suspend goToXForResult(): T`. Hedef adından "Route"/"Screen" eki atılır.
- **`enforceEdges` flag DÜŞTÜ**: tipli navigator zaten enforcement; HER ZAMAN üretilir. Bypass = `nav.raw.navigate(route)` (bilinçli escape hatch — tam dinamik navigasyon için).
- **Generated metotlarda per-call options lambda YOK**: davranış tamamen annotation'dan gelir (`@GoTo`/`@Replace`'in `singleTop`/`upTo`/`inclusive` param'ları). Aynı hedefe farklı davranış = farklı kenar = farklı metot (`goToX` vs `replaceWithX`). Gerekçe: ad-hoc `{ popUpTo }` declared-graph'ı deler, nav-haritası yalan söylerdi. Lambda YALNIZCA raw escape hatch'te: `nav.raw.navigate(route) { popUpTo(...) }` — bilinçli "grafiğin dışı", nadir/dinamik durum. (Nadir kısıt: aynı source→target'a iki farklı `upTo`'lu `@Replace` → metot adı çakışır, ayrı isim gerek.)
- **Flagship teslim**: `@GoForResult` → `suspend fun goToXForResult(): T`. Result passing topic artık API değil, suspend/process-death MEKANİZMASI meselesi.

### 15. Result passing (navigateForResult) tasarımı
- **API**: caller route'a `@GoForResult(target)`; hedef route `: ResultRoute<T>` ile ürettiği tipi deklare eder. Codegen `suspend fun goToXForResult(): NavResult<T>` (caller navigator) + `fun TargetNavigator.backWithResult(result: T)` üretir; `goToXForResult` ↔ `backWithResult` simetrik.
- **Result tipi HEDEF route'ta** (`ResultRoute<T>` marker interface), edge'de değil — DRY (tek yerde), consistency-by-construction (çelişemez), tip hem caller-dönüşüne hem `backWithResult`-param'ına oradan akar. KSP `ResultRoute<T>`'nin generic supertype arg'ını okur → compile-time feasible. (Kullanıcı önce edge-decl `resultType=` önerdi, sonra "interface kimseyi öldürmez + DRY" diye buraya döndü.)
- **Cancel**: dönüş `NavResult<T>` = `Value(T)` | `Canceled` (düz `back` = Canceled). null değil — "veri yok" ile "iptal" karışmasın.
- **Mekanizma (PD-safe)**: sonuç canlı coroutine continuation'ında değil, **back stack state'inde kalıcı, keyed bir "pending result" slotunda** akar (state-as-data → serialize olur). `goToXForResult` idempotent: pending var+sonuç yok → sadece bekle (PD sonrası re-attach); sonuç var → döndür; yok → navigate+pending+bekle. Process death'te back stack + slot restore olur, caller re-attach eder.
- **Dürüst sınır**: PD-safety çağrının re-establishable bir yerden (composition/`LaunchedEffect`/binder) yapılmasına bağlı; tek-seferlik onClick coroutine'inden çağrı PD'de kaybolur (graceful). Opsiyonel CD-tarzı callback modu (`onResult { }`) doğal PD-safe yedek olarak sunulabilir.

### 16. Multiple back stack / nested navigation (kilitleniyor)
- **Temsil**: Map-of-stacks (Nav3 resmi deseni = kullanıcının `Map<Tab, List<NavKey>>` fikri). Gezgin tutar; aktif olan(lar) düz Nav3 listesine **flatten** edilir. Genel hâli recursive container ağacı (nesting için).
- **İki graph annotation'ı (kilitli)**: `@NavGraph` (= eski `@Graph`; ekranlar parent stack'inde = shared) + `@NestedNavGraph` (KENDİ back stack'i = Map'te bir entry). `@Graph` → `@NavGraph` rename.
- **Tab vs flow = edge kindı**: bir `@NestedNavGraph`'a `@Switch` (host'tan, paralel, hepsi canlı = tab) vs `@GoTo` (ardışık, kendi stack'i = flow).
- **Tab switch = host OTOMATİK**: host'un `@NestedNavGraph` çocukları zaten switch'lenebilir; host navigator'ı `switchTo<HomeGraph>()` üretir. İkişerli `@Switch` edge yazmaya gerek yok (bottom bar bunu kullanır); explicit `@Switch` edge'i tab için büyük ölçüde subsumed.
- **Davranış kuralları (`@NestedNavGraph`)**: (a) stack boşalınca (son entry pop) → nested graph kapanır, **girişe döner**; (b) flow tamamlanınca `finish(result?)` → tek seferde kapanır + entry'ye teslim + devam (= flow-seviyesi back-with-result, `@GoForResult` makinesinin genellemesi).
- **finish = `@GoForResult` genellemesi (kilitli)**: ekran flow'a `@GoForResult(CheckoutFlow)` ile girer (`CheckoutFlow : ResultRoute<Order>`), flow `finish(order)` ile teslim → caller `NavResult.Value(order)` ile devam. Aynı suspend + pending-slot + PD makinesi, tek ekran yerine bir alt-stack döner.
- **Flatten kuralı (kilitli)**: varsayılan `[start tab] + [aktif tab]` (Nav3 recipe — Home altta, geri en sonunda Home'a çıkar/çıkış); opsiyonel override (bağımsız-tab modu). Tab ve flow aynı flatten: parent + nested.
- **Save/restore**: Map/ağaç serializable state → config change + process death otomatik (Nav3 `rememberSerializable` + per-stack decorator).

### 17. Modal (Dialog / BottomSheet) davranışı (kilitli)
- **Ortak çekirdek + render varyantı**: Screen/Dialog/BottomSheet hepsi normal back stack entry; fark sadece Nav3 **SceneStrategy** ile render (overlay, arka görünür). `@Dialog`/`@BottomSheet`/`@FullscreenModal` = composable'da kind+binding (madde 8).
- **Properties = route'ta `DialogContract`/`BottomSheetContract` (opsiyonel)**: DRY defaults; SABİT = body override, KOŞULLU = constructor param (→ generated navigate metodunun param'ı; serialize → PD-safe). Annotation'da prop YOK (transition dersiyle aynı ilke). `GezginDialog` wrapper'ı İPTAL.
- **Composable = sadece içerik** (Surface/Column); pencere + dismiss→pop Gezgin scene'inden, route contract'ından okunur. Codegen entry metadata'sını route'tan **per-entry** türetir.
- **Result**: dialog/sheet doğal sonuç-üreticisi → `ResultRoute<T>` + `backWithResult(T)` + caller `@GoForResult`. Dismiss (tap-outside/swipe) = `Canceled`.
- **BottomSheet'e özgü**: `BottomSheetSceneStrategy` Gezgin bundle eder (core'da yok); swipe-dismiss animasyonlu pop; `controller: GezginSheetController` opsiyonel param ile composable'a enjekte (`LocalGezginSheetController`'dan, scene'in sheet controller'ı) — hide-then-result / expand için.
- **Entry-scoped VM**: dialog/sheet de ekran gibi `koinViewModel` ile entry-scoped; async/backend içerik (loading→content) birebir aynı.

## Parkedilenler (sonra dönülecek)
- Deep link giriş stratejileri (app açıkken replace vs append) — "C: configurable" fikri kayıtlı, ertelendi.
- `@FlowEntryResolver`, flow ortasına giriş, kayıttan resume.
- `@Graph` vs `@Flow` / `GraphKind` (sequential vs containment) ayrımı — **TAMAMEN İPTAL**. Tek container: `@Graph`.

## Henüz konuşulmadı (sıradakiler)
- ✅ Animasyon / transition → madde 7'de (meta üzerinden). Kalan açık uç: composable↔route binding.
- Modal / Dialog / BottomSheet / fullscreen destination'lar (meta helper'ı olarak gelecek)
- Result passing + suspend `navigateForResult` + back-with-result
- Multiple back stack / bottom-nav tab (her tab kendi stack'i)
- MVI scoping / per-entry lifecycle
- Process death restore mekaniği (detay)
- Two-pane / adaptive (ekran boyutu/oryantasyon)
- UI'sız test API'si
- KMP genişlemesi (şimdilik hedef CMP/Android)

## Revizyon — geri navigasyon sözlüğü + `@NoBack` (2026-06-28)

**Değişiklik:** Önceki §4.2 kararı "geri tarafında hiç annotation yok, no-back = temizlenmiş stack'in sonucu" idi. Bu **revize edildi**.

Yeni model (kullanıcı kararı):
- Geri = runtime navigator metotları: `back()` (her ekranda), `backTo(target, inclusive=false)`, `backToStart()`, `quit()`, `backWithResult(T)`.
- `backToStart()` = `backTo(Flow::class, inclusive=false)`; `quit()` = `backTo(Flow::class, inclusive=true)`. İkisi de **codegen üretir**, enclosing flow class'ını otomatik doldurur (parametresiz); yalnızca bir flow/subgraph içindeki ekranlar için üretilir (kök seviyede üretilmez → derleme garantisi).
- `quit()` = result'sız `finish()` (aynı çıkış makinesi; `@GoForResult` ile açılmış flow'a `Canceled` teslim eder).
- **`@NoBack`** = geri tarafının tek annotation'ı; terminal ekran (result gibi). Codegen o entry'de tüm geri metotlarını üretmez + sistem/predictive back'i kapatır. `@NoBack` + `ResultRoute<T>` = derleme hatası.

**Gerekçe:** ileri = topoloji (deklare → annotation), geri = geçmiş (türet → runtime metot); "bu ekran terminal" statik bir gerçek olduğu için annotation (`@NoBack`) doğru mekanizma. Çözülen küçük kararlar: `backTo` default `inclusive=false`.

### Düzeltme (aynı gün) — `@BackTo` annotation + `@NoBack` kapsamı

Yukarıdaki taslakta iki şey düzeltildi (kullanıcı):
- **`@BackTo` runtime metot değil, annotation.** `@BackTo(Y::class, inclusive=false)` → `nav.backToY()` (ileri taraftaki `@GoTo` ile simetrik). Generic `backTo(target)` = `raw` kaçış hatch'i.
- **`@NoBack` "tüm geri'yi" silmez.** Kaldırır: doğal `back()` + `backToStart()` + sistem/predictive back. **Korur:** `@BackTo`→`backToY()` (explicit), `backWithResult()` (`@GoForResult` hedefiyse), `quit()` (flow çıkışı; terminal ekranın çıkması gerekir).
- **İptal:** önceki "`@NoBack` + `ResultRoute<T>` = derleme hatası" kuralı **geri alındı** — ikisi birlikte geçerli (sonuç `backWithResult` ile döner).
- **Yeni guardrail:** dialog/sheet'te `dismissOnBackPress = true` + `@NoBack` = derleme hatası (tezat).
- **Açık uç:** `@NoBack`'te `backToStart()` kaldırıldı (tamamlanmış flow'a re-entry), `quit()` korundu — kullanıcı onayı bekliyor. İsim ailesi `back*` mı `goBack*` mı — kullanıcıya soruldu.

### Kesinleşti (kullanıcı) — geri tamamen declared, `@NoBack` yalnız `back()`'i kaldırır

- **İsim ailesi: `back*`** (kullanıcı `goBack`'in slip olduğunu onayladı).
- **İlke: declared olan sessizce silinmez.** Bu yüzden `backToStart`/`quit` de **opt-in annotation** oldu: `@BackToStart` → `backToStart()`, `@Quit` → `quit()` (parametresiz; flow class'ını codegen doldurur; flow dışında = derleme hatası). `@BackTo(Y)` zaten annotation. Tek implicit `back()` (her ekran) + contract `backWithResult` (`ResultRoute<T>`).
- **`@NoBack` artık SADECE `back()` + sistem/predictive jesti kaldırır;** declared hiçbir şeye dokunmaz. Önceki "backToStart'ı da kaldırır" taslağı **iptal**.
- **`@NoBack` + `@BackToStart`** ("tamamlandı → yeni sipariş") ve **`@NoBack` + `ResultRoute`** (sonuç `backWithResult` ile döner) = **serbest** — mantıklı kullanım yeri olduğu için (kullanıcının "mantıklı yer yoksa compile error" şartı; burada yer var).
- **Onaylandı:** `@BackToStart`/`@Quit` = **opt-in annotation** (her flow ekranında otomatik DEĞİL); kullanıcı daima açıkça belirtir.
- **İlke (genel, kullanıcı):** *explicit opt-in > implicit/otomatik* — birkaç fazladan satıra değer çünkü okunabilirliği artırır; bir route'un tüm yeteneği annotation'larından + interface'lerinden görünmeli. (Tipli navigator = yalnız declared olan; aynı taste.)

## Gap 1 çözümü — Fragment interop / brownfield migration (2026-06-29)

**Model:** Gezgin = root navigasyon; Fragment'lar = yaprak (Zad'ın TERSİ). Ayrı "legacy'ye git/çık" interop yüzeyi yok — legacy ekran fragment-backed bir Gezgin route'u. (Önceki "exitToHost / embedded limb" strawman'ı **iptal**.)

**Kararlar:**
- Fragment varyant annotation'ları: `@Fragment*` **prefix** (kullanıcı tercihi), **Android-only**: `@FragmentScreen`/`@FragmentDialog`/`@FragmentBottomSheet`.
- Deklarasyon **class üzerinde** (`@FragmentScreen(Route::class) class XFragment : Fragment()`), factory fonksiyon DEĞİL.
- **Parametreli constructor reddedildi** (Android no-arg ctor + Bundle restore zorunluluğu; PD/config/çoklu-instance'ta çöker ya da arg'ı sessizce kaybeder; `AndroidFragment` factory entry'leri ayıramaz). Gerekçe kullanıcıya açıklandı, kabul edildi.
- Tipli erişim accessor'larla: `gezginArgs<Route>()` (Bundle-backed, PD-safe) + `gezginNav<XNavigator>()` (entry-scoped). `@FragmentArgs` ve constructor yok → "`@Screen`'in `(route, nav)` parametreleri"nin fragment karşılığı.
- Codegen: `AndroidFragment` + `arguments=route.toBundle()` + `onUpdate { bindGezgin(route, nav) }`. "Veri serialize / canlı ref re-attach".
- Migration swap: `@FragmentScreen class` → `@Screen @Composable fun`; graph/navigator/edge sabit.
- Host `FragmentActivity`/`AppCompatActivity` olmalı; `GezginDisplay` root.

**Bağlantı:** `gezginArgs`/`gezginNav` aslında Gap 2'nin (VM-driven nav + route'un VM'e teslimi) da cevabı — VM de route+navigator'a aynı accessor zemininden erişir.

**Açık alt-başlıklar (sonraki):** `@FragmentDialog`/`@FragmentBottomSheet` mekaniği (view'ı scene'de host; legacy `DialogFragment`-own-window vakası); fragment içi nav çağrılarının `nav`'a taşınması.

## Gap 2 çözümü — VM-driven navigation + navigator instantiation (2026-06-29)

**Karar:** VM'e **tipli route + tipli per-source navigator** verilir; nav doğrudan VM'de (`handleIntent` → `nav.goToX()`). Zad'ın `mainNavigator` (app-scoped, tipsiz) + `navArgs = backStack.last() as NavKey` (kırılgan) hack'ini tipli/PD-safe yapar.

- **route + nav VM'e `parametersOf` ile** (kullanıcı onayı): `koinViewModel { parametersOf(route, nav) }`. VM constructor: `(route: XRoute, nav: XNavigator, ...userDeps)`. DI-agnostik (Koin parametersOf / Hilt assisted / manuel factory). `gezgin-koin`/`gezgin-hilt` şeker verebilir.
- **Tipli per-source navigator** (god-navigator değil; kullanıcı onayı): VM yalnız source ekranın declared edge'lerini görür.
- **Navigator instantiation (kullanıcının asıl sorusu):** navigator = stable `RawNavigator`'ı saran **stateless facade `class`** (object/DI DEĞİL). Instance'ı **codegen'in ürettiği entry** kurar: `val raw = LocalGezginNavigator.current; val nav = remember(raw){ XNavigator(raw) }`. `raw` çekirdeği `GezginDisplay`'den CompositionLocal ile iner.
- **Neden class (object/DI değil):** aynı facade hem prod çekirdeği hem test çekirdeği (`GezginTestNavigator`) üstünde çalışmalı → `raw` ref tutar. `testNav.from<Source>()` = `XNavigator(testRaw)`. Bu yüzden VM'e enjekte güvenli + test edilebilir.
- **Sağlamlık değişmezi:** facade stateless + stable çekirdeğe bağlı → VM'de tutmak config-change'te stale olmaz. PD'de route, restore edilmiş back stack'ten `parametersOf`'la döner.
- **§10 first-class:** Pattern A (VM-driven, önerilen) + Pattern B (composable UiEvent→nav); ikisi de desteklenir, dayatma yok. `SavedStateHandle` = VM'in kendi UI-state save'i için (ortogonal).
- **Üç yüzey, tek model:** @Screen (parametre) / @FragmentScreen (`gezginArgs`/`gezginNav` accessor) / VM (`parametersOf`) — hepsi aynı (route, nav)'ı alır.

İşlendi: spec §10, §13, §15.

## Gap 3 çözümü — back interception = non-feature (2026-06-29)

**Karar (kullanıcı tespiti):** dinamik koşullu geri (wizard sayfa-geri, unsaved-changes) için **Gezgin hiçbir API/annotation/codegen üretmez**. Geliştirici standart multiplatform `BackHandler(enabled) { }` / `PredictiveBackHandler`'ı `@Screen` içinde kullanır; `enabled` predictive-aware (false → Gezgin default pop + §9 predictive; true → yakala). VM-driven'da back = UI event → intent → VM karar verir (default = otomatik pop; sadece custom davranış isteyen ekran handler koyar).

**Tek library-side yükümlülük (yeni API değil, correctness):** `GezginDisplay` default back'i standart back dispatcher / Nav3 NavDisplay üstünden yapmalı ki (a) kullanıcının `BackHandler`'ı (inner) öncelik alsın, (b) default pop `navigator.back()`'ten geçip observable kalsın. NavDisplay'i sardığımız için bedava.

Gerekçe: DRY / minimal-magic — `BackHandler` platform primitive'i, reinvent edilmez. (`@NoBack` statik kapatma için ayrı, kalıyor.) İşlendi: spec §4.2.

## Netleştirme — `@NoBack` sistem-back mekanizması (2026-06-29)

**Problem (kullanıcı yakaladı):** `@NoBack` yalnız navigator'dan `back()`'i kaldırıyordu; sistem back/jest hâlâ `GezginDisplay`'in koşulsuz pop'una gidiyordu → annotation runtime'da yasaklamıyordu.

**Çözüm (kullanıcı önerisi; benim central-flag önerimden daha DRY):** codegen `@NoBack` route'unun **entry'sine** `BackHandler(enabled = true) { /* no-op */ }` emit eder → sistem back **yutulur** (consume: pop yok, event yukarı sızmadığı için app'ten çıkılmaz, preview yok). Gap-3'teki `BackHandler` primitive'i yeniden kullanılır; GezginDisplay'e ayrı metadata-flag/branch gerekmez.
- `enabled = <route @NoBack mı>`: @NoBack → `true`; değil → emit etme (leaner) / `enabled=false` inert.
- Katman (LIFO, dıştan içe): GezginDisplay default pop → codegen entry BackHandler → kullanıcı ekran BackHandler; en iç enabled kazanır. `@NoBack` + kullanıcı `BackHandler { nav.quit() }` = redirect çalışır; Gap-3 dinamik BackHandler da.
- `@NoBack`'in iki codegen etkisi: (1) navigator'dan `back()` çıkar, (2) entry'ye consuming BackHandler. İşlendi: spec §4.2, §13.

**Ayrı açık soru (kullanıcının örneğinden çıktı):** entry'nin VM'i resolve edip stateless `XScreen(state, onIntent)`'e vermesi (Zad `provideScreen` gibi) vs bizim mevcut `@Screen fun(route, nav)` binder modeli. BackHandler fikri ikisiyle de çalışır; binder-location ayrı/daha büyük karar — kullanıcıya soruldu, açık.

## Fragment modal (`@FragmentDialog`/`@FragmentBottomSheet`) — iki mod + DialogFragment bridge (2026-06-29)

**Bağlam:** DialogFragment API coverage araştırıldı (source-verified). View-based ucuz; ama `onCreateDialog` + `AlertDialog.Builder` (en yaygın basit dialog) **view'sız** → view-host modeli karşılayamıyor, tam refactor gerekiyordu. Kullanıcı "migration soft olmalı, refactor'suz; gerekirse DialogFragment'ı destekle" dedi → benim önceki "DialogFragment desteklenmez" kararım **geri alındı**.

**Karar — iki mod, codegen supertype'a (KSP) göre seçer:**
1. **Düz Fragment (view-based):** `AndroidFragment` view'ı Gezgin dialog/sheet scene'inde host → pencere Gezgin'in; `DialogContract`/§9 uygulanır.
2. **DialogFragment/BottomSheetDialogFragment:** FM **show/dismiss bridge** (AndroidX `DialogFragmentNavigator` deseni; Nav3 bırakmış, Gezgin geri getirir). entry aktif→`showNow`, pop→`dismiss`, user-dismiss→gözlemle→pop (idempotent). `onCreateDialog`/Builder/tema **dokunulmaz**; `DialogContract`/§9 yok sayılır. Soft: +annotation +`gezginNav` + sonuç satırı→`backWithResult` + call site→`goToXForResult`.

**Kazanımlar:** (a) AlertDialog.Builder refactor'suz geçer (soft); (b) bridged BottomSheetDialogFragment kendi `BottomSheetBehavior`'ını tam korur → dinamik sheet kontrolü bedava (`gezginSheetControl` iptal edildi).
**Accessor:** registry-based (arguments entry-id → navigator registry), `onCreateDialog`'da bile erişilebilir.
**To-do:** view-based modu için `DialogContract`'a `dimAmount`/`width` prop'ları.
**İşlendi:** spec §16, §15.

## Gap 4 (non-goal) & Gap 5 (parked) (2026-06-29)

- **Gap 4 — scaffold slots:** **non-goal**. `onBack`→BackHandler, `onEvent`→UiEvent binder, route bağı→entry zaten var; kalan topBar/bottomBar slotlama saf UI (Compose `Scaffold`), Gezgin dayatmaz. (`provideScreen`'in VM-resolve tarafı = binder-location, ayrı tutuldu.)
- **Gap 5 — flow-scoped shared data:** **parkedildi**. Varsayılan = value object (A: iyi pratik, Gezgin özelliği değil). Opsiyonel gelecek = immutable **flow-input** (B): flow'a girişte bir kez verilen, tüm ekranların salt-okunur okuduğu, nav state'te yaşayan PD-safe veri — SharedViewModel DEĞİL (immutable, VM değil, DI yok). Kullanıcı SharedViewModel'den haz etmiyor; mutable shared VM **reddedildi**. İşlendi: spec §17.

## Binder-location — problem tanımı + kısıtlar (2026-06-29, tartışma açıldı)

**Pain:** MVI'da her ekran stateful + stateless iki composable. Stateful olan hep aynı mekanik rutini yapar: (1) VM resolve, (2) `collectAsStateWithLifecycle` ile state, (3) UiEvent/effect gözle, (4) stateless content'i çağır (bazen `Column`/`Box` içinde sar). Kullanıcı bu tekrarı codegen ile yaptırmak istiyor.

**Kısıtlar (kütüphane geneli için zorunlu):**
1. Sadece kullanıcının MVI şekline göre yapılırsa başkaları kullanamaz → **şekle-agnostik** olmalı.
2. **VM'i içeride resolve/generate ETME** — insanlar assisted param, `ViewModelStore`, scope'u değiştirmek ister; sistem buna izin vermeli.
3. **DI-agnostik** — farklı DI'lar VM'i farklı inject eder (Koin `koinViewModel`, Hilt `hiltViewModel`, androidx `viewModel(factory)`, assisted, custom store owner).
4. **Önce tartışma, sonra çözüm** (kullanıcı talebi).

**Erken içgörü (doğrulanacak):** VM **resolution**'ı (DI-specific, kullanıcı kontrolü) ile binding **rutini** (collect/observe/render, mekanik) ayrılırsa; kullanıcı bir VM-resolution lambda'sı verir, Gezgin mekanik binding'i yapar (Zad `provideScreen`'in modeli). Codegen vs runtime-helper vs contract — açık.

**Aksiyon:** Compose Destinations / Circuit / Voyager-Decompose-Molecule araştırıldı → aday yapılar + tradeoff'lar → kullanıcıyla örnekler üzerinden. Araştırma subagent'ları dispatch edildi.

### Binder-location KARARI — Candidate 2 refined (`provideXEntry(resolver)`) (2026-07-02)

Kullanıcıyla **kilitlendi** (son büyük tasarım konusu). Candidate 2 (contract + codegen), `@ScreenModel` yerine kullanıcının önerdiği **`provideXEntry(resolver)`** deseniyle:
- Codegen `provideXEntry(viewModel: @Composable (nav,args) -> GezginMvi<S,I,E>): GezginEntry` üretir (collect+observe+render). **S/I/E `@Screen` content imzasından türetilir**; VM somut tipi codegen'ce bilinmez (resolver `-> GezginMvi<S,I,E>` tiplenir, kullanıcı VM'i alt-tip).
- **VM resolution = kullanıcının lambda'sı, entry-kurulum noktasında** (Ktorfit modeli). Ekran kodunda DI **yok**, tek yerde toplanır. Assisted/store/scope kontrolü korunur → kullanıcının tüm kısıtları karşılandı.
- Ekran kodu: stateless `@Screen(state,onIntent)` + opsiyonel `@ScreenEffect(Flow<E>)`. Sözleşme opt-in `GezginMvi<S,I,E>`.
- **Core default-free** (DI-agnostik); `gezgin-koin` default resolver verebilir (mode flag/overload), codegen constructor'a bakıp `parametersOf` şeklini üretir.
- **Effect:** opsiyonel; Google state-first önerisi — dayatılmaz. **Mode imzayla:** core `@Screen(route,nav)` kendin-bağla vs mvi `@Screen(state,onIntent)` codegen-bağlar.
- Araştırma yakınsaması doğrulandı: kimse binder'ı codegen etmiyor (CD/Voyager/community runtime-helper, Circuit runtime-registry) → binder'ı codegen etmek Gezgin'in ayrışması.

Detay: `docs/gezgin-binder-location.md` (KARAR bölümü). İşlendi: spec §10.1/§11/§13/§14/§15.

## Spec konsolidasyonu — v2 (2026-07-02)

Tüm kilitli kararlar tek, temiz, self-contained `gezgin-design.md`'ye toplandı (artımlı revizyon çürüğü temizlendi; revizyon geçmişi **bu notlarda** kalıyor). **Bölüm numaraları yenilendi** — bu notlardaki eski "spec §X" referansları yazıldıkları günkü hâli gösterir, güncel spec'te numara kaymış olabilir (tarihsel kayıt, düzeltilmez). Güncel yapı: 1 Bağlam · 2 Mimari · 3 Deklarasyon · 4 Nav API (4.2 geri, 4.4 back-interception) · 5 Deep link · 6 Result · 7 Modal · 8 Multiple back stack · 9 Transition · 10 MVI+binder (10.1) · 11 Fragment migration · 12 Host · 13 Test · 14 Codegen · 15 Paketleme · 16 Kilit kararlar · 17 Parked · 18 Sonraki adım. Tasarım fazı **tamam**; sıradaki = implementation planı.

## MVI artifact split + `GezginEntry` kaldırma + DI-detection (2026-07-02, review sonrası)

Review düzeltmeleri sırasında çıkan refinement'lar (kullanıcı):
- **MVI'ı core'dan ayır:** `@MviViewModel`/`GezginMvi`/`@ScreenEffect`/binder-codegen/DI-detection → **`gezgin-mvi`** (opt-in). `gezgin-core` DI/VM/MVI bilmez (`@Screen(route,nav)` + navigator'lar + routes/deeplink/topology + `GezginDisplay` + `GezginEntryScope`). Bağlantı seam'i: core'un `GezginEntryScope` + navigator'ları; mvi = entry-producer add-on. `gezgin-mvi → gezgin-core` bağımlı.
- **`GezginEntry` kaldırıldı** (over-engineering'di): `provideXEntry` = `GezginEntryScope` extension, Nav3'ün `entry<Route>{}`'sini **doğrudan** register eder (Zad `registerAll(scope)` deseni). Bundle (`xFeatureEntries()`) = **kullanıcı-yazımı** (codegen üretirse resolver override noktası kapanır); codegen yalnız bireysel `provideXEntry`'leri üretir. `GezginDisplay { … }` trailing lambda = `GezginEntryScope`.
- **DI-detection (B2 tersine):** `@MviViewModel` VM class'ını KSP-görünür kıldığı için codegen VM'in `@HiltViewModel`/`@KoinViewModel` + ctor `@Assisted`/`@InjectedParam`'ını okur → default resolver üretir (Hilt+Koin, androidx fallback). Ayrı gezgin-koin/hilt add-on'una gerek yok. B2 "düşür"den "feasible"ye döndü (infeasibility "VM tipi lambda gövdesinde"ydi; `@MviViewModel` KSP-görünür pozisyona taşıdı).
İşlendi: spec §2.4/§3.3/§10.1/§12/§14/§15 + findings B2.

## Fragment dialog bridge iptal — sadece `@FragmentScreen` (2026-07-04, M5)

Önceki karar (2026-06-29 "Fragment modal — iki mod + DialogFragment bridge") **geri alındı**. Kullanıcı: "FragmentDialog desteğinden vazgeçelim; dialog'ları çevirmek zorunda kalsınlar, sadece fragment (screen) desteği yeter; bu baş ağrısına değmez."
- **Kaldırılan:** `@FragmentDialog`/`@FragmentBottomSheet`, FM show/dismiss bridge, `DialogFragmentNavigator` deseni, soft AlertDialog.Builder geçişi, bridged `BottomSheetBehavior` bonusu.
- **Kalan:** `@FragmentScreen` (§11.1) — view-based Fragment tam-ekran leaf. Dialog'lar native `@Dialog`/`@BottomSheet`'e çevrilir.
- **Gerekçe:** DialogFragment kendi Window'unu FM'le sahiplenince Nav3 tek-otorite back stack'iyle çift-otorite oluşuyordu (M5: PD çift-restore, back double-fire, predictive yok, dismiss/pop race). Dialog en ucuz çevrilen UI (sadece Surface/Column) → interop maliyeti riske değmez. Kapsam daraldı, brownfield'ın tek kırılgan köşesi silindi.
İşlendi: spec §7/§11/§11.2/§14/§16 + findings M5.

## Graph modeli yeniden kuruldu — `@FlowGraph`/`@NavGraph`/`@TabHost`, tab/flow = annotation (2026-07-05, M6)

M6'yı çözerken kullanıcı daha derin bir dengesizlik gördü: "navgraph/nestedNavGraph/flow dengesini tutturamadık." Kök neden: tab-vs-flow rolü **gelen edge** (`@Switch`/`@GoTo`) ile belirleniyordu → aynı `@NestedNavGraph` iki farklı runtime container'ı (paralel switcher vs sekansel substack) temsil ediyordu, rol lokal okunamıyordu, reconstruction tipten çıkaramıyordu. İçgörü: iki primitif var — **stack** (sekansel) ve **switcher** (paralel); edge gizlice switcher semantiğini smuggle ediyordu.

**Yeni model (container = annotation, rol deklarasyonda):**
- gruplama = annotation yok (parent stack).
- `@NavGraph` = transparan own-stack (tab içeriği / browsable bölüm; iç deeplink var).
- `@FlowGraph` = **opak, katı** transactional flow (§8.1).
- `@TabHost` = switcher; `@NavGraph` çocukları = tab; `switchToX()` codegen.
- **Kaldırıldı:** `@NestedNavGraph`, `@Switch`.

**`@FlowGraph` katı kuralları (kullanıcı):** kara kutu (dışarıdan yalnız container'a giriş; iç deeplink yok; üyeler dışarı `@GoTo/@Replace/@Switch` yapamaz). Giriş↔tip: `ResultFlow<T> ⟺ yalnız @GoForResult`; result'suz `@FlowGraph ⟺ yalnız @GoTo` (→ aynı flow'a ikisi birden imkânsız, teorem). Çıkışlar: `quitWith(T)` (eski `finish`; Value), `quit()`/entry-`back` (Canceled), `@QuitAndGoTo(X)` (flow'u yıkıp X'e; yalnız result'suz; `ResultFlow`'da yasak — awaiting caller'ı strand eder). **Empty-stack invariant:** root-seviye `@StartDestination` ResultFlow/quit olamaz, yalnız `@QuitAndGoTo` ile biter (nested flow-start serbest; diğer op'lar ≥1 korur). Flow örnekleri: walkthrough (root, QuitAndGoTo-only), sign-up/checkout/KYC (ResultFlow, GoForResult, quitWith).

M6 bu temizlikte eridi (reconstruction tiplerden container tree). İşlendi: §2.4/§3.1/§4.1/§4.2/§5/§6/§8/§8.1/§14/§16 + findings M6.

## Graph modeli 2. tur reorganizasyon + V1 kapsam (multi-backstack & deep-link → V2) (2026-07-05)

Kullanıcı graph modelini yeniden düzenledi (§8.1 sonrası, Zad örneğiyle). **İşlendi (2026-07-05): design-doc §2.4/§3.1/§4/§5/§8/§8.1/§12/§14/§16/§17 + by-example + findings bu modele göre reconcile edildi.**

**İsimlendirme (final):** `@TabHost`→**`@TabGraph`**; `@Replace`→**`@ReplaceTo`** (param `clearUpTo`/`inclusive`); `@Switch`→**`@SwitchTo`** (tekrar explicit route-annotation — codegen-üretimi DEĞİL); yeni **`@DefaultTab`**, **`@QuitAndGoTo`**, **`quitWith`**. `@StartDestination` artık **yalnız FlowGraph**'ta.

**@NavGraph (final):** üyeli ama sırasız, **her üyesinden girilir**; **@StartDestination YOK**; **stand-alone olabilir** (TabGraph'a bağlı olmak zorunda değil; aynı NavGraph 2 yerden erişilebilir); graph'ların çoğu bu. Container'a `@GoTo` **yasak** (yalnız üyeye) — FlowGraph'ın tersi. Üyeler: `@GoTo`/`@ReplaceTo`/`@GoForResult`/`@BackTo`/`@SwitchTo`. **`@BackToStart` YOK** (start yok → explicit `@BackTo`). Yasak: `quitWith`/`quit`/`@QuitAndGoTo`/`ResultFlow`.

**@FlowGraph:** §8.1 aynı (opak, container'dan girilir, `@StartDestination` var, `@BackToStart` var).

**@TabGraph (final, V1 tek-stack):** en nadir; üyeleri = **tab route'ları** (nested NavGraph değil); `@DefaultTab` = açılış tab'ı; üyeye `@GoTo` yok → `@ReplaceTo`/`@SwitchTo`; doğal `@NoBack`; `@BackToStart` yok; `@SwitchTo` param'ı yalnız TabGraph üyesi.

**V1 kapsam kesintileri (→ V2):**
- **Multi-backstack (per-tab retained stacks) → V2.** Zad tek-stack, ihtiyaç yok. Bu tek karar TabGraph'ın 4+1 açığını (deep-link ebeveyn belirsizliği, `@SwitchTo` bağlamı, tab-back davranışı, tab-key/content) birden kapattı. V1 `@TabGraph` = **tek ortak stack**; `switchTo` tek stack üstünde tab'a gider.
- **Deep-link (tüm özellik) → V2.** V1 deep-link'siz çıkar. V2 yönü (**kesinleşmedi**): path-hiyerarşisi = back stack; `@DeepLink(segment, parent, [args])` **tekrarlanabilir**; codegen matcher + tam-URL builder + katalog (QA); URL ön-eki multi-parent zincirini seçer; içerik farkı = route arg. **`args` küçük bir düzeltmeyle kalkabilir** (kullanıcı notu) → V2'de netleşir.

**Açık kalan küçükler (spec reconciliation'dan önce):** (i) NavGraph'lar start'sızsa **app-level açılış route'u** nasıl belirlenir? (ii) stand-alone NavGraph'ta `@SwitchTo` runtime'da TabGraph yoksa ne olur? (iii) `clearUpTo=Graph::class` = yalnız temizleme-sınırı (container'a `@GoTo` hedefi değil) — netleştir. (iv) `@SwitchTo` explicit mi kalsın, TabGraph üyelerinden otomatik mi üretilsin?
