# Gezgin readiness and ZAD root-integration contract

> Status: architecture and sequencing contract
> Date: 2026-07-17
> Gezgin baseline: `codex/zad-integration-readiness` at `3748bdb`
> Future ZAD baseline: `architectural/miracle-data-and-domain-refactor`; the future worktree starts from local tip `8e02471e1`, which contains three local AGP 9 / Android 17 commits over origin `69142a1bd`

## 1. Purpose and ownership boundary

This contract separates two bodies of work that must not be mixed:

- **Phase A — Gezgin library readiness.** This work happens first, in the Gezgin repository, on `codex/zad-integration-readiness`. It ends with a tested, documented, publishable compatibility artifact and a handoff record.
- **Phase B — ZAD integration.** This work happens only after every Phase A gate is green, in a different repository and session, from the ZAD local baseline `8e02471e1`. No ZAD production source is changed by the Phase A plan.

Phase A may add a real consumer compile fixture inside Gezgin that models the ZAD toolchain and source patterns. That fixture is compatibility proof, not ZAD application implementation.

## 2. Baseline facts

### 2.1 Gezgin at `3748bdb`

- Gezgin currently builds with Kotlin `2.2.20` and KSP `2.2.20-2.0.2`.
- The Android target currently exposes a mixed Navigation 3 dependency family: AndroidX Navigation 3 runtime alongside JetBrains Navigation 3 UI/lifecycle artifacts. The ZAD-shaped consumer proof must resolve this mismatch before readiness can be declared.
- `@Screen` is not repeatable.
- Effect handlers require an explicit route, so one composable bound to several routes retains deterministic ownership.
- `rememberNavigator` has no `restoreKey`. Android holder identity and saved snapshot identity are positional rather than explicitly session/mode namespaced.
- `BottomSheetContract` has no gesture switch, and the current `@NoBack` plus `@BottomSheet` guard assumes swipe dismissal cannot be disabled.
- MVI entry generation directly invokes the screen composable. It does not have route-bound top/bottom chrome providers.
- The graph processor requires all graph-derived declarations in a navigation module to use one Kotlin package. This is a current processor contract, not a future distributed registry design.
- Fragment interop is deliberately screen-only. `@FragmentScreen` is not modal interop.
- Real `@FragmentScreen` process-death restoration is already implemented and proven. The early `Gezgin.initFragmentInterop(json)` initialization contract and the generated result-flow collection path are existing behavior. Phase A may retain or strengthen regression coverage, but must not create a second cold-process-death implementation.

### 2.2 Future ZAD baseline at `8e02471e1`

- Kotlin `2.3.21`, Koin `4.2.2`, Koin compiler plugin `1.0.1`, AGP `9.2.1`, Android compile/target SDK `37`, JVM/JDK `21`, AndroidX Navigation 3 `1.0.0`, and lifecycle Navigation 3 `2.10.0`.
- Koin code generation uses KCP, not KSP. Gezgin remains a KSP processor.
- All ZAD graphs and routes will move to `core:navigation` and share one Kotlin package because of the current same-package generation rule. Feature entry bindings remain in feature modules.
- Current chrome audit: 25 `ColumnScope` screens, 0 `BoxScope` screens, 14 top-bar provider files, and 7 bottom-bar provider files.
- `MainFragment` is a temporary Nav3 bridge and will be removed. It must not receive an `@FragmentScreen` wrapper.
- Fragment navigation remains widespread. The temporary `FragmentNavigation` adapter is migration scaffolding, not a target architecture component.

### 2.3 Current ZAD modal inventory and process-death risk

The modal migration is a hard root-swap gate. The audit at `8e02471e1` is:

- `@FragmentScreen` processor behavior FS7 is screen-only.
- 1 standard `LoadingDialogFragment`, which becomes an App-level Compose loading overlay rather than a route.
- 27 ViewBinding `BaseBottomSheetFragment` files.
- 2 concrete `BaseComposeBottomSheetFragment` files.
- 2 concrete UPayments transaction sheet files.
- 97 `showComposeBottomSheet` pattern matches in 66 files.
- 18 files directly constructing `ComposeBottomSheetFragment`.
- 58 `showDialogFragment` occurrences across 37 production consumer files; `NavConverterUseCaseImpl.kt` is counted as a consumer.
- `ComposeBottomSheetFragment` is still a `BottomSheetDialogFragment` and stores its content in a nonserialized `lateinit` lambda. It is process-death unsafe.

