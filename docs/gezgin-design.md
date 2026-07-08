# Gezgin — Navigasyon Kütüphanesi Tasarım Spec'i

> Durum: **tasarım tamamlandı** (brainstorm bitti, implementation öncesi). Bu, tüm kilitli kararların temiz/konsolide hâlidir.
> Hedef platform: Compose Multiplatform (Android öncelikli; iOS/Desktop/Web).
> Karar geçmişi + gerekçeler: [gezgin-design-notes.md](gezgin-design-notes.md) · Binder analizi: [gezgin-binder-location.md](gezgin-binder-location.md) · Tanıtım: [gezgin-by-example.md](gezgin-by-example.md).

---

## 1. Bağlam & konumlanma

Mevcut CMP/KMP navigasyon çözümlerinde tek pakette bulunmayan bir kombinasyon:

- **Type-safe + compile-time güvenli** — string route yok; tanımsız yere gidiş **derlenmez**.
- **Annotation + codegen** — az boilerplate; graph/wiring/result/deep-link/binder codegen'de.
- **State-as-data çekirdek** — test/log/restore/MVI'yı bedavaya getirir.
- **MVI/Redux dostu** ama dayatmasız.
- Nested navigation, multiple back stack, deep link (parent reconstruction), result passing, process-death restore, modal, route/screen/app transition, UI'sız test, **brownfield (Fragment) migration**.

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
- KMP durumu (2026-07 doğrulandı): Nav3 **Android'de stable** (androidx navigation3 1.1.4); **non-Android hedeflerde JetBrains portu ALPHA** (`org.jetbrains.androidx.navigation3:navigation3-ui` 1.0.0-alpha05, CMP 1.10+; `lifecycle-viewmodel-navigation3` 2.10.0-alpha05). `GezginDisplay` adapter sınırı bu churn'ün sigortası (örn. `onBack` imza değişimi alpha11'de, `EntryProviderScope` rename — kullanıcıya sızmadan emilir). Sealed route ağacı → kotlinx.serialization kapalı-polimorfizmi bedava (iOS/web serileştirme otomatik).

### 2.3 DI-agnostik (Ktorfit modeli)
Gezgin hiçbir DI'a bağlı değil. Codegen parça üretir (entry'ler, navigator'lar, `provideXEntry`); kullanıcı DI'ı kendi bağlar (Koin/Hilt/manuel). Magic yok.

### 2.4 İlke — compile-time → annotation; runtime → değer
- **Annotation** (KSP, compile-time): container (`@NavGraph` şeffaf / `@FlowGraph` opak; V1'de her route ikisinden birinde), edge'ler (`@GoTo`/`@ReplaceTo`/`@GoForResult`, geri `@BackTo`/`@BackToStart`, flow-çıkış `@Quit`/`@QuitAndGoTo`), `@NoBack`, `@StartDestination` (yalnız `@FlowGraph`), kind (`@Screen`/`@Dialog`/…), MVI (`@ViewModel`/`@ScreenEffect`, §10.1), fragment varyantı (`@FragmentScreen`). Result marker'ları (interface): `ResultRoute<T>` (screen), `ResultFlow<T>` (yalnız `@FlowGraph`). **V1 tek-stack;** `@TabGraph`/`@SwitchTo`/multi-backstack/`@DeepLink` → V2 (§17).
- **Runtime değer**: `transition`, navigate opsiyonları (annotation'da kodlanır), modal properties (`DialogContract`/`BottomSheetContract`).

---

## 3. Deklarasyon modeli

**Kural:** navigasyon grafiği & davranışı → **sealed route ağacı**; pikseller & sunum → **composable**.

### 3.1 Route & Graph
- Route = `@Serializable` data class/object, ait olduğu graph'ın `sealed interface`'i **içinde** nested (namespaced).
- Graph = `sealed interface`; alt-graph subtyping ile bağlanır (`OrderGraph : AppGraph`) — nesting = subtyping.
- **Her route bir graph içinde** sarılı; graph iki türden biri (V1):
  - **`@NavGraph`** → **şeffaf** grup: üyeleri sırasız, **istenen üyesinden** girilir (container'a `@GoTo` **yasak** → yalnız üyeye); `@StartDestination` **yok**; stand-alone olabilir (birden çok yerden erişilir). Graph'ların çoğu bu.
  - **`@FlowGraph`** → **opak** transactional flow (§8.1): kara kutu, dışarıdan yalnız **container'a** girilir, `@StartDestination` **var**, `ResultFlow<T>` **yalnız** buna takılır.
- **Üyelik = lexical nesting (E5):** route, nested olduğu graph'ın üyesidir; onun dışında **ikinci bir graph interface'i DOĞRUDAN implement edemez** (derleme hatası; kontrol direct supertype'ta — `OrderGraph : AppGraph` alt-graph deseni transitive miras yüzünden E5 tetiklemez) — flow opaklığı bu teklik kuralına dayanır.
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

### 3.2 Kind (composable'da)
Kind annotation composable'da durur ve üç iş yapar: destination = binding, sunum kind'ı, `route:` param tipinden route'a bağ.
- `@Screen` (tam ekran, varsayılan) · `@Dialog` / `@BottomSheet` / `@FullscreenModal` (modal, bkz. §7).
- Argsız route'ta route açıkça: `@Screen(OrdersRoute::class)`.

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
- **Codegen dağılımı:** `core:navigation` → tipli navigator'lar + deep-link tablosu + graph topology + `SerializersModule`. `feature:X` → `@Screen`/`@ScreenEffect`/`@ViewModel` → codegen `EntryProviderScope.provideXEntry`'ler; **kullanıcı** `xFeatureEntries()` bundle'ını yazar. `:app` → `GezginDisplay { … }` + back stack (montaj).
- Her feature KSP'si **yalnız kendi modülünü** işler; cross-module **tip** görünürlüğü yeter (annotation okuması gerekmez → ksp#527 yok). Navigator ctor `internal`; core:navigation her navigator için **public factory** üretir (`fun RawNavigator.xNavigator(): XNavigator`) → feature'ın üretilen entry kodu navigator'ı bu factory'den alır (cross-module derlenir).
- Route-arg domain modelleri **`@Serializable` olmalı** (back stack serialize/PD). Tek-modül app: her şey tek modülde, aynı model tam ağaçla çalışır.

---

## 4. Navigasyon API — tipli per-source navigator

Codegen **her source ekran için** tipli navigator üretir; her deklare edilen kenar = bir metot. Eklenmeyen hedefe metot yoktur → **derlenmez**. Enforcement = API şeklinin doğal sonucu (`enforceEdges` flag'i yok). Bypass = `nav.raw.navigate(route)` (bilinçli escape hatch).

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
İleri = push + opt (popUpTo/singleTop) · Geri = pop + opt (backTo/backToStart/quit/withResult) · Lateral = switch (multiple back stack) · Modal = bir entry'nin sunum biçimi · Deep link = dışarıdan stack kur.

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
- Properties = route'ta **opsiyonel** `DialogContract`/`BottomSheetContract`: SABİT = body override, KOŞULLU = constructor param (→ generated navigate param, serialize → PD-safe). `dismissOnClickOutside`, `dismissOnBackPress`, `layout`. Annotation'da prop yok.
- Pencere + dismiss→pop Gezgin scene'inden; dismiss (tap-outside/swipe) = `Canceled`.
- BottomSheet: `BottomSheetSceneStrategy` core'a bundle; swipe-dismiss animasyonlu pop; `sheetState: SheetState` opsiyonel param ile composable'a enjekte.
- Sonuç: dialog/sheet doğal sonuç-üreticisi (`ResultRoute<T>` + `backWithResult`). Entry-scoped VM burada da geçerli.
- **Guardrail:** `dismissOnBackPress = true` + `@NoBack` = derleme hatası (tezat).
- **Guardrail (root, runtime):** modal kind'lı entry stack'te tek başına kalamaz — Nav3 `OverlayScene` `require(overlaidEntries.isNotEmpty())` (doğrulandı). `rememberNavigator(start)` kuruluşta modal-start'ı reddeder (§12); `@QuitAndGoTo(modal)` = KSP uyarısı.
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

> **E1:** `ResultFlow`'a giriş yalnız `@GoForResult` (bekleyen caller şart) · **E2:** `@GoForResult` hedefi `ResultRoute`/`ResultFlow` olmalı · **E3:** flow kara kutu — iç üyeye dışarıdan edge yok · **E4:** flow üyesinde `clearUpTo` flow sınırını aşamaz (üyelik **transitive** — flow içindeki nested `@NavGraph` üyeleri flow-içi sayılır) · **E5:** route ikinci bir graph interface'i implement edemez (§3.1). Ayrıca: `@NoBack`+flow-start · `ResultFlow` non-`@FlowGraph`'ta · isimsiz edge çakışması (N9).

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
- **Per-call override (N1):** typed navigator metotlarında **yok** (davranış annotation'dan); tek-seferlik farklı transition **yalnız** `raw.navigate(route) { transition = … }` escape-hatch'iyle.
- Codegen değil; `route` (NavKey) → entry metadata'sındaki `NavDisplay.TransitionKey` ailesine iner. Cascade en içteki (screen) > graph > app.

---

## 10. MVI scoping, VM-driven nav & state observability
- **Gezgin state-holder dünyasına girmez** (store/intent/event tanımlamaz). Scoping Nav3'ten: `GezginDisplay` `rememberViewModelStoreNavEntryDecorator` (+ saveable + savedState) → sıradan `koinViewModel()` otomatik **entry-scoped**, pop'ta `onCleared`, config/PD dayanıklı.
- **Route + nav VM'e** `parametersOf` ile: `koinViewModel { parametersOf(route, nav) }` (ikisi de entry'den).
- **Navigation iki şekilde** (dayatma yok): **(A) VM-driven (önerilen)** — `nav` VM'e enjekte, `handleIntent` doğrudan `nav.goToX()`; result-edge'de VM `init`'te `nav.xResults.collect { … }` kurar (PD re-attach, §6), tetik `nav.launchX()`; `UiEvent` yalnız nav-olmayan efektler. **(B) composable-driven** — VM `UiEvent.NavigateX` yayar, binder `nav.goToX()` çağırır.
- **Tipli navigator = stable `RawNavigator` üstünde stateless facade** → VM'de tutmak config-change'te güvenli; testte `testNav.from<Source>()` aynı sınıfı test-çekirdeği üstünde verir. PD'de route restore edilmiş back stack'ten `parametersOf`'la döner. `SavedStateHandle` = VM'in kendi UI-state save'i için (ortogonal).
- **State takibi:** `navigator.events: Flow<NavEvent>` + `navigator.backStack: StateFlow<...>` (log/analytics/devtools). Middleware = **observe-only** (N7: events/backStack stream'ini gözler; log/analytics). Veto/rewrite (interceptor) → V2.

### 10.1 Binder modeli (stateful↔stateless boilerplate) — iki yol, `@Screen` imzası seçer
- **Core (`gezgin-core`) — kendin bağla:** `@Screen fun XScreen(route, nav)` → codegen `entry<Route> { XScreen(route, nav) }`. VM'i içeride sen resolve edersin (herhangi MVI/DI, hatta VM'siz). Tam esneklik.
- **MVI add-on (`gezgin-mvi`) — codegen bağlar:** stateless `@Screen fun XContent(state, onIntent)` + `@ViewModel(Route::class)` işaretli, `GezginMvi<S,I,E>` implement eden bir VM. Codegen üretir:

```kotlin
// gezgin-mvi sözleşmesi (opt-in; variance = polish, artık yük taşımıyor):
interface GezginMvi<out S, in I, out E> {
    val uiState: StateFlow<S>
    val effects: Flow<E> get() = emptyFlow()       // opsiyonel
    fun onIntent(intent: I)
}

// VM: @ViewModel route'a bağlar + GezginMvi implement eder (İKİSİ DE ZORUNLU)
@ViewModel(OrderChainRoute::class)
class OrderChainViewModel(route: OrderChainRoute, nav: OrderChainNavigator, repo: Repo)
    : BaseViewModel<OrderChainState, OrderChainIntent, OrderChainEvent>()   // : GezginMvi<S,I,E>

@Screen(OrderChainRoute::class) @Composable
fun OrderChainContent(state: OrderChainState, onIntent: (OrderChainIntent) -> Unit) { /* ... */ }

// codegen ürettiği provider — GezginEntryScope extension; kayıt Gezgin registry'sine gider,
// GezginDisplay Nav3 NavEntry'sini contentKey = GezginKey.id ile kurar (§2.1).
// viewModel default'u @ViewModel'in DI-detection'ından gelir (Hilt/Koin annotation + ctor @Assisted).
fun GezginEntryScope.provideOrderChainEntry(
    viewModel: @Composable (nav: OrderChainNavigator, args: OrderChainRoute) -> OrderChainViewModel
        = { nav, args -> koinViewModel { parametersOf(args, nav) } }   // algılanan DI (Koin örn.)
) {
    register<OrderChainRoute> { route ->
        val raw = LocalGezginNavigator.current
        val nav = remember(raw) { raw.orderChainNavigator() }          // core:navigation public factory (§3.3)
        val vm  = viewModel(nav, route)
        val state by vm.uiState.collectAsStateWithLifecycle()
        OrderChainEffects(vm.effects)              // @ScreenEffect varsa
        OrderChainContent(state, vm::onIntent)     // @Screen (stateless)
    }
}

// KULLANICI yazar (feature modülü) — override noktası; codegen ÜRETMEZ:
fun GezginEntryScope.orderFeatureEntries() {
    provideOrderChainEntry()                        // default resolver (algılanan DI)
    // provideOrderDetailEntry { nav, args -> ... } // gerekirse override
}

// app — GezginDisplay'in trailing lambda'sı GezginEntryScope:
GezginDisplay(navigator = nav) { orderFeatureEntries(); homeFeatureEntries() }
```

- **`@ViewModel(Route::class)` + `GezginMvi<S,I,E>` (ikisi de zorunlu):** codegen VM'in **somut tipini** `@ViewModel`'den, **S/I/E'yi VM'in `GezginMvi` supertype arg'larından** okur (content'ten türetme yok, E-kaynağı problemi yok). Üçlü (`@ViewModel`/`@Screen`/`@ScreenEffect`) route'a göre eşlenir ve **aynı modülde olmalı** (per-module KSP eşleşmesi; aksi derleme hatası: "`@ViewModel(X)` var ama `@Screen(X)` bu modülde yok"). **Guardrail:** `@ViewModel` var ama `GezginMvi` **yok** → **derleme hatası.** Codegen ayrıca content'in `(state, onIntent)` + `@ScreenEffect`'in `Flow<E>` tiplerini VM sözleşmesine **karşı doğrular.**
- **DI-detection + default resolver (B2, Problem 1):** `@ViewModel` VM class'ını verdiği için codegen DI annotation'ını (`@HiltViewModel`/`@KoinViewModel`) + ctor `@Assisted`/`@InjectedParam`'ını **tipe göre** okur. Gezgin yalnız **route + nav** tiplerini sağlayabilir → **default resolver yalnız tüm assisted param'lar {route, nav} tipindeyse üretilir** (`provideXEntry()` boş çağrılabilir; Hilt/Koin, yoksa androidx `viewModel()`). Sağlayamadığı bir assisted varsa (örn. `@Assisted userId: String`) → **default YOK, `viewModel` zorunlu** → kullanıcı resolver'ı yazıp ekstrayı verir (genelde route'ta olmalıydı). Her hâlde override edilebilir; ayrı gezgin-koin/hilt add-on'una gerek yok.
- **Bilinmeyen content param (Problem 2):** `@Screen` content Gezgin'in rol-bazlı sağladıkları (`state`/`onIntent`/`sheetState`) dışında bir param alırsa (örn. `imageLoader: ImageLoader`) → codegen onu `provideXEntry`'ye **ek resolver param** olarak ekler (content imzasından tipli, `@Composable () -> T`); kullanıcı kurulumda verir: `provideProductEntry(imageLoader = { koinInject() })`. **Compile-safe + DI-neutral + content saf.** (CD bunu host'ta tip-anahtarlı container + eşleşmezse **runtime crash** ile yapar; Gezgin bilinçle compile-safe tarafı seçer.) Alternatif: dep'i content içinde `koinInject()` ile çöz.
- **Kullanıcıya wrapper tip yok** — `provideXEntry` = `GezginEntryScope` extension, Gezgin registry'sine kayıt yapar; `NavEntry` kurulumu (contentKey = `GezginKey.id`, scene metadata) `GezginDisplay` adapter'ında (§2.1). **Bundle = kullanıcı yazar:** codegen bireysel `provideXEntry`'leri üretir; feature-başına `xFeatureEntries()` bundle'ını **kullanıcı** yazar (resolver override noktası; codegen üretirse override kapanır). VM resolution ekranda değil kurulumda (Ktorfit) → ekranda DI yok, kontrol tam.
- **Effect:** opsiyonel `@ScreenEffect fun XEffects(effects: Flow<E>[, nav])` composable; kendi `ObserveAsEvents`'ini yapar. Google state-first önerisi → effect dayatılmaz. Analiz: [gezgin-binder-location.md](gezgin-binder-location.md).

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
- Parametreli Fragment ctor **yasak** (framework no-arg ctor + Bundle ile yeniden yaratır → parametreli ctor çöker/arg'ı sessizce kaybeder). Tipli arg `gezginArgs<Route>()` accessor'ından (registry-based; `onCreateDialog`'da bile erişilebilir).
- Codegen: `androidx.fragment.compose.AndroidFragment` + `arguments = route.toBundle()` (PD-safe) + `onUpdate { bindGezgin(route, nav) }` (canlı ref re-attach).
- **Lifecycle sözleşmesi:** `gezginArgs`/`gezginNav` bind sonrası geçerli (bind = `AndroidFragment.onUpdate`); bind öncesi erişim açıklayıcı hata fırlatır. Legacy fragment'ın kendi `OnBackPressedDispatcher` callback'i NavDisplay'i LIFO'da geçer → migration'da kaldırılmalı (dokümante edilir).
- **Migration swap:** `@FragmentScreen class XFragment {…}` → `@Screen @Composable fun XScreen(route, nav) {…}`. Graph/edge/navigator/deeplink sabit.

### 11.2 Dialog / BottomSheet — fragment interop YOK (bilinçli kesim, M5)
Legacy `DialogFragment`/`BottomSheetDialogFragment` için **köprü yok**. Bir dialog'u taşıyacaksan içeriğini (zaten sadece `Surface`/`Column`) native `@Dialog`/`@BottomSheet` composable'a çevirirsin — dialog en yaprak, en ucuz çevrilen UI parçası (§7).
- **Neden kesildi:** `DialogFragment` kendi `Window`'unu FragmentManager'la sahiplenir → Nav3'ün tek-otorite back stack'iyle **çift-otorite**: PD çift-restore (duplicate/orphan), back double-fire, predictive-back yok, dismiss/pop race. Brownfield'ın tek kırılgan köşesi buydu; baş ağrısına değmedi.
- View-based bir dialog Fragment'in varsa: ya içeriğini `@Dialog`'a taşı, ya da tam-ekran leaf olarak `@FragmentScreen` ile host et (§11.1). **Fragment interop = yalnız screen.**

---

## 12. Usage model & host
```kotlin
@Composable
fun App() {
    val navigator = rememberNavigator(start = HomeRoute)
    GezginDisplay(
        navigator   = navigator,
        transitions = navTransitions { forward { /* app-geneli */ } backward { /* app-geneli */ } },
        middleware  = listOf(NavLogger, Analytics),
    ) {                                              // this: GezginEntryScope
        homeFeatureEntries()                         // kullanıcı-yazımı bundle'lar
        orderFeatureEntries()
    }
}
```
- Codegen ekran başına `GezginEntryScope.provideXEntry(...)` üretir — Gezgin registry'sine kayıt; Nav3 `NavEntry`'sini `GezginDisplay` kurar (contentKey = `GezginKey.id`, §2.1). Modül-başına `xFeatureEntries()` bundle'ını **kullanıcı** yazar (resolver override noktası; codegen üretmez).
- `provideXEntry`'nin `viewModel` default'u `@ViewModel` DI-detection'ından (Hilt/Koin); override için custom lambda. Core-mode `@Screen(route,nav)` de aynı scope-extension şeklini üretir.
- `GezginDisplay`'in trailing lambda'sı `GezginEntryScope`; gizli plumbing'i (decorators, scene strategies, `GezginKey`→`NavEntry` adapter'ı, predictive-back) kurar. Fragment host ediliyorsa Activity `FragmentActivity`/`AppCompatActivity` olmalı.
- **Kuruluş guard'ları (runtime):** `rememberNavigator(start)` — start bir `ResultFlow` üyesi olamaz (bekleyen caller yok, §8.1) ve modal kind olamaz (§7). Root flow'da `quit`/entry-`back` → `onRootBack` (§8.1).
- **Koşullu start (N6):** `start` tek route; runtime-bağımlı açılış (onboarding mı login mi) = **decider route** — argsız bir start ekranı `LaunchedEffect`'te karar verip `replaceTo(...)` eder (if-else navigasyonu buraya iner, Guardrail 1 kırılmaz).

---

## 13. Test API
State-as-data → **UI/Compose'suz, saf Kotlin**:
```kotlin
val nav = GezginTestNavigator(start = HomeRoute)
nav.navigate(ProductRoute("42")); nav.back(); nav.replaceTo(HomeRoute)
nav.backStack; nav.current                          // switchTo/activeTab/stackOf/handleDeepLink → V2

runTest {
    val r = async { nav.fromCheckout().goToSelectAddressForResult() }   // tipli erişim: üretilmiş fromX() (reified from<X>() codegen'siz mümkün değil)
    nav.deliverResult(SelectedAddress("1", "Ev"))
    r.await() shouldBe NavResult.Value(SelectedAddress("1", "Ev"))
}
```
Enforcement'ı test etmeye gerek yok (compile-time garanti).

---

## 14. Codegen (KSP) sorumluluğu — özet
- Tipli per-source navigator'lar: stable `RawNavigator`'ı saran **stateless facade class** (edge metotları, for-result üçlüsü `launchX`/`xResults`/suspend, `raw` escape hatch) + public factory `fun RawNavigator.xNavigator()` (ctor `internal`, §3.3). Object/DI değil.
- Entry parçaları: `register<Route> { route -> XScreen(route, remember(raw){ raw.xNavigator() }) }` (Gezgin registry; `NavEntry` kurulumu + contentKey = `GezginKey.id` `GezginDisplay`'de, §2.1); modal'larda route contract'ından scene metadata.
- **Geri/çıkış:** `@NoBack` → navigator'dan `back()` çıkar + entry content'i Gezgin-sahipli enabled `NavigationBackHandler` ile sarılır (preview o entry'de başlamaz; M5′, §4.2); `@BackTo`/`@BackToStart`/`@Quit`/`@QuitAndGoTo` + `quitWith(T)` → tipli metotlar.
- **MVI-mode (§10.1):** `@ViewModel(Route)` + stateless `@Screen(state,onIntent)` + opsiyonel `@ScreenEffect` → `GezginEntryScope.provideXEntry(viewModel = <DI-detected default>)` (Gezgin registry'sine register eder; kullanıcıya wrapper tip yok). S/I/E VM'in `GezginMvi<S,I,E>` supertype'ından; default resolver VM'in DI annotation'ı (`@HiltViewModel`/`@KoinViewModel`) + ctor `@Assisted`'ından. Bundle `xFeatureEntries()` = kullanıcı-yazımı.
- **Fragment (§11.1):** `gezginArgs`/`gezginNav` accessor'ları + `AndroidFragment` (view-host). Yalnız `@FragmentScreen`; dialog/bottomsheet fragment interop yok.
- Deep-link tablosu + placeholder doğrulaması · Guardrail 1 + derleme matrisi **E1–E5** (§8.1) · Modül başına `xFeatureEntries()` · Polymorphic serialization kaydı (`GezginKey` dahil).

---

## 15. Paketleme
- `gezgin-core` (DI-agnostik): annotations, runtime, `GezginDisplay`, codegen, BottomSheet scene strategy.
- `gezgin-mvi` (opsiyonel, `gezgin-core`'a bağımlı): `@ViewModel`/`@ScreenEffect` + `GezginMvi<S,I,E>` sözleşmesi + codegen binder (`EntryProviderScope.provideXEntry`) + `ObserveAsEvents` + **DI-detection** (VM'in `@HiltViewModel`/`@KoinViewModel` annotation'ı + ctor `@Assisted`/`@InjectedParam`'ından default resolver üretir; şimdilik Hilt+Koin, androidx fallback). Bağlantı seam'i: core'un `EntryProviderScope` + navigator'ları (mvi = entry-producer add-on; `GezginEntry` wrapper tipi **yok**).
- Ayrı `gezgin-koin`/`gezgin-hilt` add-on'una **gerek yok** — DI desteği gezgin-mvi codegen'inde. (İleride farklı DI'lar için genişletilebilir; manuel-DI → resolver override.)

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
| VM-driven nav: (route,nav) VM'e `parametersOf` | Zad `mainNavigator`+`backStack.last()` hack'ini tipli/PD-safe yapar |
| binder: core `@Screen(route,nav)` + opsiyonel `gezgin-mvi` `provideXEntry(resolver)` | boilerplate codegen'de, VM resolution kullanıcının lambda'sında → DI-agnostik + kontrol |

---

## 17. Parkedilenler / gelecek
- **`@TabGraph` / `@SwitchTo` + multiple back stack (per-tab retained stacks) → V2.** V1 tek-stack; bottom-nav uygulama-yönetimli. Bu karar TabGraph'ın deep-link-ebeveyn / switch-bağlam / tab-back açıklarını V1'den kaldırdı.
- **Deep link (tüm özellik) → V2.** Yön: path-hiyerarşisi = back stack; `@DeepLink(segment, parent, [args])` tekrarlanabilir; codegen matcher + full-URL builder + katalog; `args` küçük düzeltmeyle kalkabilir (§5).
- **Flow-scoped shared data → flow-input** (immutable, nav-owned; "flow = fonksiyon, input = argümanı"): parkedildi. Varsayılan = value object (iyi pratik). Mutable SharedViewModel **reddedildi**.
- **Scaffold slot convention** (topBar/bottomBar host) → **non-goal**: ekran kendi `Scaffold`'unu yazar.
- Two-pane / adaptive (Nav3 Scene + Material3 adaptive).
- Deep link giriş stratejileri (app açıkken replace/append), `@FlowEntryResolver`.
- Keyfi runtime Nav3 metadata escape hatch (custom scene strategy).
- KMP web (beta) olgunlaşması.

---

## 18. Sonraki adım
Bu spec'ten **implementation planı** (modül iskeleti → core runtime/state → KSP processor → `GezginDisplay` → özellikler → test). Doğrulama: §13 test API ile navigasyon davranışı UI'sız assert edilebilir.
