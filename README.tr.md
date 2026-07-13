# Gezgin

**Compose Multiplatform için type-safe, annotation-tabanlı navigasyon.** Gidilecek yer bir *tip*'tir, string değil — ve tanımlamadığın bir yere gitmek **derlenmez**.

![License](https://img.shields.io/badge/license-Apache--2.0-blue) ![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF) ![Compose Multiplatform](https://img.shields.io/badge/Compose%20MP-1.11.0-4285F4) ![Status](https://img.shields.io/badge/status-alpha-orange)

> 🇬🇧 **English:** For the English README → **[README.md](README.md)**

Gezgin **Navigation 3** üzerinde çalışır. Navigasyon grafiğin bir `sealed interface` ağacıdır; bir KSP işlemcisi her route için **tipli, ekran-başına bir navigator** üretir — böylece bir ekran yalnız gerçekten deklare ettiğin kenarlar boyunca gidebilir. Back stack **gözlemlenebilir, serileştirilebilir veridir**; bu sayede process-death restore, loglama, UI'sız test ve MVI neredeyse bedavaya gelir.

---

## 10 saniyelik örnek

```kotlin
// 1 · grafik = sealed ağaç — deklare ettiğin kenar = elde ettiğin metot
@NavGraph
@Serializable
sealed interface ShopGraph {
    @GoTo(ProductRoute::class)                              // Catalog'un tek deklare çıkış kenarı
    @Serializable data object CatalogRoute : ShopGraph
    @Serializable data class ProductRoute(val id: String) : ShopGraph
    @Serializable data object CheckoutRoute : ShopGraph     // grafikte VAR — ama Catalog'dan ulaşılamaz
}

// 2 · ekranın tipli navigator'ı YALNIZ Catalog'un deklare kenarlarının metotlarına sahip
@Screen(CatalogRoute::class)
@Composable
fun CatalogScreen(nav: CatalogNavigator) {
    ProductGrid(onClick = { product -> nav.goToProduct(product.id) })  // ✅ @GoTo(ProductRoute) yazdın
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
| **Deep link** | ◑ *(yalnız manuel; declarative → V2)* | ✅ | ✅ | ◑ | ◑ |
| Olgunluk | ⚠️ *alpha* | ✅ stabil | ✅ | ✅ | ✅ |

**Gezgin'in nişi:** *ekran-başına* compile-time kısıtı + entegre **sonuçlu-flow'lar**, **entry-olarak-modallar**, ve **PD-safe-varsayılan** — hepsi Navigation 3 üstünde. Jetpack Nav'ın yeni type-safe route'larını seviyorsan ama derleyicinin *tanımlanmamış* kenarları da reddetmesini ve sonuç/flow/modal/process-death'i kutudan çıkar çıkmaz vermesini istiyorsan, Gezgin tam o boşluğu doldurur.

> 🔮 **Dürüst eksikler — V1'de bilinçli kapsam dışı, V2 yol haritasında:** **çoklu back stack** ve **declarative deep link**. Bugün Gezgin tek-stack; URL↔route tablosu ÜRETMEZ — bir deep link yine de *manuel* ele alınabilir (URL'i kendin ayrıştır, `nav.raw.navigate(route)` çağır), ama ergonomik/üretilmiş hâli V2 kalemi. Ayrıca Gezgin **alpha** ve (yine alpha olan) Navigation 3 üstünde; Jetpack Navigation Compose stabil, Voyager/Decompose ise özellikle iOS'ta daha çok savaş-testinden geçmiş çok-platform deneyimine sahip.

---

## Kurulum

> ⚠️ **Henüz Maven Central'da değil.** `maven-publish` yapılandırması şimdilik iskelet (uzak repository / signing YOK). Kaynaktan `./gradlew publishToMavenLocal` ile derleyip `mavenLocal()`'den tüketin. Aşağıdaki koordinatlar release günü için doğrudur.

KSP + serialization plugin'lerini uygulayıp artefaktları ekle (`group = dev.gezgin`, `version = 0.1.0-alpha01`):

```kotlin
plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("dev.gezgin:gezgin-core:0.1.0-alpha01")
    ksp("dev.gezgin:gezgin-processor:0.1.0-alpha01")

    // implementation("dev.gezgin:gezgin-mvi:0.1.0-alpha01")        // opsiyonel MVI add-on
    // testImplementation("dev.gezgin:gezgin-test:0.1.0-alpha01")   // UI'sız test: GezginTestNavigator + tipli fromX()
}
```

| Modül | Rol |
|---|---|
| `gezgin-core` | Zorunlu. Annotation'lar, runtime, `GezginDisplay` (Compose katmanı), modal scene strategy'leri. DI-agnostik. |
| `gezgin-processor` | Zorunlu. Tipli navigator'ları + entry provider'larını üreten KSP2 işlemcisi. |
| `gezgin-mvi` | Opsiyonel. `@MviViewModel` / `@ScreenEffect` + `GezginMvi<S, I, E>` + DI-detection (Hilt/Koin, androidx fallback). |
| `gezgin-test` | Opsiyonel (test). UI'sız `GezginTestNavigator` + tipli `fromX()` erişimcileri. |

Doğrulanan sürümler: Kotlin 2.2.20, KSP 2.2.20-2.0.2, Compose Multiplatform 1.11.0, Navigation 3 (Google `navigation3-runtime` 1.1.4 / JetBrains `navigation3` 1.0.0-alpha05), AGP 8.11.0, min SDK 24. Tam tablo + KSP seçenekleri: [docs/gezgin-design.md](docs/gezgin-design.md) §15. ⚠️ Navigation 3 hâlâ **alpha** — sürümler bilinçli pinlenmiştir.

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

Klasik yol — `navController.navigate("product/$id")` — bir typo'da *runtime*'da patlar. Burada yanlış hedef ya da yanlış argüman tipi = **derleme hatası**. Nadiren dinamik bir hedef mi gerekiyor? Bilinçli kaçış: `nav.raw.navigate(route)`.

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

// Çağıran flow'u başlatır ve sonucu toplar — gerçek process-death sonrası re-attach eder:
@GoForResult(CheckoutFlow::class)
@Serializable data object CatalogRoute : HomeGraph
// → nav.launchCheckout()  +  nav.checkoutResults: Flow<NavResult<OrderId>>
```