Gezgin will not add `DialogFragment` or `BottomSheetDialogFragment` support to absorb this inventory.

## 3. Locked target architecture

### 3.1 Root, startup, and navigator lifetime

The root composable and root MVI types are named exactly:

- `App`
- `AppViewModel`
- `AppUiState`
- `AppIntent`
- `AppEffect`
- `AppEffectBus`
- `AppEffectHandler`

`App` creates Gezgin only in `Ready(startRoute, restoreKey)`. Startup loading, root failures, SSL failures, and unexpected startup failures remain `AppUiState` while no navigator exists. The implementation must not create a navigator with a placeholder route and later swap it.

`restoreKey` is a Gezgin API input. It namespaces both the saved navigation snapshot and Android navigator-holder identity. ZAD supplies a persistent session generation plus app mode:

- the same session generation and app mode restore the same stack;
- logout/login, account changes, session-generation changes, and app-mode changes start with a fresh navigator and snapshot namespace.

### 3.2 Navigation model

- The app uses one stack.
- Tab selection uses `replaceTo` and tears down the inactive tab.
- There is no multiple-back-stack design.
- Route constructor lambdas are forbidden. ZAD migrates callbacks to typed result APIs.
- Deep-link navigation is V2 work. `joinzad://upayments-callback` remains a future ZAD `App`/`MainActivity` boundary business callback; it is not converted into a V1 navigation route.
- Sub-FragNav hosts are hoisted into serializable routes.
- ViewPager wizards temporarily remain Fragment leaves. Every retained wizard carries the same debt marker in a code comment, debt markdown, PR body, and commit message.
- The ZAD `@SaveState` system is removed after Gezgin navigation process-death coverage replaces it.

The root swap cannot happen until both of these are proven:

1. every production Fragment navigation target has a complete Fragment-to-serializable-Route mapping; and
2. the Fragment modal migration gate in section 7 is green.

### 3.3 Strict MVI boundary

ZAD navigation follows exactly:

`Intent -> handleIntent -> effect -> route-bound EffectHandler -> typed navigator`

- ViewModels do not hold navigators.
- A screen emits intents and renders state.
- A route-bound effect handler observes effects and owns typed navigation calls.
- A route-explicit `@EffectHandler(route)` may additionally declare `onIntent: (I) -> Unit`.
  Gezgin binds it to that route's owner `ViewModel::onIntent` after exact Intent-type validation.
  This is the generic return seam for `typed result -> owner Intent -> effect`; it does not expose a
  Navigator to the ViewModel and is available only through the route-explicit handler path.
- `RawNavigator` is limited in ZAD to `AppEffectHandler` and the temporary `FragmentNavigation` adapter.
- The adapter is deleted after feature migration. It is not permitted in the final dependency graph.

### 3.4 Repeatable screens and route-explicit effects

`@Screen` becomes repeatable. The same composable may bind multiple routes, subject to these rules:

- each route has its own `@MviViewModel(route)` binding;
- each route has its own route-bound effect binding and typed navigator;
- the composable State and Intent content types must be compatible with every bound route's ViewModel;
- Effect and Navigator types may differ by route;
- processor diagnostics identify the route and declaration that conflict.

`@EffectHandler(route)` is repeatable and always route-explicit. The processor rejects missing routes, duplicate handlers, and route-local type incompatibilities without effect-type inference.

### 3.5 Temporary MVI chrome compatibility

The migration-only API lives in `gezgin-mvi`, isolated from permanent core navigation contracts:

- repeatable `@TopBar(route)`;
- repeatable `@BottomBar(route)`.

Both are route-bound and use the route ViewModel's State and Intent content types. Generated MVI content preserves existing `ColumnScope` screen bodies with this structure:

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

The generator emits only the providers and IME observation needed by that route. Missing top or bottom providers are valid. Multiple providers for the same route are processor errors.

This API exists solely to migrate the audited ZAD `ColumnScope` shape. Permanent outer-container/screen scope, scroll-state access, and `bringIntoView` access are V2 debt. The temporary annotations must remain easy to remove from `gezgin-mvi` after ZAD adopts the permanent design.

