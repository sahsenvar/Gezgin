# Gezgin Phase A → ZAD Phase B Handoff

Status: **GREEN**
Generated: 2026-07-23
Immutable source/version commit: `d3262b93bd7332621e4864443c3735488a006083`
Source branch: `codex/fragment-screen-fill-max`
Artifact version: `0.1.0-alpha05`
Repository: Maven Local (`~/.m2/repository`)

The source worktree was clean at the immutable source/version checkpoint. The coordinates below were published from that exact commit, consumed by the independent ZAD-shaped Gradle 9.4.1 fixture, and integrity-locked by SHA-256. ZAD must resolve this pinned artifact set and must not use `includeBuild`, composite substitution, `projectDir`, or a moving Gezgin checkout.

## Coordinates and integrity

Production coordinates:

- `dev.gezgin:gezgin-core:0.1.0-alpha05`
- `dev.gezgin:gezgin-mvi:0.1.0-alpha05`
- `dev.gezgin:gezgin-processor:0.1.0-alpha05`

Published platform variants are part of Gradle module metadata resolution. `gezgin-test` is excluded from the ZAD production handoff.

| Coordinate | Primary artifact SHA-256 | `.module` SHA-256 | `.pom` SHA-256 |
|---|---|---|---|
| `gezgin-core` | `95dcca081810286e130fadade6b423a354f7270749128a52f97bfa16b7fd7a86` | `5da7f8ac9495c82455160805ae34b051a9d41d21be6adb21f0c0424f408ef1c7` | `ce22e4407821f3bc1a264a1598ffff977198e025b077d1d6c88bbfd90866b00b` |
| `gezgin-core-android` | `5557b3a9c34f90a7439cdeea5bb5fda2792bbb682b3c91ddbe52a993d9f4505b` | `fec1f708d9cd7cc72f721c3c0ef9e7392affb49c4f40dca5666264d04346e80d` | `4d473a893afe51650ae5bbfc015f133b32e91e3ed5590c12bb9032b7976bfe2a` |
| `gezgin-core-jvm` | `84e7d687882688ef600c919a5fea63c577a1582bca1b4ec0770fba53b2d7910f` | `818b722a5551c2ad4f47a7231c5ca9164a48a560659f77b497215d2a2e889563` | `9d4523eb92768158dbefe37bc47487040839bafbc5f703e9deae1fbb9b25622f` |
| `gezgin-mvi` | `595cb09d92b20dcf22b55fe6e48d5f2b28e3b202c3da7f1e0c2cadb7339b340f` | `c133f3cf635fb5f7e35f9cb790ab92b5ad8ea510703151d41c6c2d4894055a20` | `89d0297d5770d06157be46d892d2e1e81d148c97b47dc89149ca06df5005ba97` |
| `gezgin-mvi-android` | `e8622d0a82f39b113b99b1bfda4734548850f301b579e9219ccf9f73460ce4ea` | `d8be11c60479e51ae8b6577c32c91d8b01029ef2b0bdb2e021307ce6011dd7fa` | `3be91f5a5a80c1038fd4bf937912ae1c7a562465f4f03c73677a8aadedd70665` |
| `gezgin-mvi-jvm` | `15771436eef603ee2590ce5b3f0a403f247d88a9d359469ec87c7e949f9f4e86` | `5e6fe047c99f64b12a8e2b375b61e41cbdc4daf0231427ed89baab3d9c5d0692` | `cef5486d82facbbe87983a8b0f072b4c481b3c4b3e5ff680d5218edefff582a4` |
| `gezgin-processor` | `e0f4c44964afdf5e585ef7fbd59e561e5302ce26160bc6a7e920447512958dc2` | `7b5c2020094d44ab29dfc0a9690e5521f4f69cda219aac456eb60bad1dbb4a01` | `9a6ff14edc0d76f4dafa7363d171db9d8b15fd7c78fceb2538dfa5e199244f5f` |

The 21 hashes above were freshly recomputed from these exact primary artifact, Gradle metadata, and POM paths; all paths existed:

```bash
artifacts=(
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha05/gezgin-core-0.1.0-alpha05.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha05/gezgin-core-0.1.0-alpha05.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha05/gezgin-core-0.1.0-alpha05.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha05/gezgin-core-android-0.1.0-alpha05.aar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha05/gezgin-core-android-0.1.0-alpha05.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha05/gezgin-core-android-0.1.0-alpha05.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha05/gezgin-core-jvm-0.1.0-alpha05.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha05/gezgin-core-jvm-0.1.0-alpha05.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha05/gezgin-core-jvm-0.1.0-alpha05.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha05/gezgin-mvi-0.1.0-alpha05.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha05/gezgin-mvi-0.1.0-alpha05.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha05/gezgin-mvi-0.1.0-alpha05.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha05/gezgin-mvi-android-0.1.0-alpha05.aar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha05/gezgin-mvi-android-0.1.0-alpha05.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha05/gezgin-mvi-android-0.1.0-alpha05.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha05/gezgin-mvi-jvm-0.1.0-alpha05.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha05/gezgin-mvi-jvm-0.1.0-alpha05.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha05/gezgin-mvi-jvm-0.1.0-alpha05.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha05/gezgin-processor-0.1.0-alpha05.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha05/gezgin-processor-0.1.0-alpha05.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha05/gezgin-processor-0.1.0-alpha05.pom"
)
test "${#artifacts[@]}" -eq 21
for artifact in "${artifacts[@]}"; do test -f "$artifact"; done
shasum -a 256 "${artifacts[@]}"
```

## Verified toolchain

| Layer | Verified version |
|---|---|
| Gezgin root Gradle | `8.14` |
| Independent consumer Gradle | `9.4.1` |
| JDK | `21.0.11` |
| Kotlin | `2.3.21` |
| KSP | `2.3.9` |
| Koin runtime/annotations | `4.2.2` |
| Koin compiler plugin | `1.0.1` |
| Consumer AGP | `9.2.1` |
| AndroidX Navigation 3 runtime/UI | `1.0.0` |
| AndroidX lifecycle Navigation 3 | `2.10.0` |
| Consumer compile/target/min SDK | `37` / `37` / `26` |

The independent consumer resolves AndroidX Navigation 3 on Android:

- `androidx.navigation3:navigation3-runtime:1.0.0`
- `androidx.navigation3:navigation3-ui:1.0.0`
- `androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0`

## Alpha05 verification evidence — newly rerun

All Android commands used `ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"` after verifying that directory. The following alpha05 commands were rerun from source/version commit `d3262b93bd7332621e4864443c3735488a006083` and completed with `BUILD SUCCESSFUL`:

```bash
# Focused generated Fragment entry contract.
./gradlew :gezgin-processor:test \
  --tests dev.gezgin.processor.FragmentEntryCodegenTest \
  --rerun-tasks

# Full processor regression.
./gradlew :gezgin-processor:test --rerun-tasks

# Root regression, API, and maintained Shopr assembly gate.
./gradlew :gezgin-processor:test \
  :gezgin-core:allTests \
  :gezgin-mvi:allTests \
  apiCheck \
  :sample:shopr:assembleDebug

# Exact alpha05 publication.
./gradlew :gezgin-core:publishToMavenLocal \
  :gezgin-mvi:publishToMavenLocal \
  :gezgin-processor:publishToMavenLocal

# Independent ZAD-shaped consumer using its own Gradle 9.4.1 wrapper.
./compatibility/zad-consumer/gradlew \
  -p compatibility/zad-consumer \
  clean compileDebugKotlin --stacktrace
```

Fresh dependency reports for the alpha05 consumer were scanned for the forbidden JetBrains Android runtime families:

```bash
rg -n 'org\.jetbrains\.androidx\.navigation3|org\.jetbrains\.androidx\.lifecycle:.*navigation3' \
  compatibility/zad-consumer/build/reports/zad-readiness
```

Exact forbidden-match count: **0**. The independent consumer compiled the pinned `0.1.0-alpha05` artifacts successfully and did not use a composite/source substitution.

The alpha05 source checkpoint was clean before this handoff-only update:

```bash
git -c core.fsmonitor=false rev-parse HEAD
git -c core.fsmonitor=false status --porcelain
```

Result: `d3262b93bd7332621e4864443c3735488a006083` and empty porcelain.

## Applicable inherited contracts — not separately rerun in the alpha05 audit

Alpha05 changes only the generated screen-only Fragment host bounds plus the release version/documentation. The following previously verified contracts remain applicable in the source tree, but their dedicated historical commands were **not** rerun as part of this alpha05 audit. They must not be read as fresh alpha05 device or specialized-regression evidence:

- Real Fragment process-death (`HOME + am kill`) emulator flow and navigator re-bind.
- The separate `./gradlew clean check` repository-wide gate.
- The separate sample-app assembly and focused core Fragment JVM regression commands.
- Dedicated boundary searches for Fragment modal interop, maintained sample `@ScreenEffect`, and `Channel.UNLIMITED`.

The newly rerun alpha05 root gate still covers processor, core, MVI, public API, and Shopr assembly regressions. Maven Local publication, independent-consumer compilation, and the Android dependency-family exclusion were also freshly revalidated as recorded above.

## Runtime and code-generation contracts

