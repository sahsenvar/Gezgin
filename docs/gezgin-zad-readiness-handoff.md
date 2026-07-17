# Gezgin Phase A → ZAD Phase B Handoff

Status: **GREEN**  
Generated: 2026-07-17  
Immutable source commit: `9402fb631bc14cc49a055637bdaf8055f332d7e0`
Source branch: `codex/zad-integration-readiness`  
Artifact version: `0.1.0-alpha04`
Repository: Maven Local (`~/.m2/repository`)

The source worktree was clean at the source checkpoint. The coordinates below were republished from the exact source commit above, consumed by the independent ZAD-shaped fixture, and integrity-locked by SHA-256. ZAD must resolve this pinned artifact set and must not use `includeBuild`, composite substitution, `projectDir`, or a moving Gezgin checkout.

## Coordinates and integrity

Production coordinates:

- `dev.gezgin:gezgin-core:0.1.0-alpha04`
- `dev.gezgin:gezgin-mvi:0.1.0-alpha04`
- `dev.gezgin:gezgin-processor:0.1.0-alpha04`

Published platform variants are part of the Gradle module metadata resolution. `gezgin-test` is excluded from the ZAD production handoff.

| Coordinate | Primary artifact SHA-256 | `.module` SHA-256 | `.pom` SHA-256 |
|---|---|---|---|
| `gezgin-core` | `95dcca081810286e130fadade6b423a354f7270749128a52f97bfa16b7fd7a86` | `bc7f7b08386db4058d0cd82d08b830574e894dfefd0bd7b48ac97abcc6ce53c9` | `4be01d74f8f29b5477ecc9f05bcef9c9e85f20ccf7237d25a6cd0425074da293` |
| `gezgin-core-android` | `5557b3a9c34f90a7439cdeea5bb5fda2792bbb682b3c91ddbe52a993d9f4505b` | `d0bc27aaed03e72300947f653a0ffb692d4628f4fb1ed8bdf67e252f619a2cce` | `75b0f2e55afcee810619b88af4f60d350bbb233f124dea24cd9c2796758be648` |
| `gezgin-core-jvm` | `84e7d687882688ef600c919a5fea63c577a1582bca1b4ec0770fba53b2d7910f` | `cfddb50b6d1e1a5687c2670424e37366efdf3d0146aff77b8dae82d2e6d0bd6b` | `605591cf9752888d8ed6d60e4b57847ae54bca076babd4d6108be917c1903849` |
| `gezgin-mvi` | `595cb09d92b20dcf22b55fe6e48d5f2b28e3b202c3da7f1e0c2cadb7339b340f` | `dca0ba6b2a667c08631d4eedc5608db5f1fa59de27f6cb0ed8da8533b7ce93b6` | `5f3e38f1652a1c4a2d382b193f44b3adc74386f33c9e916875a2e8e5524f06e3` |
| `gezgin-mvi-android` | `e8622d0a82f39b113b99b1bfda4734548850f301b579e9219ccf9f73460ce4ea` | `cccc3b44f3f6c4084eb1cb283267bb971e507ad6cce2f506651dc662c7ab69e2` | `4ddc585a655df9e2225940e7d6763ce28930553e00df264b4f18e761e19bcec5` |
| `gezgin-mvi-jvm` | `15771436eef603ee2590ce5b3f0a403f247d88a9d359469ec87c7e949f9f4e86` | `554b65356900aa0705c5fc3c2b7b931b8f0078b20e0bedac1c31d55e20f1998f` | `d956a55339bc6e8434d6ac5cd3a3b5dc5a1f28341c9ff7eba3611abc3aaba41c` |
| `gezgin-processor` | `0450bffbb8756d0827e340568e792e5f7319f6a37d91b45afb3c3d8e2d7c9b5a` | `ac965476d193d36fd5176ab4d9b8b634d381569d265041dd1c0a42cc6081b9a0` | `306d29aac594254682c58f98cc07d88957a06201b48b4e2c7adb617922d707c5` |

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

Its Android runtime classpath contains neither `org.jetbrains.androidx.navigation3` nor `org.jetbrains.androidx.lifecycle:*navigation3`. Evidence is retained in ignored build reports:

- `compatibility/zad-consumer/build/reports/zad-readiness/navigation3-debugRuntimeClasspath.txt`
- `compatibility/zad-consumer/build/reports/zad-readiness/lifecycle-viewmodel-navigation3-debugRuntimeClasspath.txt`

## Verification evidence

All commands below completed successfully with `ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"` and a verified SDK directory.

```bash
./gradlew :gezgin-processor:test :gezgin-core:allTests :gezgin-mvi:allTests apiCheck :sample:shopr:assembleDebug
./gradlew :gezgin-core:publishToMavenLocal :gezgin-mvi:publishToMavenLocal :gezgin-processor:publishToMavenLocal
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer clean compileDebugKotlin --stacktrace
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency navigation3
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency lifecycle-viewmodel-navigation3
./gradlew clean check
./gradlew :sample:app:assembleDebug :sample:shopr:assembleDebug
./gradlew :gezgin-processor:test --tests '*Fragment*'
./gradlew :gezgin-core:jvmTest --tests '*Fragment*'
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer clean compileDebugKotlin --refresh-dependencies --stacktrace
```

