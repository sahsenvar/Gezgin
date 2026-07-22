# Gezgin

**Compose Multiplatform için type-safe, annotation-tabanlı navigasyon.** Gidilecek yer bir *tip*'tir, string değil — ve tanımlamadığın bir yere gitmek **derlenmez**.

![License](https://img.shields.io/badge/license-Apache--2.0-blue) ![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF) ![Compose Multiplatform](https://img.shields.io/badge/Compose%20MP-1.11.0-4285F4) ![Status](https://img.shields.io/badge/status-alpha-orange)

> 🇬🇧 **English:** For the English README → **[README.md](README.md)**

Gezgin **Navigation 3** üzerinde çalışır. Navigasyon grafiğin bir `sealed interface` ağacıdır; bir KSP işlemcisi her route için **tipli, ekran-başına bir navigator** üretir — böylece bir ekran yalnız gerçekten deklare ettiğin kenarlar boyunca gidebilir. Back stack **gözlemlenebilir, serileştirilebilir veridir**; bu sayede process-death restore, loglama, UI'sız test ve MVI neredeyse bedavaya gelir.

---

## 10 saniyelik örnek

```kotlin
// 1 · grafik = sealed ağaç — deklare ettiğin kenar = elde ettiğin metot
@NavGraph
@Serializable
sealed interface ShopGraph {
    @GoTo(ProductRoute::class)                              // Catalog → Product
    @Serializable data object CatalogRoute : ShopGraph

    @GoTo(PaymentResult::class)                             // Product → PaymentResult ("hemen al")
    @Serializable data class ProductRoute(val id: String) : ShopGraph

    // Başarıda checkout ekranını REPLACE et ve alışveriş hunisini Catalog dahil temizle,
    // ki sistem/predictive Back kullanıcıyı yeni bitirdiği akışa geri düşüremesin:
    @ReplaceTo(PaymentResult::class, clearUpTo = CatalogRoute::class, inclusive = true)
    @Serializable data object CheckoutRoute : ShopGraph

    @Serializable data object PaymentResult : ShopGraph     // terminal — ulaşılır, huniye geri gidilmez
}

// 2 · ekranın tipli navigator'ı YALNIZ Catalog'un deklare kenarlarının metotlarına sahip
@Screen(CatalogRoute::class)
@Composable
fun CatalogScreen(nav: CatalogNavigator) {
    ProductGrid(onClick = { product -> nav.goToProduct(product.id) })  // ✅ @GoTo(ProductRoute) tarafından auto-generated
    // nav.goToCheckout()   // ❌ DERLENMEZ — Catalog, Checkout'a kenar deklare etmedi
}
```

`nav.goToProduct(id)` var, çünkü `CatalogRoute` `@GoTo(ProductRoute::class)` deklare etti. `nav.goToCheckout()` bir **derleme hatası** — `CheckoutRoute` gayet geçerli bir route, sadece *Catalog'dan* ulaşılamıyor. *"Buradan nereye gidebilirim?"* sorusunun cevabı IDE otomatik-tamamlamasında — bir lint kuralıyla değil, **API'nin şekliyle** zorunlu kılınıyor.

---

## Neden Gezgin'e ihtiyaç duyduk

Bugün Compose navigasyonu genelde şunlardan biri demek:

- **String-tabanlı route'lar** — `navController.navigate("product/$id")` — burada bir typo ya da yanlış argüman tipi *runtime*'da "route not found", derleme hatası değil.
- **Her yerden çağrılabilen tek global controller**, yani "*bu* ekran hangi ekranlara ulaşabilir?" sorusunun cevabı, tüm grafiği okumadan bilinemez.
- Sonuç geri döndürmek için **elle boru döşemek**, ve process-death'i sağ atlatmak için **elle `SavedStateHandle` işi** — her biri ince bir hata yeri.
- **Navigasyon niyeti dağınık** — composable'lara ve view-model'lara serpilmiş, okunabilir tek bir yerde deklare edilmemiş.

Grafiğin **tek bakışta okunan veri** olmasını, ulaşılabilir hedeflerin **derleyici tarafından zorunlu** kılınmasını, ve sonuç / process-death / test'in **ekstra iş değil, varsayılan** olmasını istedik.

---

## Neden Gezgin? (Artıları)

- **String route yok.** Grafik bir `sealed interface` ağacı; hedef = tip. Namespaced, `@Serializable` → process-death'e dayanıklı ve çok-platform serializable bedava.
- **Tanımlamadığın yere gidiş derlenmez.** Her route yalnız deklare ettiğin kenarların metotlarına sahip tipli bir navigator alır.
- **Tüm sözlük deklaratif.** İleri (`@GoTo` / `@ReplaceTo`), geri (`back()` / `@BackTo` / `@BackToStart` / `@NoBack`), ve tipli sonuç döndüren çok-ekranlı **alt-flow'lar** (`@FlowGraph` / `ResultFlow` + `@GoForResult`) — davranış annotation'da, compile-time'da çözülür, runtime lambda yok.
- **Sonuçlar type-safe *ve* process-death-safe.** `@GoForResult` sana `launchX()` + gerçek process ölümünü sağ atlatan re-attach `xResults: Flow<NavResult<T>>` verir.
- **Modallar birinci-sınıf back-stack entry'leri.** `@Dialog` / `@BottomSheet` / `@FullscreenModal` = farklı render'lı aynı entry — elle yönetilen ayrı bir dialog state'i yok.
- **State-as-data.** `backStack: StateFlow`, `events: Flow` — gözlemle, logla, restore et, ve navigasyonu **UI olmadan test et** (`GezginTestNavigator`).
- **DI-agnostik.** Hilt / Koin / manuel — Gezgin seni bir DI'a mahkûm etmez. MVI ekranlar için opsiyonel `gezgin-mvi` add-on'u; brownfield Fragment interop'u için `@FragmentScreen`.
- **Boilerplate üretilir.** Graph wiring, sonuç kanalı, entry kaydı — hepsi KSP.

---

## Kıyaslama

İyi-niyetli bir özet (2026 itibarıyla; kütüphaneler evrilir — düzeltmeye açık). Anahtar: ✅ birinci-sınıf · ◑ mümkün / kısmi / elle · ❌ pek değil.

| Özellik | **Gezgin** | Jetpack Navigation Compose | Compose Destinations | Voyager | Decompose |
|---|:---:|:---:|:---:|:---:|:---:|
| Compile-time type-safe hedefler | ✅ | ✅ *(2.8+)* | ✅ *(KSP)* | ✅ | ✅ |
| **Tanımlanmamış** kenara gidişi reddeder (ekran-başına navigator) | ✅ | ❌ | ❌ | ❌ | ◑ |
| Deklaratif ileri/geri davranışı (`@ReplaceTo`/`@BackTo`/`@NoBack`) | ✅ | ◑ *(elle `popUpTo`)* | ◑ | ◑ | ◑ |
| Type-safe sonuç geçişi | ✅ | ◑ *(`SavedStateHandle`)* | ✅ | ✅ | ✅ |
| Sonuç **process-death**'i sağ atlatır | ✅ | ◑ *(elle)* | ◑ | ◑ | ✅ |
| Tipli sonuç döndüren çok-ekranlı **alt-flow** | ✅ | ❌ | ❌ | ◑ | ◑ |
| Dialog / sheet / fullscreen = back-stack **entry** | ✅ | ◑ *(dialog)* | ◑ | ◑ | ◑ |
| State-as-data (gözlemlenebilir + serializable back stack) | ✅ | ◑ | ◑ | ◑ | ✅ |
| Navigasyonun UI'sız testi | ✅ | ◑ | ◑ | ◑ | ✅ |
| Elle graph wiring yok (codegen) | ✅ | ◑ | ✅ | ❌ | ❌ |
| Compose Multiplatform | ◑ *(Android + desktop; iOS/web derleme-düzeyi)* | ◑ | ◑ | ✅ | ✅ |
| Brownfield Fragment interop | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Çoklu back stack** (alt-nav sekmeleri, master/detail) | ❌ *(V2)* | ✅ | ✅ | ✅ | ✅ |
| **Deep link** | ❌ *(V2)* | ✅ | ✅ | ◑ | ◑ |
| Olgunluk | ⚠️ *alpha* | ✅ stabil | ✅ | ✅ | ✅ |

**Gezgin'in nişi:** *ekran-başına* compile-time kısıtı + entegre **sonuçlu-flow'lar**, **entry-olarak-modallar**, ve **PD-safe-varsayılan** — hepsi Navigation 3 üstünde. Jetpack Nav'ın yeni type-safe route'larını seviyorsan ama derleyicinin *tanımlanmamış* kenarları da reddetmesini ve sonuç/flow/modal/process-death'i kutudan çıkar çıkmaz vermesini istiyorsan, Gezgin tam o boşluğu doldurur.

> 🔮 **Dürüst eksikler — bu artefaktın bilinçli kapsamı dışında, V2 yol haritasında:** **çoklu back stack** ve **deep-link route dispatch**. Gezgin tek-stack'tir ve bu sürüm URL↔route dispatch sözleşmesi sunmaz ya da üretmez. Generic `Throwable` serileştirme, kalıcı screen-container/chrome API'leri ve Fragment modal interop da bu artefaktın dışındadır. Gezgin **alpha**'dır; Android Navigation 3 ailesi stable, desktop JetBrains portu ise alpha'dır.

---

## Kurulum

KSP + serialization plugin'lerini uygulayıp Maven Central koordinatlarını kullan (`group = io.github.sahsenvar`, `version = 0.1.0`):

```kotlin
plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("io.github.sahsenvar:gezgin-core:0.1.0")
    ksp("io.github.sahsenvar:gezgin-processor:0.1.0")

    // implementation("io.github.sahsenvar:gezgin-mvi:0.1.0")        // opsiyonel MVI add-on
    // testImplementation("io.github.sahsenvar:gezgin-test:0.1.0")   // UI'sız test: GezginTestNavigator + tipli fromX()
}
```

ZAD geçişi sırasında bir sheet route üzerinden composable taşımadan Material'ın varsayılan handle'ını geçici olarak kaldırabilir:

```kotlin
override val dragHandleMode: BottomSheetDragHandleMode
    get() = BottomSheetDragHandleMode.None
```

`Default` Material handle'ını korur; `None`, host'a `dragHandle = null` verir ve özel handle consumer-owned sheet içeriğinde kalır. Bu enum ve `BottomSheetContract.dragHandleMode`, `@OptIn(ExperimentalGezginMigrationApi::class)` gerektirir. Kalıcı presentation-slot API'si değil, migration köprüsüdür; V2 tasarımında deprecated edilebilir, değiştirilebilir veya kaldırılabilir.

| Modül | Rol |
|---|---|
| `gezgin-core` | Zorunlu. Annotation'lar, runtime, `GezginDisplay` (Compose katmanı), modal scene strategy'leri. DI-agnostik. |
| `gezgin-processor` | Zorunlu. Tipli navigator'ları + entry provider'larını üreten KSP2 işlemcisi. |
| `gezgin-mvi` | Opsiyonel. `@MviViewModel` / route-bound `@EffectHandler` + `GezginMvi<S, I, E>` + DI-detection (Hilt/Koin, androidx fallback). |
| `gezgin-test` | Opsiyonel (test). UI'sız `GezginTestNavigator` + tipli `fromX()` erişimcileri. |

İki build sınırı bilinçli olarak ayrıdır:

| Sınır | Doğrulanan sürümler |
|---|---|
| Gezgin root | Gradle 9.0.0, Kotlin 2.3.21, KSP 2.3.9, AGP 8.13.2, Compose Multiplatform 1.11.0; Android'de AndroidX Navigation 3 1.0.0 + lifecycle Navigation 3 2.10.0; desktop'ta JetBrains Navigation 3 1.0.0-alpha05 + lifecycle Navigation 3 2.10.0-alpha05; min SDK 24. |
| Bağımsız ZAD-shaped consumer | Kendi Gradle 9.4.1 wrapper'ı, Kotlin 2.3.21, KSP 2.3.9, AGP 9.2.1, JDK/JVM 21, compile/target SDK 37, Koin 4.2.2 + compiler plugin 1.0.1, AndroidX Navigation 3 1.0.0 + lifecycle Navigation 3 2.10.0. Dört Gezgin artefaktını tek exclusive repository'den (release smoke'ta Maven Central) çözer; composite/source substitution veya Maven Local fallback kullanmaz. |

Bunlar birbirinin yerine uygulanacak upgrade talimatları değil, farklı build rolleridir. Tam sözleşme: [docs/gezgin-design.md](docs/gezgin-design.md) §15.

### KSP seçenekleri

`ksp { arg("<ad>", "<değer>") }` ile verilir:

| Seçenek | Default | Ne zaman |
|---|---|---|
| `gezgin.emitSerializers` | `true` | Polimorfik `Route` `SerializersModule`'ünü kendin sağlıyorsan `false` ver (opt-out). |
| `gezgin.emitTestAccessors` | `false` | Tipli `GezginTestNavigator.fromX()` test erişimcilerini üretmek için `true` ver (opt-in). Flag'i modülün **`main`** KSP round'unda (graph'ların olduğu round) aç; erişimciler `main`'e üretilir, böylece `test` kaynak kümesi `nav.fromX()`'i doğrudan çağırır — çok-modül düzeninde de çalışır. `:gezgin-test`'i `main` compile classpath'ine `compileOnly` ekle (erişimciler derlensin; app runtime'ına sızmaz), `test` için `testImplementation` ile yeniden ekle. |

---

## Örnekler, sırayla

### 1 · Grafik = sealed route ağacı

```kotlin
@NavGraph
@Serializable
sealed interface HomeGraph {
    @Serializable
    data object FeedRoute : HomeGraph               // app-start route'u (host'a verilir)

    @GoTo(ProductRoute::class)
    @Serializable
    data object CatalogRoute : HomeGraph

    @Serializable
    data class ProductRoute(val id: String) : HomeGraph   // route = veri
}
```

Üyelik lexical nesting'den değil **deklare edilen supertype**'tan gelir — böylece büyük bir grafik dosyalara bölünebilir (flow başına bir dosya), 1000 satırlık graph dosyası olmadan.

### 2 · ⭐ Tanımlamadığın yere gidiş derlenmez

```kotlin
@Screen(CatalogRoute::class)
@Composable
fun CatalogScreen(nav: CatalogNavigator) {          // CatalogRoute'un kenarlarından üretilen tipli navigator
    Button(onClick = { nav.goToProduct(id = "sku-42") }) { Text("Ürünü aç") }   // ✅
    // nav.goToFeed()   // ❌ derleme hatası — Catalog'dan Feed'e deklare kenar yok
}
```

Klasik yol — `navController.navigate("product/$id")` — bir typo'da *runtime*'da patlar. Burada yanlış hedef ya da yanlış argüman tipi = **derleme hatası**.

### 3 · İleri & geri = deklaratif sözlük

```kotlin
@ReplaceTo(OrderPlacedRoute::class)                 // checkout flow'unu temizle ki geri form'a dönemesin
@Serializable
data class PaymentRoute(val cartId: String) : CartGraph
// → nav.replaceToOrderPlaced(orderId)

@NoBack                                             // terminal ekran: sistem/predictive back burada no-op
@Serializable
data class OrderPlacedRoute(val orderId: String) : CartGraph
```

| Annotation | Üretilen | Ne yapar |
|---|---|---|
| `@GoTo(X::class)` | `nav.goToX(params)` | push (değere göre single-top) |
| `@ReplaceTo(X::class, clearUpTo = …)` | `nav.replaceToX(params)` | bir route'a kadar temizle, sonra push |
| `@BackTo(X::class)` | `nav.backToX()` | belirli ataya sıçra |
| `@BackToStart` | `nav.backToStart()` | flow başına dön |
| `@NoBack` | — | terminal ekran için implicit/sistem back'i kapat |
| — | `nav.back()` / `nav.backWithResult(r)` | bir adım geri / tipli sonuçla dön |

### 4 · Tipli, PD-safe sonuç döndüren alt-flow'lar

```kotlin
@FlowGraph
@Serializable
sealed interface CheckoutFlow : ShopGraph, ResultFlow<OrderId> {   // tüm flow bir OrderId döndürür
    @StartDestination @Serializable data object CartRoute : CheckoutFlow
    // … PaymentRoute … ; nav.quitWith(OrderId(...)) flow'u bitirir ve sonucu teslim eder
}

// Çağıran result edge'ini deklare eder; route-bound handler başlatır ve sonucu toplar:
@GoForResult(CheckoutFlow::class)
@Serializable data object CatalogRoute : HomeGraph
// → nav.launchCheckout()  +  nav.checkoutResults: Flow<NavResult<OrderId>>
```

Maintained strict-MVI deseninde generated navigator route-bound `@EffectHandler`'a aittir: handler `launchX()` çağırır, `xResults`'ı `LaunchedEffect` içinde toplar ve her `NavResult`'ı typed Intent olarak VM'e iletir. Restore sonrası caller route/handler yeniden composition'a girince collector re-attach olur; navigator'ın kaydedilen result-bus slotu in-flight veya teslim edilmiş ama tüketilmemiş sonucu korur. Navigator'ı VM'e koyma; suspend `goToXForResult()` process-ömrü convenience'ıdır, PD-safe strict-MVI ownership modeli değildir.

### 5 · Modallar özel state değil, back-stack entry'leri

```kotlin
@Dialog(ConfirmRoute::class)          // ayrıca @BottomSheet, @FullscreenModal
@Composable
fun ConfirmDialog(route: ConfirmRoute, nav: ConfirmNavigator) {
    Button(onClick = { nav.backWithResult(true) }) { Text("Evet") }
}
```

Dialog / sheet / fullscreen modal = farklı render'lı, ekranla aynı entry — back stack'te durur, process-death'i sağ atlatır, ve tıpkı başka bir route gibi sonuç döndürebilir.

Sheet'ler üç bağımsız dismiss anahtarı sunar. Kullanıcı tarafından kapatılamaması gereken bir route üçünü de kapatır; `sheetGesturesEnabled` kaynak uyumluluğu için varsayılan olarak `true`'dur:

```kotlin
@Serializable
data object LockedSheetRoute : ShopGraph, BottomSheetContract {
    override val dismissOnBackPress: Boolean get() = false
    override val dismissOnClickOutside: Boolean get() = false
    override val sheetGesturesEnabled: Boolean get() = false
}
```

### 6 · Host bağlantısı

```kotlin
setContent {
    val navigator = rememberNavigator(
        start = FeedRoute,
        topology = gezginTopology,    // graph paketine üretilir
        json = gezginJson,            // üretilir: process-genelinde stable Json
        restoreKey = "$sessionGeneration:$appMode",
        onRootBack = { finish() },
    )
    GezginDisplay(navigator = navigator) {
        homeGraphEntries()            // üretilen provideXEntry() çağrılarını SEN toplarsın
    }
}
```

`restoreKey`, hem kayıtlı snapshot'ı hem de Android navigator-holder kimliğini namespace eder. Aynı boş-olmayan key ile recreation aynı stack'i restore eder; key değişirse `start` route'unda fresh navigator kurulur. Eski overload kaynak uyumluluğu için stabil bir legacy namespace kullanır; session/account/mode-aware uygulamalar kalıcı ve açık bir key vermelidir.

### 7 · State-as-data → gözlemle, restore et, UI'sız test et

```kotlin
navigator.backStack   // StateFlow<List<Route>> — gözlemle / logla
navigator.events      // Flow<NavEvent>          — analytics / devtools

// UI'sız test (gezgin-test): tipli navigasyonu sür, back stack'e assert et, Compose'suz.
val nav = GezginTestNavigator(start = CatalogRoute, topology = gezginTopology)
nav.fromCatalog().goToProduct("sku-42")
assertEquals(listOf(CatalogRoute, ProductRoute("sku-42")), nav.backStack)
```

Back stack `@Serializable` veri olduğundan process-death restore otomatiktir; bozuk / uyumsuz bir snapshot, crash-loop yerine fresh start'a düşer.

### Strict MVI add-on'u

Maintained MVI örnekleri yalnız şu yönü kullanır:

`intent -> onIntent -> effect -> @EffectHandler(route) -> typed navigator`

ViewModel state'i tutar ve effect emit eder; navigator tutmaz. Route-bound handler effect'i gözler ve tipli navigasyon çağrısına sahip olur:

```kotlin
@Screen(HomeRoute::class)
@Screen(FeaturedRoute::class)
@Composable
fun ColumnScope.SharedContent(
    state: SharedState,
    onIntent: (SharedIntent) -> Unit,
) { /* state render et; intent emit et */ }

@MviViewModel(HomeRoute::class)
class HomeViewModel : ViewModel(), GezginMvi<SharedState, SharedIntent, HomeEffect> {
    private val effectSink = GezginEffects<HomeEffect>()
    override val effects: Flow<HomeEffect> = effectSink.flow
    // uiState kısaltıldı
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

`@Screen` repeatable'dır. Bağlanan her route'un kendi `@MviViewModel(route)`'u ve route-bound handler'ı vardır. Paylaşılan content fonksiyonu tüm route'larla uyumlu State ve Intent tipleri kullanmalıdır; Effect ve tipli Navigator tipleri route'a göre farklı olabilir.

`@TopBar(route)` ve `@BottomBar(route)`, repeatable, migration-only ve `@ExperimentalGezginMigrationApi` ile korunan `gezgin-mvi` API'leridir. Üretilen yapı dış `Column`, top bar, content'in `ColumnScope`'unu koruyan `Column(Modifier.fillMaxWidth().weight(1f))` ve yalnız IME gizliyken bottom bar sırasındadır. Yalnız mevcut ZAD ekran şeklini korumak için vardır; migration kalıcı app-owned container'a geçtiğinde kaldırılmalıdır. Consumer açıkça `@OptIn(ExperimentalGezginMigrationApi::class)` bildirmelidir.

### Fragment interop

`@FragmentScreen`, eski View-tabanlı bir `Fragment`'ı yalnızca **screen entry** olarak host eder; route (`gezginArgs`) ve tipli navigator (`gezginNav`) enjekte edilir. Kullanan uygulama, Fragment restore başlamadan önce `Application.onCreate()` içinde `Gezgin.initFragmentInterop(gezginJson)` çağırmalıdır. Screen'in gerçek process-death restore'u zaten desteklenir; bu artefakt `DialogFragment` veya `BottomSheetDialogFragment` köprüsü eklemez.

---

## Devamı

- **[docs/gezgin-by-example.md](docs/gezgin-by-example.md)** — özellik özellik tam rehber (Türkçe).
- **[docs/gezgin-design.md](docs/gezgin-design.md)** — tasarım spesifikasyonu.
- **[sample/](sample/)** — iki çalışabilir örnek app (çok-modüllü showcase + tek-modüllü Shopr).
- **[CHANGELOG.md](CHANGELOG.md)** — sürüm notları.

## Lisans

Apache License 2.0 — bkz. [LICENSE](LICENSE). `Copyright 2026 Gezgin contributors`.
