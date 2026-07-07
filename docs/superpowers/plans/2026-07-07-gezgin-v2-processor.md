# Gezgin Faz 2 — KSP Processor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox syntax.

**Goal:** `docs/gezgin-design.md` §14'ün Faz-2 dilimi: annotation seti + KSP processor — sealed graph okuma, E1–E5 doğrulama, `GezginTopology`/`SerializersModule` emiti ve **tipli per-source navigator** codegen'i ("tanımsız yere gidiş derlenmez" garantisinin doğduğu yer).

**Architecture:** `:gezgin-core`'a saf annotation/marker seti eklenir (bağımlılıksız). Yeni `:gezgin-processor` (JVM) KSP2 üzerinde çalışır: (1) model okuma → (2) doğrulama → (3) KotlinPoet codegen. Testler kctfork (kotlin-compile-testing fork) ile: pozitif derleme + **classloader'dan yükleyip gerçek `RawNavigator`'a karşı ÇALIŞTIRMA** + negatif derleme (hata kodu/mesaj assert).

**Tech Stack:** KSP2 (`symbol-processing-api`) · KotlinPoet (+`kotlinpoet-ksp`) · `dev.zacsweers.kctfork:ksp` (KSP2 destekli kotlin-compile-testing fork'u; sürümü execution'da doğrula, `useKsp2()` şart) · mevcut `:gezgin-core` jvm artefaktı test classpath'inde.

## Global Constraints

