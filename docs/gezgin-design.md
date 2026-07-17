# Gezgin — Navigasyon Kütüphanesi Tasarım Spec'i

> Durum: **maintained current contract**. Bu belge uygulanan public yüzeyi ve Phase A ZAD-readiness sınırlarını anlatır.
> Hedef platform: Compose Multiplatform (Android öncelikli; iOS/Desktop/Web).
> Karar geçmişi + gerekçeler: [gezgin-design-notes.md](gezgin-design-notes.md) · Binder analizi: [gezgin-binder-location.md](gezgin-binder-location.md) · Tanıtım: [gezgin-by-example.md](gezgin-by-example.md).

---

## 1. Bağlam & konumlanma

Mevcut CMP/KMP navigasyon çözümlerinde tek pakette bulunmayan bir kombinasyon:

- **Type-safe + compile-time güvenli** — string route yok; tanımsız yere gidiş **derlenmez**.
- **Annotation + codegen** — az boilerplate; graph/wiring/result/binder codegen'de (deep-link → 🔮 V2).
- **State-as-data çekirdek** — test/log/restore/MVI'yı bedavaya getirir.
- **MVI/Redux dostu** ama dayatmasız.
- Nested navigation, result passing, process-death restore, modal, route/screen/app transition, UI'sız test ve **brownfield screen-Fragment migration**. Multiple back stack ve deep-link route dispatch V2'dir.

**Konumlanma:** Compose Destinations'ın codegen ergonomisi + Nav3/Decompose'un sahiplenilen state-as-data back stack'i + Circuit'in MVI yakınlığı — hepsi **Navigation 3 (Nav3)** üzerinde.

---

## 2. Temel mimari kararlar

### 2.1 Çekirdek — state-as-data, Gezgin sahibi
Back stack = gözlemlenebilir + serializable `Route` yapısı; Gezgin tutar. `navigate`/`back` ergonomik bir cephe, altta saf `state → state'` dönüşümü. Sonuç: UI'sız test, restore = serialize, log = state'i dinle, MVI = state'i gözle.