Dependency reports were captured and checked with:

```bash
mkdir -p compatibility/zad-consumer/build/reports/zad-readiness
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency navigation3 > compatibility/zad-consumer/build/reports/zad-readiness/navigation3-debugRuntimeClasspath.txt 2>&1
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency lifecycle-viewmodel-navigation3 > compatibility/zad-consumer/build/reports/zad-readiness/lifecycle-viewmodel-navigation3-debugRuntimeClasspath.txt 2>&1
! rg -n 'org\.jetbrains\.androidx\.navigation3|org\.jetbrains\.androidx\.lifecycle:.*navigation3' compatibility/zad-consumer/build/reports/zad-readiness
```

The strict boundaries were checked with:

```bash
! rg -n 'DialogFragment|BottomSheetDialogFragment' gezgin-core/src gezgin-mvi/src gezgin-processor/src
! rg -n '@ScreenEffect' sample --glob '*.kt'
! rg -n 'Channel\.UNLIMITED' compatibility/zad-consumer sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_featured_feed
```

The published integrity table was generated from the exact primary artifact, Gradle metadata, and POM paths:

```bash
artifacts=(
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha04/gezgin-core-0.1.0-alpha04.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha04/gezgin-core-0.1.0-alpha04.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha04/gezgin-core-0.1.0-alpha04.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha04/gezgin-core-android-0.1.0-alpha04.aar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha04/gezgin-core-android-0.1.0-alpha04.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha04/gezgin-core-android-0.1.0-alpha04.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha04/gezgin-core-jvm-0.1.0-alpha04.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha04/gezgin-core-jvm-0.1.0-alpha04.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha04/gezgin-core-jvm-0.1.0-alpha04.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha04/gezgin-mvi-0.1.0-alpha04.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha04/gezgin-mvi-0.1.0-alpha04.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha04/gezgin-mvi-0.1.0-alpha04.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha04/gezgin-mvi-android-0.1.0-alpha04.aar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha04/gezgin-mvi-android-0.1.0-alpha04.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha04/gezgin-mvi-android-0.1.0-alpha04.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha04/gezgin-mvi-jvm-0.1.0-alpha04.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha04/gezgin-mvi-jvm-0.1.0-alpha04.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha04/gezgin-mvi-jvm-0.1.0-alpha04.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha04/gezgin-processor-0.1.0-alpha04.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha04/gezgin-processor-0.1.0-alpha04.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha04/gezgin-processor-0.1.0-alpha04.pom"
)
shasum -a 256 "${artifacts[@]}"
```

The real device gate and source-checkpoint cleanliness commands were:

```bash
./maestro/run-15-fragment-pd.sh
git -c core.fsmonitor=false diff --check
git -c core.fsmonitor=false status --short --branch
git -c core.fsmonitor=false diff --stat
git -c core.fsmonitor=false rev-parse HEAD
test -z "$(git -c core.fsmonitor=false status --porcelain)"
```

Every dependency-report and boundary command above succeeded; each negated `rg` succeeded because the forbidden search returned no matches. All 21 checksum paths existed and produced the values recorded in the integrity table. The source-checkpoint porcelain was empty at `9402fb631bc14cc49a055637bdaf8055f332d7e0`. Final clean-worktree evidence is re-run after the separate handoff commit, as required by the two-checkpoint sequence.

Key results:

- Route-explicit `@EffectHandler` may receive an exactly typed owner `onIntent` dispatcher; the
  independent consumer compiles this binding and wrong Intent types fail with `MV23`.
- Every route not annotated with `@NoBack` now receives a typed navigator with one-step `back()`,
  even when it declares no other edge. A bare `@NoBack` route remains the explicit no-navigator case.
- Phase A.1 drag-handle contract/metadata/Material-host tests, Android unit tests, core/MVI checks, processor tests, and API checks completed with `BUILD SUCCESSFUL` from source commit `9402fb631bc14cc49a055637bdaf8055f332d7e0`.
- Independent Gradle 9.4.1 consumer resolved and compiled `0.1.0-alpha04`; forbidden JetBrains Navigation 3/lifecycle-navigation3 matches remained zero.
- Main processor/core/MVI/API gate: 147 tasks, `BUILD SUCCESSFUL`.
- Clean repository gate: 668 tasks, 650 executed, `BUILD SUCCESSFUL`.
- Independent consumer clean and refresh builds: 8 tasks each, `BUILD SUCCESSFUL`.
- Sample app and Shopr assemblies: 189 tasks, `BUILD SUCCESSFUL`.
- Fragment-focused processor and core regressions passed.
- Boundary searches found no Fragment modal interop implementation, maintained sample `@ScreenEffect`, or `Channel.UNLIMITED` in strict-MVI proof slices.

### Real Fragment process-death regression

The installed sample APK was exercised on the `AGP37_Phone` emulator with the maintained `./maestro/run-15-fragment-pd.sh` flow.

