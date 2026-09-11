# Gezgin screen wrapper and MVI de-opinionation

> Status: approved design, ready for implementation planning
> Date: 2026-09-11
> Baseline: `main` at `27c9b5c` (merge of `7382a63`)
> Target release: `0.3.0` (breaking)

## 1. Purpose

Gezgin currently dictates one MVI style. `GezginMvi<S, I, E>`, `GezginEffects`, `@MviViewModel`
and the DI-detection subsystem force every consumer into the library author's own shape, and every
MVI-mode entry is wrapped in a hard-coded double `Column` that exists only to preserve ZAD's
migration shape.

This design removes that opinion. Gezgin keeps what only Gezgin can do — a typed, compile-time
navigation graph — and hands the screen's container, its ViewModel, its state plumbing and its
side-effect policy back to the application, through one declarative mechanism: **wrapper functions
with named slots**.

The permanent replacement for `@TopBar` / `@BottomBar` named as V2 debt in
`docs/gezgin-zad-root-integration-spec.md` §145 is delivered here.

## 2. Scope

In scope: `gezgin-core` annotations, `gezgin-processor` entry codegen and model readers, deletion
of `gezgin-mvi`, rewrite of every sample, documentation.

Out of scope: the navigation graph itself (`@NavGraph`, `@GoTo`, `@BackTo`, `@ReplaceTo`,
`@GoForResult`, `@NoBack`), navigator codegen, topology, serializers, result plumbing,
`gezgin-test`. None of these change.

## 3. What is removed

| Removed | Replacement |
|---|---|
| `GezginMvi<S, I, E>` | the application's own base type, unknown to Gezgin |
| `GezginEffects`, `ObserveEffects` | the application's own effect plumbing, inside its wrapper |
| `@MviViewModel(route)` | a `@ScreenSlot` provider bound to the wrapper's ViewModel slot |
| `@EffectHandler(route)` | a `@ScreenSlot` provider bound to the wrapper's effect slot |
| `@TopBar(route)`, `@BottomBar(route)`, `@ExperimentalGezginMigrationApi` | application-defined slot markers |
| `VmDiClassifier`, `VmDiKind`, Hilt/Koin/androidx resolver emission | the provider body, written by the application |
| Validations `MV7`, `MV12` and the DI classification rules | none needed; Gezgin makes no DI claim |
| Unconditional `Column { Column(Modifier.fillMaxWidth().weight(1f)) { … } }` | whatever the wrapper renders |
| The `gezgin-mvi` artifact | folded into `gezgin-core`; 4 published modules become 3 |

`gezgin-mvi` is deleted rather than emptied. A consumer on `0.2.x` removes the dependency as part
of the `0.3.0` upgrade.

## 4. Public API

Three new annotations in `gezgin-core`, plus one meta-annotation added to the existing `@Screen`.

```kotlin
/** Marks a composable that wraps screen content. Its slots are filled by the processor. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class ScreenWrapper

/**
 * Marks an application-defined annotation as a slot marker. The marked annotation must declare
 * exactly one `KClass<out Route>` parameter; any other parameters are ignored by Gezgin.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class ScreenSlot

/** Binds one `@ScreenWrapper` parameter to the slot marker whose providers fill it. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
public annotation class FilledBy(public val marker: KClass<out Annotation>)
```

`@Screen` gains `@ScreenSlot` so the content slot obeys the same rule as every other slot. Its
existing shape (`@Repeatable`, one `KClass<out Route>` parameter) already satisfies the contract,
so this is additive.

### 4.1 Application side, complete

```kotlin
// :design-system — the application's slot vocabulary
@ScreenSlot @Repeatable annotation class ViewModelOf(val route: KClass<out Route>)
@ScreenSlot @Repeatable annotation class Effects(val route: KClass<out Route>)
@ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)
@ScreenSlot @Repeatable annotation class BottomBar(val route: KClass<out Route>)

@ScreenWrapper
@Composable
fun <S : UiState, I : UiIntent, E : UiEvent> MviScreenRoot(
  @FilledBy(ViewModelOf::class) viewModel: @Composable () -> BaseViewModel<S, I, E>,
  @FilledBy(Effects::class)     onEffect: (E) -> Unit,
  @FilledBy(TopBar::class)      topBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
  @FilledBy(BottomBar::class)   bottomBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
  @FilledBy(Screen::class)      content: @Composable ColumnScope.(S, (I) -> Unit) -> Unit,
) {
  val vm = viewModel()
  val state by vm.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(vm) { vm.effects.collect(onEffect) }
  Scaffold(
    topBar = { topBar(state, vm::onIntent) },
    bottomBar = { bottomBar(state, vm::onIntent) },
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) { content(state, vm::onIntent) }
  }
}
```

