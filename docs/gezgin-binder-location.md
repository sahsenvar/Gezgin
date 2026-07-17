# Gezgin MVI binder location — maintained contract

> Durum: uygulanmış current contract. Tarihsel adaylar artık kullanım rehberi değildir; güncel ZAD-readiness mimarisi bu belgedeki strict akıştır.

## Karar

Binder, opsiyonel `gezgin-mvi` modülünde ve generated `GezginEntryScope.provideXEntry(...)` fonksiyonunda yaşar. Core navigation MVI/DI bilmez. VM resolution kurulum noktasında açık bir resolver seam'i olarak kalır; stateless screen content DI çağrısı içermez.

Maintained ZAD akışı tam olarak şöyledir:

`Intent -> onIntent -> Effect -> @EffectHandler(route) -> typed navigator`

- Screen state render eder ve intent emit eder.
- `@MviViewModel(route)` ile bağlı ViewModel state'i günceller veya route-specific Effect emit eder.
- ViewModel navigator tutmaz ve doğrudan navigasyon çağırmaz.
- Route-bound handler effect'i gözler ve yalnız generated typed navigator metodunu çağırır.
- Genel Gezgin core-mode capability (`@Screen(route, nav)`) korunur; bu, maintained strict-MVI örneklerinin yönünü değiştirmez.

## Repeatable screen ve route-explicit handler

`@Screen` repeatable'dır. Aynı stateless content birden fazla route'a bağlanabilir:

```kotlin
@Screen(HomeRoute::class)
@Screen(FeaturedRoute::class)
@Composable
fun ColumnScope.SharedContent(
    state: SharedState,
    onIntent: (SharedIntent) -> Unit,
) {
    // Render state; user actions call onIntent(...).
}
```

Her route'un kendi VM ve handler binding'i vardır. Shared content'in State ve Intent tipleri bütün route'larla uyumlu olmalıdır; Effect ve Navigator tipleri route'a göre farklı olabilir:

```kotlin
@MviViewModel(HomeRoute::class)
class HomeViewModel : ViewModel(), GezginMvi<SharedState, SharedIntent, HomeEffect> {
    private val effectSink = GezginEffects<HomeEffect>()
    override val effects: Flow<HomeEffect> = effectSink.flow

    override fun onIntent(intent: SharedIntent) {
        if (intent == SharedIntent.OpenNext) {
            effectSink.send(HomeEffect.OpenFeatured)
        }
    }
}

@EffectHandler(HomeRoute::class)
@Composable
fun HomeEffectHandler(
    effects: Flow<HomeEffect>,
    nav: HomeNavigator,
) {
    ObserveEffects(effects) { effect ->
        if (effect == HomeEffect.OpenFeatured) nav.goToFeatured()
    }
}

@MviViewModel(FeaturedRoute::class)
class FeaturedViewModel : ViewModel(), GezginMvi<SharedState, SharedIntent, FeaturedEffect> {
    // Same State/Intent contract, route-specific Effect type.
}

@EffectHandler(FeaturedRoute::class)
@Composable
fun FeaturedEffectHandler(
    effects: Flow<FeaturedEffect>,
    nav: FeaturedNavigator,
) {
    ObserveEffects(effects) { effect ->
        if (effect == FeaturedEffect.BackHome) nav.goToHome()
    }
}
```

Processor route, function and type conflicts fail-loud: duplicate handlers, missing route bindings, incompatible shared State/Intent, wrong Effect type and wrong typed Navigator are compile errors.

## Generated binder shape

Generated provider şu sorumlulukları taşır:

1. Route'a ait typed navigator'ı yalnız VM resolver veya handler ihtiyaç duyuyorsa oluşturur.
2. DI-detected veya caller-supplied resolver ile entry-scoped VM'i edinir.
3. `uiState`'i lifecycle-aware collect eder.
4. Route-bound handler'a `vm.effects` ve gerekiyorsa typed navigator verir.
5. Stateless content'i `state` ve `vm::onIntent` ile çağırır.

Hilt/Koin/androidx detection yalnız resolver üretir; runtime core'u bir DI framework'üne bağlamaz. Gezgin'in sağlayamadığı assisted constructor parametreleri default resolver'ı kapatır. `state`, `onIntent` ve bottom-sheet `controller` dışındaki zorunlu content parametreleri `provideXEntry` üzerinde açık `@Composable () -> T` resolver parametrelerine dönüşür.

## Migration-only chrome

`@TopBar(route)` ve `@BottomBar(route)` repeatable, temporary `gezgin-mvi` API'leridir. Bunlar ZAD'ın mevcut `ColumnScope` screen şeklini migration sırasında korur; permanent container, scrolling veya screen-scope contract değildir.

Generated sıra:

```kotlin
Column {
    TopBar(state, onIntent)
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Screen(state, onIntent)
    }
    if (!imeVisible) {
        BottomBar(state, onIntent)
    }
}
```

Top veya bottom provider olmayabilir. Aynı route için duplicate provider ya da State/Intent mismatch compile error'dür. Bu API'ler ZAD kalıcı app-owned container'a geçtiğinde `gezgin-mvi`den kaldırılmalıdır.

## Deprecated compatibility bridge

Deprecated `@ScreenEffect`, route argümanı taşımayan eski yüzeydir. Processor yalnız handler'ın exact `Flow<E>` tipi, explicit handler tarafından işgal edilmemiş tam bir `@MviViewModel` route'u bulursa binding çıkarır.

Şu durumların tamamı derlemeyi kırar:

- `Flow<E>` için sıfır VM route adayı;
- birden fazla unoccupied route adayı;
- bütün adayların explicit handler tarafından işgal edilmesi;
- aynı route'a birden fazla legacy handler;
- legacy ve explicit handler overlap'i.

Bu köprü yalnız eski source uyumluluğu içindir. Yeni ve maintained kod `@EffectHandler(Route::class)` kullanır.

## Sınırlar

- Deep-link route dispatch bu artefaktta yoktur; V2'dir.
- `@FragmentScreen` yalnız screen interop'tur; Fragment modal binder'ı yoktur.
- `@TopBar`/`@BottomBar` kalıcı app chrome çözümü değildir.
- Generic `Throwable` serialization ve multiple back stack bu binder'ın işi değildir.
