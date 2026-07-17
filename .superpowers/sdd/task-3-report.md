# Task 3 report: route-explicit repeatable MVI binding

## Status

`GREEN`

## Scope

- Made `@Screen(route)` repeatable without changing its target or retention.
- Added binary-retained, repeatable `@EffectHandler(route)` in `gezgin-mvi`.
- Kept no-argument `@ScreenEffect` source-compatible and deprecated it as a legacy bridge.
- Kept `gezgin-processor` independent from `gezgin-mvi` in production; both MVI annotations are read by
  FQN strings and `gezgin-mvi` remains test-only in the processor build.
- Expanded every repeated `@Screen(route)` annotation into its own entry candidate while retaining the
  shared composable declaration.
- Indexed ViewModels and effect handlers by route, with exact generic-aware State, Intent, Effect, and
  route-navigator validation.
- Allowed routes sharing one composable to use different ViewModels, Effect types, handler functions, and
  typed Navigators.
- Kept legacy `@ScreenEffect` only when one matching route remains and no explicit handler overlaps it.
- Added fail-loud diagnostics for duplicate explicit handlers (`MV14`), handler routes without MVI screens
  (`MV15`), route-local Effect mismatches (`MV16`), ambiguous legacy matches (`MV17`), and explicit/legacy
  overlap (`MV18`). Existing `MV5`, `MV6`, `MV9`, and `MV11` diagnostics cover per-route State/Intent,
  zero-match legacy, duplicate legacy, signature, and navigator mismatches.
- Preserved the existing same-package/graph validation and unambiguous legacy compile fixture.

No Task 4 codegen/chrome, Task 5, sample, ZAD, or API-dump source was changed.

## Red evidence

Before production changes, the new Task 3 tests were run with task outputs forced fresh:

```bash
./gradlew :gezgin-processor:test \
  --tests dev.gezgin.processor.MviModelReaderTest \
  --tests dev.gezgin.processor.ValidationTest \
  --rerun-tasks
```

The run failed for the intended missing behavior:

```text
76 tests completed, 11 failed
ValidationTest > repeatable Screen binds one composable to two routes() FAILED
MviModelReaderTest > repeatable Screen emits one route-local model per route with explicit handlers() FAILED
MviModelReaderTest > explicit handler route without a Screen names route and function() FAILED
MviModelReaderTest > explicit handler effect mismatch names route and function() FAILED
MviModelReaderTest > explicit handler navigator mismatch names route and function() FAILED
BUILD FAILED
```

The remaining new failures covered duplicate explicit handlers, legacy zero/multi/overlap, and per-route
State/Intent mismatches. They failed because `@Screen` was not repeatable, `@EffectHandler` did not exist,
and the processor still joined legacy handlers globally by Effect type.

## Green evidence

After implementation, the same focused command passed fresh:

```text
BUILD SUCCESSFUL in 31s
76 tests, 0 failures
```

The full processor suite also passed with every task rerun:

```bash
./gradlew :gezgin-processor:test --rerun-tasks
```

```text
BUILD SUCCESSFUL in 37s
183 tests, 0 failures, 0 errors
```

The focused suite retains `MVI_SOURCE` as an unambiguous no-argument `@ScreenEffect` compile fixture; it
passes with the expected deprecation warning. `git diff --check` was clean before the report was written.

## Files changed

- `.superpowers/sdd/task-3-report.md`
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/annotation/KindAnnotations.kt`
- `gezgin-mvi/src/commonMain/kotlin/dev/gezgin/mvi/annotation/EffectHandler.kt`
- `gezgin-mvi/src/commonMain/kotlin/dev/gezgin/mvi/annotation/ScreenEffect.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModel.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModelReader.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/ViewModelModelReader.kt`
- `gezgin-processor/src/test/kotlin/dev/gezgin/processor/fixtures/MviSource.kt`
- `gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviModelReaderTest.kt`
- `gezgin-processor/src/test/kotlin/dev/gezgin/processor/ValidationTest.kt`

## Residual risks

- Task 3 proves route-local processor models and diagnostics with entry emission disabled for the repeated
  model fixture. Route-specific generated entry source/goldens remain deliberately owned by Task 4.
- Public API dumps, maintained samples, and documentation migration remain deliberately owned by Task 7.
- The compile-testing backend validates same-compilation-unit KSP behavior. Existing package/graph tests
  remain green, but this task adds no new physical multi-module fixture beyond the already maintained
  processor and compatibility coverage.
