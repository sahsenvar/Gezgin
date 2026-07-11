# Gezgin — Örneklerle Tanıtım

> Compose Multiplatform için **type-safe, annotation + codegen** tabanlı navigasyon kütüphanesi.
> Tek bir küçük app (**Shopr**) üzerinden, tasarladığımız özellikleri sırayla gösteriyor.
> Altta **Navigation 3 (Nav3)** çalışır; Gezgin onun üzerine ergonomi + codegen + tip güvenliği koyar.

## 30 saniyelik özet

- **String route yok.** Navigasyon grafiği = `sealed interface` ağacı; gidilecek yer = tip.
- **Tanımlamadığın yere gidiş _derlenmez_.** Her ekran için tipli bir navigator üretilir; sadece deklare ettiğin kenarların metodu olur.
- **Boilerplate codegen'de.** Graph wiring, result kanalı, entry kaydı — hepsi KSP üretir (deep-link tablosu → 🔮 V2).
- **State-as-data çekirdek.** Back stack = gözlemlenebilir + serializable veri → test (UI'sız), log, process-death restore, MVI _bedavaya_ gelir.
- **DI-agnostik.** Koin/Hilt/manuel — kütüphane seni bir DI'a mahkûm etmez.

---

## 1. Navigasyon grafiği = sealed ağaç

Grafiği ve davranışı **sealed route ağacı** tutar; pikselleri composable. Nesting = subtyping.

```kotlin
@NavGraph
@Serializable
sealed interface HomeGraph : ShopGraph {

    @Serializable
    data object FeedRoute : HomeGraph                       // app-start olarak host'a verilir (NavGraph'ta @StartDestination yok)

    @GoTo(ProductRoute::class)
    @Serializable
    data object CatalogRoute : HomeGraph

    @Serializable
    data class ProductRoute(val id: String) : HomeGraph     // route = veri
}
```

**Neden önemli:** Grafik tek bakışta okunuyor, namespaced (`HomeGraph.ProductRoute`), ve `@Serializable` olduğu için **kendiliğinden** process-death'e dayanıklı + iOS/Web'de polimorfik serialize "bedava".

> **Guardrail 1 (derleme-zamanı):** app-start route'u (host'a verilen) ve `@FlowGraph` `@StartDestination`'ı codegen'in **argümansız** kurabileceği bir şey olmalı (`data object` ya da tüm parametreleri default/nullable). Aksi halde **build patlar** — "start ekranını kuramıyorum" hatasını runtime'da değil derlemede alırsın.

**Büyük graph'ı böl — flat-file yerleşim.** Üyelik `sealed interface` içine nesting'den değil, **deklare edilen supertype**'tan gelir → bir flow'u kapsayan graph'ın gövdesine gömmek **zorunda değilsin**; aynı pakette ayrı bir dosyada top-level durabilir. Sample'daki gerçek `SignUpFlow`, `AuthGraph.kt`'den `SignUpFlow.kt`'ye çıkarılmış hâliyle:

```kotlin
// SignUpFlow.kt — AuthGraph.kt'den AYRI dosya, AYNI paket (dev.gezgin.sample.navigation)
@FlowGraph
@Serializable
sealed interface SignUpFlow : AuthGraph {                       // supertype = üyelik; nesting'e gerek yok

    @StartDestination @GoTo(ProfileInfoScreenRoute::class)
    @Serializable data object CredentialsScreenRoute : SignUpFlow

    @GoTo(TermsScreenRoute::class)
    @Serializable data class ProfileInfoScreenRoute(val email: String) : SignUpFlow

    @BackToStart @Quit @QuitAndGoTo(HomeGraph.WelcomeScreenRoute::class)
    @Serializable data object TermsScreenRoute : SignUpFlow
}
```

Nested ve flat-file **denk** okunur (Kotlin sealed: alt-tip aynı paket+modülde). 500-1000 satırlık graph dosyalarını flow başına bölmek için önerilen desen. **Dikkat:** flow'u nested↔top-level taşımak FQ'sunu değiştirir → önceki sürümden serialize edilmiş back-stack restore edilemez (bir kez konumlandır, sonra sabit tut).