- DKA/activity recreation restored `HelpFragment`, decoded `HelpScreenRoute(topic = "navigasyon")`, and re-bound its generated navigator.
- Real `HOME + am kill` changed the process PID from `6647` to `6754`.
- The cold process restored the same Fragment depth and route args.
- `Panoya dön` navigated back to Dashboard after restore, proving navigator re-bind.
- Final output: `MADDE 15: PASS`.

The harness now uses an explicit MAIN/LAUNCHER Activity start and verifies both a live PID and the foreground Activity. This replaces an Android 16 emulator `monkey` invocation that exited `-5` without producing a launch event; it does not clear app/task state or weaken restore assertions.

## Runtime and code-generation contracts

### Restore namespace

`rememberNavigator(..., restoreKey = key)` requires a non-blank stable key. The key namespaces both the saved snapshot and Android holder identity. Recreating with the same key restores the stack and pending result slots; changing the key creates a fresh navigator at `start`. ZAD must derive it from persistent session/account generation plus app mode so logout, account change, session reset, and mode change invalidate the previous navigation state.

### Route-bound strict MVI

The maintained direction is:

`Intent -> onIntent -> effect -> @EffectHandler(route) -> typed navigator`

`@Screen` is repeatable. Each bound route owns its own `@MviViewModel(route)` and route-explicit `@EffectHandler(route)`. A shared content function must use State and Intent types compatible with every route; Effect and generated Navigator types may differ by route. ViewModels do not own `Navigator` or `RawNavigator`.

An explicit handler may declare `onIntent: (I) -> Unit`. Generated entry code binds it to
`vm::onIntent` only when `I` exactly matches that route's owner ViewModel Intent type. This lets a
handler collect a typed navigator result and return it through `result -> Intent -> ViewModel`
without placing a Navigator in the ViewModel or introducing a process-wide callback relay.

Deprecated `@ScreenEffect` has no route argument and is compatibility-only. Its exact `Flow<E>` type must identify exactly one `@MviViewModel` route not occupied by an explicit handler. Zero candidates, multiple unoccupied candidates, duplicate legacy handlers, or explicit-handler overlap fail compilation. New ZAD code must use `@EffectHandler(Route::class)`.

For PD-safe results, an effect handler calls generated `launchX()`, collects generated `xResults` while composed, and forwards each typed `NavResult` to the ViewModel as an Intent. The serialized result slot survives process death and collector re-attachment. Suspend `goToXForResult()` is process-lifetime convenience, not ZAD's strict-MVI ownership model.

### Migration-only chrome

Repeatable route-bound `@TopBar(route)` and `@BottomBar(route)` exist only to preserve ZAD's current `ColumnScope` screen shape during migration. Generated order is outer `Column`, top bar, weighted full-width content `Column`, then bottom bar only while the IME is hidden. They are not the permanent outer-screen/container API and must be removed when ZAD adopts its app-owned container.

### Bottom sheets and modal dismissal

`BottomSheetContract` exposes independent `dismissOnBackPress`, `dismissOnClickOutside`, and `sheetGesturesEnabled` values; gesture support defaults to `true`. A non-dismissible sheet sets all three to `false`. `@NoBack` with a bottom sheet is valid only when back dismissal and sheet gestures are both disabled; invalid combinations fail loudly at entry creation. Dismissal produces `Canceled`, and typed result routes remain serializable back-stack entries.

`BottomSheetDragHandleMode.Default/None` is a migration-only compatibility bridge. `Default` preserves Material 3's built-in handle; `None` reaches the real host as `dragHandle = null` while consumer-owned sheet content remains rendered. It adds no serialized route field, composable route lambda, processor feature, ZAD component, or global host override. It is not the permanent V2 presentation/slot API and may be deprecated, replaced, or removed after that design is approved.

### Fragment boundary

Fragment interop remains screen-only. `@FragmentScreen` injects serializable route args and a typed navigator, and the app initializes `Gezgin.initFragmentInterop(gezginJson)` before Fragment restoration. Existing real process-death support was regression-tested; it was not reimplemented. There is no `DialogFragment` or `BottomSheetDialogFragment` bridge. ZAD must convert Fragment dialogs and sheets to Compose routes.

## Phase B authorization and hard gates

This handoff authorizes the separate ZAD Phase B plan against baseline `8e02471e13ad663954b5f96861318de3ac64505d` (whose documented upstream reference is `69142a1bd`). It does not waive any ZAD gate.

Before root ownership changes, ZAD must prove:

1. Every Fragment destination has an explicit serializable Route mapping; `UNMAPPED=0`, unknown targets fail loudly, and retained wizard leaves carry the agreed debt marker.
2. Production Fragment modal classes, APIs, wrappers, and call sites are zero.
3. Graph and route declarations live under `core:navigation` in the single required package; route constructors contain no lambdas.
4. Feature navigation follows strict MVI and contains no ViewModel navigator ownership.
5. The combined root-swap gate is green immediately before replacing the old root.

The authoritative modal audit is 58 `showDialogFragment` occurrences across 37 production consumer files; `NavConverterUseCaseImpl.kt` is included as a consumer.
