# Task 4 report: route-bound MVI chrome generation

## Status

`GREEN`

## Scope

- Added repeatable, binary-retained `@TopBar(route)` and `@BottomBar(route)` only in `gezgin-mvi`.
- Marked both annotations as migration-only ZAD compatibility bindings, not permanent container APIs.
- Read chrome annotations by FQN in `gezgin-processor`; no processor production dependency on `gezgin-mvi` was added.
- Associated top and bottom providers strictly by route and added them to the route-local MVI entry model/dump.
- Added fail-loud diagnostics:
  - `MV19`: duplicate top providers for one route;
  - `MV20`: duplicate bottom providers for one route;
  - `MV21`: provider route without an MVI screen;
  - `MV22`: malformed or route-ViewModel-incompatible State/Intent provider signature.
- Generated one independent entry per repeated `@Screen(route)` binding.
- Generated the migration wrapper for MVI entries only:

```kotlin
Column {
    TopBar(state, vm::onIntent)
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Screen(state, vm::onIntent)
    }
    if (!imeVisible) {
        BottomBar(state, vm::onIntent)
    }
}
```

- Used `WindowInsets.ime.getBottom(LocalDensity.current) > 0` only when a bottom provider exists.
- Preserved `ColumnScope` for the shared screen body through the nested weighted `Column`.
- Kept core-mode `EntryCodegen.kt` unchanged.
- Added no BoxScope, permanent container/screen scope, CompositionLocal state, scrolling, `bringIntoView`, or `imePadding` API.
- Expanded `compatibility/zad-consumer` to two routes sharing one `ColumnScope` screen, with separate Koin ViewModels, Effect types, route-bound handlers, typed navigators, and one route-local chrome pair.
- Kept the compatibility ViewModels strict-MVI: each receives only its `@InjectedParam route`; navigators exist only in route-bound effect handlers.

## Red evidence

The Task 3 baseline passed before adding Task 4 tests:

```bash
./gradlew :gezgin-processor:test \
  --tests dev.gezgin.processor.MviEntryCodegenTest \
  --tests dev.gezgin.processor.MviModelReaderTest \
  --rerun-tasks
```

```text
BUILD SUCCESSFUL in 19s
```

After adding only Task 4 fixtures/tests, the same focused command failed for the intended missing behavior:

```text
66 tests completed, 10 failed
BUILD FAILED in 21s
```

The ten failures were the three wrapper/IME codegen tests and seven model tests covering both/top-only/
bottom-only/content-only bindings, repeatability, duplicate providers, unknown routes, and State/Intent
mismatches. Existing tests remained green.

The real consumer then exposed a second integration-level regression after the initial processor green:

```text
e: .../GezginMviEntries.kt:9:43 Cannot access
'val RowColumnParentData?.weight: Float': it is internal in file.
```

A focused regression test was added first to forbid the generated top-level
`androidx.compose.foundation.layout.weight` import. It failed, then passed after codegen emitted the
literal `.weight(1f)` call so the outer `ColumnScope` supplies the public member extension.

## Final green evidence

Focused processor tests, all tasks forced fresh:

```bash
./gradlew :gezgin-processor:test \
  --tests dev.gezgin.processor.MviEntryCodegenTest \
  --tests dev.gezgin.processor.MviModelReaderTest \
  --rerun-tasks
```

```text
66 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL in 21s
```

Full processor regression plus MVI JVM tests, all tasks forced fresh:

```bash
./gradlew :gezgin-processor:test :gezgin-mvi:jvmTest --rerun-tasks
```

```text
gezgin-processor: 16 suites, 200 tests, 0 failures, 0 errors, 0 skipped
gezgin-mvi:jvmTest: 2 suites, 5 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL in 43s
```

Maven Local publication:

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:publishToMavenLocal \
  :gezgin-mvi:publishToMavenLocal \
  :gezgin-processor:publishToMavenLocal
```

```text
BUILD SUCCESSFUL in 1s
```

Independent consumer with its own Gradle `9.4.1` wrapper:

```bash
./compatibility/zad-consumer/gradlew \
  -p compatibility/zad-consumer \
  clean compileDebugKotlin --stacktrace
```

```text
BUILD SUCCESSFUL in 1s
```

## Generated source inspection

Inspected:

`compatibility/zad-consumer/build/generated/ksp/debug/kotlin/dev/gezgin/compat/zad/GezginMviEntries.kt`

Mechanical checks all returned `true`:

- exactly two generated `provide...Entry` functions;
- both default Koin resolvers use `parametersOf(args)`;
- neither entry uses `parametersOf(nav...)` or `viewModel(nav...)`;
- each route invokes its own effect handler and typed navigator;
- only `ZadCompatibilityRoute` contains top/bottom providers and IME observation;
- `FeaturedCompatibilityRoute` contains no chrome or IME body leakage;
- both routes invoke the same shared screen with `state` and `vm::onIntent`;
- top/content/bottom ordering is preserved;
- generated content uses `Column(Modifier.fillMaxWidth().weight(1f))`;
- no top-level `weight` import is emitted.

## Files changed

- `.superpowers/sdd/task-4-report.md`
- `gezgin-mvi/src/commonMain/kotlin/dev/gezgin/mvi/annotation/ScreenChrome.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModel.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModelReader.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/MviEntryCodegen.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/MviDump.kt`
- `gezgin-processor/src/test/kotlin/dev/gezgin/processor/fixtures/MviSource.kt`
- `gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviEntryCodegenTest.kt`
- `gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviModelReaderTest.kt`
- `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityGraph.kt`
- `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityScreen.kt`
- `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityViewModel.kt`

## Residual risks

- Public API dumps, maintained docs, and sample migration intentionally remain Task 7 ownership.
- Kotlin compile-testing cannot fully compile every generated Compose body because of the existing plugin-less
  backend limitation; the independent Android consumer is the real generated-source compile proof for Task 4.
- Task 4 proves the common-safe IME expression through generated source/goldens and Android compilation. A
  separate external Desktop consumer fixture is not part of this task; `gezgin-mvi:jvmTest` remains green.