---

## 2. ⭐ Tanımlamadığın yere gidiş _derlenmez_

Her route'taki kenar (`@GoTo` vb.) o ekranın **tipli navigator'ını** üretir. Metot adı hedeften türetilir (`ProductRoute` → `goToProduct`).

```kotlin
@Screen(CatalogRoute::class)
@Composable
fun CatalogScreen(nav: CatalogNavigator) {
    ProductGrid(
        onClick = { product -> nav.goToProduct(product.id) }   // ✅ var, çünkü @GoTo(ProductRoute) yazdık
    )
    // nav.goToCheckout()   // ❌ DERLENMEZ — CatalogRoute'tan Checkout'a kenar yok
}
```

Klasik yöntemle kıyas:

```kotlin
// Klasik (string ya da tek global navController): her yerden her yere gidebilirsin,
// hata runtime'da patlar ("route not found"):
navController.navigate("product/$id")          // 😬 typo? yanlış arg tipi? çalışınca anlarsın

// Gezgin: yanlış hedef / yanlış arg tipi = COMPILE ERROR.
nav.goToProduct(id = product.id)               // 🔒 imza tipli, IDE tamamlar
```

**Neden önemli:** "Nereye gidebilirim?" sorusunun cevabı **IDE otomatik-tamamlamasında**. Enforcement bir flag değil, API'nin şeklinin doğal sonucu. Nadiren dinamik bir hedef gerekirse bilinçli kaçış: `nav.raw.navigate(route)`.

---

## 3. İleri-gidiş sözlüğü: push / replace / switch

Aynı hedefe farklı davranış = farklı kenar = farklı metot. Davranış annotation'da (compile-time), runtime lambda yok.

| Annotation | Üretilen metot | Ne yapar |
|---|---|---|
| `@GoTo(X::class, Y::class)` | `goToX(params)` · `goToY(params)` | her hedef için push (değere göre singleTop) |
| `@ReplaceTo(X::class, clearUpTo = Self)` | `replaceToX(params)` | `clearUpTo`'ya kadar temizle + push (`Self` = mevcut route) |
| `@GoForResult(X::class)` | `launchX(...)` · `xResults: Flow<NavResult<T>>` · `suspend goToXForResult()` | tetik + PD-safe re-attach stream + sugar (hedef `ResultFlow`/`ResultRoute`) |

Checkout'un en güzel yeri — **başarıyla biten ödeme**: result ekranını push'lamak yerine **replace** edersin ki kullanıcı geri tuşuyla ödeme formuna dönemesin:

```kotlin
@ReplaceTo(OrderPlacedRoute::class)            // ödeme akışını temizle (clearUpTo = Self)
@Serializable
data class PaymentRoute(val cartId: String) : CartGraph

// PaymentScreen / VM:
nav.replaceToOrderPlaced(orderId)             // geri = OrderPlaced'in altı, form değil
```

**Neden önemli:** "Geri gidince formu görmesin" gibi niyetler **deklaratif**. `popUpTo(..., inclusive=true)` elle hesaplamak yok.

**Geri sözlüğü** — ileri = *topoloji*, geri = *geçmiş*. İlke: declared olan sessizce silinmez; route'a bakınca **tüm** geri yüzeyini görürsün:

| | Üretilen | Ne yapar |
|---|---|---|
| tek implicit | `nav.back()` | bir adım geri (her ekranda; `@NoBack` ile kapanır) |
| contract'tan | `nav.backWithResult(r)` | `ResultRoute<T>` ise tipli sonuçla dön |
| `@BackTo(CartRoute::class)` | `nav.backToCart()` | belirli ataya sıçra |
| `@BackToStart` | `nav.backToStart()` | flow başına (flow dışında = derleme hatası) |
| `@Quit` | `nav.quit()` | flow'dan çık (yalnız `@FlowGraph` üyesinde; `@NavGraph`'ta = derleme hatası) |

