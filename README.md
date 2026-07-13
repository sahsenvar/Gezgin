# Gezgin

**Type-safe, annotation-driven navigation for Compose Multiplatform.** The destination is a *type*, not a string — and navigating somewhere you didn't declare **doesn't compile**.

![License](https://img.shields.io/badge/license-Apache--2.0-blue) ![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF) ![Compose Multiplatform](https://img.shields.io/badge/Compose%20MP-1.11.0-4285F4) ![Status](https://img.shields.io/badge/status-alpha-orange)

> 🇹🇷 **Türkçe:** Bu README'nin Türkçesi için → **[README.tr.md](README.tr.md)**

Gezgin runs on **Navigation 3**. Your navigation graph is a `sealed interface` tree, and a KSP processor generates a **typed, per-screen navigator** for every route — so a screen can only navigate along the edges you actually declared. The back stack is **observable, serializable data**, so process-death restore, logging, UI-less testing, and MVI fall out almost for free.

---

## The 10-second example

```kotlin
@Screen(CatalogRoute::class)
@Composable
fun CatalogScreen(nav: CatalogNavigator) {
    ProductGrid(onClick = { product -> nav.goToProduct(product.id) })  // ✅ you declared @GoTo(ProductRoute)
    // nav.goToCheckout()   // ❌ WON'T COMPILE — CatalogRoute has no edge to Checkout
}
```

`nav.goToProduct(id)` exists because the route declared `@GoTo(ProductRoute::class)`. `nav.goToCheckout()` is a **compile error**. The answer to *"where can I go from here?"* lives in IDE autocomplete — enforced by the **shape of the API**, not a lint rule you can forget.

---

## Why we needed Gezgin

Compose navigation today usually means one of:

- **Stringly-typed routes** — `navController.navigate("product/$id")` — where a typo or a wrong argument type is a *runtime* "route not found", not a compile error.
- **A single global controller** you can call from anywhere, so "which screens can *this* screen reach?" is unanswerable without reading the entire graph.
- **Manual plumbing** to pass a result back, and **manual `SavedStateHandle` work** to survive process death — each an easy place to get subtly wrong.
- **Navigation intent scattered** across composables and view-models instead of declared in one place you can read.

We wanted the graph to be **data you read at a glance**, the reachable destinations to be **enforced by the compiler**, and results / process-death / testing to be **the default, not extra work**.

---

## Why Gezgin

- **No string routes.** The graph is a `sealed interface` tree; a destination is a type. Namespaced, `@Serializable` → process-death-safe and multiplatform-serializable for free.
- **Navigating to an undeclared destination doesn't compile.** Each route gets a typed navigator with methods *only* for the edges you declared.
- **The whole vocabulary is declarative.** Forward (`@GoTo` / `@ReplaceTo`), backward (`back()` / `@BackTo` / `@BackToStart` / `@NoBack`), and multi-screen **sub-flows** with a typed result (`@FlowGraph` / `ResultFlow` + `@GoForResult`) — behavior lives in annotations, resolved at compile time, no runtime lambdas.
- **Results are type-safe *and* process-death-safe.** `@GoForResult` generates `launchX()` + a re-attach `xResults: Flow<NavResult<T>>` that survives a real process kill.
- **Modals are first-class back-stack entries.** `@Dialog` / `@BottomSheet` / `@FullscreenModal` are the same entries with a different render — no separate dialog state to hand-manage.
- **State-as-data.** `backStack: StateFlow`, `events: Flow` — observe it, log it, restore it, and **test navigation without a UI** (`GezginTestNavigator`).
- **DI-agnostic.** Hilt / Koin / manual — Gezgin never forces a DI framework. Optional `gezgin-mvi` add-on for MVI screens; `@FragmentScreen` for brownfield Fragment interop.
- **Boilerplate is generated.** Graph wiring, the result channel, entry registration — all KSP.

---

## How it compares

A good-faith summary (as of 2026; libraries evolve — corrections welcome). Legend: ✅ first-class · ◑ possible / partial / manual · ❌ not really.

| Feature | **Gezgin** | Jetpack Navigation Compose | Compose Destinations | Voyager | Decompose |
|---|:---:|:---:|:---:|:---:|:---:|
| Compile-time type-safe destinations | ✅ | ✅ *(2.8+)* | ✅ *(KSP)* | ✅ | ✅ |
| Rejects navigating to an **undeclared** edge (per-source navigator) | ✅ | ❌ | ❌ | ❌ | ◑ |
| Declarative forward/back behavior (`@ReplaceTo`/`@BackTo`/`@NoBack`) | ✅ | ◑ *(manual `popUpTo`)* | ◑ | ◑ | ◑ |
| Type-safe result passing | ✅ | ◑ *(`SavedStateHandle`)* | ✅ | ✅ | ✅ |
| Result survives **process death** | ✅ | ◑ *(manual)* | ◑ | ◑ | ✅ |
| Multi-screen **sub-flows** returning a typed result | ✅ | ❌ | ❌ | ◑ | ◑ |
| Dialog / sheet / fullscreen as back-stack **entries** | ✅ | ◑ *(dialog)* | ◑ | ◑ | ◑ |
| State-as-data (observable + serializable back stack) | ✅ | ◑ | ◑ | ◑ | ✅ |
| UI-less testing of navigation | ✅ | ◑ | ◑ | ◑ | ✅ |
| No manual graph wiring (codegen) | ✅ | ◑ | ✅ | ❌ | ❌ |
| Compose Multiplatform | ◑ *(Android + desktop; iOS/web compile-level)* | ◑ | ◑ | ✅ | ✅ |
| Brownfield Fragment interop | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Multiple back stacks** (bottom-nav tabs, master/detail) | ❌ *(V2)* | ✅ | ✅ | ✅ | ✅ |
| **Deep links** | ◑ *(manual only; declarative → V2)* | ✅ | ✅ | ◑ | ◑ |
| Maturity | ⚠️ *alpha* | ✅ stable | ✅ | ✅ | ✅ |

**Gezgin's niche:** the *per-source* compile-time restriction, plus integrated **flows-with-result**, **modals-as-entries**, and **PD-safe-by-default** — all on Navigation 3. If you already like Jetpack Nav's new type-safe routes but want the compiler to also reject *undeclared* edges and hand you results / flows / modals / process-death out of the box, that's the gap Gezgin fills.

> 🔮 **Honest gaps — deliberately out of V1 scope, on the V2 roadmap:** **multiple back stacks** and **declarative deep links**. Today Gezgin is single-stack, and it does not generate a URL↔route table — a deep link can still be handled *manually* (parse the URL yourself and call `nav.raw.navigate(route)`), but the ergonomic, generated version is a V2 item. Gezgin is also **alpha** and rides Navigation 3 (also alpha), whereas Jetpack Navigation Compose is stable and Voyager/Decompose have more battle-tested multiplatform (esp. iOS) mileage.

---

## Installation

> ⚠️ **Not on Maven Central yet.** The `maven-publish` config is currently a skeleton (no remote repository / signing). Build from source with `./gradlew publishToMavenLocal` and consume from `mavenLocal()`. The coordinates below are correct for release day.

Apply the KSP + serialization plugins and add the artifacts (`group = dev.gezgin`, `version = 0.1.0-alpha01`):

```kotlin
plugins {
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("dev.gezgin:gezgin-core:0.1.0-alpha01")
    ksp("dev.gezgin:gezgin-processor:0.1.0-alpha01")

    // implementation("dev.gezgin:gezgin-mvi:0.1.0-alpha01")        // optional MVI add-on
    // testImplementation("dev.gezgin:gezgin-test:0.1.0-alpha01")   // UI-less testing: GezginTestNavigator + typed fromX()
}
```

| Module | Role |
|---|---|
| `gezgin-core` | Required. Annotations, runtime, `GezginDisplay` (the Compose layer), modal scene strategies. DI-agnostic. |
| `gezgin-processor` | Required. The KSP2 processor that generates the typed navigators + entry providers. |
| `gezgin-mvi` | Optional. `@MviViewModel` / `@ScreenEffect` + `GezginMvi<S, I, E>` + DI-detection (Hilt/Koin, androidx fallback). |
| `gezgin-test` | Optional (test). UI-less `GezginTestNavigator` with typed `fromX()` accessors. |

Verified against Kotlin 2.2.20, KSP 2.2.20-2.0.2, Compose Multiplatform 1.11.0, Navigation 3 (Google `navigation3-runtime` 1.1.4 / JetBrains `navigation3` 1.0.0-alpha05), AGP 8.11.0, min SDK 24. Full table + KSP options: [docs/gezgin-design.md](docs/gezgin-design.md) §15. ⚠️ Navigation 3 is still **alpha** — versions are pinned deliberately.

---

## Examples, in order

### 1 · The graph is a sealed route tree

```kotlin
@NavGraph
@Serializable
sealed interface HomeGraph {
    @Serializable
    data object FeedRoute : HomeGraph               // the app-start route (given to the host)

    @GoTo(ProductRoute::class)
    @Serializable
    data object CatalogRoute : HomeGraph

    @Serializable
    data class ProductRoute(val id: String) : HomeGraph   // a route is data
}
```

Membership comes from the **declared supertype**, not from lexical nesting — so a large graph can be split across files (one flow per file) without a 1000-line graph file.

### 2 · ⭐ Navigating to an undeclared destination doesn't compile

```kotlin
@Screen(CatalogRoute::class)
@Composable
fun CatalogScreen(nav: CatalogNavigator) {          // typed navigator generated from CatalogRoute's edges
    Button(onClick = { nav.goToProduct(id = "sku-42") }) { Text("Open product") }   // ✅
    // nav.goToFeed()   // ❌ compile error — no edge declared from Catalog to Feed
}
```

The classic alternative — `navController.navigate("product/$id")` — fails at *runtime* on a typo. Here a wrong target or a wrong argument type is a **compile error**. Need a rare dynamic target? A deliberate escape hatch: `nav.raw.navigate(route)`.

### 3 · Forward & back are a declarative vocabulary

```kotlin
@ReplaceTo(OrderPlacedRoute::class)                 // clear the checkout flow so Back can't return to the form
@Serializable
data class PaymentRoute(val cartId: String) : CartGraph
// → nav.replaceToOrderPlaced(orderId)

@NoBack                                             // terminal screen: system/predictive Back is a no-op here
@Serializable
data class OrderPlacedRoute(val orderId: String) : CartGraph
```

| Annotation | Generated | Behavior |
|---|---|---|
| `@GoTo(X::class)` | `nav.goToX(params)` | push (single-top by value) |
| `@ReplaceTo(X::class, clearUpTo = …)` | `nav.replaceToX(params)` | clear up to a route, then push |
| `@BackTo(X::class)` | `nav.backToX()` | pop to a specific ancestor |
| `@BackToStart` | `nav.backToStart()` | back to a flow's start |
| `@NoBack` | — | close implicit/system Back for a terminal screen |
| — | `nav.back()` / `nav.backWithResult(r)` | one step back / return a typed result |

### 4 · Sub-flows that return a typed, PD-safe result

```kotlin
@FlowGraph
@Serializable
sealed interface CheckoutFlow : ShopGraph, ResultFlow<OrderId> {   // the whole flow returns an OrderId
    @StartDestination @Serializable data object CartRoute : CheckoutFlow
    // … PaymentRoute … ; nav.quitWith(OrderId(...)) finishes the flow and delivers the result
}

// The caller launches the flow and collects the result — re-attaches after a real process death:
@GoForResult(CheckoutFlow::class)
@Serializable data object CatalogRoute : HomeGraph
// → nav.launchCheckout()  +  nav.checkoutResults: Flow<NavResult<OrderId>>
```

Collect `xResults` in your view-model's `init {}` (or a composable `LaunchedEffect`) and the result is delivered even if the OS killed and restored the process mid-flow.

### 5 · Modals are back-stack entries, not special state

```kotlin
@Dialog(ConfirmRoute::class)          // also @BottomSheet, @FullscreenModal
@Composable
fun ConfirmDialog(route: ConfirmRoute, nav: ConfirmNavigator) {
    Button(onClick = { nav.backWithResult(true) }) { Text("Yes") }
}
```

A dialog / sheet / fullscreen modal is the same entry as a screen with a different render — it's on the back stack, it survives process death, and it can return a result exactly like any other route.

### 6 · Host wiring

```kotlin
setContent {
    val navigator = rememberNavigator(
        start = FeedRoute,
        topology = gezginTopology,    // generated into the graph package
        json = gezginJson,            // generated: a process-wide stable Json
        onRootBack = { finish() },
    )
    GezginDisplay(navigator = navigator) {
        homeGraphEntries()            // you assemble the generated provideXEntry() calls
    }
}
```

### 7 · State-as-data → observe, restore, test without a UI

```kotlin
navigator.backStack   // StateFlow<List<Route>> — observe / log
navigator.events      // Flow<NavEvent>          — analytics / devtools

// UI-less test (gezgin-test): drive typed navigation and assert on the back stack, no Compose needed.
val nav = GezginTestNavigator(start = CatalogRoute, topology = gezginTopology)
nav.fromCatalog().goToProduct("sku-42")
assertEquals(listOf(CatalogRoute, ProductRoute("sku-42")), nav.backStack)
```

Because the back stack is `@Serializable` data, process-death restore is automatic; a corrupted / incompatible snapshot falls back to a fresh start instead of crash-looping.

### Optional add-ons

- **`gezgin-mvi`** — annotate a `ViewModel` with `@MviViewModel(XRoute::class)`, implement `GezginMvi<State, Intent, Effect>`, and the codegen binds it to the screen (with one-shot, loss-free effects via `@ScreenEffect`).
- **`@FragmentScreen`** — host a legacy View-based `Fragment` as an entry, injecting the route (`gezginArgs`) and a typed navigator (`gezginNav`) — for incremental brownfield migration to Compose.

---

## Learn more

- **[docs/gezgin-by-example.md](docs/gezgin-by-example.md)** — the full guided tour, feature by feature.
- **[docs/gezgin-design.md](docs/gezgin-design.md)** — the design specification.
- **[sample/](sample/)** — two runnable sample apps (a multi-module showcase + a single-module Shopr).

## License

Apache License 2.0 — see [LICENSE](LICENSE). `Copyright 2026 Gezgin contributors`.
