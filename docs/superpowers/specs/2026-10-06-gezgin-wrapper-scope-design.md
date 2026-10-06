# Gezgin wrapper scope (`GezginWrapperScope`)

> Status: design approved in conversation, awaiting written-spec review
> Date: 2026-10-06
> Baseline: `main` at `212eda0`
> Target release: `1.1.0` (breaking for existing `@ScreenWrapper` functions, see §9)
> Origin: GitHub issue #87 (supersedes #85)

## 1. Purpose

A `@ScreenWrapper` function cannot see the route it wraps. It receives only slot lambdas, so a
wrapper cannot read a route flag such as `@NoBack`, cannot know which graph the route belongs to,
and cannot tell whether it is the top entry. This design gives every wrapper a mandatory scope
receiver, `GezginWrapperScope`, that Gezgin fills in, carrying the route, its compile-time
metadata and a little back-stack state.

## 2. Scope

In scope: one public interface and one public class in `gezgin-core`, KSP emission of per-route
metadata into the generated wrapper entries, two validation rules, migration of every existing
wrapper and test, documentation.

Out of scope, deliberately:

- **`GezginSheetWrapperScope` / `sheetController`.** The bottom-sheet support is incomplete and the
  maintainer will rework it in a later version. Nothing is built on top of it now. A wrapper whose
  content slot is `@BottomSheet` uses the base `GezginWrapperScope`; the sheet controller stays
  reachable through the existing slot-provider role (`ProviderRole.SHEET_CONTROLLER`). This is
  recorded as technical debt.
- **Sub-scopes per kind** (`GezginScreenWrapperScope`, `GezginDialogWrapperScope`). With the sheet
  scope gone they would be empty interfaces. They can be added later as backward-compatible
  sub-interfaces.
- **`GezginRouteNavigator` and a `navigator` member.** Generated navigators keep their current
  shape. Wrappers go back through the navigator a slot provider already receives.
- **`Route.annotations` / `Route.name` extensions and any process-wide registry.** The scope
  carries the same data, so no global state is needed.
- **`GezginTopology`.** It is slated for removal; nothing here reads or extends it.
- Generic scope (`GezginWrapperScope<R>`), `isLaunchedForResult`, a `metadata` map, `previousRoute`
  or any back-stack mutation API, an application-defined scope subtype. Reasons are in issue #87.

## 3. Public API (`gezgin-core`)

```kotlin
public interface GezginWrapperScope {
    public val route: Route
    public val routeName: String
    public val routeAnnotations: List<Annotation>
    public val graph: GezginGraph
    public val canGoBack: Boolean
    public val isAloneInBackStack: Boolean
    public val isTop: Boolean
}

public class GezginGraph(
    public val name: String,
    public val kind: GraphKind,
    public val annotations: List<Annotation>,
    public val parent: GezginGraph?,
)

public enum class GraphKind { Nav, Flow }
```

Usage:

```kotlin
@ScreenWrapper
@Composable
fun <S, I, E> GezginWrapperScope.AppScreenRoot(
    @FilledBy(ViewModelOf::class) viewModel: @Composable () -> Vm<S, I, E>,
    @FilledBy(Screen::class) content: @Composable (S, (I) -> Unit) -> Unit,
) {
    val noBack = routeAnnotations.any { it is NoBack }
    if (graph.kind == GraphKind.Flow) { … }
}
```

### 3.1 Member semantics

| Member | Meaning |
|---|---|
| `route` | The route instance of this entry. |
| `routeName` | The route's name as declared, e.g. `OptionOrderChainScreenRoute`. A string constant written by KSP, so R8 obfuscation cannot change it. Never derived from `::class.simpleName` or `serialName` (callback routes have no serializer, and `serialName` is fully qualified and overridable by `@SerialName`). |
| `routeAnnotations` | The route's annotations as instances, see §4.2. Empty list when the route has none. |
| `graph` | The nearest enclosing graph, `@NavGraph` or `@FlowGraph`. `graph.parent` walks outward through both kinds, ending at `null`. |
| `canGoBack` | `!isAloneInBackStack && !noBack`. `false` for a `@NoBack` route and for a lone entry. |
| `isAloneInBackStack` | This entry is the only one on the stack. Differs from `canGoBack`: a `@NoBack` route need not be alone, and a deep-linked screen is alone. |
| `isTop` | This entry is the top of the stack. A screen under an open sheet or dialog is still composed and RESUMED but has `isTop == false`. |

`canGoBack`, `isAloneInBackStack` and `isTop` are observable: reading them in composition
recomposes when the stack changes.

## 4. Generated code

### 4.1 Wrapper call

`WrapperEntryCodegen.provideEntryFun` currently emits `register<R>(…) { route -> Wrapper(slot = …) }`.
It now emits, inside that lambda:

```kotlin
val scope = rememberGezginWrapperScope(
    route = route,
    routeName = "OptionOrderChainScreenRoute",
    routeAnnotations = remember { listOf(NoBack(), Foo(1)) },
    graph = <graph constant>,
    noBack = true,
)
scope.Wrapper<…>(slot = { … })
```

`rememberGezginWrapperScope` is a `@GezginInternalApi` composable in `gezgin-core`. It reads
`LocalGezginRawNavigator` and `LocalGezginEntryId` (already provided around every entry by
`toNavEntry`) and builds an internal `GezginWrapperScope` implementation. Per entry, with `keys`
being `RawNavigator.keysState`:

- `isAloneInBackStack` = `keys.size == 1`
- `isTop` = `keys.last().id == entryId`
- `canGoBack` = `!isAloneInBackStack && !noBack`

Entries for routes without a wrapper (`EntryCodegen`) are unchanged. Core-mode hand-written
`register<R> { … }` is unchanged and has no scope.

