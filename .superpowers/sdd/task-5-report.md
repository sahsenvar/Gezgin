# Task 5 report: bottom-sheet gesture contract and no-back safety

## Status

`GREEN` for Task 5 implementation and tests. Public API dump refresh remains assigned to Task 7.

## Scope

- Added `BottomSheetContract.sheetGesturesEnabled` with source-compatible default `true`.
- Carried the value through `GezginBottomSheetProps`, entry metadata, props/scene equality, and the
  Material 3 `ModalBottomSheet(sheetGesturesEnabled = ...)` parameter.
- Kept the existing dismiss switches independent:
  - `dismissOnBackPress` maps only to `shouldDismissOnBackPress`;
  - `dismissOnClickOutside` maps only to `shouldDismissOnClickOutside`;
  - `sheetGesturesEnabled` maps only to the Material 3 gesture parameter.
- Revised the runtime `@NoBack + @BottomSheet` predicate to require both
  `dismissOnBackPress == false` and `sheetGesturesEnabled == false`.
- Kept `dismissOnClickOutside` outside that predicate; runtime tests prove both outside values are
  accepted when back dismissal and sheet gestures are disabled.
- Revised processor SC7 so a no-back bottom sheet without `BottomSheetContract` fails statically,
  while a contract-bearing route reaches the runtime value guard because KSP cannot evaluate route getters.
- Used getter-only overrides in core fixtures and compile-testing sources.
- Preserved entry-pinned dismissal, canceled-result delivery, programmatic `navigator.back()`, and
  `GezginSheetController.hideAndBack()` behavior.
- Added no Fragment modal interop, `PlatformDisplay` redesign, deep-link work, or multiple-stack work.

## TDD red evidence

Contract/default, metadata, and scene equality tests were added before production fields. The focused
run failed only on the missing API/props members:

```bash
./gradlew :gezgin-core:jvmTest \
  --tests '*BottomSheetContractTest*' \
  --tests '*EntryMetadataTest*' \
  --tests '*BottomSheetSceneTest*' \
  --rerun-tasks
```

```text
No parameter with name 'sheetGesturesEnabled' found
Unresolved reference 'sheetGesturesEnabled'
'sheetGesturesEnabled' overrides nothing
BUILD FAILED
```

The no-back matrix was then added before changing the old unconditional runtime ban:

```text
11 tests completed, 2 failed
guard - back false gestures false GECER ve outside predicate'e katilmaz
guard - back false gestures true require firlatir
BUILD FAILED
```

The Material 3 UI proof was added before wiring the parameter. With both route values still reaching
Material 3's default `true`, the false case retained drag-handle dismiss semantics and failed:

```text
GezginBottomSheetSceneTest > sheetGesturesEnabled true ve false Material3 dismiss semantics'ine ulasir FAILED
2 tests completed, 1 failed
BUILD FAILED
```

Finally, processor positive tests were added before revising SC7. Both contract-bearing routes were
still rejected by the old unconditional policy:

```text
EntryCodegenTest > SC7 allow — @NoBack + @BottomSheet WITH BottomSheetContract reaches runtime value guard FAILED
ValidationTest > SC7 accepts no-back bottom sheet with runtime-valued structural contract FAILED
2 tests completed, 2 failed
BUILD FAILED
```

## Final green evidence

Focused Task 5 core tests, forced fresh:

```bash
./gradlew :gezgin-core:jvmTest \
  --tests '*BottomSheet*' \
  --tests '*ModalRootGuardTest*' \
  --tests '*EntryMetadataTest*' \
  --rerun-tasks
```

```text
BUILD SUCCESSFUL
```

Dialog/back/stacked-overlay regressions, forced fresh:

```bash
./gradlew :gezgin-core:jvmTest \
  --tests '*Dialog*' \
  --tests '*OnBackTest*' \
  --tests '*StackedOverlay*' \
  --rerun-tasks
```

```text
BUILD SUCCESSFUL
```

Full processor suite, forced fresh:

```bash
./gradlew :gezgin-processor:test --rerun-tasks
```

```text
203 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

Full core JVM and Android unit-test suites, forced fresh with the portable SDK preflight:

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:jvmTest :gezgin-core:testDebugUnitTest --rerun-tasks
```

```text
jvmTest: 188 tests, 0 failures, 0 errors, 0 skipped
testDebugUnitTest: 164 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

## API check

`./gradlew :gezgin-core:apiCheck --rerun-tasks` compiled the Android/JVM API surfaces and then failed
against intentionally stale Task 7 dumps. The generated API delta contains this Task 5 addition:

```text
+ public fun getSheetGesturesEnabled ()Z
```

It also contains pre-existing branch additions from earlier tasks:

```text
+ Screen.Container
+ rememberNavigator(..., restoreKey, ...)
```

Running `apiDump` here would absorb Task 2/3 public-artifact work and violate the locked Task 7 ownership,
so no API dump was changed in Task 5.

## Files changed

- `.superpowers/sdd/task-5-report.md`
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/Contracts.kt`
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/BottomSheetScene.kt`
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/EntryAdapter.kt`
- `gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/GezginBottomSheetContractTest.kt`
- `gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/GezginEntryMetadataTest.kt`
- `gezgin-core/src/commonTest/kotlin/dev/gezgin/core/fixtures/TestGraph.kt`
- `gezgin-core/src/jvmTest/kotlin/dev/gezgin/core/compose/GezginBottomSheetSceneTest.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModelReader.kt`
- `gezgin-processor/src/test/kotlin/dev/gezgin/processor/EntryCodegenTest.kt`
- `gezgin-processor/src/test/kotlin/dev/gezgin/processor/ValidationTest.kt`

## Residual risks

- `:gezgin-core:apiCheck` remains red until Task 7 refreshes all accumulated Android/JVM API dumps.
- The true/false Material 3 gesture proof runs against Desktop Material 3 semantics; Android compilation
  and unit tests are green, but this task does not add a connected-device drag gesture test.
- Existing Compose UI tests emit the repository's pre-existing `runComposeUiTest` deprecation warnings;
  this task does not migrate the test framework API.