```kotlin
// :feature:home — per screen, one annotation per provider
@ViewModelOf(ContactDetailScreenRoute::class)
@Composable
fun contactDetailViewModel(route: ContactDetailScreenRoute): ContactDetailViewModel =
  koinViewModel { parametersOf(route) }

@Effects(ContactDetailScreenRoute::class)
fun handleContactDetailEffect(effect: ContactDetailEffect, nav: ContactDetailNavigator) { … }

@TopBar(ContactDetailScreenRoute::class)
@Composable
fun ContactDetailTopBar(state: ContactDetailUiState, onIntent: (ContactDetailIntent) -> Unit) { … }

@Screen(ContactDetailScreenRoute::class)
@Composable
fun ColumnScope.ContactDetailScreen(
  state: ContactDetailUiState,
  onIntent: (ContactDetailIntent) -> Unit,
) { … }
```

`UiState`, `UiIntent`, `UiEvent` and `BaseViewModel` are the application's own types. Gezgin never
names, constrains or resolves them; the wrapper's own bounds are checked by the Kotlin compiler at
the generated call site.

## 5. Generated output

```kotlin
public fun GezginEntryScope.provideContactDetailEntry() {
  register<ContactDetailScreenRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->
    val nav = LocalGezginRawNavigator.current.contactDetailNavigator(LocalGezginEntryId.current)
    MviScreenRoot<ContactDetailUiState, ContactDetailIntent, ContactDetailEffect>(
      viewModel = { contactDetailViewModel(route = route) },
      onEffect = { effect -> handleContactDetailEffect(effect, nav) },
      topBar = { state, onIntent -> ContactDetailTopBar(state = state, onIntent = onIntent) },
    ) { state, onIntent ->
      ContactDetailScreen(state = state, onIntent = onIntent)
    }
  }
}
```

Notes on the shape:

- The `viewModel:` parameter that `provideXEntry` carries today is gone. DI is resolved inside the
  application's own provider, so the entry function takes no parameters at all in the common case.
- A slot with no provider and a Kotlin default (`bottomBar` above) is omitted from the call.
- Type arguments are always emitted explicitly. Kotlin cannot infer a wrapper's type parameters
  from lambda parameter types, so the processor computes them (§7).
- `route` and `nav` are ordinary local values in the `register` lambda; slots capture them by
  closure. This is how a generic wrapper reaches a fully typed navigator without ever naming its
  type.

## 6. Slot resolution

A `@ScreenWrapper` function declares zero or more slot parameters. A slot parameter is a parameter
annotated `@FilledBy(M::class)` whose type is a function type (`@Composable` or not).

For a route `R`:

1. Collect every provider declaration annotated with a slot marker naming `R`.
2. For each slot parameter of a candidate wrapper, find the provider whose marker matches.
3. A wrapper is a candidate for `R` when the provider bound to its `@FilledBy(Screen::class)` slot
   exists and that provider's signature matches the slot's function type (§6.1), and every slot
   without a Kotlin default has a provider.
4. Exactly one candidate must remain. Zero or more than one is a compile-time error (§9).

### 6.1 Signature matching

A provider matches a slot of type `@Composable Recv.(T1, …, Tn) -> Unit` when:

- The provider's extension receiver equals `Recv` (or both are absent).
- The provider's parameters are partitioned into **slot parameters** and **role parameters**.
  Role parameters are those whose type is one of the Gezgin-supplied roles (§6.2); every other
  parameter is a slot parameter.
- The slot parameters, in declaration order, unify positionally with `T1 … Tn`.

Unification is shallow: a concrete type must equal the slot's type; a slot type that is one of the
wrapper's type parameters binds to the provider's concrete type; a slot type of the form
`(X) -> Unit` unifies with `(Concrete) -> Unit` by binding `X`. Nothing deeper is attempted, and an
un-unifiable pair is a plain mismatch error naming both sides.