### 3.6 Bottom-sheet dismissal contract

`BottomSheetContract` gains:

```kotlin
val sheetGesturesEnabled: Boolean get() = true
```

The value is copied through route metadata, equality, adapter resolution, guards, tests, API dumps, and documentation, and is passed to Material 3 `ModalBottomSheet(sheetGesturesEnabled = ...)`.

ZAD `preventDismiss` maps to all three values:

```kotlin
override val dismissOnBackPress: Boolean get() = false
override val dismissOnClickOutside: Boolean get() = false
override val sheetGesturesEnabled: Boolean get() = false
```

The current `@NoBack` plus `@BottomSheet` guard is re-evaluated now that swipe can be disabled. A modal over a no-back route must not claim full back prevention unless back dismissal and sheet gestures are both disabled. Outside dismissal remains independently governed by `dismissOnClickOutside`; ZAD's `preventDismiss` disables it as well.

### 3.7 Temporary bottom-sheet drag-handle compatibility

ZAD must preserve Design-owned sheets that intentionally omit Material 3's default drag handle and render a custom handle inside the sheet content. Gezgin must not force the Material default in those routes. Phase A therefore adds this migration-only compatibility contract:

```kotlin
enum class BottomSheetDragHandleMode {
    Default,
    None,
}

interface BottomSheetContract {
    val dragHandleMode: BottomSheetDragHandleMode
        get() = BottomSheetDragHandleMode.Default
}
```

The temporary contract has these locked semantics:

- `Default` preserves Gezgin's current Material 3 behavior.
- `None` reaches the actual host call as `ModalBottomSheet(dragHandle = null)`.
- The property is a getter-only presentation override. It does not add serialized route state or require constructor parameters.
- A custom handle remains consumer-owned content. Gezgin does not gain a ZAD component, theme dependency, arbitrary composable lambda, or global host override.
- A ZAD custom handle's close action follows strict MVI: `CloseClicked intent -> handleIntent -> effect -> EffectHandler -> typed navigator`. It does not call a navigator or Fragment controller directly from screen content.
- No route-bound `@BottomSheetDragHandle` provider and no unrestricted `ModalBottomSheet` host replacement are introduced in V1.

`BottomSheetDragHandleMode` is explicitly temporary. It exists only to remove the ZAD migration blocker without prematurely designing Gezgin's permanent bottom-sheet presentation API. Its public documentation and API declaration must carry a migration/debt marker. It may be deprecated, replaced, or removed when the future route-bound presentation/slot design is approved; consumers must not treat the enum as the V2 customization model.

### 3.8 Fragment interop boundary

- Fragment interop remains screen-only.
- No `DialogFragment` or `BottomSheetDialogFragment` adapter is added.
- Existing FS7 behavior remains.
- Existing real `@FragmentScreen` process-death support is treated as complete and proven.
- Regression tests may exercise the existing restore path, but Phase A has no new cold-process-death implementation milestone.

## 4. Phase A — Gezgin library readiness

Phase A is implemented and verified entirely in the Gezgin repository.

### A1. Prove the real ZAD-shaped consumer

Add a real consumer compile fixture that pins the ZAD family:

- Kotlin `2.3.21`;
- KSP compatible with Kotlin `2.3.21`;
- Koin `4.2.2` KCP and compiler plugin `1.0.1`;
- real `@KoinViewModel` and `@InjectedParam route`;
- AGP `9.2.1`, compile/target SDK `37`, JVM/JDK `21`;
- AndroidX Navigation 3 `1.0.0` and lifecycle Navigation 3 `2.10.0`.

The fixture must compile generated Gezgin MVI code against real Koin and Navigation 3 artifacts. Stub-only processor tests are insufficient. Gezgin's Kotlin/KSP and Android Navigation 3 dependency families are aligned until the fixture compiles without JetBrains Navigation 3 artifacts leaking into the Android runtime graph.

The build boundary is fixed: the Gezgin root keeps its Gradle `8.14` wrapper, while `compatibility/zad-consumer` owns and always executes its own Gradle `9.4.1` wrapper. The consumer never uses `includeBuild`, composite substitution, `projectDir`, or another source dependency. The Gezgin root build first publishes one exact artifact version to Maven Local; the consumer then resolves only that pinned Maven Local version with its own wrapper. Every Android command performs this portable SDK preflight first:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
~~~

