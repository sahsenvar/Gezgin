# Task 6 report: strict-MVI maintained sample proof

## Status

`GREEN`

## Scope

- Added reachable `HomeGraph.FeaturedFeed` without changing the existing `HomeGraph.Feed` start route.
- Bound the same `ColumnScope.FeedScreen` composable to `Feed` and `FeaturedFeed`.
- Kept shared `FeedUiState` and `FeedIntent`, while using separate route-local ViewModels, Effects,
  `@EffectHandler(route)` functions, and generated typed Navigators.
- Converted Feed navigation to the strict path:
  `FeedIntent.OpenCatalog -> FeedViewModel.onIntent -> FeedEffect.NavigateToCatalog ->`
  `@EffectHandler(HomeGraph.Feed) -> FeedNavigator.goToCatalog()`.
- Added the equivalent isolated Featured path, targeting `Product("featured")` through
  `FeaturedFeedNavigator`; neither strict proof ViewModel contains a navigator.
- Added migration-only route-local `@TopBar` and `@BottomBar` providers for Feed. Generated-source
  inspection proves the chrome and IME gate do not leak into the Featured entry.
- Added reachable `OrderLockSheetRoute`, actual `@BottomSheet` content, generated entry registration, and
  all three prevent-dismiss values as getter-only `false` overrides.
- Updated `ShopGraphEntries.kt` mechanically for the Featured and OrderLock generated entries.
- Upgraded the independent ZAD consumer from vacuous handler signatures to a real clickable intent,
  buffered route-local effect, `ObserveEffects`, and typed-navigator navigation in both directions.
- Left Gezgin's general ViewModel-navigator capability and the other existing sample ViewModels untouched.
  No route constructor lambda, Fragment modal interop, processor production change, or library runtime
  change was added.

## TDD red evidence

The focused Shopr baseline passed before changes:

```bash
./gradlew :sample:shopr:testDebugUnitTest \
  --tests dev.gezgin.sample.shopr.ShopGraphTopologyTest \
  --rerun-tasks
```

```text
BUILD SUCCESSFUL
```

Tests for the desired generated entries/navigators, strict intent/effect paths, route-local handler targets,
and real non-dismissible sheet entry were then added before production code. The focused run failed at
`compileDebugUnitTestKotlin` for the intended missing behavior:

```text
Unresolved reference 'FeaturedFeedNavigator'
Unresolved reference 'screen_featured_feed'
No value passed for parameter 'nav' in FeedViewModel
Unresolved reference 'NavigateToCatalog'
Unresolved reference 'FeaturedFeed'
Unresolved reference 'OrderLockSheetRoute'
BUILD FAILED
```

After the minimal implementation, one test-only generic assertion needed its two `KClass` values compared
by qualified name. The focused run then passed, and the post-review CTA-label polish was followed by another
fresh focused/full run.

## Final verification evidence

All commands below used `--rerun-tasks`.

Focused and full Shopr proof:

```bash
./gradlew :sample:shopr:testDebugUnitTest \
  --tests dev.gezgin.sample.shopr.ShopGraphTopologyTest \
  --rerun-tasks
./gradlew :sample:shopr:test :sample:shopr:compileDebugKotlin --rerun-tasks
```

```text
7 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

Maintained sample compilation:

```bash
./gradlew :sample:app:compileDebugKotlin \
  :sample:feature:auth:compileDebugKotlin \
  :sample:feature:home:compileDebugKotlin \
  :sample:feature:profile:compileDebugKotlin \
  --rerun-tasks
```

```text
BUILD SUCCESSFUL
```

Maintained one-hop, multi-hop, replace, and navigation behavior:

```bash
./gradlew :sample:navigation:test \
  --tests dev.gezgin.sample.navigation.AppNavBehaviorTest \
  --rerun-tasks
```

```text
19 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

Result, restore, Fragment serialization, and process-death-adjacent core regressions:

```bash
./gradlew :gezgin-core:jvmTest \
  --tests '*RawNavigatorTest*' \
  --tests '*NavResultTest*' \
  --tests '*ResultBusTest*' \
  --tests '*SavedStateTest*' \
  --tests '*RememberNavigator*' \
  --tests '*NavigatorIdentityRestoreTest*' \
  --tests '*Fragment*' \
  --rerun-tasks
./gradlew :gezgin-core:testDebugUnitTest \
  --tests '*RememberNavigatorAndroidIdentityTest*' \
  --rerun-tasks
```

```text
jvmTest: 73 tests, 0 failures, 0 errors, 0 skipped
testDebugUnitTest: 4 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

Relevant MVI, sheet-validation, Fragment, and Faz 8 processor regressions:

```bash
./gradlew :gezgin-processor:test \
  --tests dev.gezgin.processor.MviEntryCodegenTest \
  --tests dev.gezgin.processor.MviModelReaderTest \
  --tests dev.gezgin.processor.EntryCodegenTest \
  --tests dev.gezgin.processor.ValidationTest \
  --tests '*Fragment*' \
  --tests '*Faz8SpikeTest*' \
  --rerun-tasks
```

```text
153 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

Maven Local publication and the independent Gradle 9.4.1 consumer:

```bash
./gradlew :gezgin-core:publishToMavenLocal \
  :gezgin-mvi:publishToMavenLocal \
  :gezgin-processor:publishToMavenLocal \
  --rerun-tasks
./compatibility/zad-consumer/gradlew \
  -p compatibility/zad-consumer \
  clean compileDebugKotlin --stacktrace --rerun-tasks
```

```text
publish: BUILD SUCCESSFUL
consumer: BUILD SUCCESSFUL
```

## Generated-source and boundary inspection

Mechanical checks all returned `true`:

- `provideFeedEntry` and `provideFeaturedFeedEntry` are both generated;
- each entry invokes its own route-local effect handler and typed navigator;
- Feed has top/content/bottom ordering and the IME bottom-bar gate;
- Featured has no Feed chrome or IME leakage;
- neither generated sample entry constructs a ViewModel with a navigator;
- both consumer handlers receive their own typed navigator;
- consumer Koin resolution uses `parametersOf(args)` and never `parametersOf(nav)` or `viewModel(nav)`.

Source boundary scans found no typed/Raw navigator in the strict proof or consumer ViewModels, no direct
`Channel.UNLIMITED`, no `@ScreenEffect`, and no constructor function type in the graph declarations.

## Files changed

- `.superpowers/sdd/task-6-report.md`
- `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityScreen.kt`
- `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityViewModel.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/ShopGraphEntries.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/nav/ShopGraph.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedChrome.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedEffect.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedEffectHandler.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedScreen.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedUiState.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedViewModel.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_featured_feed/FeaturedFeedEffect.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_featured_feed/FeaturedFeedEffectHandler.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_featured_feed/FeaturedFeedViewModel.kt`
- `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/sheet_order_lock/OrderLockBottomSheet.kt`
- `sample/shopr/src/test/kotlin/dev/gezgin/sample/shopr/ShopGraphTopologyTest.kt`

## Residuals

- `adb devices -l` reported no attached device, so the conditional existing
  `./maestro/run-15-fragment-pd.sh` device script was not run. The named non-device Fragment/Faz 8,
  result, saved-state, navigator-identity, and Android recreation suites are green.
- Task 7 still owns public API dumps and maintained documentation. Task 8 owns repository-wide final
  verification and handoff; neither scope was absorbed here.