Receiver mismatch is deliberately not reported by the processor beyond candidate elimination. When
elimination leaves no candidate the error names the screen's receiver and every wrapper's content
receiver, and the underlying Kotlin error at the generated call site
(`receiver type mismatch`) remains available as a second, precise signal.

### 6.2 Gezgin-supplied roles

Any provider may declare a parameter of one of these types; the processor fills it and does not
count it against slot matching.

| Role type | Value supplied |
|---|---|
| exactly `R` (not a supertype, not the graph interface) | the `register` lambda's `route` |
| `<X>Navigator` for `R` | `LocalGezginRawNavigator.current.<x>Navigator(LocalGezginEntryId.current)` |
| `GezginSheetController` | `LocalGezginSheetController.current` (bottom-sheet entries only) |

Roles are the mechanism that lets a generic wrapper compose with fully typed navigation. They also
replace today's ad-hoc `roleExtraParams` handling in `MviEntryCodegen`.

## 7. Type parameter resolution

Every type parameter of a candidate wrapper must be bound before the call is emitted. Bindings come
only from §6.1 unification, applied across all filled slots:

- `S`, `I` bind from the content slot against the `@Screen` provider.
- `E` binds from the effect slot against its provider.
- A type parameter appearing in no filled slot cannot be bound. This is an error (§9, `SW7`) whose
  message names the parameter and instructs the author to surface it in a slot.

This rule is why the design carries no `@Screen(viewModel = …)` argument and why the wrapper needs
no `reified` type parameter: the ViewModel type reaches the wrapper as the return type of a slot
provider, not as a type argument the processor must be told about.

## 8. Discovery

Verified against KSP 2.3.9 through a two-stage `kctfork` compilation, with the declaring module
supplied both as a class directory and as a JAR. Results were identical in both forms.

| Capability | Result |
|---|---|
| `getSymbolsWithAnnotation(M)` where `M` is declared on the classpath and used in sources | works — providers are found |
| `getSymbolsWithAnnotation` over declarations that live on the classpath | returns empty — classpath declarations are not enumerable by annotation |
| `getClassDeclarationByName` for a classpath annotation class | works |
| reading meta-annotations of a classpath annotation class | works — `@ScreenSlot` is visible |
| reading constructor parameters of a classpath annotation class | works — the route parameter is visible |
| `getFunctionDeclarationsByName(…, includeTopLevel = true)` for a classpath top-level function | works |
| reading parameter annotations of a classpath function | works — `@FilledBy` is visible |
| `getDeclarationsFromPackage(P)` for a classpath package | works — enumerates the package's declarations |

The algorithm per KSP round therefore is:

1. Slot markers, in-module: `getSymbolsWithAnnotation("dev.gezgin.core.annotation.ScreenSlot")`.
2. Wrappers, in-module: `getSymbolsWithAnnotation("dev.gezgin.core.annotation.ScreenWrapper")`.
3. Slot markers and wrappers declared elsewhere: for each package named by the
   `gezgin.wrapperPackages` KSP option, `getDeclarationsFromPackage` and filter by the two
   annotations.
4. Providers: for every marker found in steps 1–3, `getSymbolsWithAnnotation(markerFq)` over this
   module's sources.

Step 4 is the one placement constraint this design imposes: **a provider must live in the same
module as the `@Screen` it serves.** Classpath declarations are not enumerable by annotation, so a
top bar compiled into another module would be invisible to the round that generates the entry.
Wrappers and slot markers are exempt — they are reached by package enumeration in step 3 — and the
constraint matches how features are already organised, since the provider and the screen share the
route's state and intent types.

A single-module application needs no configuration. A multi-module application sets one option,
naturally in a convention plugin:

```kotlin
ksp { arg("gezgin.wrapperPackages", "com.example.designsystem") }
```

The option accepts a comma-separated list. A package that yields no wrapper and no marker is an
error (`SW9`) rather than a silent no-op, because the failure mode it guards against — every screen
silently losing its chrome — is invisible at runtime.

`getDeclarationsFromPackage` is `@KspExperimental`. The processor opts in at its single call site.
If a future KSP drops it, the fallback is an option carrying fully-qualified declaration names
resolved through `getFunctionDeclarationsByName` / `getClassDeclarationByName`, both of which are
stable API and were verified above.

## 9. Error catalog

All errors are KSP-time and name both the offending declaration and the fix. Codes follow the
existing `SC`/`MV` convention with a new `SW` prefix.