**İç temsil — entry kimliği (R2):** stack elemanı kullanıcının route'u değil, internal `@Serializable` zarf: `GezginKey(route: Route, id: Long, flowPath: List<Long>)`. `id` = push başına monotonik **instance kimliği** → Nav3 `NavEntry.contentKey` (Nav3 sözleşmesi: aynı contentKey = paylaşılan decorator state — eşit-değerli iki route böylece **ayrı** ViewModelStore + saved state alır). `flowPath` = kapsayan flow-instance zinciri (§8.1 flow-unit sınırı). `id` sayacı + stack, saved state'e beraber serialize edilir (PD'de kimlikler korunur). Public yüzey unwrap eder: `backStack: StateFlow<List<Route>>` — kullanıcı ve test API'si zarfı hiç görmez.

### 2.2 Altyapı — Nav3 üzerine (adapter sınırı: `GezginDisplay`)
- **Nav3**: render (`NavDisplay`), `List<NavKey>` back stack, transition + predictive back, per-entry lifecycle & saved state, scene (modal/two-pane).
- **Gezgin**: codegen, type-safe route/graph, typed result, MVI scoping, transition DSL. (deep-link + multiple back stack → V2, §17)
- `GezginDisplay` Nav3'ü tamamen sarar → Nav3 churn'ünü kullanıcıdan gizler.
- KMP durumu (2026-07 checkout'u): Android hedefi AndroidX `navigation3-runtime`/`navigation3-ui` **1.0.0** ve `lifecycle-viewmodel-navigation3` **2.10.0** kullanır. Desktop hedefi JetBrains `navigation3-ui` **1.0.0-alpha05** ve `lifecycle-viewmodel-navigation3` **2.10.0-alpha05** kullanır. `GezginDisplay` adapter sınırı platform ailelerini ayırır; Android runtime graph'ına JetBrains Navigation 3 UI/lifecycle artefaktı sızmamalıdır.

### 2.3 DI-agnostik (Ktorfit modeli)
Gezgin hiçbir DI'a bağlı değil. Codegen parça üretir (entry'ler, navigator'lar, `provideXEntry`); kullanıcı DI'ı kendi bağlar (Koin/Hilt/manuel). Magic yok.

### 2.4 İlke — compile-time → annotation; runtime → değer
- **Annotation** (KSP, compile-time): container (`@NavGraph` şeffaf / `@FlowGraph` opak; V1'de her route ikisinden birinde), edge'ler (`@GoTo`/`@ReplaceTo`/`@GoForResult`, geri `@BackTo`/`@BackToStart`, flow-çıkış `@Quit`/`@QuitAndGoTo`), `@NoBack`, `@StartDestination` (yalnız `@FlowGraph`), kind (`@Screen`/`@Dialog`/…), MVI (`@MviViewModel`/route-bound `@EffectHandler`, §10.1), fragment varyantı (`@FragmentScreen`). Result marker'ları (interface): `ResultRoute<T>` (screen), `ResultFlow<T>` (yalnız `@FlowGraph`). **V1 tek-stack;** `@TabGraph`/`@SwitchTo`/multi-backstack/`@DeepLink` → V2 (§17).
- **Runtime değer**: `transition`, navigate opsiyonları (annotation'da kodlanır), modal properties (`DialogContract`/`BottomSheetContract`).

---

## 3. Deklarasyon modeli

**Kural:** navigasyon grafiği & davranışı → **sealed route ağacı**; pikseller & sunum → **composable**.

### 3.1 Route & Graph
- Route = `@Serializable` data class/object; ait olduğu graph'ı **doğrudan supertype** olarak deklare eder (`: OrderGraph`). Namespace için graph'ın `sealed interface`'i **içine** nested yazılır **ya da** (büyük graph'larda) aynı pakette **ayrı dosyada top-level** durur — üyelik dosya konumundan değil supertype'tan gelir.
- Graph = `sealed interface`; alt-graph subtyping ile bağlanır (`OrderGraph : AppGraph`) — bağ **deklare edilen supertype**, nesting görsel kolaylık.
- **Her route bir graph içinde** sarılı; graph iki türden biri (V1):
  - **`@NavGraph`** → **şeffaf** grup: üyeleri sırasız, **istenen üyesinden** girilir (container'a `@GoTo` **yasak** → yalnız üyeye); `@StartDestination` **yok**; stand-alone olabilir (birden çok yerden erişilir). Graph'ların çoğu bu.
  - **`@FlowGraph`** → **opak** transactional flow (§8.1): kara kutu, dışarıdan yalnız **container'a** girilir, `@StartDestination` **var**, `ResultFlow<T>` **yalnız** buna takılır.
- **Üyelik = doğrudan (declared) supertype:** route/alt-graph, **doğrudan implement ettiği** graph/flow interface'inin üyesidir (`X : SignUpFlow` → `SignUpFlow`'un). Bir üye **ikinci bir graph/flow'u DOĞRUDAN implement edemez** (derleme hatası) — route'ta **E5**, graph/flow'da **N11** (tek ebeveyn; `OrderGraph : AppGraph` alt-graph deseni transitive miras yüzünden tetiklemez) — flow opaklığı bu teklik kuralına dayanır. Nesting **görsel**: bir üyeyi kapsayan graph'ın gövdesine yazmak üyeliği vermez, `: Parent` verir; iyi biçimli nested kodda ikisi çakışır (nested, `: Parent` yazmakla birebir aynı okunur — model bit-bit korunur). **Hibrit fallback:** yalnız kapsayan flow'u supertype olarak deklare **etmeyen** (`: Route`-tek) nested üyede en yakın kapsayan graph üye sayılır (ör. `ResultFlow` mirasından kaçınmak için bilinçle `: Route` bırakılan iç flow).
- `@StartDestination` (**yalnız `@FlowGraph`**) flow'un giriş üyesini işaretler. **Guardrail 1:** start, codegen'in argümansız kurabileceği bir şey olmalı (`data object`, ya da tüm param default/nullable — codegen default'suz nullable param'lara `null` geçer) — aksi halde **derlenmez**.
- **V1 tek-stack** (Nav3'ün tek listesi). Paralel per-tab stack, tab switcher (`@TabGraph`), deep-link → **V2** (§17). Bottom-nav = uygulama-yönetimli (Gezgin `goTo`/`replaceTo` verir).

```kotlin
@NavGraph @Serializable                                  // şeffaf grup; üyeden girilir, start yok
sealed interface OrderGraph : AppGraph {
    @GoTo(OrderDetailRoute::class) @Serializable data object OrdersRoute : OrderGraph

    @GoTo(OrderInvoiceRoute::class)
    @Serializable
    data class OrderDetailRoute(val orderId: String) : OrderGraph {
        override val transition get() = transition { /* forward; back; predictive */ }
    }
    @Serializable data class OrderInvoiceRoute(val orderId: String) : OrderGraph
}
```

**Flat-file yerleşim (büyük graph'lar için önerilen):** üyelik supertype'tan geldiği için bir alt-graph/flow, kapsayan graph'ın `sealed interface`'i içine nested yazılmak **zorunda değil** — aynı pakette **ayrı bir dosyada** top-level durabilir (`@FlowGraph @Serializable sealed interface SignUpFlow : AuthGraph { … }`; Kotlin sealed kuralı alt-tipi aynı paket+modülde tutar). Nested ve flat-file **denk** okunur; tek-dosya-graph'lar 500-1000 satıra şiştiğinde flow'ları kendi dosyalarına bölmek okunurluğu korur. Canlı kanıt: `sample/navigation` (`SignUpFlow.kt`, `AvatarFlow.kt` — `AvatarFlow` içinde `ZoomFlow` nested kalır).

> **⚠️ Sürüm/PD uyarısı:** bir flow'u nested ↔ top-level arası taşımak **FQ'sunu değiştirir** (`AuthGraph.SignUpFlow` → `SignUpFlow`). `flowPath` ve polimorfik serializer adları FQ-tabanlı olduğundan, **önceki app sürümünden** serialize edilmiş bir back-stack yeni yerleşimle restore **edilemez** (bilinmeyen tip → decode hatası/kayıp entry). Yerleşim değişimi bir migration'dır: canlı kurulu tabanı olan app'te bir flow'u bir kez konumlandır, sonra FQ'sunu sabit tut.

### 3.2 Kind (composable'da)
Kind annotation composable'da durur ve üç iş yapar: destination = binding, sunum kind'ı, hedef route'a **açık** bağ (`route` zorunlu).
- `@Screen` (tam ekran, varsayılan) · `@Dialog` / `@BottomSheet` / `@FullscreenModal` (modal, bkz. §7).
- Route her zaman açık verilir: `@Screen(OrdersRoute::class)` (sentinel/çıkarım yok). `route:` param'ı opsiyonel — route verisini taşır ve varsa tipi annotation route'uyla aynı olmalı (aksi = derleme hatası).
- `@Screen` **repeatable**'dır: aynı stateless composable birden çok route'a bağlanabilir. MVI-mode'da her route kendi `@MviViewModel(route)` ve route-bound effect handler'ına sahiptir; ortak content'in State/Intent tipleri bütün bağlarla uyumlu olmalı, Effect/Navigator tipleri route'a göre farklı olabilir (§10.1).

### 3.3 Multi-module yerleşim
Kotlin `sealed` alt-tipleri **aynı modül**de olmak zorunda → tüm sealed graph'lar **tek nav modülünde** toplanır (cross-module `OrderGraph : AppGraph` derlenmez):
```
core:domain        → route-arg domain modelleri (@Serializable)
   ▲
core:navigation    → TÜM sealed graph'lar + route'lar + contract'lar (Dialog/BottomSheet/ResultRoute); yalnız core:domain'e bağımlı
   ▲
feature:A / feature:B / … / :app   → hepsi core:navigation'ı görür
```
- Tek sealed ağaç → tüm route/navigator herkese görünür (cross-feature `@GoTo` **derlenir**); polimorfik serialization **compile-time** (runtime merge yok; deep-link reconstruction V2'de aynı ağaçtan gelir).
- **Codegen dağılımı:** `core:navigation` → tipli navigator'lar + graph topology + `SerializersModule` (deep-link tablosu **🔮 V2**, §5). `feature:X` → `@Screen`/`@EffectHandler`/`@MviViewModel` → codegen `GezginEntryScope.provideXEntry`'ler; **kullanıcı** `xFeatureEntries()` bundle'ını yazar. `:app` → `GezginDisplay { … }` + back stack (montaj).
- Her feature KSP'si **yalnız kendi modülünü** işler; cross-module **tip** görünürlüğü yeter (annotation okuması gerekmez → ksp#527 yok). Navigator ctor `internal`; core:navigation her navigator için **public factory** üretir (`fun RawNavigator.xNavigator(): XNavigator`) → feature'ın üretilen entry kodu navigator'ı bu factory'den alır (cross-module derlenir).
- **Nav modülü tek-paket kısıtı (`[PKG]`):** bir nav modülündeki **her graph/route AYNI pakette** olmalı — bu ortak paket navigator'ların üretim hedefidir. Navigator'lar hep bu hedefe üretilir, ama cross-module probe/factory-import route'un **deklarasyon paketinde** arar; alt-paketlere bölünmüş bir nav modülü navigator'ı route'un paketi DIŞINDA üretir → cross-module lookup sessizce ıskalar. Bu yüzden processor ayrışık paketi **derleme hatasıyla reddeder** (fail-loud, sessiz-kırık yerine). Alt-paketlere bölme ihtiyacı → 🔮 V2.
- Route-arg domain modelleri **`@Serializable` olmalı** (back stack serialize/PD). Tek-modül app: her şey tek modülde, aynı model tam ağaçla çalışır.

---

## 4. Navigasyon API — tipli per-source navigator

Codegen **her source ekran için** tipli navigator üretir; her deklare edilen kenar = bir metot. Eklenmeyen hedefe metot yoktur → **derlenmez**. Enforcement = API şeklinin doğal sonucu (`enforceEdges` flag'i yok). Maintained kullanım ve ZAD entegrasyonu navigasyonu yalnız üretilmiş tipli metotlarla dispatch eder.

### 4.1 İleri edge seti (route'ta annotation)
| Annotation | Üretilen metot | Operasyon |
|---|---|---|
| `@GoTo(vararg, singleTop = true)` | `goToX(params)` | push (`singleTop` = yalnız **top** ile değer karşılaştırması; stack ortasındaki eş değere dokunulmaz) |
| `@ReplaceTo(target, clearUpTo = Self::class, inclusive = true)` | `replaceToX(params)` | `clearUpTo`'ya kadar temizle + push; sentinel = `clearUpTo: KClass<out NavKey> = Self::class` (annotation param'ı object alamaz) = mevcut route → default = mevcut'u değiştir. Flow üyesinde `clearUpTo` flow-dışına işaret edemez (**E4**, §8.1) |
| `@QuitAndGoTo(X)` | `quitAndGoToX()` | flow'u topluca yıkıp X'e (replace-like, §8.1); yalnız result'suz `@FlowGraph` üyesinde |
| `@GoForResult(target)` | `launchX(params)` · `xResults: Flow<NavResult<T>>` · `suspend goToXForResult(): NavResult<T>` | tetik + re-attach stream + sugar (§6); hedef zorunlu `ResultFlow`/`ResultRoute` (**E2**) |

Metot adı hedeften "Route"/"Screen" eki atılarak türetilir. Generated metotlarda **per-call options lambda yok** — davranış annotation'dan; aynı hedefe farklı davranış = farklı kenar = farklı metot. **İsim çakışması (N9):** aynı hedefe iki edge (ör. farklı `clearUpTo`) → aynı metot adı → opsiyonel `name=` ile ayrıştır (`@ReplaceTo(X, clearUpTo = A, name = "replaceViaA")` → `replaceViaA()`); isimsiz çakışma = derleme hatası.

### 4.2 Geri
İleri **topoloji yaratır** (deklare); geri **geçmişi tüketir**. İlke: **declared olan sessizce silinmez** — route'un annotation'larına + interface'lerine bakınca navigator'ın tüm geri yüzeyini görürsün.

- **Tek implicit:** `back()` — her ekranda (pop 1). Tek opt-out: `@NoBack`.
- **Contract'tan:** `backWithResult(T)` — ekran `ResultRoute<T>` (yani `@GoForResult` hedefi) ise.

**Declared geri annotation'ları (ileri ile simetrik, opt-in):**
| Annotation | Üretilen | Operasyon |
|---|---|---|
| `@BackTo(Y::class, inclusive = false)` | `backToY()` | Y atasına kadar pop (default `inclusive=false` → Y'nin üstüne düş; iki Y instance'ı varsa **nearest-ancestor**, M3). Y stack'te yoksa **no-op + `NavEvent.BackToTargetMissing`** (history runtime gerçeği, topoloji garantisi değil) |
| `@BackToStart` | `backToStart()` | flow başına (`raw.backTo(Flow::class, inclusive=false)`) |
| `@Quit` | `quit()` | flow'dan çık (`raw.backTo(Flow::class, inclusive=true)`) |

- `@BackToStart`/`@Quit` parametresiz; flow class'ını codegen doldurur; **yalnız `@FlowGraph` içindeki** ekranlarda üretilir (`@NavGraph`'ta start yok → `@BackToStart` yok, geri için explicit `@BackTo`; kök seviyede de derlenmez). `@Quit` = result'suz çıkış (`ResultFlow`'a `Canceled`); `ResultFlow<T>` ekranında ayrıca `quitWith(T)` (Value ile çık — §6/§8.1). Flow entry'sinde `back()` = `quit()`. Flow'u yıkıp dışarı bir hedefe: `@QuitAndGoTo(X)` (§4.1/§8.1).
- **`@NoBack` (terminal ekran, örn. result):** iki codegen etkisi — (1) navigator'dan `back()` çıkar; (2) codegen entry content'ini **Gezgin-sahipli enabled `NavigationBackHandler`** ile sarar → back tüketilir (pop yok), predictive preview o entry'de **hiç başlamaz**. (Gerekçe M5′: NavDisplay'de per-entry "back'i tüket / preview kapat" public API'si **yok** — `isBackEnabled` içeride `previousEntries.isNotEmpty()`; buna karşın entry içindeki nested handler alpha09'dan beri dispatcher LIFO'sunda kazanır. Kaynak-doğrulamalı ama resmî dokümanda yazmadığından on-device doğrulama implementation'da.) **Runtime kural:** root entry'de `noBack` yok sayılır → back = `onRootBack` (kullanıcı app'e hapsolmaz). **Back authority:** screen entry'lerde `GezginDisplay`; **modal'larda dialog'un kendi window'u** (`dismissOnBackPress`, §7 carve-out). **Declared olana dokunmaz** (`@BackTo`/`@BackToStart`/`@Quit`/`backWithResult` kalır) → `@NoBack` + `ResultRoute` veya `@NoBack` + `@BackToStart` serbest. Ekranın kendi `BackHandler`'ı daha iç → `@NoBack` + `BackHandler { nav.quit() }` = geri'yi çıkışa yönlendir.

### 4.3 Operasyon modeli (library-agnostik)
İleri = push + opt (popUpTo/singleTop) · Geri = pop + opt (backTo/backToStart/quit/withResult) · Lateral = switch (multiple back stack, V2) · Modal = bir entry'nin sunum biçimi · Deep link = dışarıdan stack kurma modeli (yalnız V2; current dispatch API'si yok).

### 4.4 Dinamik geri-yakalama (interception) — Gezgin API'si DEĞİL
State'e bağlı "geri'yi ben mi yöneteyim" (wizard sayfa-geri, kaydedilmemiş değişiklik) için Gezgin özellik üretmez — geliştirici standart multiplatform `BackHandler(enabled) { }` / `PredictiveBackHandler`'ı `@Screen` içinde kullanır (`enabled=false` → jest düşer → Gezgin default observable pop + §9 predictive; `true` → yakala). VM-driven'da back = UI event → intent → VM karar verir. **Tek library-side yükümlülük:** `GezginDisplay` default back'i standart dispatcher / Nav3 üstünden yapar → kullanıcı `BackHandler`'ı (inner) öncelik alır + default pop observable kalır.

---

## 5. Deep link — **V1 kapsamı dışı (→ V2, §17)**
V1 deep-link'siz çıkar. V2 yönü (kesinleşmedi): **path-hiyerarşisi = back stack.** Her deep-link'lenen route kendi segment'i + deep-link parent'ını `@DeepLink(segment, parent = X::class)` (**tekrarlanabilir**) ile verir; codegen full-URL'leri birleştirir, bir matcher + tip-güvenli URL builder + katalog (QA) üretir. Gelen URL'in ön-eki multi-parent zincirini seçer; içerik farkı = route arg. `{arg}` placeholder'ları derleme-zamanı route property'leriyle eşleşir — **deep-link `annotation` kalmalı** (KSP annotation string'ini okur; property initializer'ı okuyamaz → placeholder doğrulaması ancak annotation'da mümkün). Detay V2'de.

---

## 6. Result passing
- Caller route'a `@GoForResult(target)`; **hedef route** `: ResultRoute<T>` ile ürettiği tipi deklare eder (DRY, consistency-by-construction). Codegen hedefe `fun TargetNavigator.backWithResult(result: T)`, caller'a aşağıdaki üçlüyü üretir.
- `NavResult<T>` = `Value(T)` | `Canceled` (düz `back` = Canceled).
- **Üretilen yüzey — launch/receive ayrımı (R1, ActivityResult modeli):** `@GoForResult` edge'i **üç üye** üretir:
  - `launchX(params)` — tetik; sonuç dönmez, push eder.
  - `val xResults: Flow<NavResult<T>>` — **re-attach yüzeyi**; replay-until-consumed, her recreation'da (config change + **PD dahil**) yeniden collect edilir. PD-safety'nin taşıyıcısı budur.
  - `suspend goToXForResult(params): NavResult<T>` — sugar (= launch + ilk sonuç). **Dokümante sınır:** VM ömrü içinde (config change dahil) güvenli; PD sınırını aşacaksan stream'i kullan.
- **PD-safe slot:** sonuç canlı continuation'da değil, navigator'ın serializable state'indeki **keyed slotta** akar. Slot key = **`(callerEntryId, edgeId)`** (`callerEntryId` = `GezginKey.id`, §2.1). Caller-entry + edge başına **en fazla bir in-flight istek**: bekleyen istek varken `launchX`/`goToXForResult` tekrar çağrılırsa push edilmez, mevcut isteğe **re-attach** edilir (idempotent → `LaunchedEffect`'in config-change'te yeniden fırlaması zararsız). Teslim = ilk tüketiciye bir kez (suspend ve stream aynı slotu paylaşır, çakışmaz). Caller entry stack'ten kalkarsa slot düşer + **`NavEvent.ResultDropped`** yayınlanır (sessiz kayıp yok).
- **Flow-level result (M2):** bir *flow* de sonuç döndürebilir — **`@FlowGraph`** interface'i `: ResultFlow<T>` implement eder (route için `ResultRoute<T>`'nin paraleli; `ResultFlow` **yalnız** `@FlowGraph`'a takılır, aksi derleme hatası). `@GoForResult(CheckoutFlow::class)` → aynı üçlü: `launchCheckout(startArgs)` / `checkoutResults: Flow<NavResult<T>>` / `suspend goToCheckoutForResult(startArgs)` (flow start'ını push eder, keyed slot). Flow içindeki **her** ekranın navigator'ı `quitWith(result: T)` alır (T = en yakın kapsayan `ResultFlow`'un arg'ı; KSP supertype'tan okur). `quitWith` = tüm flow alt-stack'ini **atomik** pop + caller'a `Value` (state-as-data → atomiklik + PD bedava). `quit()`/entry-`back` = Canceled. **Giriş↔tip bağı:** `@GoForResult` hedefi **zorunlu** `ResultFlow` (`@GoForResult(result'suz)` = hata); `@GoTo` hedefi **ResultFlow olamaz** (= hata) → aynı flow'a hem `@GoTo` hem `@GoForResult` kendiliğinden imkânsız. `ResultFlow`'da `@QuitAndGoTo` **yasak** (awaiting caller'ı strand eder). `@GoForResult(X)` X bir `ResultRoute` route ise → screen-mode (codegen ayırt eder). Nested `ResultFlow` nadir: `quitWith` en-yakın-kapsayanı bitirir.

---

## 7. Modal (Dialog / BottomSheet / Fullscreen)
Hepsi normal back stack entry; fark sadece Nav3 **SceneStrategy** ile overlay render (arka görünür).
- Kind composable'da (`@Dialog`/`@BottomSheet`/`@FullscreenModal`); composable = **sadece içerik** (Surface/Column).
- Properties = route'ta **opsiyonel** `DialogContract`/`FullscreenModalContract`/`BottomSheetContract`: SABİT = body override, KOŞULLU = constructor param (→ generated navigate param, serialize → PD-safe). `DialogContract`: `dismissOnClickOutside`, `dismissOnBackPress`, `usePlatformDefaultWidth` (spec'in soyut `layout`'unun somut `DialogProperties` karşılığı — `false` = içerik genişliği/geniş modal). `FullscreenModalContract`: yalnız dismiss'ler (`usePlatformDefaultWidth` YOK — tam-ekran tanımı gereği SABİT `false`). Contract yoksa adapter tip-varsayılan `DialogProperties`. Annotation'da prop yok.
- Pencere + dismiss→pop Gezgin scene'inden; dismiss (tap-outside/swipe) = `Canceled`.
- BottomSheet: `BottomSheetSceneStrategy` core'a bundle; swipe-dismiss animasyonlu pop; `controller: GezginSheetController` opsiyonel param ile composable'a enjekte (`LocalGezginSheetController`'dan). `BottomSheetContract.sheetGesturesEnabled` varsayılan `true`'dur ve doğrudan Material 3 `ModalBottomSheet(sheetGesturesEnabled = …)` değerine iner.
- Sonuç: dialog/sheet doğal sonuç-üreticisi (`ResultRoute<T>` + `backWithResult`). Entry-scoped VM burada da geçerli.
- **Guardrail:** `dismissOnBackPress = true` + `@NoBack` = **kuruluş-zamanı runtime guard** (tezat: `@NoBack` geri'yi yutar, `dismissOnBackPress` geri'yle kapat der). `dismissOnBackPress` runtime değer (route-instance property, KSP okuyamaz) → derleme yerine entry kuruluşunda (`toNavEntry`) `require` reddeder.
- **Guardrail (`@NoBack` × `@BottomSheet`, runtime):** bu birleşim artık geçerlidir, ancak route hem `dismissOnBackPress = false` hem `sheetGesturesEnabled = false` sağlamalıdır; aksi entry kuruluşunda fail-loud olur. `dismissOnClickOutside` bağımsızdır. ZAD `preventDismiss` eşlemesi üçünü de `false` yapar:

```kotlin
override val dismissOnBackPress: Boolean get() = false
override val dismissOnClickOutside: Boolean get() = false
override val sheetGesturesEnabled: Boolean get() = false
```
- **Guardrail (root, runtime):** modal kind'lı entry stack'te tek başına kalamaz — Nav3 `OverlayScene` `require(overlaidEntries.isNotEmpty())` (doğrulandı). `rememberNavigator(start)` kuruluşta modal-start'ı reddeder (§12).
- **`@QuitAndGoTo(modal)` — KSP-okunamaz, dokümante sınır (Task 4.3 adjudication):** Bu guard'ın "KSP uyarısı" olması **kanonik cross-module mimaride (§3.3) İMKANSIZ.** Sebep: `@QuitAndGoTo(X)` **edge**'i route interface'inde (nav modülü, `GraphModel`) yaşar; X'in modal-**kind**'ı ise `@Dialog`/`@BottomSheet`/`@FullscreenModal` **composable**'ında (ayrı feature modülü, `EntryFunctionModel`) yaşar. Nav-modülünün KSP çalıştırması edge'i görür ama hedefin kind'ını GÖREMEZ (composable başka modülde, resolver'da yok); feature-modülünün çalıştırması kind'ı görür ama `model.graphs.isEmpty()` → edge doğrulaması orada koşmaz. Yalnız tek-modül (monolit) kurulumda join mümkün, cross-module'da değil → topolojiye göre tutarsız sinyal olurdu; bu yüzden **best-effort monolit-only uyarısı da ŞİPLENMEZ.** Ayrıca `@QuitAndGoTo(modal)` runtime'da **çökmez**: `[.., Caller, X]` → Caller X'in altında kalır → `OverlayScene` overlaidEntries dolu (geçerli overlay). Yani bu yalnız advisory bir tasarım-smell; correctness ihlali değil. **Sonuç: §7'nin TÜM modal guard'ları runtime'dır** — modal props route-instance değeri (KSP-görünmez, §2.4) ve modal kind feature-modülü composable'ında (nav-modülü edge/graph'ından ayrık); KSP'nin hiçbirine erişimi yok.
- **Back:** dialog entry'sinde back'i dialog'un kendi window'u tüketir (`dismissOnBackPress` → pop = `Canceled`); predictive preview yok. Back authority modal'larda window'dadır (§4.2 carve-out).
- **Modal-üstü-modal (N8):** dialog/sheet normal back-stack entry → doğal stack'lenir (`[.., dialogB, dialogC]`, back tek tek kapatır); Nav3 `OverlayScene` destekliyor. Design = destekli; N-derin overlay scrim katmanı + predictive-back **implementation'da on-device doğrulanacak**.

---

## 8. Navigasyon topolojisi (V1 tek-stack)
- **Temsil:** V1'de **tek** back stack (Nav3'ün düz listesi). Paralel per-tab stack (Map-of-stacks) + tab switcher → **V2** (§17).
- **İki graph türü:** `@NavGraph` = şeffaf grup (üyeden giriş, serbest nav, §3.1); `@FlowGraph` = opak transactional flow (§8.1). Rol deklarasyonda sabit, lokal okunur.
- **Flow davranışı:** flow tamamlanınca `quitWith(result)` = flow'un alt-dizisini **atomik** pop + caller'a sonuç (§6; `ResultFlow<T>`); `@Quit`/entry-`back` = result'suz (`Canceled`); `@QuitAndGoTo(X)` = flow'u yıkıp X'e.
- **Kök/boş back stack (N5):** kökte (dip) son entry'de `back()` → `GezginDisplay`'in opsiyonel `onRootBack`'i (default = platform `expect/actual`: Android `finish()`, desktop no-op — Esc root'ta yutulur, iOS no-op). Root asla programatik boşalmaz (§8.1 empty-stack invariant'ı, **runtime guard**).
- Save/restore: serializable state → config change + PD otomatik.

### 8.1 `@FlowGraph` — katı, opak flow
Transactional sub-journey (checkout, sign-up, KYC, walkthrough). Kara kutu:
- **Giriş:** yalnız container'a — iç üyeye dışarıdan her edge = **E3**. Result'suz flow'a normal ileri-edge'lerle girilir (`@GoTo`/`@ReplaceTo`/`@QuitAndGoTo` → start'a iner); `ResultFlow`'a **yalnız** `@GoForResult` (**E1**). (Deep-link V2'de de yalnız container'a.)
- **İçeride:** serbest `@GoTo`; `@GoForResult(External)` = round-trip (çağır, flow'a dön), çıkış değil.
- **Dışarı çıkış yok:** üyeler `@GoTo`/`@ReplaceTo` ile dışarı **çıkamaz**; flow üyesinde `clearUpTo` flow-dışına işaret edemez (**E4** — bekleyen caller temizlik kurbanı olamaz); yalnız aşağıdaki çıkışlar.

**Derleme matrisi (edge × hedef):**

| Hedef ↓ / Edge → | `@GoTo` | `@ReplaceTo` | `@GoForResult` | `@QuitAndGoTo` |
|---|---|---|---|---|
| Normal route / `@NavGraph` üyesi | ✅ | ✅ | ✅ (yalnız `ResultRoute`, aksi **E2**) | ✅ |
| result'suz `@FlowGraph` container | ✅ | ✅ | ❌ **E2** | ✅ |
| `ResultFlow` container | ❌ **E1** | ❌ **E1** | ✅ | ❌ **E1** |
| Herhangi bir flow'un **iç üyesi** | ❌ **E3** | ❌ **E3** | ❌ **E3** | ❌ **E3** |

> **E1:** `ResultFlow`'a giriş yalnız `@GoForResult` (bekleyen caller şart) · **E2:** `@GoForResult` hedefi `ResultRoute`/`ResultFlow` olmalı · **E3:** flow kara kutu — iç üyeye dışarıdan edge yok · **E4:** flow üyesinde `clearUpTo` flow sınırını aşamaz (üyelik **transitive** — flow içindeki nested `@NavGraph` üyeleri flow-içi sayılır) · **E5:** route ikinci bir graph interface'i implement edemez (§3.1). Ayrıca: `@NoBack`+flow-start · `ResultFlow` non-`@FlowGraph`'ta · isimsiz edge çakışması (N9) · **N11:** graph/flow 2+ annotated graph/flow'u doğrudan implement edemez (E5'in graph-seviyesi karşılığı; tek ebeveyn) · **N12:** öksüz nested `@FlowGraph` (non-graph deklarasyona gömülü, hiçbir annotated graph'a bağlı değil) reddedilir — **top-level `@FlowGraph` muaf** (meşru root/bağımsız flow) · **N13:** her `@NavGraph`/`@FlowGraph` `sealed interface` olmalı — cross-file üye keşfi `getSealedSubclasses`'e dayanır ve non-sealed tipte boş döner, yani ayrı dosyada `: G` deklare eden üye sessizce düşer. **Davranış değişikliği (Faz 8):** non-graph deklarasyona (ör. namespace `object`) gömülü root `@FlowGraph`, Faz 8 öncesi geçerli bir bağımsız root flow iken artık N12 ile reddedilir → root flow'u **top-level**'a taşı ya da annotated bir parent ver.

**Çıkışlar:**

| Flow tipi | Çıkışlar | Root olabilir? |
|---|---|---|
| `ResultFlow<T>` | `quitWith(T)` · `quit()`=Canceled · entry-`back`=Canceled | **Hayır** — runtime: `rememberNavigator(start)` kuruluşta `require` (§12); `@QuitAndGoTo` da yasak (E1) |
| result'suz `@FlowGraph` | `quit()` · entry-`back` · `@QuitAndGoTo(X)` | **Evet** — runtime guard: root flow'da `quit()`/entry-`back` → `onRootBack` (boş stack yok) |

- **Flow-unit sınırı (`flowPath`, §2.1):** flow'a her giriş yeni **instance id** üretir; üye push'ları kaynağın `flowPath`'ini miras alır (nested flow zinciri uzatır); round-trip dış hedefler almaz. `quitWith`/`quit`/`@QuitAndGoTo` = "tepeden aşağı, `flowPath`'inde bu flow-id'si olan tüm entry'ler" **atomik** pop → sınır tip değil **kimlik**; re-entrancy (flow üyesinin kendi flow'una `@GoForResult`'u dahil) belirsizlik yaratmaz.
- **`@QuitAndGoTo(X)`:** flow-unit'i topluca yıkar, yerine X koyar → `[.., Caller, X]` (caller altta kalır; X'ten back → Caller). X = normal route ya da result'suz flow container'ı; başka flow'un iç üyesi olamaz (**E3**).
- **Empty-stack invariant'ı (runtime):** aktif root stack asla boşalmaz. Root'u işaretleyen annotation olmadığından (root = `rememberNavigator(start)`'ın runtime değeri, §12) bu **derleme değil runtime garantisidir**: kuruluşta ResultFlow-start `require`'ı; root flow'da `quit`/entry-`back` → `onRootBack`. Nested flow-start'lar serbest (nested boşalır, parent kalır); diğer op'lar ≥1 korur (Replace push eder)..

**Örnek (checkout, ResultFlow):**
```kotlin
@FlowGraph @Serializable
sealed interface CheckoutFlow : AppGraph, ResultFlow<OrderId> {
    @StartDestination @Serializable data object Cart : CheckoutFlow
    @GoTo @Serializable data object Payment : CheckoutFlow      // içeride @GoTo serbest
    // bir yerde: nav.quitWith(orderId)  → caller'a Value(orderId), flow atomik yıkılır
    // iptal:     nav.quit()             → Canceled
}
// caller: val r = nav.goToCheckoutForResult(cart)   // r: NavResult<OrderId>
```

---

## 9. Transition
- Runtime değer: route'ta `override val transition get() = transition { forward; back; predictive }` (**getter zorunlu** — backing field yok → kotlinx.serialization'a takılmaz; initializer'lı hâli serializable-olmayan property yüzünden derlenmez) — doğrudan ya da **opsiyonel `ScreenContract`** üzerinden (contract = route'un runtime sunum değerlerinin tipli evi; §7 `DialogContract`/`BottomSheetContract`'ın paraleli, **hepsi opsiyonel**). App/graph seviyesi = ağaç boyunca devralınan değer. Predictive yazılmazsa = back (platform notu: predictive preview Android; iOS edge-swipe, desktop/web Esc=back — §2.2).
- **Per-call override (N1):** typed navigator metotlarında **yok**; davranış annotation'dan gelir. Maintained sözleşme ad-hoc dispatch veya per-call transition override önermiyor.
- Codegen değil; `route` (NavKey) → entry metadata'sındaki `NavDisplay.TransitionKey` ailesine iner. Cascade en içteki (screen) > graph > app.

---

## 10. MVI scoping, strict navigation boundary & state observability

- **Gezgin state-holder dünyasına girmez.** `GezginDisplay` Nav3 ViewModel/saveable/saved-state decorator'larını kurar; `@MviViewModel(route)` ile eşlenen VM entry-scoped'dur.
- **Maintained ZAD biçimi tektir:** `Intent -> onIntent -> effect -> @EffectHandler(route) -> typed navigator`.
- ViewModel navigator tutmaz ve doğrudan navigasyon çağırmaz. State'i günceller veya route-specific Effect emit eder.
- Route-bound handler `Flow<E>`'yi gözler ve yalnız o route'un üretilmiş typed navigator metodunu çağırır.
- `navigator.events` ve `navigator.backStack` observe-only log/analytics/devtools yüzeyleridir; veto/rewrite middleware V2'dir.

### 10.1 Binder modeli

Core-mode genel Gezgin kullanımı için mevcut kalır: `@Screen fun XScreen(route, nav)` içeriğini kullanıcı bağlar. `gezgin-mvi` kullanan maintained örnekler ise aşağıdaki strict sözleşmeyi izler:

```kotlin
interface GezginMvi<out S, in I, out E> {
    val uiState: StateFlow<S>
    val effects: Flow<E> get() = emptyFlow()
    fun onIntent(intent: I)
}

@Screen(HomeRoute::class)
@Screen(FeaturedRoute::class)
@Composable
fun ColumnScope.SharedContent(state: SharedState, onIntent: (SharedIntent) -> Unit) { /* ... */ }

@MviViewModel(HomeRoute::class)
class HomeViewModel : ViewModel(), GezginMvi<SharedState, SharedIntent, HomeEffect> {
    private val effectSink = GezginEffects<HomeEffect>()
    override val effects: Flow<HomeEffect> = effectSink.flow
    override fun onIntent(intent: SharedIntent) {
        if (intent == SharedIntent.OpenNext) effectSink.send(HomeEffect.OpenFeatured)
    }
}

@EffectHandler(HomeRoute::class)
@Composable
fun HomeEffectHandler(effects: Flow<HomeEffect>, nav: HomeNavigator) {
    ObserveEffects(effects) { effect ->
        if (effect == HomeEffect.OpenFeatured) nav.goToFeatured()
    }
}
```

- `@Screen` repeatable'dır. Her route kendi `@MviViewModel(route)` binding'ine sahiptir. Paylaşılan content'in State ve Intent tipleri tüm bağlı route'larla uyumlu olmalı; Effect ve Navigator route'a göre farklı olabilir.
- `@EffectHandler(route)` repeatable ve route-explicit'tir. Handler `Flow<E>` ve isteğe bağlı, o route'a ait `nav: XNavigator` alır. Duplicate, eksik, yanlış effect tipi veya yanlış navigator tipi fail-loud processor hatasıdır.
- `@MviViewModel(route)` ve `GezginMvi<S,I,E>` birlikte zorunludur. Content/handler/VM aynı KSP modülünde route'a göre eşlenir; content'in `(state, onIntent)` ve handler'ın `Flow<E>` tipleri VM sözleşmesine karşı doğrulanır.
- DI detection Hilt/Koin/androidx resolver'ı üretebilir. Assisted parametreler Gezgin'in sağlayabildiği route alanlarıyla sınırlıdır; ekstra content dependency'leri `provideXEntry` üzerinde açık resolver param'ı olur.
- Route-bound `@TopBar`/`@BottomBar`, yalnız ZAD geçişi için temporary `gezgin-mvi` API'leridir. Generated entry sırası: dış `Column` -> top -> `Column(Modifier.fillMaxWidth().weight(1f)) { Screen(...) }` -> yalnız `!imeVisible` iken bottom. Bu API kalıcı container/scroll/screen-scope sözleşmesi değildir ve migration sonunda kaldırılır.

**Deprecated compatibility bridge:** `@ScreenEffect` route argümanı taşımayan eski yüzeydir. Processor yalnız exact `Flow<E>` tipi, explicit handler tarafından işgal edilmemiş **tam bir** VM route'u bulursa inference yapar. Sıfır aday (`MV6`), birden fazla boş aday (`MV17`), explicit overlap (`MV18`) veya aynı route'a birden fazla legacy handler (`MV9`) derlemeyi kırar. Yeni kod yalnız `@EffectHandler(Route::class)` kullanır.

Güncel binder açıklaması: [gezgin-binder-location.md](gezgin-binder-location.md).

---

## 11. Brownfield migration — Fragment interop (Android)

**Model:** Gezgin **ana navigasyon (root)** olur; mevcut Fragment'lar **yaprak** olarak Gezgin destination'larına sarılır. Ayrı "legacy'ye git / dışarı çık" interop yüzeyi **yok** — legacy ekran yalnız **fragment-backed bir route** (`goToLegacyScreen` → `@GoTo`; `goBackToLegacy` → `back()`/`quit()`). `@FragmentScreen` Android-only (dialog varyantı yok — §11.2).

### 11.1 `@FragmentScreen` — class üzerinde, constructor YOK
```kotlin
@FragmentScreen(OrderChainRoute::class)
class OrderChainFragment : Fragment() {
    private val args by gezginArgs<OrderChainRoute>()       // tipli; route Bundle'dan → PD-safe
    private val nav  by gezginNav<OrderChainNavigator>()    // @Screen'in 'nav' param'ının karşılığı
    // onViewCreated: nav.goToOrderDetail(...) ; nav.back()
}
```
- `@FragmentScreen` kullanan uygulama `Application.onCreate()` içinde, FragmentManager restore başlamadan önce `Gezgin.initFragmentInterop(gezginJson)` çağırır. Bu erken kurulum gerçek process-death restore yolu için zorunludur ve mevcut, tamamlanmış screen-restore sözleşmesidir.
- Parametreli Fragment ctor **yasak** (framework no-arg ctor + Bundle ile yeniden yaratır → parametreli ctor çöker/arg'ı sessizce kaybeder). Tipli arg `gezginArgs<Route>()` accessor'ından: route Fragment'ın kendi `arguments: Bundle`'ından decode edilir (**registry değil — Bundle-backed**, PD-safe), `onUpdate` zamanlamasından bağımsız; `onCreateView`/`onViewCreated`'dan itibaren güvenle okunur.
- Codegen: `androidx.fragment.compose.AndroidFragment` + `arguments = route.toBundle()` (PD-safe) + `onUpdate { bindGezgin(route, nav) }` (canlı ref re-attach).
- **Lifecycle sözleşmesi (iki delege = iki farklı kaynak = iki farklı geçerlilik penceresi):** `gezginArgs` **Bundle-backed**'dir (yukarı bkz.) → `onCreateView`/`onViewCreated`'dan itibaren PD-safe, `onUpdate`'e bağlı DEĞİL. `gezginNav` ise **canlı-instance registry**'sinden okur; bu registry'yi `AndroidFragment.onUpdate` doldurur (canlı navigator serileştirilemez, Bundle'a giremez) → `gezginNav` bind (= ilk `onUpdate`) tamamlanana dek geçersizdir, bind öncesi erişim açıklayıcı hata fırlatır (pratikte `nav` yalnız kullanıcı etkileşiminde okunur → bind kesin bitmiştir). Legacy fragment'ın kendi `OnBackPressedDispatcher` callback'i NavDisplay'i LIFO'da geçer → migration'da kaldırılmalı (dokümante edilir).
- **Migration swap:** `@FragmentScreen class XFragment {…}` → `@Screen @Composable fun XScreen(route, nav) {…}`. Graph/edge/typed navigator sabit kalır. Deep-link dispatch bu artefaktta yoktur.

### 11.2 Dialog / BottomSheet — fragment interop YOK (bilinçli kesim, M5)
Legacy `DialogFragment`/`BottomSheetDialogFragment` için **köprü yok**. Bir dialog'u taşıyacaksan içeriğini (zaten sadece `Surface`/`Column`) native `@Dialog`/`@BottomSheet` composable'a çevirirsin — dialog en yaprak, en ucuz çevrilen UI parçası (§7).
- **Neden kesildi:** `DialogFragment` kendi `Window`'unu FragmentManager'la sahiplenir → Nav3'ün tek-otorite back stack'iyle **çift-otorite**: PD çift-restore (duplicate/orphan), back double-fire, predictive-back yok, dismiss/pop race. Brownfield'ın tek kırılgan köşesi buydu; baş ağrısına değmedi.
- View-based bir dialog Fragment'in varsa: ya içeriğini `@Dialog`'a taşı, ya da tam-ekran leaf olarak `@FragmentScreen` ile host et (§11.1). **Fragment interop = yalnız screen.**

---

## 12. Usage model & host
```kotlin
@Composable
fun App() {
    // Codegen graph paketine (§3.3/§14) gezginTopology + kararlı, process-wide gezginJson üretir; kurulum
    // core rememberNavigator'ı çağırır. Graph modülü düz-JVM (Compose compiler plugin YOK) olduğundan oraya
    // @Composable ÜRETİLMEZ — üretilse Compose-lowering almadan derlenir (bytecode'da Composer/$changed/$default
    // yok) ve tüketici ilk ekranda runtime'da NoSuchMethodError alır. gezginJson düz bir `val` olduğu için güvenli.
    val navigator = rememberNavigator(
        start = HomeRoute,
        topology = gezginTopology,
        json = gezginJson,
        restoreKey = "$sessionGeneration:$appMode",
    )
    // Özel Json istenirse gezginJson yerine kendi Json'unu geçir:
    //   val json = remember { Json { serializersModule = gezginSerializersModule } }
    //   val navigator = rememberNavigator(
    //       start = HomeRoute, topology = gezginTopology, json = json, restoreKey = restoreKey,
    //   )

    // Observe-only middleware (§10.1): navigator.events akışını DIŞARIDAN collect edersin —
    // GezginDisplay'in bir constructor param'ı DEĞİL. Akışı hiçbir şekilde etkilemez (log/analytics).
    LaunchedEffect(navigator) {
        navigator.events.collect { event -> /* NavLogger / Analytics */ }
    }

    GezginDisplay(
        navigator   = navigator,
        transitions = navTransitions { forward { /* app-geneli */ } backward { /* app-geneli */ } },
    ) {                                              // this: GezginEntryScope
        homeFeatureEntries()                         // kullanıcı-yazımı bundle'lar
        orderFeatureEntries()
    }
}
```
- Codegen ekran başına `GezginEntryScope.provideXEntry(...)` üretir — Gezgin registry'sine kayıt; Nav3 `NavEntry`'sini `GezginDisplay` kurar (contentKey = `GezginKey.id`, §2.1). Modül-başına `xFeatureEntries()` bundle'ını **kullanıcı** yazar (resolver override noktası; codegen üretmez).
- `provideXEntry`'nin `viewModel` default'u `@MviViewModel` DI-detection'ından (Hilt/Koin); override için custom lambda. Core-mode `@Screen(route,nav)` de aynı scope-extension şeklini üretir.
- `GezginDisplay`'in trailing lambda'sı `GezginEntryScope`; gizli plumbing'i (decorators, scene strategies, `GezginKey`→`NavEntry` adapter'ı, predictive-back) kurar. Fragment host ediliyorsa Activity `FragmentActivity`/`AppCompatActivity` olmalı.
- **Restore namespace:** aynı boş-olmayan `restoreKey`, aynı saved stack'i ve Android holder kimliğini restore eder; farklı key supplied `start` ile bağımsız/fresh navigator kurar. Eski overload stabil legacy namespace ile source-compatible'dır, fakat session/account/app-mode sınırı olan uygulamalar persistent generation + mode key'i açıkça vermelidir.
- **Kuruluş guard'ları (runtime):** `rememberNavigator(start)` — start bir `ResultFlow` üyesi olamaz (bekleyen caller yok, §8.1) ve modal kind olamaz (§7). Root flow'da `quit`/entry-`back` → `onRootBack` (§8.1).
- **Koşullu start:** startup state hazır olmadan navigator kurulmaz. Uygulama gerçek `start` ve `restoreKey` değerlerini önce belirler, sonra `rememberNavigator` çağırır; placeholder/decider route oluşturup sonradan replace etmek maintained ZAD sözleşmesi değildir.

---

## 13. Test API
State-as-data → **UI/Compose'suz, saf Kotlin**:
```kotlin
val nav = GezginTestNavigator(start = HomeRoute, topology = gezginTopology)   // gezginTopology: codegen üretir (§14)
nav.navigate(ProductRoute("42")); nav.back(); nav.replaceTo(HomeRoute)
nav.backStack; nav.current                          // tab/deep-link test yüzeyleri → V2

runTest {
    val r = async { nav.fromCheckout().goToSelectAddressForResult() }   // tipli erişim: üretilmiş fromX() (reified from<X>() codegen'siz mümkün değil)
    nav.backWithResult(SelectedAddress("1", "Ev"))                      // top pending-target'a Value teslim + pop (runtime `backWithResult` ile aynı ad)
    r.await() shouldBe NavResult.Value(SelectedAddress("1", "Ev"))
}
```
Enforcement'ı test etmeye gerek yok (compile-time garanti). Tipli `fromX()` erişimcileri `gezgin.emitTestAccessors=true` KSP seçeneğiyle üretilir; flag'i graph'ların bulunduğu **`main` KSP round'unda** açtığında (kanonik çok-modül düzeni: graph'lar `main`'de, testler `test`'te) erişimciler `main`'e üretilir ve `test` kaynak kümesi `nav.fromX()`'i doğrudan çağırır — **çok-modül düzeninde de çalışır** (by-example §8). Maintained davranış testleri dispatch için bu typed erişimcileri kullanır.

---

## 14. Codegen (KSP) sorumluluğu — özet
- Tipli per-source navigator'lar: stable `RawNavigator`'ı saran **stateless facade class** (edge metotları ve for-result üçlüsü `launchX`/`xResults`/suspend) + public factory `fun RawNavigator.xNavigator()` (ctor `internal`, §3.3). Object/DI değil.
- Entry parçaları: `register<Route> { route -> XScreen(route, remember(raw){ raw.xNavigator() }) }` (Gezgin registry; `NavEntry` kurulumu + contentKey = `GezginKey.id` `GezginDisplay`'de, §2.1); modal'larda route contract'ından scene metadata.
- **Geri/çıkış:** `@NoBack` → navigator'dan `back()` çıkar + entry content'i Gezgin-sahipli enabled `NavigationBackHandler` ile sarılır (preview o entry'de başlamaz; M5′, §4.2); `@BackTo`/`@BackToStart`/`@Quit`/`@QuitAndGoTo` + `quitWith(T)` → tipli metotlar.
- **MVI-mode (§10.1):** `@MviViewModel(Route)` + repeatable stateless `@Screen(state,onIntent)` + route-bound `@EffectHandler(Route)` → `GezginEntryScope.provideXEntry(viewModel = <DI-detected default>)`. S/I/E VM'in `GezginMvi<S,I,E>` supertype'ından; default resolver VM'in DI annotation'ı (`@HiltViewModel`/`@KoinViewModel`) + ctor `@Assisted`/`@InjectedParam` bilgisinden gelir. Navigation intent -> effect -> handler -> typed navigator zincirindedir.
- **Fragment (§11.1):** `gezginArgs`/`gezginNav` accessor'ları + `AndroidFragment` (view-host). Yalnız `@FragmentScreen`; dialog/bottomsheet fragment interop yok.
- **Deep-link tablosu + placeholder doğrulaması → 🔮 V2** (§5/§17; processor'da bugün deep-link codegen'i yok) · Guardrail 1 + derleme matrisi **E1–E5** (§8.1) · Modül başına `xFeatureEntries()` · Polymorphic serialization kaydı (`GezginKey` dahil).

---

## 15. Paketleme
- `gezgin-core` (DI-agnostik): annotations, runtime, `GezginDisplay`, üretilen kodun runtime hedef yüzeyi (navigator facade / `GezginEntryScope`), BottomSheet scene strategy.
- `gezgin-processor` (KSP2 symbol processor, zorunlu): tipli navigator'ları, entry provider'larını, graph topology + `SerializersModule`'ü üretir (`ksp(project(":gezgin-processor"))` ile uygulanır). **Tüm codegen burada** — `gezgin-core` codegen İÇERMEZ.
- `gezgin-mvi` (opsiyonel, `gezgin-core`'a bağımlı): `@MviViewModel`/`@EffectHandler` + migration-only `@TopBar`/`@BottomBar`, `GezginMvi<S,I,E>` sözleşmesi, codegen binder, `ObserveEffects` ve DI-detection. Chrome annotation'ları kalıcı core API'sine taşınmaz; ZAD migration sonunda kaldırılır.
- Ayrı `gezgin-koin`/`gezgin-hilt` add-on'una **gerek yok** — DI desteği gezgin-mvi codegen'inde. (İleride farklı DI'lar için genişletilebilir; manuel-DI → resolver override.)

### Doğrulanan build sınırları

| Sınır | Exact sürümler ve rol |
|---|---|
| Gezgin root | Gradle 8.14; Kotlin 2.3.21; KSP 2.3.9; AGP 8.11.0; Compose Multiplatform 1.11.0; AndroidX Navigation 3 1.0.0 + lifecycle Navigation 3 2.10.0; desktop JetBrains Navigation 3 1.0.0-alpha05 + lifecycle 2.10.0-alpha05; min SDK 24. |
| `compatibility/zad-consumer` | Kendi Gradle 9.4.1 wrapper'ı; Kotlin 2.3.21; KSP 2.3.9; AGP 9.2.1; JDK/JVM 21; compile/target SDK 37; Koin 4.2.2 + compiler plugin 1.0.1; AndroidX Navigation 3 1.0.0 + lifecycle 2.10.0. Yalnız pinli Maven Local artefaktlarını çözer; `includeBuild`, composite substitution, `projectDir` veya başka source dependency kullanmaz. |

---

## 16. Kilit kararlar (gerekçeyle)
| Karar | Gerekçe |
|---|---|
| state-as-data çekirdek | test/log/restore/MVI bedava |
| Nav3 üzerine | model birebir uyumlu; render/transition/scene/lifecycle hazır; KMP (Android stable, non-Android alpha — §2.2) |
| iç temsil `GezginKey(route, id, flowPath)` | benzersiz `contentKey` (Nav3 sözleşmesi) + flow-unit sınırı + result-slot kimliği tek zarfta; public API'de görünmez (R2) |
| result: launch/receive ayrımı (`launchX` + `xResults` + suspend sugar) | ActivityResult dersi — PD re-attach stream'de; idempotent tetik (R1) |
| DI-agnostik (Ktorfit) | kullanıcıyı DI'a mahkûm etmemek |
| tipli per-source navigator | "eklenmeyen yere gidilemez" = derleme garantisi |
| compile-time→annotation, runtime→değer | her bilgi doğru mekanizmada |
| result tipi hedefte (`ResultRoute<T>`) | DRY + consistency-by-construction |
| container = annotation (`@NavGraph` şeffaf / `@FlowGraph` opak); her route bir graph'ta; V1 tek-stack | rol deklarasyonda → lokal; `@FlowGraph` katı (§8.1); tab/multi-stack/deep-link → V2 |
| modal = render varyantı + route contract | parçaların yeniden kullanımı; DRY |
| geri/çıkış: implicit `back()` + declared `@BackTo`/`@BackToStart`/`@Quit`/`@QuitAndGoTo` + `quitWith(T)`; `@NoBack` terminal | declared sessizce silinmez; flow çıkışları katı (§8.1); `@NoBack` = entry-içi Gezgin-sahipli handler (dispatcher LIFO'sunda kazanır; M5′) |
| back interception = non-feature | platform `BackHandler` yeterli; reinvent yok |
| brownfield: Gezgin root + Fragment yaprak (yalnız screen) | fragment dialog interop yok → native `@Dialog`'a çevir; bridge'in PD/race kırılganlığı kesildi (M5) |
| strict MVI navigation | intent -> effect -> route-bound handler -> typed navigator; ViewModel navigator tutmaz |
| binder: core `@Screen(route,nav)` + opsiyonel `gezgin-mvi` `provideXEntry(resolver)` | genel core capability korunur; maintained ZAD örnekleri strict MVI kullanır |
| temporary chrome | `@TopBar`/`@BottomBar` yalnız migration compatibility; kalıcı app-owned container geldiğinde silinir |

---

## 17. Parkedilenler / gelecek
- **`@TabGraph` / `@SwitchTo` + multiple back stack (per-tab retained stacks) → V2.** V1 tek-stack; bottom-nav uygulama-yönetimli. Bu karar TabGraph'ın deep-link-ebeveyn / switch-bağlam / tab-back açıklarını V1'den kaldırdı.
- **Deep link (tüm özellik) → V2.** Yön: path-hiyerarşisi = back stack; `@DeepLink(segment, parent, [args])` tekrarlanabilir; codegen matcher + full-URL builder + katalog; `args` küçük düzeltmeyle kalkabilir (§5).
- **Flow-scoped shared data → flow-input** (immutable, nav-owned; "flow = fonksiyon, input = argümanı"): parkedildi. Varsayılan = value object (iyi pratik). Mutable SharedViewModel **reddedildi**.
- **Kalıcı scaffold/container sözleşmesi** → V2. Mevcut `gezgin-mvi` top/bottom chrome yalnız migration-only köprüdür.
- Two-pane / adaptive (Nav3 Scene + Material3 adaptive).
- Deep link giriş stratejileri (app açıkken replace/append), `@FlowEntryResolver`.
- Keyfi runtime Nav3 metadata escape hatch (custom scene strategy).
- KMP web (beta) olgunlaşması.

---

## 18. Maintained acceptance sınırı

Bu artefaktın current sözleşmesi: API dump'ları Android/JVM'de aynı public yüzeyi taşır; maintained docs/sample strict MVI'ı önerir; Fragment interop yalnız screen'dir; deep-link route dispatch mevcut değildir ve V2'de kalır. Full Phase A publication/handoff doğrulaması ayrı görevdir; bu belge source commit veya yayınlanmış artefakt iddiası yapmaz.