`xResults`'ı view-model'in `init {}`'inde (ya da bir composable `LaunchedEffect`'te) topla; OS process'i flow ortasında öldürüp restore etse bile sonuç teslim edilir.

### 5 · Modallar özel state değil, back-stack entry'leri

```kotlin
@Dialog(ConfirmRoute::class)          // ayrıca @BottomSheet, @FullscreenModal
@Composable
fun ConfirmDialog(route: ConfirmRoute, nav: ConfirmNavigator) {
    Button(onClick = { nav.backWithResult(true) }) { Text("Evet") }
}
```

Dialog / sheet / fullscreen modal = farklı render'lı, ekranla aynı entry — back stack'te durur, process-death'i sağ atlatır, ve tıpkı başka bir route gibi sonuç döndürebilir.

### 6 · Host bağlantısı

```kotlin
setContent {
    val navigator = rememberNavigator(
        start = FeedRoute,
        topology = gezginTopology,    // graph paketine üretilir
        json = gezginJson,            // üretilir: process-genelinde stable Json
        onRootBack = { finish() },
    )
    GezginDisplay(navigator = navigator) {
        homeGraphEntries()            // üretilen provideXEntry() çağrılarını SEN toplarsın
    }
}
```

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

### Opsiyonel add-on'lar

- **`gezgin-mvi`** — bir `ViewModel`'i `@MviViewModel(XRoute::class)` ile işaretle, `GezginMvi<State, Intent, Effect>` implement et; codegen onu ekrana bağlar (`@ScreenEffect` ile tek-seferlik, kayıpsız efektler).
- **`@FragmentScreen`** — View-tabanlı eski bir `Fragment`'ı entry olarak host et; route (`gezginArgs`) ve tipli navigator (`gezginNav`) enjekte edilir — Compose'a kademeli brownfield geçiş için.

---

## Devamı

- **[docs/gezgin-by-example.md](docs/gezgin-by-example.md)** — özellik özellik tam rehber (Türkçe).
- **[docs/gezgin-design.md](docs/gezgin-design.md)** — tasarım spesifikasyonu.
- **[sample/](sample/)** — iki çalışabilir örnek app (çok-modüllü showcase + tek-modüllü Shopr).

## Lisans

Apache License 2.0 — bkz. [LICENSE](LICENSE). `Copyright 2026 Gezgin contributors`.