### Restore namespace

`rememberNavigator(..., restoreKey = key)` requires a non-blank stable key. The key namespaces both the saved snapshot and Android holder identity. Recreating with the same key restores the stack and pending result slots; changing the key creates a fresh navigator at `start`. ZAD must derive it from persistent session/account generation plus app mode so logout, account change, session reset, and mode change invalidate the previous navigation state.

### Route-bound strict MVI

The maintained direction is:

`Intent -> onIntent -> effect -> @EffectHandler(route) -> typed navigator`

`@Screen` is repeatable. Each bound route owns its own `@MviViewModel(route)` and route-explicit `@EffectHandler(route)`. A shared content function must use State and Intent types compatible with every route; Effect and generated Navigator types may differ by route. ViewModels do not own `Navigator` or `RawNavigator`.

An explicit handler may declare `onIntent: (I) -> Unit`. Generated entry code binds it to `vm::onIntent` only when `I` exactly matches that route's owner ViewModel Intent type. This lets a handler collect a typed navigator result and return it through `result -> Intent -> ViewModel` without placing a Navigator in the ViewModel or introducing a process-wide callback relay.

Deprecated `@ScreenEffect` has no route argument and is compatibility-only. Its exact `Flow<E>` type must identify exactly one `@MviViewModel` route not occupied by an explicit handler. Zero candidates, multiple unoccupied candidates, duplicate legacy handlers, or explicit-handler overlap fail compilation. New ZAD code must use `@EffectHandler(Route::class)`.

For PD-safe results, an effect handler calls generated `launchX()`, collects generated `xResults` while composed, and forwards each typed `NavResult` to the ViewModel as an Intent. The serialized result slot survives process death and collector re-attachment. Suspend `goToXForResult()` is process-lifetime convenience, not ZAD's strict-MVI ownership model.

### Migration-only chrome

Repeatable route-bound `@TopBar(route)` and `@BottomBar(route)` exist only to preserve ZAD's current `ColumnScope` screen shape during migration. Generated order is outer `Column`, top bar, weighted full-width content `Column`, then bottom bar only while the IME is hidden. They are not the permanent outer-screen/container API and must be removed when ZAD adopts its app-owned container.

### Bottom sheets and modal dismissal

`BottomSheetContract` exposes independent `dismissOnBackPress`, `dismissOnClickOutside`, and `sheetGesturesEnabled` values; gesture support defaults to `true`. A non-dismissible sheet sets all three to `false`. `@NoBack` with a bottom sheet is valid only when back dismissal and sheet gestures are both disabled; invalid combinations fail loudly at entry creation. Dismissal produces `Canceled`, and typed result routes remain serializable back-stack entries.

`BottomSheetDragHandleMode.Default/None` is a migration-only compatibility bridge. `Default` preserves Material 3's built-in handle; `None` reaches the real host as `dragHandle = null` while consumer-owned sheet content remains rendered. It adds no serialized route field, composable route lambda, processor feature, ZAD component, or global host override. It is not the permanent V2 presentation/slot API and may be deprecated, replaced, or removed after that design is approved.

### Fragment boundary

Fragment interop remains screen-only. `@FragmentScreen` injects serializable route args and a typed navigator, and the app initializes `Gezgin.initFragmentInterop(gezginJson)` before Fragment restoration. Every generated screen-only `AndroidFragment<Fragment>` entry now supplies `modifier = Modifier.fillMaxSize()`, so the Fragment host consumes the full bounds offered by its parent instead of collapsing to measured child chrome. The focused and full processor suites freshly verify this generated-source contract for alpha05.

Existing real process-death support was not reimplemented. There is no `DialogFragment` or `BottomSheetDialogFragment` bridge. ZAD must convert Fragment dialogs and sheets to Compose routes.

## Phase B authorization and hard gates

This handoff authorizes the separate ZAD Phase B plan against baseline `8e02471e13ad663954b5f96861318de3ac64505d` (whose documented upstream reference is `69142a1bd`). It does not waive any ZAD gate.

Before root ownership changes, ZAD must prove:

1. Every Fragment destination has an explicit serializable Route mapping; `UNMAPPED=0`, unknown targets fail loudly, and retained wizard leaves carry the agreed debt marker.
2. Production Fragment modal classes, APIs, wrappers, and call sites are zero.
3. Graph and route declarations live under `core:navigation` in the single required package; route constructors contain no lambdas.
4. Feature navigation follows strict MVI and contains no ViewModel navigator ownership.
5. The combined root-swap gate is green immediately before replacing the old root.

The authoritative modal audit is 58 `showDialogFragment` occurrences across 37 production consumer files; `NavConverterUseCaseImpl.kt` is included as a consumer.
