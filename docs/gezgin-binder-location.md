# Binder-location — araştırma sentezi + aday yapılar

> Durum: tartışma (çözüm henüz kilitli değil). Kaynak: 3 paralel araştırma (Compose Destinations, Circuit, Voyager/Decompose/Molecule + community), source-verified.

## Problem + kısıtlar (recap)

MVI'da her ekran stateful + stateless iki composable. Stateful olan hep aynı mekanik rutini yapar: (1) VM resolve, (2) `collectAsStateWithLifecycle`, (3) UiEvent/effect gözle, (4) stateless content'i çağır (bazen `Column`/`Box` içinde sar). Bunu codegen ile yaptırmak isteniyor. **Kısıtlar:** (a) MVI-şekle-agnostik, (b) VM'i içeride resolve etme — assisted/store/scope kontrolü kullanıcıda, (c) DI-agnostik, (d) önce tartışma.

## Araştırma bulguları (condensed)

**Compose Destinations** — VM resolution'ı **kullanıcıya** bırakır (`hiltViewModel()`/`koinViewModel()` default-param). DI-agnostik iki dikişle: tipli args decoder'ı (`Destination.argsFrom(SavedStateHandle)` → VM'e tipli args) + `dependenciesContainerBuilder` (framework-nötr, statik-dep map, recomposition-aware DEĞİL). **MVI binder'ı hiç üretmez** — "tam da burası Gezgin'in fırsatı." Eksik dep = runtime crash.

**Circuit** — `Presenter(@Composable present(): State)` / `Ui(Content(state))`. **eventSink state'in içinde** (`state.eventSink(event)`) — ayrı effect/event Flow YOK; navigasyon = event → `navigator.goTo/pop`. Wiring = runtime `CircuitContent` + factory registry (Screen key → factory); **binder codegen değil**, `@CircuitInject` sadece **factory'leri** üretir. DI-agnostik: runtime DI bilmez; DI sadece factory set'ini doldurur; `@CircuitInject` framework-spesifik multibinding anno üretir (`circuit.codegen.mode`: dagger/hilt/anvil/kotlin-inject/metro). Assisted: `Screen`/`Navigator`/`Circuit` `create()`'te assisted verilir; args `Screen` data class'ının içinde. State engine = **Compose runtime'ın kendisi** (StateFlow yok; Molecule sadece headless/test).

**Voyager** — `rememberScreenModel { ctor }` (kullanıcı ctor'u), assisted destekli, screen/navigator-scope. State convention (`StateScreenModel` + manuel collect). **Effect DIY**. Runtime helper + convention.

**Decompose** — düz class, **constructor injection**, DI lock-in yok, `ComponentContext` threadlenir. Event = düz callback; nav = child-state. Effect'i state/callback'e "eritir." Yeni zihinsel model + context plumbing bedeli.

**Molecule** — presenter-as-composable → `StateFlow` (kullanıcı scope verir). **Producer** tarafını sadeleştirir (reducer boilerplate'i), binder'ı değil. Ortogonal — binder ile compose olur.

**Community** — `MviScreen(vm, onEffect){ state -> }` runtime helper + `ObserveAsEvents`/`collectAsEffect` + `collectAsStateWithLifecycle`. Resolver = tek swappable satır.

### Üç yakınsama
1. **Kimse VM'i binder içinde resolve etmez** — hepsi instantiation'ı kullanıcının bir call/lambda'sına bırakır; binder resolve edilmiş örneği **parametre olarak alır**. (Senin kısıtın (b) evrensel.)
2. **Kimse binder'ı KSP ile üretmez** — hepsi runtime-helper (community/Voyager) ya da runtime-registry (Circuit). Binder'ı codegen etmek **boş alan = Gezgin'in ayrışması** (CD raporu birebir böyle diyor).
3. **Effect'te iki felsefe:** ayrı effect Flow (senin Zad MVI'ın, community) **vs** eventSink-in-state (Circuit). Google resmi olarak **state-first** öneriyor (Channel + çok tüketici = exactly-once garanti edilemez); ayrı effect Flow popüler ama resmi-önerilmeyen.

## Aday yapılar (OrderChain üstünde)

Değişmez ilke (araştırma doğruladı): **VM resolution kullanıcının; binder resolve edilmiş VM'i alır.** İki aday, "binder'ı ne yapar" ekseninde ayrışıyor.

### Candidate 1 — runtime helper (MVI-agnostik, adapter'lı)

Gezgin generic bir composable helper verir; kullanıcı ince `@Screen`'de çağırır:

```kotlin
// Gezgin ships (gezgin-mvi ya da core, ~30 satır):
@Composable
fun <VM, S, E> GezginScreen(
    viewModel: @Composable () -> VM,            // KULLANICI resolve eder — DI + kontrol burada
    state: (VM) -> StateFlow<S>,                // adapter: state nerede
    effects: ((VM) -> Flow<E>)? = null,         // adapter: effect nerede (opsiyonel)
    onEffect: (E) -> Unit = {},
    content: @Composable (S, VM) -> Unit,
) {
    val vm = viewModel()
    val s by state(vm).collectAsStateWithLifecycle()
    effects?.let { ObserveAsEvents(it(vm), onEffect = onEffect) }
    content(s, vm)
}

// Kullanıcı, KENDİ BaseViewModel'ı için BİR KEZ ince sarmalayıcı tanımlar (adapter'lar sabit):
@Composable
fun <S, I, E> MviScreen(vm: BaseViewModel<S, I, E, *>, onEffect: (E)->Unit = {},
                        content: @Composable (S, (I)->Unit)->Unit) =
    GezginScreen(viewModel = { vm }, state = { it.uiState }, effects = { it.event }, onEffect = onEffect) { s, v ->
        content(s, v::onIntent)
    }

// Böylece PER-SCREEN tek satır wiring:
@Screen @Composable
fun OrderChainScreen(route: OrderChainRoute, nav: OrderChainNavigator) =
    MviScreen(koinViewModel { parametersOf(route, nav) }) { state, onIntent ->
        OrderChainContent(state, onIntent)             // stateless content
    }
```

- **+** Contract yok; MVI-agnostik (adapter'lar); DI-agnostik + kontrol tam (resolution kullanıcının); bugün çalışır; Gezgin yüzeyi minik. `Column`/`Box` sarma → helper'a `container` param'ı ya da content içinde.
- **−** Codegen değil — per-screen ~1 satırlık `MviScreen(...) { }` call'u kalır (sıfır değil).

### Candidate 2 — contract + codegen (sıfır binder)

Gezgin **opt-in** bir MVI sözleşmesi tanımlar; VM onu implement ederse codegen tüm binder'ı üretir:

```kotlin
// gezgin-mvi contract (opt-in):
interface GezginMvi<S, I, E> {
    val uiState: StateFlow<S>
    val effects: Flow<E> get() = emptyFlow()          // opsiyonel
    fun onIntent(intent: I)
}
// kullanıcının BaseViewModel'ı bunu implement eder (bir kez).

// Kullanıcı iki küçük şey yazar: resolver (DI, kontrol) + stateless content:
@ScreenModel(OrderChainRoute::class)
@Composable
fun rememberOrderChainVm(route: OrderChainRoute, nav: OrderChainNavigator): OrderChainViewModel =
    koinViewModel { parametersOf(route, nav) }        // DI-agnostik, kontrol burada

@Screen(OrderChainRoute::class)                        // artık STATELESS content'i işaretler
@Composable
fun OrderChainContent(state: OrderChainState, onIntent: (OrderChainIntent) -> Unit) { ... }

// Codegen binder entry'sini üretir:
entry<OrderChainRoute> { route ->
    val nav = remember(raw) { OrderChainNavigator(raw) }
    val vm  = rememberOrderChainVm(route, nav)         // kullanıcının resolver'ını çağırır
    val s by vm.uiState.collectAsStateWithLifecycle()
    ObserveAsEvents(vm.effects) { /* effect handling hook */ }
    OrderChainContent(s, vm::onIntent)
}
```

- **+** Per-screen **sıfır binder** — kullanıcı sadece resolver + stateless content yazar.
- **−** `GezginMvi` sözleşmesini dayatır (opt-in ama şekle bağlar); per-screen **iki** fonksiyon (resolver + content); codegen'in `@ScreenModel`+`@Screen`'i route'a göre eşlemesi; effect-handling hook'unun yeri. Daha çok magic/parça.

### Candidate 3 (referans) — Circuit eventSink-in-state — neden core değil

Circuit'in modeli zarif (state-first, Compose-native, ayrı effect Flow yok) ama **paradigma değişimi**: state-holder = `@Composable present()`, "ViewModel + collectAsState" değil. Senin mevcut ViewModel-tabanlı MVI'ın (Zad) buna uymaz; migration odaklı bir kütüphane için fazla yıkıcı. **Core değil**, ama "event'i state'te modelle" fikri effect-duruşumuzu besliyor (aşağı).

## Effect duruşu (karar gerektiriyor)

Google: one-off event'i **ayrı effect Flow yerine state'te** modelle (Channel + çok tüketici = exactly-once garanti edilemez). Senin MVI'ın ayrı `UiEvent` Flow kullanıyor → popüler ama resmi-önerilmeyen tarafta.

**Öneri:** Gezgin effect Flow'u **destekler ama dayatmaz.** Binder'da `effects` adapter'ı **opsiyonel** — yoksa sadece state bind edilir (state-modeled event'ler böyle çalışır). İkisini de destekle, default'u zorlama.

## Tradeoff tablosu

| | Candidate 1 (runtime helper) | Candidate 2 (contract + codegen) |
|---|---|---|
| Per-screen binder | ~1 satır `MviScreen(...) { }` | sıfır (codegen) |
| MVI şekli | agnostik (adapter) | `GezginMvi` sözleşmesi (opt-in) |
| DI / kontrol | tam kullanıcıda | tam kullanıcıda (resolver fn) |
| Gezgin yüzeyi | minik (bir composable) | codegen + contract + eşleme |
| Magic | düşük | orta |
| Bugün çalışır | evet | codegen yazılmalı |
| Başkaları için | herkes (contract yok) | contract'ı benimseyen |

## KARAR (kilitlendi) — Candidate 2 (refined), `provideXEntry(resolver)`

Kullanıcı ile kilitlendi. Candidate 2 seçildi; `@ScreenModel` resolver fonksiyonu **yerine**, kullanıcının önerdiği **codegen'in ürettiği `provideXEntry(resolver)`** deseni — VM resolution'ı entry-kurulum noktasında lambda ile geçilir, ekran kodunda DI sıfır.

**Şekil:**
```kotlin
// codegen üretir (sen çağırırsın):
fun provideOrderChainEntry(
    viewModel: @Composable (nav: OrderChainNavigator, args: OrderChainRoute)
                 -> GezginMvi<OrderChainState, OrderChainIntent, OrderChainEvent>
): GezginEntry = gezginEntry<OrderChainRoute> { route ->
    val nav = remember(raw) { OrderChainNavigator(LocalGezginNavigator.current) }
    val vm  = viewModel(nav, route)
    val state by vm.uiState.collectAsStateWithLifecycle()
    OrderChainEffects(vm.effects)          // @ScreenEffect varsa
    OrderChainContent(state, vm::onIntent) // @Screen (stateless)
}

// kullanıcı — ekran kodu: SADECE stateless content (+ opsiyonel @ScreenEffect), DI YOK
@Screen(OrderChainRoute::class) @Composable
fun OrderChainContent(state: OrderChainState, onIntent: (OrderChainIntent) -> Unit) { ... }

// kullanıcı — kurulum noktası (DI tek yerde):
fun optionEntries() = listOf(
    provideOrderChainEntry { nav, args -> koinViewModel { parametersOf(args, nav) } },
    …
)
```

**Kararlar:**
- **Sözleşme:** opt-in `GezginMvi<S,I,E>` (`uiState` / `effects`[opsiyonel] / `onIntent`). VM implement eder (base'de bir kez).
- **Codegen VM'in somut tipini bilmez** — S/I/E'yi `@Screen` content imzasından türetir, resolver'ı `-> GezginMvi<S,I,E>` tipler; kullanıcının somut VM'i alt-tip olarak uyar (sözleşme dışında hiçbir yere VM tipi yazılmaz).
- **VM resolution = kullanıcının lambda'sı, kurulum noktasında** (Ktorfit modeli). Ekran kodunda DI yok; assisted/store/scope tam kontrol.
- **Core default-free** (DI-agnostik). `gezgin-koin` add-on default resolver verebilir (KSP mode flag / overload); codegen VM constructor'ına bakıp `parametersOf` şeklini üretir (assisted param varsa `parametersOf(args,nav)`, yoksa düz `koinViewModel()`).
- **Effect:** opsiyonel `@ScreenEffect fun XEffects(effects: Flow<E>[, nav])` composable; kendi `ObserveAsEvents`'ini yapar. Google state-first önerisi: effect dayatılmaz, state-modeled event'ler de çalışır.
- **Mode seçimi imzayla:** core `@Screen fun(route, nav)` (kendin-bağla) vs mvi `@Screen fun(state, onIntent)` (codegen bağlar). Aynı annotation, imza seçer.

İşlendi: spec §10.1, §11, §13, §14, §15.