Terminal ekran (result gibi) `@NoBack` → **sadece** doğal `back()` + sistem/predictive back kapanır; declared olanlar durur:

```kotlin
@NoBack @BackTo(FeedRoute::class)               // terminal: doğal back yok ama backToFeed() var
@Serializable
data class OrderPlacedRoute(val orderId: String) : CartGraph   // @Quit olamaz: CartGraph bir @NavGraph
```

---

## 4. ⭐ Result passing — tip güvenli ve process-death'e dayanıklı

Bir ekrandan değer döndürmek (adres seç, fotoğraf çek, onay al). Hedef route ürettiği tipi **kendisi** deklare eder (`ResultRoute<T>`), çağıran `@GoForResult` der.

```kotlin
@Serializable
data class SelectAddressRoute(val userId: String) :
    CartGraph, ResultRoute<Address>            // "ben Address döndürürüm"

@GoForResult(SelectAddressRoute::class)
@Serializable
data class CheckoutRoute(val cartId: String) : CartGraph
```

Çağıran taraf — düz, suspend, callback yok:

```kotlin
// CheckoutScreen / VM:
when (val r = nav.goToSelectAddressForResult(userId)) {
    is NavResult.Value -> setState { copy(address = r.value) }   // tip: Address
    NavResult.Canceled -> Unit                                   // düz geri = iptal
}
```

Döndüren taraf:

```kotlin
@Screen(SelectAddressRoute::class)
@Composable
fun SelectAddressScreen(route: SelectAddressRoute, nav: SelectAddressNavigator) {
    AddressList(onPick = { nav.backWithResult(it) })             // ✅ tipli geri
}
```

Klasik yöntemle kıyas (Zad'da gerçekte gördüğümüz acı):

```kotlin
// Klasik: callback'i @Serializable route'un İÇİNE gömmek →
// fonksiyon serialize edilemez → process death'te kaybolur, restore patlar:
data class SelectAddressRoute(val onPicked: (Address) -> Unit) : NavKey   // 😬
```

**Neden önemli:** Sonuç, canlı bir coroutine continuation'ında değil; navigator state'inde **kalıcı, keyed bir slotta** akar. PD sınırını da aşmak için **receive yüzeyi** var — `@GoForResult` üç üye üretir: `launchSelectAddress(userId)` (tetik) · `selectAddressResults: Flow<NavResult<Address>>` (re-attach; her recreation'da yeniden collect edilir, süreç ölüp dirilse bile sonuç **replay** edilir) · üstteki suspend sugar (VM ömrü içinde güvenli).

```kotlin
// PD-safe re-attach — VM init'te kurulur:
init { viewModelScope.launch { nav.selectAddressResults.collect(::onAddress) } }
fun onPickAddress() = nav.launchSelectAddress(userId)     // tetik; sonuç yukarıdan akar
```

"Lambda yasak" kuralı, serializable-stack'i _gerçekten_ mümkün kılan şey.

---

## 5. Modal = navigasyon (dialog / bottom-sheet / fullscreen)

Modal ayrı bir mekanizma değil — normal back stack entry'si, sadece **render varyantı** (Nav3 SceneStrategy ile overlay; arka görünür). Kind composable'da, properties route'ta.

Sonuç döndüren bir onay dialog'u (başlık/mesaj backend'den geliyor → constructor param):

```kotlin
@Serializable
data class ConfirmOrderDialog(val summary: String) :
    CartGraph, DialogContract, ResultRoute<Boolean> {
    override val dismissOnClickOutside = false                  // SABİT davranış = override
}

@Dialog(ConfirmOrderDialog::class)                               // kind: dialog
@Composable
fun ConfirmOrderDialogScreen(route: ConfirmOrderDialog, nav: ConfirmOrderNavigator) {
    Surface {                                                    // composable = SADECE içerik
        Text(route.summary)
        Row {
            TextButton(onClick = { nav.backWithResult(false) }) { Text("Vazgeç") }
            Button(onClick     = { nav.backWithResult(true)  }) { Text("Onayla") }
        }
    }
}
```

Bottom-sheet'te `controller` composable'a enjekte edilir (önce hide animasyonu, sonra sonuç):

```kotlin
@BottomSheet(SortSheetRoute::class)
@Composable
fun SortSheetScreen(nav: SortSheetNavigator, controller: GezginSheetController) {
    SortOptions(onPick = { option ->
        scope.launch { controller.hide(); nav.backWithResult(option) }
    })
}
```

**Neden önemli:** Dialog/sheet de doğal "sonuç üreticisi" (`ResultRoute<T>` + `backWithResult`). Dışarı tıklama/swipe = `Canceled`. Entry-scoped ViewModel dialog/sheet için de geçerli — loading→content akışı ekranla birebir aynı. Ekstra `GezginDialog` sarmalayıcısı yok → DRY.

---

## 6. 🔮 Sekmeler — **V2 (planlanan)**

**V1'de tek-stack; bottom-nav uygulama-yönetimli** (Gezgin `goTo`/`replaceTo` verir, app kendi bar'ını çizer). Aşağıdaki `@TabGraph` + per-sekme back stack **V2 vizyonu** — her sekme kendi geçmişini korur:

```kotlin
@TabGraph @Serializable sealed interface ShopGraph                                    // (V2) switcher = bottom-nav

@DefaultTab @NavGraph @Serializable sealed interface HomeGraph    : ShopGraph { /* ... */ }         // default tab
@NavGraph              @Serializable sealed interface CartGraph    : ShopGraph { /* ... */ }
@NavGraph              @Serializable sealed interface ProfileGraph : ShopGraph { /* ... */ }

@Composable
fun ShopShell(nav: ShopNavigator) {
    Scaffold(
        bottomBar = {
            BottomBar(
                onHome    = { nav.switchToHome() },
                onCart    = { nav.switchToCart() },
                onProfile = { nav.switchToProfile() },
            )
        }
    ) { GezginDisplay(navigator = nav) { shopEntries() } }
}
```

**Neden önemli:** Profile sekmesinde 3 ekran derine indin, Home'a geçip geri döndün — **Profile stack'in duruyor**. Temsil: `Map<Tab, List<Route>>` (Nav3'ün resmi deseni), aktif sekme(ler) düz listeye flatten edilir. Save/restore otomatik (serializable state).

---

## 7. 🔮 Deep link — **V2 (planlanan)**

> V1'de yok. V2 yönü: path-hiyerarşisi = back stack (`@DeepLink(segment, parent)` tekrarlanabilir), compile-time placeholder doğrulaması, tip-güvenli URL builder + QA katalog.

```kotlin
@DeepLink("shopr://product/{id}")
@Serializable
data class ProductRoute(val id: String) : HomeGraph
```

```kotlin
navigator.handleDeepLink("shopr://product/42")
// Gezgin tip zincirini yürür → back stack'i kurar:  [FeedRoute, ProductRoute("42")]
// kullanıcı geri tuşuna basınca Feed'e düşer, app'ten atılmaz.
```

V2'de `{id}` yer tutucusu **derlemede** route property'leriyle eşleştirilecek:

```kotlin
@DeepLink("shopr://product/{productId}")       // ❌ DERLENMEZ — route'ta productId yok (sadece id var)
data class ProductRoute(val id: String) : HomeGraph
```

**Neden önemli (🔮 V2 hedefi):** Tipik kütüphanelerde deep-link bir string-eşleştirme; yanlış param adını **kullanıcı tıklayınca** öğrenirsin. Gezgin'in V2 yönünde typo/eksik/decode-edilemez param = **build hatası** olacak; üstelik hedefin atalarını (graph zinciri) doğru kuracak.

---

## 8. UI'sız test — saf Kotlin

State-as-data çekirdek sayesinde navigasyon davranışını **Compose/emülatör olmadan** assert edersin.

```kotlin
@Test
fun checkout_adres_secimini_doner() = runTest {
    val nav = GezginTestNavigator(start = CartGraph.CartRoute, topology = gezginTopology)   // gezginTopology: codegen üretir

    val result = async { nav.from<CheckoutRoute>().goToSelectAddressForResult(userId = "u1") }
    nav.deliverResult(Address(id = "1", label = "Ev"))

    result.await() shouldBe NavResult.Value(Address("1", "Ev"))
}

@Test
fun replaceTo_odeme_akisini_temizler() {
    val nav = GezginTestNavigator(start = CartGraph.PaymentRoute("cart1"), topology = gezginTopology)
    nav.from<PaymentRoute>().replaceToOrderPlaced("order1")   // ödeme akışını temizle
    nav.current shouldBe OrderPlacedRoute("order1")
    nav.backStack shouldHaveSize 1                            // form gitti, geri ödemeye dönülemez
}
```

**Neden önemli:** Navigasyon mantığı (result, replace, back, quit) birim testte; enforcement'ı test etmene gerek yok — o zaten compile-time garanti.

---

## 9. State-as-data: gözlemle, logla, restore et

Back stack gözlemlenebilir veri olduğu için Redux-vari araçlar bedava:

```kotlin
navigator.backStack    // StateFlow<List<Route>>   → devtools / "şu an neredeyiz" göstergesi
navigator.events       // Flow<NavEvent>           → analytics / ekran-görüntüleme logları

// Observe-only middleware: events'i DIŞARIDAN collect edersin (GezginDisplay param'ı DEĞİL) —
// OkHttp interceptor gibi "takılır" ama akışı etkilemez, yalnız gözler.
LaunchedEffect(nav) {
    nav.events.collect { event -> /* NavLogger / Analytics */ }
}

GezginDisplay(
    navigator   = nav,
    transitions = navTransitions { forward { /* app-geneli ileri */ } backward { /* app-geneli geri */ } },
) { shopEntries() }                                  // multi-module: her feature kendi entry bundle'ını verir
```

Transition üç seviyede; en içteki kazanır (screen > graph > app), runtime değer:

```kotlin
@Serializable
data class ProductRoute(val id: String) : HomeGraph {
    override val transition get() = transition { /* forward; back; predictive */ }
}
```

**Neden önemli:** Process death restore = state'i serialize/deserialize. Log = state'i dinle. MVI = state'i gözle. Hepsi tek bir "navigasyon = veri" kararından düşüyor.

---

## Kapanış — Gezgin'in tek cümlelik vaadi

> Navigasyonu **tip güvenli veri** olarak modelleyip boilerplate'i codegen'e yıkarak; "tanımsız yere gidiş derlenmez", "sonuç process-death'e dayanır", "test UI'sız çalışır" garantilerini _aynı anda_ veren CMP navigasyon katmanı.

| Özellik | Gezgin'in yaklaşımı |
|---|---|
| Route güvenliği | sealed ağaç + tipli per-source navigator (derleme garantisi) |
| Boilerplate | KSP codegen (wiring, result, entry; deep-link → 🔮 V2) |
| Result passing | `ResultRoute<T>` + `backWithResult`; launch/receive ayrımı, PD-safe keyed slot |
| Modal | render varyantı + route contract (DRY) |
| Sekmeler (🔮 V2) | `@TabGraph` + per-sekme stack; V1 tek-stack, bottom-nav app-yönetimli |
| Deep link (🔮 V2) | path-hiyerarşisi = back stack; compile-time placeholder |
| Test | `GezginTestNavigator`, saf Kotlin |
| Gözlemlenebilirlik | `backStack: StateFlow`, `events: Flow` (observe-only, `LaunchedEffect`'te collect) |
| DI | agnostik (Koin/Hilt/manuel) |
| Altyapı | Navigation 3 (Android stable; iOS/Desktop/Web JetBrains portu **alpha**, CMP 1.10+) |