### A2. Add restore namespaces

Add `restoreKey` to `rememberNavigator` and all platform implementations. Preserve a source-compatible default for existing consumers. Prove:

- same key restores the snapshot;
- changed key starts from the supplied start route;
- same key resolves the same Android holder identity across recreation;
- changed key resolves a different Android holder identity;
- Desktop/JVM saveable state follows the same namespace semantics.

### A3. Make route binding explicit and repeatable

Implement repeatable `@Screen`, route-bound repeatable `@EffectHandler` (including its optional,
exactly typed owner-`onIntent` dispatch seam), per-route MVI validation, and per-route code generation.
Diagnostics must cover duplicate, missing, and incompatible bindings.

### A4. Add temporary MVI chrome

Implement `@TopBar(route)` and `@BottomBar(route)` only in `gezgin-mvi`, with processor models and generated `Column` wrapping. Prove top/content/bottom ordering, content weight, `ColumnScope`, IME-aware bottom behavior, missing-provider behavior, repeatable multi-route use, and fail-loud duplicate/type mismatch behavior.

### A5. Add sheet gesture control

Implement `BottomSheetContract.sheetGesturesEnabled`, propagate it to runtime scene properties, and revise the no-back guard. Cover default compatibility, all dismissal combinations relevant to the guard, equality/metadata, and actual Material 3 wiring.

### A6. Refresh public artifacts

Update Android/JVM API dumps, README files, focused docs, and samples. The sample must demonstrate:

- one composable bound to two routes;
- compatible shared State/Intent types;
- separate ViewModels;
- route-specific Effect types and typed navigators;
- strict intent-to-effect-to-handler navigation;
- route-bound temporary chrome;
- a non-dismissible bottom sheet with all three dismissal switches overridden.

### A7. Produce the handoff artifact

Run full tests, API validation, Android/JVM publication checks, and the real consumer fixture. After those validations pass, request explicit permission for the implementation/source commit; create it only after permission is granted. The handoff records that immutable source commit, exact artifact version, checksums or repository coordinates, required consumer plugin versions, and the successful commands. Request a second explicit permission before creating the separate handoff commit. Only after that handoff commit exists, run the final clean-worktree verification. No commit is implicit in a successful verification run.

### A8. Publish the temporary drag-handle compatibility extension

The existing Phase A handoff artifact predates section 3.7. Before affected ZAD bottom sheets migrate, Gezgin publishes a new immutable compatibility artifact that:

- adds `BottomSheetDragHandleMode.Default/None` to `gezgin-core` without changing the root Gradle/toolchain boundary;
- propagates the mode through route metadata, equality, adapter resolution, runtime scene properties, API dumps, docs, and samples;
- proves `Default` is source/behavior compatible and `None` reaches Material 3 as `dragHandle = null`;
- proves a consumer may render its own handle inside sheet content without a captured route lambda;
- marks the API migration-only and records its future replacement/removal boundary;
- updates the immutable handoff version, source commit, checksums, and verification evidence before ZAD consumes it.

## 5. Phase A acceptance gates

ZAD work must not begin until every row is green.

| Gate | Required evidence |
|---|---|
| Toolchain | Clean ZAD-shaped fixture compiles with Kotlin 2.3.21, Koin 4.2.2 KCP, AGP 9.2.1, JDK 21, and the ZAD AndroidX Nav3 family. |
| Dependency family | Fixture dependency inspection shows no JetBrains Navigation 3 UI/lifecycle artifact in Android runtime classpaths. |
| Restore namespace | Common/Desktop tests and Android holder-identity tests prove same-key restore and changed-key reset. |
| Route binding | Processor tests prove repeatable screens, route-explicit handlers, distinct route codegen, and fail-loud duplicate/type conflicts. |
| MVI chrome | Golden/codegen tests prove the exact `ColumnScope` wrapper and IME-aware bottom behavior. |
| Bottom sheet | Contract, metadata, runtime wiring, guard, equality, and default compatibility tests pass. |
| Temporary sheet handle | `Default` preserves current output, `None` wires `dragHandle = null`, consumer-owned custom content compiles without route lambdas, and the removal boundary is documented. |
| Fragment regression | Existing true process-death Fragment screen suite remains green; no modal interop has been added. |
| Public API | API dumps, docs, and samples match the route-explicit implementation without a legacy inference surface. |
| Publication | Android and JVM artifacts publish to a local test repository and the fixture resolves those artifacts successfully. |
| Full verification | Repository-wide checks pass from a clean worktree and the handoff record names the exact artifact. |

