# Gezgin Phase A → ZAD Phase B Handoff

Status: **GREEN**  
Generated: 2026-07-17  
Immutable source commit: `57b10f29a5e413b2c4b36e3ab960d478b02f75f9`
Source branch: `codex/zad-integration-readiness`  
Artifact version: `0.1.0-alpha02`
Repository: Maven Local (`~/.m2/repository`)

The source worktree was clean at the source checkpoint. The coordinates below were republished from the exact source commit above, consumed by the independent ZAD-shaped fixture, and integrity-locked by SHA-256. ZAD must resolve this pinned artifact set and must not use `includeBuild`, composite substitution, `projectDir`, or a moving Gezgin checkout.

## Coordinates and integrity

Production coordinates:

- `dev.gezgin:gezgin-core:0.1.0-alpha02`
- `dev.gezgin:gezgin-mvi:0.1.0-alpha02`
- `dev.gezgin:gezgin-processor:0.1.0-alpha02`

Published platform variants are part of the Gradle module metadata resolution. `gezgin-test` is excluded from the ZAD production handoff.

| Coordinate | Primary artifact SHA-256 | `.module` SHA-256 | `.pom` SHA-256 |
|---|---|---|---|
| `gezgin-core` | `95dcca081810286e130fadade6b423a354f7270749128a52f97bfa16b7fd7a86` | `8eb384d09b65e4e33b7d8c315e3a2d2beabc59ee623d706e86c510441ef9fe07` | `f81910a9ed7beb5d9d05f3ac15ff0063116809d1d8e791a8a8378f5976ad6d61` |
| `gezgin-core-android` | `5557b3a9c34f90a7439cdeea5bb5fda2792bbb682b3c91ddbe52a993d9f4505b` | `a26195b2c9d08c8e5065c31df12553227e53ea2a669cda25f6256ec587a7a43e` | `16d611b355bc22a0b7d16e0e1d762bb2bd45537bd4bcd98addce4dad00c273e2` |
| `gezgin-core-jvm` | `84e7d687882688ef600c919a5fea63c577a1582bca1b4ec0770fba53b2d7910f` | `46e4fc92e53458fc9dea2c43e6c1413134c3fab39b6cca0e57035ee42c3d821b` | `a2de16951d17e9f6b581ebc8e7a6b1bc68b938ab9ee230a96995cdd2db3e583f` |
| `gezgin-mvi` | `595cb09d92b20dcf22b55fe6e48d5f2b28e3b202c3da7f1e0c2cadb7339b340f` | `c30d450c9e1bb954c4167e285703ba4bdd07ed7a9cde8c16fb127da46ccbc4fb` | `65c0e52a4cb0d963fbcdb5a48f3d95c97db1e1e897abd16beb8bc1950ed281d6` |
| `gezgin-mvi-android` | `e8622d0a82f39b113b99b1bfda4734548850f301b579e9219ccf9f73460ce4ea` | `77f6cab81247767a6f3fb998dc2fbe0b667ad7f4e7300448e12c794b491b06c7` | `36166e2392828525f3b3d2a229acd2fcd6fc218dae3fe211c56495dfddaafd55` |
| `gezgin-mvi-jvm` | `15771436eef603ee2590ce5b3f0a403f247d88a9d359469ec87c7e949f9f4e86` | `927e6a66d70309b4146387e3b6132f3e1dc195794f80d3f11dc31a5025ab94df` | `8b4ac1bef417bfd5da065d8aabd8f1dd77b2d4279a4d8d413e805acd606eb031` |
| `gezgin-processor` | `f4684087d05fb96f97a60f9920a21c0cdd017784904cc71c0ce0b48a4efbe47e` | `5f3e966507cd03c461325a64c0eb9abd9de1909874d0861c67fb3d29c63b638b` | `f2a038e1d46ce3f8c369251399b8da2bb46ecf2895be92a33e6babb7b37848a1` |

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
./gradlew :gezgin-processor:test :gezgin-core:allTests :gezgin-mvi:allTests apiCheck
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
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha02/gezgin-core-0.1.0-alpha02.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha02/gezgin-core-0.1.0-alpha02.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha02/gezgin-core-0.1.0-alpha02.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha02/gezgin-core-android-0.1.0-alpha02.aar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha02/gezgin-core-android-0.1.0-alpha02.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha02/gezgin-core-android-0.1.0-alpha02.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha02/gezgin-core-jvm-0.1.0-alpha02.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha02/gezgin-core-jvm-0.1.0-alpha02.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha02/gezgin-core-jvm-0.1.0-alpha02.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha02/gezgin-mvi-0.1.0-alpha02.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha02/gezgin-mvi-0.1.0-alpha02.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha02/gezgin-mvi-0.1.0-alpha02.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha02/gezgin-mvi-android-0.1.0-alpha02.aar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha02/gezgin-mvi-android-0.1.0-alpha02.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha02/gezgin-mvi-android-0.1.0-alpha02.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha02/gezgin-mvi-jvm-0.1.0-alpha02.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha02/gezgin-mvi-jvm-0.1.0-alpha02.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha02/gezgin-mvi-jvm-0.1.0-alpha02.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha02/gezgin-processor-0.1.0-alpha02.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha02/gezgin-processor-0.1.0-alpha02.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha02/gezgin-processor-0.1.0-alpha02.pom"
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

Every dependency-report and boundary command above succeeded; each negated `rg` succeeded because the forbidden search returned no matches. All 21 checksum paths existed and produced the values recorded in the integrity table. The source-checkpoint porcelain was empty at `57b10f29a5e413b2c4b36e3ab960d478b02f75f9`. Final clean-worktree evidence is re-run after the separate handoff commit, as required by the two-checkpoint sequence.

Key results:

- Phase A.1 drag-handle contract/metadata/Material-host tests, Android unit tests, core/MVI checks, processor tests, and API checks completed with `BUILD SUCCESSFUL` from source commit `57b10f29a5e413b2c4b36e3ab960d478b02f75f9`.
- Independent Gradle 9.4.1 consumer resolved and compiled `0.1.0-alpha02`; forbidden JetBrains Navigation 3/lifecycle-navigation3 matches remained zero.
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
