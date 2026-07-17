# Gezgin Phase A → ZAD Phase B Handoff

Status: **GREEN**  
Generated: 2026-07-17  
Immutable source commit: `1aff450304fa592d67a9cfa735b5bafd614ac28f`  
Source branch: `codex/zad-integration-readiness`  
Artifact version: `0.1.0-alpha01`  
Repository: Maven Local (`~/.m2/repository`)

The source worktree was clean at the source checkpoint. The coordinates below were republished from the exact source commit above, consumed by the independent ZAD-shaped fixture, and integrity-locked by SHA-256. ZAD must resolve this pinned artifact set and must not use `includeBuild`, composite substitution, `projectDir`, or a moving Gezgin checkout.

## Coordinates and integrity

Production coordinates:

- `dev.gezgin:gezgin-core:0.1.0-alpha01`
- `dev.gezgin:gezgin-mvi:0.1.0-alpha01`
- `dev.gezgin:gezgin-processor:0.1.0-alpha01`

Published platform variants are part of the Gradle module metadata resolution. `gezgin-test` is excluded from the ZAD production handoff.

| Coordinate | Primary artifact SHA-256 | `.module` SHA-256 | `.pom` SHA-256 |
|---|---|---|---|
| `gezgin-core` | `95dcca081810286e130fadade6b423a354f7270749128a52f97bfa16b7fd7a86` | `8aebd7abfff70367bc32762e5d18d9010948a786ba976a5463d22d8f570028a2` | `6d70ce252a306f476b40edcd78c012a863ed4cb1ccdaa96a17a480273d19ed68` |
| `gezgin-core-android` | `671ebec8a45a13f98991a67cc6faf8c58d82d4ac17e0a5db192fb01014c1de65` | `1eec24bf072657355b6e530338803194d565bad8c1bd168001e30316104ebadd` | `b73ba5726d09b8afcf42671c23edf9699618f6f55bd2b009e6e404b5c9f72b90` |
| `gezgin-core-jvm` | `6a40ca8b0c25bb0f18eae834f2c38ec59d7e9e9335d761d3c623cb0f520050f5` | `f8b2c48a4b488b3c930c547a8eccc73d1fe81df2cabd58e0b35ffb999adab17e` | `b83b914f7b907dc65b0d8c559d5864b2e989b50e97c2752b78307183ee7d45e7` |
| `gezgin-mvi` | `595cb09d92b20dcf22b55fe6e48d5f2b28e3b202c3da7f1e0c2cadb7339b340f` | `c35c1a24c849522f6ae681fb89eec97ac58e768a63887f835a015707c53bc660` | `5b13ab89dc62cc5503d6887227dbac57a4c5e6b1a2b3ca336556556c6f822ee0` |
| `gezgin-mvi-android` | `e8622d0a82f39b113b99b1bfda4734548850f301b579e9219ccf9f73460ce4ea` | `395a153994ab67b0589cffb52433b94ec7ad3235359ef13f2bb7afe7e78b2dff` | `8ab41a940c00ecccb78a04977a88c9246acf3624ae08c80c274798a9556db925` |
| `gezgin-mvi-jvm` | `15771436eef603ee2590ce5b3f0a403f247d88a9d359469ec87c7e949f9f4e86` | `6dbec3d67a9a89ab1d168ae0632b49889e3624cc09870be2d0dfceaa15c411b9` | `37592cf230d4232169c442f057883e3fb3dbcf5e0a0ea3dca992913a3a6cd303` |
| `gezgin-processor` | `d9b30026a22caff0754b13266f86be30abd45eea5e77986b85c32615f6a507ca` | `fd20f3079f2013c0e780cdacdd3f61440a4b08461713d6a64de3d0166d23ce84` | `c02392665150d5ddc468bef5d453bdd5b42c903b2d8c54fb03bfe55692dc4680` |

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
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha01/gezgin-core-0.1.0-alpha01.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha01/gezgin-core-0.1.0-alpha01.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core/0.1.0-alpha01/gezgin-core-0.1.0-alpha01.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha01/gezgin-core-android-0.1.0-alpha01.aar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha01/gezgin-core-android-0.1.0-alpha01.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-android/0.1.0-alpha01/gezgin-core-android-0.1.0-alpha01.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha01/gezgin-core-jvm-0.1.0-alpha01.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha01/gezgin-core-jvm-0.1.0-alpha01.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-core-jvm/0.1.0-alpha01/gezgin-core-jvm-0.1.0-alpha01.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha01/gezgin-mvi-0.1.0-alpha01.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha01/gezgin-mvi-0.1.0-alpha01.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi/0.1.0-alpha01/gezgin-mvi-0.1.0-alpha01.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha01/gezgin-mvi-android-0.1.0-alpha01.aar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha01/gezgin-mvi-android-0.1.0-alpha01.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-android/0.1.0-alpha01/gezgin-mvi-android-0.1.0-alpha01.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha01/gezgin-mvi-jvm-0.1.0-alpha01.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha01/gezgin-mvi-jvm-0.1.0-alpha01.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-mvi-jvm/0.1.0-alpha01/gezgin-mvi-jvm-0.1.0-alpha01.pom"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha01/gezgin-processor-0.1.0-alpha01.jar"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha01/gezgin-processor-0.1.0-alpha01.module"
  "$HOME/.m2/repository/dev/gezgin/gezgin-processor/0.1.0-alpha01/gezgin-processor-0.1.0-alpha01.pom"
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

Every dependency-report and boundary command above succeeded; each negated `rg` succeeded because the forbidden search returned no matches. All 21 checksum paths existed and produced the values recorded in the integrity table. The source-checkpoint porcelain was empty at `1aff450304fa592d67a9cfa735b5bafd614ac28f`. Final clean-worktree evidence is re-run after the separate handoff commit, as required by the two-checkpoint sequence.

Key results:

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