## 6. Phase B — future ZAD integration sequence

This section is a handoff contract, not a list of Gezgin implementation tasks. It is executed in a different ZAD repository/session after Phase A.

### B1. Establish migration controls

- Start from local ZAD tip `8e02471e1` on `architectural/miracle-data-and-domain-refactor`.
- Consume the exact Phase A Gezgin artifact and plugin matrix.
- Move all graph and route declarations to `core:navigation` in one Kotlin package; keep feature entry bindings in feature modules.
- Build a complete Fragment-to-serializable-Route mapping and make missing mappings a root-swap blocker.
- Create the technical-debt records named in section 9 before accepting intentional temporary leaves.

### B2. Introduce the App root without creating navigation early

- Add the exact App MVI type family from section 3.1.
- Keep startup loading/root/SSL/unexpected failures in `AppUiState` before navigation exists.
- Create Gezgin only for `Ready(startRoute, restoreKey)`.
- Derive `restoreKey` from persistent session generation plus app mode.
- Preserve `joinzad://upayments-callback` at the `App`/`MainActivity` business-callback boundary.

### B3. Convert Fragment modals before root swap

Use this order:

1. consume and verify the immutable Phase A extension that contains the temporary drag-handle mode;
2. existing Compose/Nav3 modal bindings to `@Dialog`/`@BottomSheet`;
3. wrapper-based Compose sheets to real serializable `@BottomSheet` routes;
4. ViewBinding sheets to Compose;
5. migrate each callback-owning caller, modal leaf, and typed result route as one coherent unit, then remove that unit's `FragmentResult` or constructor lambda;
6. `LoadingDialogFragment` to the App-level loading overlay;
7. all production Fragment modal APIs and call sites to zero.

`ComposeBottomSheetFragment` and its nonserialized content lambda cannot survive the gate.

ZAD routes that use a Design-owned custom handle set `dragHandleMode = BottomSheetDragHandleMode.None` and render that handle inside their own content. This is not permission to move ZAD visual components into Gezgin. Callback removal must not precede ownership migration, and no process-wide callback bus, singleton relay, or untyped result bridge may replace the old callback.

### B4. Hoist eligible Fragment navigation and preserve marked leaves

- Hoist sub-FragNav hosts into real routes.
- Keep ViewPager wizards as temporary Fragment leaves with synchronized code, markdown, PR-body, and commit-message debt markers.
- Do not wrap `MainFragment`; remove it.
- Keep ordinary Fragment leaves behind current screen-only interop.

### B5. Introduce the temporary adapter and prove mapping coverage

- Restrict the adapter's raw navigation access to the migration boundary.
- Translate only through the proven Fragment-to-Route map.
- Block unknown targets rather than guessing.
- Do not perform the root swap until modal APIs are zero and mapping coverage is complete.

### B6. Perform the single-stack root swap

- Use one Gezgin stack.
- Use `replaceTo` for tabs and mode-root replacement; inactive tabs tear down.
- Keep app-level root chrome separate from temporary screen chrome.
- Remove the old root shell and `MainFragment` bridge only after behavioral parity checks.

### B7. Migrate feature MVI and remove scaffolding

- Move every feature to `Intent -> handleIntent -> effect -> EffectHandler -> typed navigator`.
- Remove navigator fields from ViewModels.
- Finish any constructor-lambda and Fragment-callback migrations not already completed with their modal caller/leaf units in B3. Constructor-lambda zero remains a hard gate before root swap; it is not a precondition that forces callbacks to be deleted before their owners are migrated.
- Delete the temporary `FragmentNavigation` adapter after its last consumer.
- Remove the ZAD `@SaveState` processor after navigation process-death coverage is proven.

## 7. Fragment modal hard gate

The ZAD root swap is forbidden until all of the following are true:

- production `LoadingDialogFragment` usage is zero and loading is App state;
- production `BaseBottomSheetFragment` concrete usages are zero;
- production `BaseComposeBottomSheetFragment` concrete usages are zero;
- production UPayments transaction Fragment sheets are zero;
- production direct `ComposeBottomSheetFragment` construction is zero;
- production `showComposeBottomSheet` wrapper use is zero;
- production `showDialogFragment` calls are zero;
- production `DialogFragment` and `BottomSheetDialogFragment` navigation APIs are zero;
- modal arguments and results are serializable typed routes/results;
- `preventDismiss` routes set back, outside, and gesture dismissal to false;
- routes with Design-owned custom handles set the temporary mode to `None`, render their handle as consumer content, and preserve the approved visual and close behavior;
- process-death tests cover restored modal routes without captured lambdas or Fragment result callbacks.

## 8. Global unexpected-error contract for future ZAD

This behavior belongs to the future ZAD App layer; Gezgin does not gain generic Throwable serialization.

- Core-side failures emit transient `AppEffect.ShowUnexpectedError(Throwable)`.
- `Throwable` never enters a Route, saved state, or serialized navigation snapshot.
- Every Throwable is reported to Crashlytics before UI coalescing.
- `CancellationException` never opens the generic dialog.
- `RemoteError.Http` maps to serializable `UnexpectedErrorUi` containing the server/client/backend display fields required by the UI.
- Other failures map to a localized generic fallback.
- `UnexpectedErrorDialogRoute(UnexpectedErrorUi)` implements `DialogContract` and uses getter overrides with `dismissOnBackPress = false` and `dismissOnClickOutside = false`.
- **Kapat:** `AppIntent -> AppEffect ->` exactly one back operation.
- **Bildir:** the same one-back operation, followed by an App-level snackbar. Real report transport is technical debt.
- V1 has no recovery/error correlation ID and no two-step back navigation.
- `AppEffectBus` is bounded; it must not use `Channel.UNLIMITED`.
- Duplicate visible error dialogs coalesce, while every originating Throwable is still logged.
- Only `AppEffectHandler` and the temporary Fragment adapter may use `RawNavigator`.

## 9. Required future ZAD debt records

The future ZAD work creates `docs/technical-debt/unexpected-error-dialog.md` for real report transport and two-step recovery. It also records:

- ViewPager wizard hoisting;
- removal/replacement of temporary MVI chrome;
- replacement/removal of temporary `BottomSheetDragHandleMode.Default/None` after a permanent route-bound presentation/slot design is approved;
- declarative deep-link navigation, while retaining the UPayments boundary callback;
- permanent outer-container/screen scope and scroll/`bringIntoView` access.

These are explicit deferred items, not hidden acceptance gaps.

## 10. V2 and out-of-scope boundaries

The following are not part of Gezgin Phase A or ZAD V1:

- a distributed graph registry;
- multiple navigation stacks;
- generic Throwable serialization;
- step-count back-navigation APIs;
- `DialogFragment` or `BottomSheetDialogFragment` interop;
- permanent chrome/container APIs;
- permanent route-bound bottom-sheet presentation and drag-handle slot APIs;
- deep-link route dispatch;
- retained inactive-tab stacks.

## 11. Handoff contract

Phase A hands the future ZAD session one immutable compatibility statement containing:

- the exact Gezgin artifact version and source commit;
- confirmation that the Gezgin root used Gradle `8.14`, while the independent consumer used its own Gradle `9.4.1` wrapper without `includeBuild` or composite substitution;
- the Kotlin, KSP, Koin KCP, AGP, JDK, and Navigation 3 matrix proven by the fixture;
- local/remote repository coordinates and integrity data for the tested artifact;
- commands and clean outputs for full verification, API validation, publication, fixture compilation, and dependency inspection;
- restore-key semantics and the default-compatibility behavior;
- route-explicit processor diagnostics and duplicate/type-mismatch limits;
- temporary chrome removal boundary;
- temporary bottom-sheet drag-handle semantics, default compatibility, and explicit replacement/removal boundary;
- confirmation that Fragment interop remains screen-only and existing real process-death support is unchanged;
- confirmation that Phase B remains blocked by the Fragment mapping and modal gates.

The handoff sequence has two authorization checkpoints: first permission for the verified implementation/source commit, then a separate permission for the handoff commit that records that source commit. Final clean-worktree evidence is collected only after the handoff commit.

No ZAD source work is considered authorized or complete merely because Phase A publishes an artifact.