### 4.2 Metadata emission

KSP already holds the data (`RouteModel`, `GraphModelNode.directParentFqs`/`membershipParentFq`,
`isFlow`). It is written as file-private top-level constants in the per-package
`GezginWrapperEntries.kt`, deduplicated per file; a graph constant references its parent constant.

- **Graph name:** the graph interface's simple name, as a string literal. **Kind:** `Flow` if the
  declaration carries `@FlowGraph`, otherwise `Nav`.
- **Annotations** (route and graph): every annotation on the declaration is reproduced as an
  instance by calling the annotation constructor with the same argument values (primitives,
  strings, enums, `KClass`, arrays, nested annotations). This includes Gezgin's own annotations
  (`@NoBack`, `@GoTo`, `@Open`, `@NavGraph`, …) and the application's custom annotations. It
  excludes annotations that belong to the compiler or its plugins rather than to the program:
  everything in `kotlin.*`, `kotlinx.serialization.*` and `androidx.compose.runtime.*`
  (`@Serializable`, `@SerialName`, `@Suppress`, `@OptIn`, `@Immutable`, …). The exclusion list is a
  single constant in the processor.
- **Not reproducible:** an annotation that is not accessible from the generated file (private or
  internal to another module) or whose arguments KSP cannot render is skipped, with warning
  `[SW15]` naming the annotation and the route. It never fails the build.
- All string literals in processor and core are English
  (`ProductionErrorMessageLanguageTest`).

## 5. Validation

Both are errors raised in `WrapperModelReader.readWrapper`, which starts reading
`declaration.extensionReceiver`. Codes continue the existing `SW1`–`SW12` series; the `SW14`
proposed in the issue (receiver kind mismatch) does not exist because there are no sub-scopes, so
the unfillable-parameter rule takes `SW14`.

- **SW13:** `@ScreenWrapper <fq>` does not declare a `GezginWrapperScope` receiver. Message
  states that screen wrappers reach the route, graph and back-stack state through that scope and
  shows `fun <…> GezginWrapperScope.AppScreenRoot(…)`.
- **SW14:** parameter `<name>` of `@ScreenWrapper <fq>` cannot be filled: it has no `@FilledBy`
  and no default value. Message says to add `@FilledBy(<Marker>::class)` to make it a slot, and to
  read route, graph and back-stack data from the receiver instead. Today such a parameter is
  silently ignored and the failure appears later as `No value passed for parameter '…'` in
  generated code.
- **SW15 (warning):** see §4.2.

## 6. Technical debt recorded

- `GezginSheetWrapperScope` and a `sheetController` scope member, pending the bottom-sheet rework.
- Per-kind sub-scopes.
- Reading route metadata outside a wrapper (a registry behind `Route.annotations`/`Route.name`).
  Not needed by any current consumer; can be added without breaking this API.

## 7. Testing

- `WrapperModelReaderTest`: SW13 (no receiver, wrong receiver type), SW14 (unmarked parameter with
  and without a default).
- `WrapperEntryCodegenTest`: the generated call is `scope.Wrapper(…)`; constants for name,
  annotations (Gezgin's own and a custom one with arguments), graph chain with parents; excluded
  annotations absent; SW15 warning for an inaccessible annotation, build still succeeds.
- `gezgin-core` runtime tests for the scope: `isTop` flips when a modal is pushed over the screen,
  `isAloneInBackStack` and `canGoBack` on a lone entry, a `@NoBack` entry, a normal entry.
- Every inline `@ScreenWrapper` source in the seven existing wrapper test files gains the receiver
  (`WrapperBinderTest`, `WrapperModelReaderTest`, `WrapperEntryCodegenTest`,
  `SlotProviderReaderTest`, `WrapperCrossModuleTest`, `WrapperAnnotationsTest`,
  `CallbackModalEntryCodegenTest`, plus fixture `MviCodegenSource`).
- Sample and compatibility builds compile with the migrated wrappers.

## 8. Documentation

`CHANGELOG.md` (new `1.1.0` entry with a breaking-change note and the migration step; also fix the
stale `SW1`–`SW11` mention), `README.md` and `README.tr.md` wrapper sections, the SW error table in
`docs/superpowers/specs/2026-09-11-gezgin-screen-wrapper-design.md`.

## 9. Compatibility and migration

SW13 breaks every existing `@ScreenWrapper`. Migration is mechanical: add `GezginWrapperScope.`
before the function name. Wrappers to migrate in this repository: `ShoprScreenRoot`,
`AppScreenRoot`, `ShowcaseScreenRoot`, `ShowcaseSheetRoot` (all under `sample/`), and
`ZadCompatibilityUi` (`compatibility/zad-consumer`). Core-mode `register<R> { … }` users are not
affected. The change ships in `1.1.0`, a minor release carrying a breaking change on a
declaration that is young (introduced in `0.3.0`); the changelog states this plainly.

## 10. Decisions log

| Decision | Reason |
|---|---|
| Scope built in the generated `register` body | One code path (`WrapperEntryCodegen`); no new contract between `core` and `processor`. |
| Metadata passed into the scope, no registry | No global state, no empty-value case, no topology dependency. |
| `routeName` from KSP, not `serialName` | Callback routes have no serializer; `serialName` is fully qualified and overridable. |
| All annotations included except compiler/plugin ones | Maintainer's choice; custom application annotations must be visible. |
| `graph` = nearest graph, either kind | Makes `GraphKind.Nav` and `graph.annotations` meaningful. |
| No navigator on the scope | Maintainer's choice; wrappers keep using the provider-supplied navigator. |
| No sub-scopes, no SW kind-mismatch rule | Empty interfaces with nothing to validate; addable later. |