- Spec = `docs/gezgin-design.md` (§3, §4, §6, §8.1, §14); çelişkide spec kazanır. Master plan = `docs/superpowers/plans/2026-07-07-gezgin-v1-implementation.md` (Global Constraints geçerli).
- **Kapsam sapması (bilinçli, bu planla kayıt altında):** master plan 2.5'teki `provideXEntry`/`GezginEntryScope` codegen'i **Faz 3'e taşındı** — Compose bağımlılığı gerektirir; Faz 2 gate'i navigator+topology+validation üzerinedir. Deep-link tablosu V2 (yazılmaz).
- Üyelik = **lexical nesting** (E5); flow chain = nesting zinciri. Edge'ler yalnız route CLASS'ları üzerinde.
- Navigator: `class XNavigator internal constructor(raw: RawNavigator, entryId: Long)` — **caller = entryId** (Faz 1 final-review kararı: explicit-caller API); factory `fun RawNavigator.xNavigator(entryId: Long): XNavigator` (public). For-result üçlüsü explicit-caller overload'larını kullanır.
- Edge id formatı: `"<SourceSimpleName>→<TargetSimpleName>[#name]"` (N9 `name=` varsa suffix).
- Metot adı türetme: hedef simple name'den `Route`/`Screen` eki atılır (`ProductRoute` → `goToProduct`).
- Üretilen dosyalar: nav modülünün paketine `Gezgin` prefix'li (`GezginTopology_<Module>.kt` yerine basitçe `GezginGenerated.kt` + `<X>Navigator.kt` — görevlerde net).
- Hata kodları: **E1** ResultFlow'a giriş yalnız @GoForResult · **E2** @GoForResult hedefi ResultRoute/ResultFlow · **E3** flow iç üyesine dışarıdan edge · **E4** flow üyesinde clearUpTo flow-dışı · **E5** route ikinci graph interface'i implement edemez · **G1** start argsız kurulamıyor · **N9** isimsiz edge çakışması · **R1** ResultFlow non-@FlowGraph'ta · **NB1** @NoBack+flow-start · **SD1** @FlowGraph'ta @StartDestination tam bir tane / @NavGraph'ta hiç · **FX1** @BackToStart/@Quit yalnız flow üyesinde · **FX2** @QuitAndGoTo yalnız result'suz flow üyesinde, hedef ResultFlow olamaz (E1'in alt hali) . KSP `logger.error("[Ex] mesaj", symbol)` formatı.
- TDD: her doğrulama kuralı = 1 FAIL compile-testi (+1 pozitif); her codegen davranışı = compile+run testi. Sık commit.

## Faz-2 görev haritası

| # | Görev | Gate |
|---|---|---|
| 2.0 | kctfork+KSP2 spike: `:gezgin-processor` iskeleti + trivial processor + 1 compile-test | spike testi yeşil (NAMED RISK) |
| 2.1 | Annotation seti + marker'lar (`:gezgin-core`) | derlenir; Faz-1 testleri etkilenmez |
| 2.2 | Model okuma: `GraphModel` (graph/üye/nesting-chain/start/edge/result-tipleri) | model-dump testleri |
| 2.3 | Doğrulama: E1–E5, G1, N9, R1, NB1, SD1, FX1, FX2 | kural başına FAIL+pozitif compile-testler |
| 2.4 | Topology + SerializersModule codegen | üretilen kod compile+load; içerik runtime assert |
| 2.5 | Navigator codegen (edge/back/result üçlüsü/factory) | tipli round-trip GERÇEK RawNavigator'da çalışır; undeclared-edge derlenmez |
| 2.6 | `GezginTestNavigator.from<Source>()` tipli erişim (`:gezgin-test`) | §13 örneği tipli hâliyle çalışır |

---

### Task 2.0: `:gezgin-processor` iskeleti + kctfork/KSP2 spike

**Files:**
- Modify: `settings.gradle.kts` (+`include(":gezgin-processor")`), `gradle/libs.versions.toml`
- Create: `gezgin-processor/build.gradle.kts`, `gezgin-processor/src/main/kotlin/dev/gezgin/processor/GezginProcessor.kt`, `.../GezginProcessorProvider.kt`, `gezgin-processor/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`, `gezgin-processor/src/test/kotlin/dev/gezgin/processor/CompileHarness.kt` + `SpikeTest.kt`

**Interfaces:** Produces: çalışan compile-test harness'ı — `compile(vararg sources: SourceFile): JvmCompilationResult` helper'ı (KSP2, gezgin-core jvm classpath'te, messageOutput yakalanır). Sonraki TÜM görevler bunu kullanır.

- [ ] **Step 1:** Version catalog ekleri (sürümleri execution'da doğrula; KSP zaten `2.2.20-2.0.2`):
```toml
kotlinpoet = "2.2.0"
kctfork = "0.9.0"
[libraries]
ksp-api = { module = "com.google.devtools.ksp:symbol-processing-api", version.ref = "ksp" }
kotlinpoet = { module = "com.squareup:kotlinpoet", version.ref = "kotlinpoet" }
kotlinpoet-ksp = { module = "com.squareup:kotlinpoet-ksp", version.ref = "kotlinpoet" }
kctfork-ksp = { module = "dev.zacsweers.kctfork:ksp", version.ref = "kctfork" }
```
- [ ] **Step 2:** `gezgin-processor/build.gradle.kts` — `plugins { kotlin("jvm") }`; deps: `implementation(libs.ksp.api, libs.kotlinpoet, libs.kotlinpoet.ksp)`; test: `kctfork-ksp`, `kotlin("test")`, `kotlinx-coroutines-test`, `testImplementation(project(":gezgin-core"))`.
- [ ] **Step 3:** Trivial processor (yalnız "Gezgin processor alive" info log) + provider + service dosyası.
- [ ] **Step 4:** `CompileHarness.kt`:
```kotlin
fun compileGezgin(vararg sources: SourceFile): JvmCompilationResult =
    KotlinCompilation().apply {
        this.sources = sources.toList()
        configureKsp(useKsp2 = true) { symbolProcessorProviders += GezginProcessorProvider() }
        inheritClassPath = true      // gezgin-core + coroutines test classpath'ten gelir
        messageOutputStream = ByteArrayOutputStream().also { messages = it }  // harness alanı
    }.compile()
```
(kctfork API'si sürüme göre ufak farklı olabilir — spike'ın işi tam bunu sabitlemek. Harness ayrıca `result.generatedSourceFor(name)` benzeri yardımcı sunmalı.)
- [ ] **Step 5:** SpikeTest: `Route` implement eden 1 sınıflık kaynak derlenir, `exitCode == OK`, processor log'u görünür. FAIL→GREEN klasik TDD burada anlamsız (altyapı) — testin kendisi gate.
- [ ] **Step 6:** Commit `chore(processor): module skeleton + kctfork/KSP2 spike harness`.

**Eskalasyon:** kctfork KSP2 ile çalışmazsa (yarım gün sınırı): fallback = `useKsp2=false` (KSP1) ile ilerle + ledger'a not (KSP1/KSP2 farkı model-okuma API'sinde minimal); o da olmazsa BLOCKED raporu.

### Task 2.1: Annotation seti + marker'lar (`:gezgin-core`)

**Files:** Create: `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/annotation/Annotations.kt`, `.../core/Markers.kt`; Test: `AnnotationsSmokeTest.kt` (commonTest)

**Interfaces:** Produces (İMZALAR SÖZLEŞME — processor string-FQN ile okur: paket `dev.gezgin.core.annotation`):
```kotlin
@Target(AnnotationTarget.CLASS) annotation class NavGraph
@Target(AnnotationTarget.CLASS) annotation class FlowGraph
@Target(AnnotationTarget.CLASS) annotation class StartDestination
@Target(AnnotationTarget.CLASS) @Repeatable
annotation class GoTo(val target: KClass<out Route>, val singleTop: Boolean = true, val name: String = "")
@Target(AnnotationTarget.CLASS) @Repeatable
annotation class ReplaceTo(val target: KClass<out Route>, val clearUpTo: KClass<out Route> = Self::class, val inclusive: Boolean = true, val name: String = "")
@Target(AnnotationTarget.CLASS) @Repeatable
annotation class GoForResult(val target: KClass<out Route>, val name: String = "")
@Target(AnnotationTarget.CLASS) @Repeatable
annotation class BackTo(val target: KClass<out Route>, val inclusive: Boolean = false)
@Target(AnnotationTarget.CLASS) annotation class BackToStart
@Target(AnnotationTarget.CLASS) annotation class Quit
@Target(AnnotationTarget.CLASS) annotation class QuitAndGoTo(val target: KClass<out Route>)
@Target(AnnotationTarget.CLASS) annotation class NoBack
```
`Markers.kt` (`dev.gezgin.core`): `object Self : Route` (sentinel; serialization'a kayıtsız) · `interface ResultRoute<T>` · `interface ResultFlow<T>`.
Kind annotation'ları (`@Screen/@Dialog/@BottomSheet/@FullscreenModal`) **Faz 3'te** (Compose ile birlikte) — burada YAZILMAZ.

- [ ] Steps: smoke test (bir fixture route'a annotation'ları uygula, derle, `assertTrue(true)` yerine annotation'ın `@Repeatable` çift kullanımını içeren gerçek kullanım) → FAIL (unresolved) → implement → PASS → full jvmTest → commit `feat(core): annotation set + ResultRoute/ResultFlow/Self markers`.

### Task 2.2: Model okuma — `GraphModel`

**Files:** Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/model/GraphModel.kt`, `.../model/ModelReader.kt`; Test: `ModelReaderTest.kt` (+`fixtures/ShopSource.kt` — string kaynaklı test fixture'ı: by-example Shopr grafiğinin küçültülmüşü: `@NavGraph HomeGraph{Feed,Catalog,Product(id)}` + `@FlowGraph CheckoutFlow: ResultFlow<OrderId>{@StartDestination Cart, Payment}` + nested `@FlowGraph PayAuthFlow{@StartDestination Otp}` CheckoutFlow içinde + edge'ler)

**Interfaces:** Produces:
```kotlin
data class RouteModel(val fqName: String, val simpleName: String, val graphFq: String,
    val flowChainFq: List<String>, val ctorParams: List<ParamModel>, val isStart: Boolean,
    val noBack: Boolean, val resultTypeFq: String?,            // ResultRoute<T> ise T
    val edges: List<EdgeModel>, val backEdges: List<BackEdgeModel>)
data class EdgeModel(val kind: EdgeKind, val targetFq: String, val singleTop: Boolean,
    val clearUpToFq: String?, val inclusive: Boolean, val name: String)   // kind: GO_TO/REPLACE_TO/GO_FOR_RESULT/QUIT_AND_GO_TO
data class GraphModelNode(val fqName: String, val isFlow: Boolean, val isResultFlow: Boolean,
    val resultTypeFq: String?, val startFq: String?, val memberFq: List<String>, val parentFlowFq: String?)
data class GraphModel(val graphs: List<GraphModelNode>, val routes: List<RouteModel>)
class ModelReader(resolver: Resolver, logger: KSPLogger) { fun read(): GraphModel }
```
Okuma kuralları: üyelik = **declarations nested in the interface**; flow chain = nesting zinciri boyunca @FlowGraph'lar; `ResultFlow<T>`/`ResultRoute<T>` T'si supertype type-argument'tan (transitively, substitution ile); annotation arg'ları KClass → `KSType` üzerinden FQ name.
Test yöntemi: processor'a test-only "model dump" opsiyonu (`ksp arg gezgin.dumpModel=true` → modeli deterministik string olarak `GezginModelDump.txt`'e yazar); test derleyip dump'ı assert eder (satır bazlı: her route'un chain'i, start'lar, edge'ler, result tipleri).

- [ ] Steps: dump-assert testleri yaz (Feed chain boş; Cart chain=[CheckoutFlow]; Otp chain=[CheckoutFlow,PayAuthFlow]; CheckoutFlow resultType=OrderId; Product ctorParams=[id:String]; edge listesi) → FAIL → ModelReader implement → PASS → commit `feat(processor): GraphModel + ModelReader (nesting, chains, supertype result types)`.

### Task 2.3: Doğrulama kuralları

**Files:** Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/Validation.kt`; Test: `ValidationTest.kt`

Her kural için: **ihlal eden minimal kaynak → `exitCode != OK` + mesajda `[Ex]` kodu assert**; ve ihlalsiz pozitif derleme. Kurallar (Global Constraints'teki kod listesi): E1 (@GoTo/@ReplaceTo/@QuitAndGoTo → ResultFlow container), E2 (@GoForResult → result'suz hedef), E3 (herhangi edge → flow İÇ üyesi), E4 (flow üyesinde clearUpTo flow-dışı), E5 (route nested olduğu graph dışında ikinci graph interface'i implement eder), G1 (FlowGraph start'ı zorunlu-paramlı), N9 (aynı hedefe iki edge, name'siz), R1 (ResultFlow'u @NavGraph implement eder), NB1 (@NoBack + @StartDestination), SD1 (@FlowGraph'ta 0 veya 2 start; @NavGraph'ta start var), FX1 (@Quit @NavGraph üyesinde), FX2 (@QuitAndGoTo ResultFlow üyesinde).

- [ ] Steps: kural başına test grubu (RED: kod yokken hepsi OK derlenir → validation implement → ihlaller FAIL olur; pozitifler OK kalır) → commit `feat(processor): validation E1-E5 + guardrails (G1,N9,R1,NB1,SD1,FX1,FX2)`.

### Task 2.4: Topology + SerializersModule codegen

**Files:** Create: `.../codegen/TopologyCodegen.kt`; Test: `TopologyCodegenTest.kt`

**Üretilen (golden şekil — nav modülünün paketinde `GezginGenerated.kt`):**
```kotlin
val gezginTopology: GezginTopology = GezginTopology(
    flowChains = mapOf(
        Cart::class to listOf(FlowType("com.x.CheckoutFlow", isResultFlow = true)),
        Otp::class to listOf(FlowType("com.x.CheckoutFlow", true), FlowType("com.x.PayAuthFlow", false)),
        /* … tüm flow üyeleri */),
    flowStarts = mapOf("com.x.CheckoutFlow" to Cart::class, "com.x.PayAuthFlow" to Otp::class),
    edges = mapOf("Catalog→CheckoutFlow" to EdgeSpec("Catalog→CheckoutFlow", OrderId.serializer()), /* tüm @GoForResult edge'leri */),
)
val gezginSerializersModule: SerializersModule = SerializersModule {
    polymorphic(Route::class) { subclass(Feed::class); /* tüm somut route'lar */ }
}
```
Test: compile → `result.classLoader.loadClass(...)` → reflection'la `gezginTopology`'i al, `GezginTopology` API'siyle içerik assert (chains/starts/edges — Faz 1 tipleri classpath'te olduğundan CAST edilebilir).

- [ ] Steps: run-assert testleri → FAIL → KotlinPoet emit → PASS → commit `feat(processor): topology + serializers module codegen`.

### Task 2.5: Navigator codegen — çekirdek değer önermesi

**Files:** Create: `.../codegen/NavigatorCodegen.kt`; Test: `NavigatorCodegenTest.kt`

**Üretilen (golden şekil — kaynak başına `<X>Navigator.kt`):**
```kotlin
class CatalogNavigator internal constructor(private val raw: RawNavigator, private val entryId: Long) {
    fun goToProduct(id: String) { raw.navigate(Product(id), singleTop = true) }
    fun launchCheckout() { raw.launchForResult(entryId, "Catalog→CheckoutFlow", Cart) }
    val checkoutResults: Flow<NavResult<OrderId>> get() = raw.results(entryId, "Catalog→CheckoutFlow")
    suspend fun goToCheckoutForResult(): NavResult<OrderId> =
        raw.navigateForResult(entryId, "Catalog→CheckoutFlow", Cart)
    fun back() { raw.back() }
}
fun RawNavigator.catalogNavigator(entryId: Long): CatalogNavigator = CatalogNavigator(this, entryId)
```
Kurallar: `@GoTo` → `goToX(ctorParams)`; `@ReplaceTo` → `replaceToX(params)` (`clearUpTo=Self` → null geç); `@GoForResult(Flow)` → flow-mode üçlü (start route push, start argsız — G1 garanti); `@GoForResult(ResultRoute)` → screen-mode üçlü (hedef params metot parametresi olur); `@BackTo(Y)` → `backToY()`; `@BackToStart` → `backToStart()` (=`raw.backTo(startClass, inclusive=false)`); `@Quit` → `quit()`; ResultFlow üyesi → `quitWith(t: T)`; `ResultRoute<T>` kaynak → `backWithResult(t: T)`; `@NoBack` → `back()` üretilmez; `@QuitAndGoTo(X)` → `quitAndGoToX()` (=quit+navigate... runtime karşılığı: `raw` üzerinde quitFlow+push — RawNavigator'a küçük `quitAndGoTo(route)` yardımcı eklenmesi gerekirse görev içinde ekle+test et); `name=` → metot adı override; `val raw` escape hatch (public getter).

**Run-testler (kctfork classLoader + GERÇEK RawNavigator):** (a) tipli round-trip: `catalogNavigator.goToCheckoutForResult()` async + üretilen `PaymentNavigator.quitWith(OrderId("x"))` → Value döner (Faz-1 goForResultRoundTrip'in tipli karşılığı); (b) `@NoBack`'li kaynağın navigator'ında `back` metodu YOK (reflection assert); (c) **negatif:** `catalogNav.goToPayment(...)` çağıran kaynak → **derlenmez** (undeclared edge = çekirdek vaat); (d) PD re-attach: entryId sabit → `results` explicit-caller ile.

- [ ] Steps: run+negatif testler → FAIL → codegen implement → PASS → commit `feat(processor): typed per-source navigator codegen — undeclared nav derlenmez`.

### Task 2.6: `from<Source>()` tipli erişim (`:gezgin-test`)

**Files:** Modify: `gezgin-test/.../GezginTestNavigator.kt`; codegen: `.../codegen/TestApiCodegen.kt` (kaynak başına `fun GezginTestNavigator.from(marker: KClass<CatalogRoute>): CatalogNavigator` benzeri extension — **karar:** reified yerine üretilmiş isimli fonksiyon `fun GezginTestNavigator.fromCatalog(): CatalogNavigator` = en yakın Catalog entry'sinin id'siyle navigator kurar; `from<X>()` reified generic tek fonksiyonla YAPILAMAZ — tip→navigator eşlemesi codegen'siz bilinemez; spec §13'ün `from<CheckoutRoute>()` yazımı `fromCheckout()` olarak normalize edilir, plan sapması ledger'a).
GezginTestNavigator'a: `fun entryIdOf(route: KClass<out Route>): Long` (nearest, internal keys üzerinden — gezgin-core `keys`'i `:gezgin-test`'e açmak için `internal` → `@GezginInternalApi` opt-in public'e çevrilir; küçük core değişikliği bu görevde).

- [ ] Steps: run-test (by-example §8'in tipli hâli: `nav.fromPayment().replaceToOrderPlaced(...)` benzeri fixture üzerinde) → implement → commit `feat(processor,test): typed from-accessors for GezginTestNavigator`.

---

## FAZ 2 GATE
`./gradlew :gezgin-core:jvmTest :gezgin-test:jvmTest :gezgin-processor:test --console=plain` tamamen yeşil + 2.5'in negatif testi (undeclared edge derlenmez) mevcut → final whole-branch review (fable) → fix → `main`'e merge → Faz 3 planı.

## Riskler
1. **kctfork×KSP2** — Task 2.0 spike; fallback KSP1.
2. `inheritClassPath` ile gezgin-core'un JVM artefaktının görünmesi — spike'ta doğrulanır (gerekirse `classpaths += buildDir jar`).
3. `@Repeatable` annotation'ların KSP'de okunması (repeated container) — 2.2'de açık test.
4. `quitAndGoTo` runtime yardımcısı RawNavigator'a eklenirse Faz-1 yüzeyi büyür — görev içinde test + final review'da işaretle.
