# Gezgin V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `docs/gezgin-design.md` (V1, R2-review sonrası) spec'ini uygulayan, Nav3 üzerinde çalışan, annotation+KSP-codegen'li, state-as-data CMP navigasyon kütüphanesi.

**Architecture:** Saf-Kotlin core runtime (`GezginKey` zarfı + `GezginState` + result slotları — UI'sız test edilir) → KSP processor (E1–E5 doğrulama + tipli navigator/entry codegen) → `GezginDisplay` Nav3 adapter'ı (contentKey=id, back/scene/decorator wiring) → MVI add-on → Fragment interop → Shopr sample + on-device doğrulama. Her faz kendi başına derlenen, test edilen bir teslimat.

**Tech Stack:** Kotlin 2.2.x + KSP2 · Compose Multiplatform 1.11.0 · androidx navigation3 **1.1.4** (Android, stable) / `org.jetbrains.androidx.navigation3:navigation3-ui` **1.0.0-alpha05** (non-Android, alpha) · `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` **2.10.0-alpha05** · kotlinx.serialization · kotlinx-coroutines · kotlin-test + kotlinx-coroutines-test · KSP testleri: kotlin-compile-testing(-ksp).

## Global Constraints

- Spec = `docs/gezgin-design.md` (2026-07-07 hâli, R2 çözümleri işlenmiş). Çelişkide spec kazanır.
- **V1 tek-stack**; `@TabGraph`/`@SwitchTo`/multi-backstack/deep-link **yazılmaz** (§17).
- İç temsil `GezginKey(route, id, flowPath)`; `contentKey = id`; public yüzey `List<Route>` (§2.1). Kullanıcı zarfı asla görmez.
- Result: launch/receive ayrımı — `launchX` / `xResults: Flow<NavResult<T>>` / suspend sugar; slot key `(callerEntryId, edgeId)`; edge başına tek in-flight (§6).
- Derleme matrisi E1–E5 (§8.1); Guardrail 1 (§3.1); `@NoBack` = entry-içi Gezgin-sahipli handler (§4.2).
- Empty-stack invariant'ı **runtime** guard (§8.1): kuruluşta ResultFlow/modal-start `require`; root flow'da `quit` → `onRootBack`.
- Paket: `dev.gezgin.*`. Artefaktlar: `gezgin-core`, `gezgin-processor`, `gezgin-mvi` (§15). Navigator ctor `internal` + public factory `fun RawNavigator.xNavigator()` (§3.3).
- TDD: her davranış önce başarısız test. Sık commit (conventional commits). DRY/YAGNI.
- Faz 0–1'de KMP hedefleri **jvm + android** (hızlı test döngüsü); ios/wasm hedefleri Faz 3'te açılır.
- Sürüm pinleri yukarıda; execution başında `gradle/libs.versions.toml`'a yazılırken güncel patch doğrulanır (major/alpha seviyesi değişmez).

---

## Faz haritası ve gate'ler

| Faz | Teslimat | Gate (geçiş kriteri) |
|---|---|---|
| **0** | git repo + Gradle KMP iskeleti | `:gezgin-core:jvmTest` yeşil (sanity) |
| **1** | Core runtime (bu dokümanda tam detay) | §13 API'si raw seviyede çalışır; PD save/restore round-trip testi yeşil |
| **2** | KSP processor | E1–E5 + guardrail'ler compile-test'te; üretilen navigator derlenir |
| **3** | `GezginDisplay` (Nav3) | Sample'da 3 ekranlı akış cihazda; @NoBack LIFO on-device doğrulandı |
| **4** | Modal & scene'ler | Dialog/BottomSheet result üretir; N8 overlay on-device |
| **5** | MVI add-on | by-example MVI örneği derlenir + çalışır |
| **6** | Fragment interop | `@FragmentScreen` PD testi geçer |
| **7** | Shopr sample + doğrulama + docs | by-example'daki her senaryo e2e; on-device checklist kapalı |

**Scope notu:** Bu doküman Faz 0–1'i bite-sized/TDD detayında verir (load-bearing katman). Faz 2–7 alt bölümlerde görev + dosya + arayüz + gate seviyesinde tanımlıdır; her faz başında aynı şablonla kendi detay planı çıkarılır (writing-plans scope-check kuralı — her plan kendi başına çalışan yazılım üretir).

## Dosya yapısı (Faz 0–1 sonu hedefi)

```
settings.gradle.kts · build.gradle.kts · gradle/libs.versions.toml · .gitignore
gezgin-core/
  build.gradle.kts                                (KMP: jvm + androidTarget; serialization + coroutines)
  src/commonMain/kotlin/dev/gezgin/core/
    Route.kt          → Route marker interface
    NavResult.kt      → NavResult<T> (Value/Canceled)
    NavEvent.kt       → NavEvent sealed hierarchy
    GezginKey.kt      → @Serializable iç zarf
    Topology.kt       → GezginTopology + FlowType + EdgeSpec (codegen'in dolduracağı runtime sözleşme)
    GezginState.kt    → stack op'ları (push/pop/replaceUpTo/backTo/quitFlow)
    ResultBus.kt      → keyed slotlar (launch/deliver/results/drop)
    RawNavigator.kt   → facade (state+bus+events) + SavedState (serialize/restore)
  src/commonTest/kotlin/dev/gezgin/core/
    fixtures/TestGraph.kt  → el-yazımı test route hiyerarşisi + topology + SerializersModule
    *Test.kt               (görevlerde)
gezgin-test/ …görev T1.11 (GezginTestNavigator; ayrı artefakt)
```

---

# FAZ 0 — Repo + Gradle iskeleti

### Task 0.1: git repo + temel dosyalar

**Files:**
- Create: `.gitignore`, `README.md`

**Interfaces:** Produces: commit edilebilir repo (proje şu an git repo'su DEĞİL).

- [ ] **Step 1: git init + .gitignore + README**

```bash
cd /Users/sahansenvar/StudioProjects/Gezgin && git init -b main
```

`.gitignore`:
```
.gradle/
build/
local.properties
.idea/
.kotlin/
*.iml
.DS_Store
```

`README.md`:
```markdown
# Gezgin
Type-safe, annotation + KSP-codegen, state-as-data navigation for Compose Multiplatform (Navigation 3 üzerinde).
Tasarım: docs/gezgin-design.md · Tanıtım: docs/gezgin-by-example.md
```

- [ ] **Step 2: İlk commit**

```bash
git add .gitignore README.md docs/
git commit -m "chore: repo init — design docs + review findings"
```

### Task 0.2: Gradle KMP iskeleti + sanity test

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `gezgin-core/build.gradle.kts`, `gezgin-core/src/commonTest/kotlin/dev/gezgin/core/SanityTest.kt`

**Interfaces:** Produces: `:gezgin-core` modülü (jvm+android target'lı KMP, serialization plugin'li), test altyapısı.

- [ ] **Step 1: Version catalog + settings + root build**

`gradle/libs.versions.toml` (execution'da güncel patch doğrula; major/alpha seviyesi sabit):
```toml
[versions]
kotlin = "2.2.20"
ksp = "2.2.20-2.0.2"
agp = "8.11.0"
compose-multiplatform = "1.11.0"
coroutines = "1.10.2"
serialization = "1.9.0"
androidx-navigation3 = "1.1.4"
jb-navigation3 = "1.0.0-alpha05"
jb-lifecycle-nav3 = "2.10.0-alpha05"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
android-library = { id = "com.android.library", version.ref = "agp" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`settings.gradle.kts`:
```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "gezgin"
include(":gezgin-core")
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g
android.useAndroidX=true
kotlin.code.style=official
```

Root `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gezgin-core/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}
kotlin {
    jvm()
    androidTarget()
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
android { namespace = "dev.gezgin.core"; compileSdk = 36; defaultConfig { minSdk = 24 } }
```

- [ ] **Step 2: Sanity test yaz**

`gezgin-core/src/commonTest/kotlin/dev/gezgin/core/SanityTest.kt`:
```kotlin
package dev.gezgin.core
import kotlin.test.Test
import kotlin.test.assertTrue

class SanityTest { @Test fun toolchainWorks() = assertTrue(true) }
```

- [ ] **Step 3: Çalıştır — yeşil beklenir**

Run: `./gradlew :gezgin-core:jvmTest -q --console=plain`
Expected: BUILD SUCCESSFUL, 1 test.

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/ gradle.properties gezgin-core/
git commit -m "chore: gradle KMP skeleton (:gezgin-core jvm+android, serialization, test infra)"
```

---

# FAZ 1 — Core runtime (saf Kotlin, UI'sız)

Tüm testler: `./gradlew :gezgin-core:jvmTest --tests '<Sınıf>' -q --console=plain`

### Task 1.1: Temel tipler — `Route`, `NavResult`, `NavEvent`

**Files:**
- Create: `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/Route.kt`, `NavResult.kt`, `NavEvent.kt`
- Test: `gezgin-core/src/commonTest/kotlin/dev/gezgin/core/NavResultTest.kt`

**Interfaces:** Produces: `interface Route` (tüm graph interface'lerinin kökü — app'in kök graph'ı `AppGraph : Route`); `sealed interface NavResult<out T>` = `Value(value: T)` | `Canceled`; `sealed interface NavEvent` = `Pushed(route)` / `Popped(route)` / `Replaced(removed, pushed)` / `FlowQuit(flowInstanceId, canceled)` / `ResultDropped(edgeId)` / `BackToTargetMissing(target: String)` / `RootBack`.

- [ ] **Step 1: Failing test**

```kotlin
package dev.gezgin.core
import kotlin.test.*

class NavResultTest {
    @Test fun valueCarriesPayload() {
        val r: NavResult<String> = NavResult.Value("adres")
        assertEquals("adres", (r as NavResult.Value).value)
    }
    @Test fun canceledIsSingleton() {
        val r: NavResult<Nothing> = NavResult.Canceled
        assertIs<NavResult.Canceled>(r)
    }
}
```

- [ ] **Step 2: Run** — Expected: FAIL (unresolved `NavResult`).
- [ ] **Step 3: Implement**

```kotlin
// Route.kt
package dev.gezgin.core
/** Tüm graph interface'lerinin kökü. App'in kök sealed graph'ı bunu extend eder (AppGraph : Route). */
interface Route

// NavResult.kt
package dev.gezgin.core
sealed interface NavResult<out T> {
    data class Value<T>(val value: T) : NavResult<T>
    data object Canceled : NavResult<Nothing>
}

// NavEvent.kt
package dev.gezgin.core
sealed interface NavEvent {
    data class Pushed(val route: Route) : NavEvent
    data class Popped(val route: Route) : NavEvent
    data class Replaced(val removed: List<Route>, val pushed: Route) : NavEvent
    data class FlowQuit(val flowInstanceId: Long, val canceled: Boolean) : NavEvent
    data class ResultDropped(val edgeId: String) : NavEvent
    data class BackToTargetMissing(val target: String) : NavEvent
    data object RootBack : NavEvent
}
```

- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** — `git add gezgin-core/src && git commit -m "feat(core): Route marker, NavResult, NavEvent"`

### Task 1.2: Test fixture graph'ı + `GezginKey` serialization round-trip

**Files:**
- Create: `GezginKey.kt`; Test fixture: `commonTest/.../fixtures/TestGraph.kt`; Test: `GezginKeyTest.kt`

**Interfaces:**
- Produces: `@Serializable data class GezginKey(val route: Route, val id: Long, val flowPath: List<Long> = emptyList())`; fixture: `ShopGraph : Route` altında `Feed`, `Catalog`, `Product(id)`, `CheckoutFlow`(ResultFlow benzeri) üyeleri `Cart`, `Payment`, `Done` + `testSerializersModule`.
- Consumes: Task 1.1 `Route`.

- [ ] **Step 1: Fixture yaz** (spec §3 modelinin el-yazımı karşılığı; codegen henüz yok)

```kotlin
package dev.gezgin.core.fixtures
import dev.gezgin.core.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface ShopGraph : Route
@Serializable data object Feed : ShopGraph
@Serializable data object Catalog : ShopGraph
@Serializable data class Product(val id: String) : ShopGraph

@Serializable sealed interface CheckoutFlow : ShopGraph   // fixture: ResultFlow<OrderId> temsili
@Serializable data object Cart : CheckoutFlow
@Serializable data object Payment : CheckoutFlow
@Serializable data class OrderId(val v: String)

val testSerializersModule = SerializersModule {
    polymorphic(Route::class) {
        subclass(Feed::class); subclass(Catalog::class); subclass(Product::class)
        subclass(Cart::class); subclass(Payment::class)
    }
}
```

- [ ] **Step 2: Failing test**

```kotlin
package dev.gezgin.core
import dev.gezgin.core.fixtures.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class GezginKeyTest {
    private val json = Json { serializersModule = testSerializersModule }

    @Test fun roundTripsPolymorphicRouteWithIdentity() {
        val key = GezginKey(Product("42"), id = 7, flowPath = listOf(3, 5))
        val restored = json.decodeFromString<GezginKey>(json.encodeToString(GezginKey.serializer(), key))
        assertEquals(key, restored)
    }
    @Test fun equalRoutesWithDifferentIdsAreDistinctKeys() {
        assertNotEquals(GezginKey(Product("42"), 1), GezginKey(Product("42"), 2))
    }
}
```

- [ ] **Step 3: Run** — FAIL. **Step 4: Implement**

```kotlin
package dev.gezgin.core
import kotlinx.serialization.Serializable

@Serializable
data class GezginKey(
    val route: Route,                       // polimorfik (app SerializersModule'ü ile)
    val id: Long,                           // instance kimliği → Nav3 contentKey (§2.1)
    val flowPath: List<Long> = emptyList(), // kapsayan flow-instance zinciri, dış → iç (§8.1)
)
```

- [ ] **Step 5: Run** — PASS. **Step 6: Commit** — `git commit -am "feat(core): GezginKey envelope + polymorphic serialization"`

### Task 1.3: `GezginTopology` — runtime'ın codegen'den alacağı sözleşme

**Files:** Create: `Topology.kt`; fixture'a `testTopology` ekle; Test: `TopologyTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class FlowType(val id: String, val isResultFlow: Boolean)
data class EdgeSpec(val id: String, val resultSerializer: kotlinx.serialization.KSerializer<*>?)
class GezginTopology(
    private val flowChains: Map<kotlin.reflect.KClass<out Route>, List<FlowType>>, // route → kapsayan flow zinciri (dış→iç, lexical nesting)
    private val flowStarts: Map<String, kotlin.reflect.KClass<out Route>>,          // flowTypeId → @StartDestination route
    val edges: Map<String, EdgeSpec>,
) {
    fun flowChain(route: kotlin.reflect.KClass<out Route>): List<FlowType>
    fun startOf(flowTypeId: String): kotlin.reflect.KClass<out Route>
}
```
- Consumes: 1.1–1.2 tipleri. (Faz 2'de codegen bu yapıyı üretir; Faz 1'de fixture el-yazımı.)

- [ ] **Step 1: Failing test**

```kotlin
class TopologyTest {
    @Test fun flowChainOfFlowMemberContainsCheckout() {
        assertEquals(listOf(FlowType("CheckoutFlow", isResultFlow = true)),
            testTopology.flowChain(Cart::class))
    }
    @Test fun flowChainOfPlainRouteIsEmpty() {
        assertTrue(testTopology.flowChain(Feed::class).isEmpty())
    }
    @Test fun startOfCheckoutIsCart() {
        assertEquals(Cart::class, testTopology.startOf("CheckoutFlow"))
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** (yukarıdaki imzalar; `flowChain` default `emptyList()`, `startOf` `getValue`). Fixture'a ekle:

```kotlin
val checkoutFlow = FlowType("CheckoutFlow", isResultFlow = true)
val testTopology = GezginTopology(
    flowChains = mapOf(Cart::class to listOf(checkoutFlow), Payment::class to listOf(checkoutFlow)),
    flowStarts = mapOf("CheckoutFlow" to Cart::class),
    edges = mapOf("Catalog→CheckoutFlow" to EdgeSpec("Catalog→CheckoutFlow", OrderId.serializer())),
)
```

- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(core): GezginTopology runtime contract"`

### Task 1.4: `GezginState.push` — monotonik id, `flowPath` ataması, singleTop

**Files:** Create: `GezginState.kt`; Test: `GezginStatePushTest.kt`

**Interfaces:**
- Produces:
```kotlin
class GezginState(initial: List<GezginKey>, internal var nextId: Long, private val topology: GezginTopology) {
    val stack: List<GezginKey>                       // read-only görünüm
    fun push(route: Route, enterFlow: Boolean = false, singleTop: Boolean = true): GezginKey?
    // null = singleTop dedup (top eşit değer → push yok). enterFlow=true: container-entry edge'i
    // (@GoTo(Flow)/@GoForResult(Flow)/@QuitAndGoTo(Flow)) — hedef flow için YENİ instance id.
}
```
- Consumes: 1.2 `GezginKey`, 1.3 topology.

**flowPath algoritması (spec §8.1):** `target = topology.flowChain(route)`, `source = top?.let{topology.flowChain(it.route)} ?: emptyList()`; `common = enterFlow ise min(commonPrefix, target.size-1) değilse commonPrefix(target, source)`; `flowPath = top.flowPath.take(common) + target.drop(common).map { freshFlowId() }`. (enterFlow, hedef flow'un KENDİ tipine denk gelen segmenti daima yeniden mint'ler → re-entrancy yeni instance.)

- [ ] **Step 1: Failing test**

```kotlin
class GezginStatePushTest {
    private fun state(vararg routes: Route): GezginState {
        val s = GezginState(emptyList(), nextId = 0, topology = testTopology)
        routes.forEach { s.push(it, enterFlow = testTopology.flowChain(it::class).isNotEmpty() && s.stack.none { k -> testTopology.flowChain(k.route::class).isNotEmpty() }) }
        return s
    }
    @Test fun idsAreMonotonic_andDuplicateValuesGetDistinctIds() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Product("42"), singleTop = false); s.push(Feed); s.push(Product("42"), singleTop = false)
        assertEquals(listOf(0L, 1L, 2L), s.stack.map { it.id })
    }
    @Test fun singleTopDedupsOnlyTop() {
        val s = state(Feed)
        assertNull(s.push(Feed))                       // top eşit → dedup
        s.push(Product("1")); assertNotNull(s.push(Feed))  // ortadaki eşe dokunma (§4.1)
    }
    @Test fun enteringFlowMintsInstanceId_membersInherit() {
        val s = state(Feed)
        val cart = s.push(Cart, enterFlow = true)!!    // container-entry → start push
        val pay = s.push(Payment)!!                    // flow-içi @GoTo
        assertEquals(1, cart.flowPath.size)
        assertEquals(cart.flowPath, pay.flowPath)      // miras
    }
    @Test fun reentryMintsNewInstance() {
        val s = state(Feed)
        val first = s.push(Cart, enterFlow = true)!!; s.push(Payment)
        val second = s.push(Cart, enterFlow = true, singleTop = false)!!  // yeniden giriş
        assertNotEquals(first.flowPath, second.flowPath)                  // re-entrancy sınırı (§8.1)
    }
    @Test fun externalRoundTripTargetHasEmptyFlowPath() {
        val s = state(Feed); s.push(Cart, enterFlow = true)
        assertTrue(s.push(Product("x"))!!.flowPath.isEmpty())             // dış hedef miras almaz
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement**

```kotlin
package dev.gezgin.core
import kotlin.reflect.KClass

class GezginState(initial: List<GezginKey>, internal var nextId: Long, private val topology: GezginTopology) {
    private val _stack = initial.toMutableList()
    val stack: List<GezginKey> get() = _stack

    fun push(route: Route, enterFlow: Boolean = false, singleTop: Boolean = true): GezginKey? {
        val top = _stack.lastOrNull()
        if (singleTop && top?.route == route) return null
        val target = topology.flowChain(route::class)
        val source = top?.let { topology.flowChain(it.route::class) } ?: emptyList()
        var common = target.zip(source).takeWhile { (a, b) -> a.id == b.id }.count()
        if (enterFlow && target.isNotEmpty()) common = minOf(common, target.size - 1)
        val flowPath = (top?.flowPath ?: emptyList()).take(common) +
            List(target.size - common) { nextId++ }        // her yeni flow segmenti taze id
        return GezginKey(route, nextId++, flowPath).also { _stack += it }
    }
}
```

- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(core): GezginState.push — monotonic ids, flowPath, singleTop"`

### Task 1.5: `pop` + `replaceUpTo` (Self / clearUpTo / inclusive)

**Files:** Modify: `GezginState.kt`; Test: `GezginStateReplaceTest.kt`

**Interfaces:** Produces:
```kotlin
fun pop(): GezginKey?                            // son entry; boşaltmaz (son entry'de null döner — guard üst katmanda RootBack'e çevirir)
fun replaceUpTo(route: Route, clearUpTo: KClass<out Route>?, inclusive: Boolean, enterFlow: Boolean = false): GezginKey
// clearUpTo == null → Self semantiği (yalnız top). Atomik: temizle+push tek adım (transient boş görünmez).
```

- [ ] **Step 1: Failing test**

```kotlin
class GezginStateReplaceTest {
    @Test fun replaceSelfSwapsTop() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed); s.push(Product("1"))
        s.replaceUpTo(Catalog, clearUpTo = null, inclusive = true)
        assertEquals(listOf(Feed, Catalog), s.stack.map { it.route })
    }
    @Test fun replaceUpToAncestorInclusiveClearsThrough() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed); s.push(Catalog); s.push(Product("1"))
        s.replaceUpTo(Product("2"), clearUpTo = Catalog::class, inclusive = true)
        assertEquals(listOf(Feed, Product("2")), s.stack.map { it.route })
    }
    @Test fun popOnLastEntryReturnsNull_stackIntact() {
        val s = GezginState(emptyList(), 0, testTopology); s.push(Feed)
        assertNull(s.pop())                                        // empty-stack invariant'ı (§8.1)
        assertEquals(1, s.stack.size)
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement**

```kotlin
fun pop(): GezginKey? {
    if (_stack.size <= 1) return null
    return _stack.removeAt(_stack.lastIndex)
}

fun replaceUpTo(route: Route, clearUpTo: KClass<out Route>?, inclusive: Boolean, enterFlow: Boolean = false): GezginKey {
    val cutFrom = if (clearUpTo == null) _stack.lastIndex else {
        val i = _stack.indexOfLast { clearUpTo.isInstance(it.route) }   // nearest-ancestor (§4.2/M3)
        require(i >= 0) { "clearUpTo hedefi stack'te yok: ${clearUpTo.simpleName}" }
        if (inclusive) i else i + 1
    }
    while (_stack.size > cutFrom) _stack.removeAt(_stack.lastIndex)
    return push(route, enterFlow = enterFlow, singleTop = false)!!
}
```

- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(core): pop + replaceUpTo (Self/clearUpTo/inclusive, atomic)"`

### Task 1.6: `backTo` — nearest-ancestor + missing-target no-op

**Files:** Modify: `GezginState.kt`; Test: `GezginStateBackToTest.kt`

**Interfaces:** Produces: `fun backTo(target: KClass<out Route>, inclusive: Boolean): List<GezginKey>?` — null = hedef yok (üst katman `NavEvent.BackToTargetMissing` yayınlar, N-e); değilse poplanan entry listesi.

- [ ] **Step 1: Failing test**

```kotlin
class GezginStateBackToTest {
    @Test fun popsToNearestAncestorExclusive() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Product("A"), singleTop = false); s.push(Feed); s.push(Product("B"), singleTop = false); s.push(Catalog)
        val removed = s.backTo(Product::class, inclusive = false)!!   // nearest = Product("B")
        assertEquals(listOf(Catalog), removed.map { it.route })
        assertEquals(Product("B"), s.stack.last().route)
    }
    @Test fun missingTargetReturnsNull_noMutation() {
        val s = GezginState(emptyList(), 0, testTopology); s.push(Feed)
        assertNull(s.backTo(Catalog::class, inclusive = false))
        assertEquals(1, s.stack.size)
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement**

```kotlin
fun backTo(target: KClass<out Route>, inclusive: Boolean): List<GezginKey>? {
    val i = _stack.dropLast(1).indexOfLast { target.isInstance(it.route) }  // top hariç ara
    if (i < 0) return null
    val keepUntil = if (inclusive) i else i + 1
    val removed = _stack.subList(keepUntil, _stack.size).toList()
    while (_stack.size > keepUntil) _stack.removeAt(_stack.lastIndex)
    return removed
}
```

- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(core): backTo nearest-ancestor + missing-target no-op"`

### Task 1.7: `quitFlow` — instance-id sınırı + root-flow guard

**Files:** Modify: `GezginState.kt`; Test: `GezginStateQuitFlowTest.kt`

**Interfaces:** Produces:
```kotlin
fun quitFlow(flowInstanceId: Long): List<GezginKey>?
// null = flow-unit stack dibinde (root flow) → üst katman onRootBack çağırır (§8.1 runtime guard)
// değilse: tepeden aşağı flowPath'inde id'yi taşıyan TÜM entry'ler atomik poplanır, poplananlar döner
fun currentFlowId(): Long?      // top entry'nin en içteki flow instance id'si (quit/quitWith hedefi)
```

- [ ] **Step 1: Failing test** (re-entrancy senaryosu = M2′ kapanış kanıtı)

```kotlin
class GezginStateQuitFlowTest {
    @Test fun quitPopsOnlyOwnInstance_evenWhenContiguous() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed)
        s.push(Cart, enterFlow = true); s.push(Payment)                          // instance 1
        s.push(Cart, enterFlow = true, singleTop = false); s.push(Payment, singleTop = false) // instance 2 (bitişik!)
        val inner = s.currentFlowId()!!
        val removed = s.quitFlow(inner)!!
        assertEquals(2, removed.size)                                            // yalnız iç instance
        assertEquals(Payment, s.stack.last().route)                              // dış instance duruyor
    }
    @Test fun quitOnRootFlowReturnsNull_stackIntact() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Cart, enterFlow = true); s.push(Payment)                          // flow = root
        assertNull(s.quitFlow(s.currentFlowId()!!))
        assertEquals(2, s.stack.size)
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement**

```kotlin
fun currentFlowId(): Long? = _stack.lastOrNull()?.flowPath?.lastOrNull()

fun quitFlow(flowInstanceId: Long): List<GezginKey>? {
    val first = _stack.indexOfFirst { flowInstanceId in it.flowPath }
    if (first <= 0) return null                       // dipte (veya yok) → root guard: onRootBack
    val removed = _stack.filter { flowInstanceId in it.flowPath }
    _stack.removeAll { flowInstanceId in it.flowPath }  // atomik: tek mutasyon geçişi
    return removed
}
```

- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(core): quitFlow by instance id + root-flow guard"`

### Task 1.8: `ResultBus` — keyed slot: idempotent launch, deliver, replay, drop

**Files:** Create: `ResultBus.kt`; Test: `ResultBusTest.kt`

**Interfaces:** Produces:
```kotlin
class ResultBus {
    data class Slot(val callerEntryId: Long, val edgeId: String, val targetEntryId: Long, val result: NavResult<Any?>? = null)
    val slots: List<Slot>
    fun launch(callerEntryId: Long, edgeId: String, targetEntryId: Long): Boolean  // false = zaten in-flight (idempotent, push YAPMA sinyali)
    fun deliver(targetEntryId: Long, result: NavResult<Any?>): Boolean             // false = slot yok
    fun <T> results(callerEntryId: Long, edgeId: String): kotlinx.coroutines.flow.Flow<NavResult<T>>  // replay-until-consumed
    fun dropFor(callerEntryIds: Set<Long>): List<Slot>                             // caller silindi → düşen slotlar (ResultDropped)
    fun restore(slots: List<Slot>)                                                 // PD restore
}
```

- [ ] **Step 1: Failing test**

```kotlin
class ResultBusTest {
    @Test fun secondLaunchOnSameEdgeIsNoOp() {
        val bus = ResultBus()
        assertTrue(bus.launch(callerEntryId = 1, edgeId = "e", targetEntryId = 9))
        assertFalse(bus.launch(1, "e", 10))                        // idempotent (§6)
        assertEquals(1, bus.slots.size)
    }
    @Test fun deliverThenLateCollectorReplaysOnce() = kotlinx.coroutines.test.runTest {
        val bus = ResultBus()
        bus.launch(1, "e", 9)
        bus.deliver(9, NavResult.Value("adres"))                   // collector YOKKEN teslim (PD senaryosu)
        val r = bus.results<String>(1, "e").first()                // geç collector → replay
        assertEquals(NavResult.Value("adres"), r)
        assertTrue(bus.slots.isEmpty())                            // tüketildi
    }
    @Test fun liveCollectorGetsResultImmediately() = kotlinx.coroutines.test.runTest {
        val bus = ResultBus(); bus.launch(1, "e", 9)
        val deferred = async { bus.results<String>(1, "e").first() }
        runCurrent(); bus.deliver(9, NavResult.Value("x"))
        assertEquals(NavResult.Value("x"), deferred.await())
    }
    @Test fun droppedCallersLoseSlots() {
        val bus = ResultBus(); bus.launch(1, "e", 9)
        val dropped = bus.dropFor(setOf(1))
        assertEquals("e", dropped.single().edgeId); assertTrue(bus.slots.isEmpty())
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** (iç yapı: `MutableStateFlow<List<Slot>>`; `results` = slots akışını filtrele, dolu sonucu emit edip slotu kaldır — tek-tüketici garantisi `compareAndSet` ile).

```kotlin
package dev.gezgin.core
import kotlinx.coroutines.flow.*

class ResultBus {
    data class Slot(val callerEntryId: Long, val edgeId: String, val targetEntryId: Long, val result: NavResult<Any?>? = null)
    private val state = MutableStateFlow<List<Slot>>(emptyList())
    val slots: List<Slot> get() = state.value

    fun launch(callerEntryId: Long, edgeId: String, targetEntryId: Long): Boolean {
        if (state.value.any { it.callerEntryId == callerEntryId && it.edgeId == edgeId }) return false
        state.update { it + Slot(callerEntryId, edgeId, targetEntryId) }
        return true
    }
    fun deliver(targetEntryId: Long, result: NavResult<Any?>): Boolean {
        var hit = false
        state.update { list -> list.map { if (it.targetEntryId == targetEntryId && it.result == null) { hit = true; it.copy(result = result) } else it } }
        return hit
    }
    @Suppress("UNCHECKED_CAST")
    fun <T> results(callerEntryId: Long, edgeId: String): Flow<NavResult<T>> = state
        .mapNotNull { list -> list.firstOrNull { it.callerEntryId == callerEntryId && it.edgeId == edgeId && it.result != null } }
        .filter { slot -> state.value.contains(slot) && consume(slot) }   // ilk tüketici kazanır
        .map { it.result as NavResult<T> }
    private fun consume(slot: Slot): Boolean {
        val cur = state.value
        return cur.contains(slot) && state.compareAndSet(cur, cur - slot)
    }
    fun dropFor(callerEntryIds: Set<Long>): List<Slot> {
        val dropped = state.value.filter { it.callerEntryId in callerEntryIds }
        state.update { list -> list.filterNot { it.callerEntryId in callerEntryIds } }
        return dropped
    }
    fun restore(slots: List<Slot>) { state.value = slots }
}
```

- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(core): ResultBus keyed slots (idempotent launch, replay-until-consumed, drop)"`

### Task 1.9: `RawNavigator` facade — op'lar + events + result üçlüsünün raw karşılığı

**Files:** Create: `RawNavigator.kt`; Test: `RawNavigatorTest.kt`

**Interfaces:** Produces (Faz 2 codegen'i ve Faz 3 display'i BUNU sarar):
```kotlin
class RawNavigator(
    start: Route, private val topology: GezginTopology,
    internal val onRootBack: () -> Unit = {},
    restored: SavedState? = null,
) {
    val backStack: kotlinx.coroutines.flow.StateFlow<List<Route>>   // unwrap edilmiş public görünüm (§2.1)
    val events: kotlinx.coroutines.flow.Flow<NavEvent>              // MutableSharedFlow(extraBufferCapacity=64)
    val current: Route
    internal val keys: List<GezginKey>                              // GezginDisplay adapter'ı için

    fun navigate(route: Route, singleTop: Boolean = true)           // @GoTo (enterFlow'u topology'den çözer: hedef flow-start container-entry'si mi)
    fun back()                                                      // pop; hedef pending-target ise Canceled teslim; dipte → onRootBack; flow-entry'de → quit()
    fun replaceTo(route: Route, clearUpTo: KClass<out Route>? = null, inclusive: Boolean = true)
    fun backTo(target: KClass<out Route>, inclusive: Boolean = false)
    fun quit()                                                      // Canceled ile flow kapat (root'ta onRootBack)
    fun quitWith(result: Any?)                                      // Value ile atomik kapat + caller'a teslim
    fun launchForResult(edgeId: String, route: Route)               // idempotent (§6)
    fun <T> results(edgeId: String): Flow<NavResult<T>>             // caller = ÇAĞRI ANINDAKİ top entry id
    suspend fun <T> navigateForResult(edgeId: String, route: Route): NavResult<T>  // sugar = launch + results.first()
    fun backWithResult(result: Any?)                                // top = pending target ise deliver + pop
}
```
Not: `results`/`launchForResult` caller kimliğini top-entry'den alır; codegen (Faz 2) tipli navigator'da bunu entry-scope'tan bağlar.

- [ ] **Step 1: Failing test**

```kotlin
class RawNavigatorTest {
    private fun nav(onRootBack: () -> Unit = {}) =
        RawNavigator(start = Feed, topology = testTopology, onRootBack = onRootBack)

    @Test fun goForResultRoundTrip() = kotlinx.coroutines.test.runTest {
        val n = nav(); n.navigate(Catalog)
        val r = async { n.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent()
        assertEquals(Payment::class, run { n.navigate(Payment); n.current::class })
        n.quitWith(OrderId("o1"))                                   // atomik teardown + deliver
        assertEquals(NavResult.Value(OrderId("o1")), r.await())
        assertEquals(Catalog, n.current)                            // flow yıkıldı, caller top
    }
    @Test fun plainBackOnPendingTargetDeliversCanceled() = kotlinx.coroutines.test.runTest {
        val n = nav()
        val r = async { n.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent(); n.back()                                      // flow entry'de back = quit = Canceled (§8.1)
        assertEquals(NavResult.Canceled, r.await())
    }
    @Test fun backAtRootInvokesOnRootBack_notEmpty() {
        var root = 0
        val n = nav { root++ }
        n.back()
        assertEquals(1, root); assertEquals(Feed, n.current)
    }
    @Test fun duplicateLaunchDoesNotDoublePush() {
        val n = nav()
        n.launchForResult("Catalog→CheckoutFlow", Cart)
        n.launchForResult("Catalog→CheckoutFlow", Cart)             // idempotent
        assertEquals(2, n.backStack.value.size)                     // [Feed, Cart] — tek push
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** — `GezginState` + `ResultBus` + `MutableSharedFlow<NavEvent>` kompozisyonu. Kritik noktalar: (a) `navigate` `enterFlow`'u `topology.flowChain(route)`'un top'a göre derinleşmesinden çözer; (b) `back()` sırası: top pending-target mi → `Canceled` teslim; top flow-entry mi (flowPath'li ve altındaki entry o id'yi taşımıyor) → `quit()`; pop null → `onRootBack` + `NavEvent.RootBack`; (c) `quitWith`: `state.quitFlow(currentFlowId())` null → `onRootBack`, değilse poplanan entry'lerden pending-target bul → `bus.deliver`; (d) her mutasyon `backStack` StateFlow'unu `keys.map{it.route}` ile günceller + event yayınlar; (e) `backTo` null dönerse `NavEvent.BackToTargetMissing`.

- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(core): RawNavigator facade — ops, events, result trio (raw)"`

### Task 1.10: PD simülasyonu — `SavedState` serialize/restore round-trip

**Files:** Modify: `RawNavigator.kt` (+`SavedState`); Test: `SavedStateTest.kt`

**Interfaces:** Produces:
```kotlin
@Serializable class SavedState(val keys: List<GezginKey>, val nextId: Long, val pendingSlots: List<SavedSlot>)
@Serializable class SavedSlot(val callerEntryId: Long, val edgeId: String, val targetEntryId: Long, val payloadJson: String?, val canceled: Boolean)
fun RawNavigator.save(json: Json): SavedState        // slot payload'ı topology.edges[edgeId].resultSerializer ile encode
fun RawNavigator(start:…, restored: SavedState?)     // ctor: restore keys + nextId + slots (payload decode)
```

- [ ] **Step 1: Failing test** — "süreç öldü, sonuç slotta bekliyordu, restore sonrası geç collector alıyor" (R1 kanıtı):

```kotlin
class SavedStateTest {
    private val json = Json { serializersModule = testSerializersModule }
    @Test fun pendingDeliveredResultSurvivesProcessDeath() = kotlinx.coroutines.test.runTest {
        val n1 = RawNavigator(Feed, testTopology)
        n1.navigate(Catalog)
        n1.launchForResult("Catalog→CheckoutFlow", Cart)
        n1.backWithResult(OrderId("o1"))                  // sonuç teslim, TÜKETİCİ YOK (continuation "öldü")
        val saved = json.encodeToString(SavedState.serializer(), n1.save(json))

        val n2 = RawNavigator(Feed, testTopology, restored = json.decodeFromString(SavedState.serializer(), saved))
        assertEquals(listOf(Feed, Catalog), n2.backStack.value)               // stack restore
        val r = n2.results<OrderId>("Catalog→CheckoutFlow").first()           // geç re-attach (VM init)
        assertEquals(NavResult.Value(OrderId("o1")), r)
    }
    @Test fun nextIdSurvives_noIdCollisionAfterRestore() = kotlinx.coroutines.test.runTest {
        val n1 = RawNavigator(Feed, testTopology); n1.navigate(Catalog)
        val saved = n1.save(json)
        val n2 = RawNavigator(Feed, testTopology, restored = saved)
        n2.navigate(Product("z"))
        assertEquals(n2.keys.map { it.id }.toSet().size, n2.keys.size)        // benzersiz id'ler
    }
}
```

- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** — `save`: `bus.slots`'u `SavedSlot`'a çevir (payload = `json.encodeToString(edge.resultSerializer as KSerializer<Any?>, value)`); ctor `restored != null` ise `GezginState(restored.keys, restored.nextId, topology)` + `bus.restore(decode edilen slotlar)`. Hmm not: `backWithResult` hedef poplandığı için slot `result` dolu ama `targetEntryId` artık stack'te yok — slot yine saklanır (caller yaşadıkça).
- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(core): SavedState serialize/restore — PD-safe stack + pending results"`

### Task 1.11: `GezginTestNavigator` (§13) — `:gezgin-test` modülü

**Files:**
- Create: `gezgin-test/build.gradle.kts` (KMP, `api(project(":gezgin-core"))` + coroutines-test), `gezgin-test/src/commonMain/kotlin/dev/gezgin/test/GezginTestNavigator.kt`
- Modify: `settings.gradle.kts` (`include(":gezgin-test")`)
- Test: `gezgin-test/src/commonTest/kotlin/dev/gezgin/test/GezginTestNavigatorTest.kt`

**Interfaces:** Produces (§13 raw-seviye yüzeyi; tipli `from<Source>()` Faz 2 codegen'iyle gelir — burada reified stub):
```kotlin
class GezginTestNavigator(start: Route, topology: GezginTopology, val raw: RawNavigator = …) {
    val backStack: List<Route>; val current: Route
    fun navigate(route: Route); fun back(); fun replaceTo(route: Route)
    fun deliverResult(result: Any?)     // top pending-target'a Value teslim + pop (test yardımcı)
}
```

- [ ] **Step 1: Failing test** — by-example §8'in raw uyarlaması:

```kotlin
class GezginTestNavigatorTest {
    @Test fun replaceToClearsPaymentFlow() {
        val nav = GezginTestNavigator(start = Payment, topology = testTopology)
        nav.replaceTo(Product("order1"))
        assertEquals(Product("order1"), nav.current)
        assertEquals(1, nav.backStack.size)
    }
    @Test fun checkoutReturnsSelectedResult() = kotlinx.coroutines.test.runTest {
        val nav = GezginTestNavigator(start = Catalog, topology = testTopology)
        val r = async { nav.raw.navigateForResult<OrderId>("Catalog→CheckoutFlow", Cart) }
        runCurrent(); nav.deliverResult(OrderId("o1"))
        assertEquals(NavResult.Value(OrderId("o1")), r.await())
    }
}
```

- [ ] **Step 2: Run** (`./gradlew :gezgin-test:jvmTest -q --console=plain`) — FAIL. **Step 3: Implement** — ince delegasyon katmanı; `deliverResult` = `raw.backWithResult(result)`.
- [ ] **Step 4: Run** — PASS. **Step 5: Commit** — `git commit -am "feat(test): GezginTestNavigator — §13 UI-less test API (raw surface)"`

**FAZ 1 GATE:** `./gradlew :gezgin-core:jvmTest :gezgin-test:jvmTest -q --console=plain` tamamen yeşil → Faz 2 detay planı yazılır.

---

# FAZ 2 — KSP processor (`:gezgin-processor`, detay planı faz başında)

Görevler (her biri kotlin-compile-testing-ksp ile TDD; test fixture = mini `core:navigation` kaynağı):

| # | Görev | Üretir / Doğrular |
|---|---|---|
| 2.1 | Annotation seti (`gezgin-core`'a): `@NavGraph @FlowGraph @StartDestination @GoTo @ReplaceTo @GoForResult @BackTo @BackToStart @Quit @QuitAndGoTo @NoBack @Screen @Dialog @BottomSheet @FullscreenModal` + `ResultRoute<T>`/`ResultFlow<T>` + `object Self : Route` (`clearUpTo: KClass<out Route> = Self::class`, §4.1/N-a) | derlenen API yüzeyi |
| 2.2 | Topology okuma: sealed ağaç tarama, **üyelik = lexical nesting**, `flowChain`, `@StartDestination` | `GezginTopology` üretimi (Task 1.3 sözleşmesi) |
| 2.3 | Doğrulama: **E1–E5** matrisi (§8.1) + Guardrail 1 (argsız start) + `@NoBack`+flow-start + `@NoBack`+`dismissOnBackPress` + N9 `name=` çakışması + `ResultFlow` non-FlowGraph + `@ViewModel`↔`@Screen` aynı-modül | her kural için: hata mesajlı FAIL compile-testi + geçen pozitif test |
| 2.4 | Navigator codegen: per-source stateless facade — edge metotları, geri yüzeyi (`back` yalnız `@NoBack` yoksa, `backTo…`/`backToStart`/`quit`/`quitWith(T)`), for-result üçlüsü (`launchX`/`xResults`/`suspend goToXForResult`), `raw` escape hatch, `internal` ctor + public factory `fun RawNavigator.xNavigator()` (§3.3/§4/M7′) | üretilen kaynak derlenir; edge yoksa metot yok (negatif compile testi) |
| 2.5 | Entry codegen (core-mode): `GezginEntryScope.provideXEntry` → `register<Route> { … }`; kind metadata (screen/modal); `SerializersModule` + topology emiti `core:navigation`'a | üretilen registry Faz 1 runtime'ına bağlanır |

**Gate:** fixture graph'tan üretilen navigator + topology ile Task 1.9–1.11 testlerinin tipli karşılıkları yeşil (örn. `nav.from<CatalogRoute>().goToCheckoutForResult…` derlenir; `nav.goToUndeclared()` derlenmez).

# FAZ 3 — `GezginDisplay` (Nav3 entegrasyonu; detay planı faz başında)

| # | Görev |
|---|---|
| 3.1 | `GezginEntryScope` + registry; `GezginKey → NavEntry` adapter'ı (**contentKey = key.id**, metadata route'tan) |
| 3.2 | `rememberNavigator(start)` — `rememberSaveable` + `SavedState` (Task 1.10); kuruluş guard'ları: ResultFlow-üyesi start / modal start `require` (§12) |
| 3.3 | Decorator'lar: saveable + savedState + `rememberViewModelStoreNavEntryDecorator`; **duplicate-value testi**: `[Detail(42), Cart, Detail(42)]` ayrı VM store (R2 kanıtı, instrumented) |
| 3.4 | Back wiring: `NavDisplay(onBack = { gezgin.back() })`; `@NoBack` = entry-içi Gezgin-sahipli `NavigationBackHandler` sarmalayıcı (§4.2/M5′); iOS/desktop hedeflerini aç |
| 3.5 | Transition cascade (screen > graph > app) → `NavDisplay` transition metadata'sı (§9) |
| 3.6 | **On-device doğrulama (M5′):** @NoBack ekranında sistem back + predictive gesture → preview yok, pop yok; içerideki kullanıcı `BackHandler`'ı önceliği. Sonuç `docs/gezgin-review-findings-r2.md`'ye işlenir |

**Gate:** 3 ekran + 1 flow'luk mini sample Android cihazda: ileri/geri/quitWith/PD (developer options "Don't keep activities") senaryoları elle + maestro/uiautomator smoke.

# FAZ 4 — Modal & scene'ler (detay planı faz başında)

4.1 `DialogSceneStrategy` wiring + `DialogContract` (SABİT=override, KOŞULLU=ctor param) · 4.2 `BottomSheetSceneStrategy` (core'a bundle, `sheetState` enjeksiyonu) · 4.3 dismiss→`Canceled` (tap-outside/swipe = `raw.back()` yolu) · 4.4 modal-root runtime guard (N-c) · 4.5 **on-device N8**: dialog-üstü-dialog scrim + back sırası. **Gate:** by-example §5 örnekleri çalışır.

# FAZ 5 — MVI add-on (`:gezgin-mvi`; detay planı faz başında)

5.1 `GezginMvi<out S, in I, out E>` + `ObserveAsEvents` · 5.2 `@ViewModel(Route)` + `@ScreenEffect` codegen (`provideXEntry(viewModel = default)`); S/I/E'yi supertype'tan okuma (BaseViewModel substitution testi) · 5.3 DI-detection (`@HiltViewModel`/`@KoinViewModel` + `@Assisted`/`@InjectedParam`; sağlanamayan assisted → default yok) · 5.4 bilinmeyen content param → ek resolver param (Problem 2) · 5.5 content/effect tip doğrulaması + aynı-modül kuralı. **Gate:** by-example MVI örneği (OrderChain) derlenir + sample'da çalışır.

# FAZ 6 — Fragment interop (Android-only; detay planı faz başında)

6.1 `@FragmentScreen` codegen (`AndroidFragment` + `route.toBundle()`) · 6.2 `gezginArgs`/`gezginNav` delegate'leri + lifecycle sözleşmesi (bind öncesi erişim = açıklayıcı hata; §11.1) · 6.3 parametreli-ctor yasağı KSP kontrolü · 6.4 instrumented PD testi (fragment restore + re-bind). **Gate:** örnek legacy fragment sample'da leaf olarak çalışır, PD'yi atlatır.

# FAZ 7 — Shopr sample + kapanış

7.1 by-example'daki TÜM senaryoları Shopr sample'ında birebir kur (Feed/Catalog/Product, CheckoutFlow+ResultFlow, ConfirmOrderDialog, SortSheet) · 7.2 e2e senaryo testleri + PD koşusu · 7.3 on-device checklist'in kapanışı: M5′ LIFO, N8 overlay, predictive-per-platform (iOS swipe / desktop Esc) · 7.4 KDoc + README + versiyonlama (0.1.0-alpha01), maven-publish iskeleti.

---

## Riskler / açık doğrulamalar (execution sırasında takip)

1. **M5′ (Faz 3.6):** entry-içi handler'ın NavDisplay LIFO davranışı resmî olarak belgesiz — on-device kanıt şart; ters çıkarsa fallback: `onBack` no-op + `predictivePopTransitionSpec` ile snap-back'i gizleme (çirkin ama çalışır).
2. **Non-Android Nav3 alpha (M6′):** JB `alpha05`'te API kırılması gelirse yalnız `GezginDisplay` adapter'ı değişir; core/processor etkilenmez (mimari sigorta).
3. **`ResultBus.results` çoklu-collector yarışı:** Task 1.8 `compareAndSet` tek-tüketici garantisini test eder; Faz 3'te gerçek dispatcher'la stres testi eklenir.
4. **KSP2 + kotlin-compile-testing uyumu:** Faz 2 başında harness spike'ı (yarım gün); sorun çıkarsa alternatif: `ksp-testing` (dev.zacsweers) harness'ı.