| Code | Condition |
|---|---|
| `SW1` | `@ScreenWrapper` function has no `@FilledBy(Screen::class)` slot |
| `SW2` | `@FilledBy` parameter's type is not a function type |
| `SW3` | `@ScreenSlot` annotation does not declare exactly one `KClass<out Route>` parameter |
| `SW4` | two providers claim the same slot for the same route |
| `SW5` | a slot without a Kotlin default has no provider for a route that has a `@Screen` |
| `SW6` | at least one wrapper is in scope but none is a candidate for a route, or more than one is |
| `SW7` | a candidate wrapper has a type parameter bound by no filled slot |
| `SW8` | a provider's parameters do not unify with its slot's signature |
| `SW9` | a package named by `gezgin.wrapperPackages` yields no wrapper and no slot marker |
| `SW10` | a provider names a route that has a `@Screen` in this module, but no wrapper slot consumes that provider's marker |

Zero `@ScreenWrapper` in scope is not an error. Content is then called bare, exactly as core-mode
entries are emitted today, and the double `Column` disappears for everyone.

## 10. Entry kinds

`EntryKind.SCREEN`, `DIALOG`, `BOTTOM_SHEET` and `FULLSCREEN_MODAL` all participate. A modal is not
special-cased: its content typically declares a `GezginSheetController` role parameter, which makes
its signature distinct, which in turn makes a modal-specific wrapper a distinct candidate under the
ordinary matching rules. The current `BOTTOM_SHEET` branch that skips wrapping is deleted along
with the rest of the hard-coded chrome.

`@FragmentScreen` entries are unchanged and are not wrapped. A Fragment brings its own view
hierarchy; introducing a Compose wrapper around it is a separate question and is explicitly
deferred.

## 11. Migration

`0.3.0` is breaking and is released as such. There is no deprecation window: a parallel legacy
codegen path would double the processor's surface for the duration, and the sole production
consumer (ZAD) has not yet adopted `gezgin-mvi`, so the compatibility debt would buy nothing.

Consumer-side migration, per screen:

1. Delete the `GezginMvi` supertype; keep the ViewModel and its state/intent/effect types as they
   are.
2. Write the application's base types and one `@ScreenWrapper` — once, not per screen.
3. Replace `@MviViewModel(R::class)` with a `@ViewModelOf(R::class)` provider that calls the DI
   framework directly.
4. Replace `@EffectHandler(R::class)` with an `@Effects(R::class)` provider; the function body is
   unchanged, including its typed navigator parameter.
5. Replace `@TopBar` / `@BottomBar` with the application's own markers, or move that chrome into
   the wrapper's `Scaffold`.

The `docs/gezgin-zad-root-integration-spec.md` sequencing assumes full `gezgin-mvi` adoption and
must be revised against this design before the ZAD root-integration work resumes.

## 12. Testing

- Golden codegen tests per generated shape: no wrapper, content-only wrapper, wrapper with optional
  slots omitted, wrapper with every slot filled, wrapper over each `EntryKind`.
- Role-injection tests: provider taking the route, the typed navigator, both, neither.
- Unification tests: concrete match, type-parameter binding, `(X) -> Unit` binding, mismatch.
- One negative compile test per `SW` code, asserting the message names the offending declaration.
- Cross-module tests through `CompileHarness.compileGezginModule`, covering both discovery paths
  (in-module annotations, and `gezgin.wrapperPackages` against a compiled dependency).
- `sample/hello` is rewritten as the canonical two-screen proof and must build green; `sample/shopr`
  and the `sample/feature/*` modules are rewritten onto the new API.

## 13. Verified assumptions

Two load-bearing mechanisms were proven before this document was written, not assumed.

- A generic `@Composable` wrapper with a `noinline` content slot compiles, and a call site with
  explicit type arguments resolves. Proven in `:sample:hello`; the `inline`/`reified` variant also
  compiles, though §7 removes the need for it.
- A screen whose receiver disagrees with its wrapper's content slot fails to compile at the
  generated call site with `receiver type mismatch`. The compile-time guarantee that motivates
  Gezgin is preserved rather than traded away.
- Every discovery capability in §8, in both class-directory and JAR form.

## 14. Open items

- Whether `gezgin.wrapperPackages` should also accept fully-qualified declaration names for the
  case where a design-system package is large and enumeration cost matters. Deferred until a
  measurement exists.
- Fragment-hosted entries and wrappers (§10).
