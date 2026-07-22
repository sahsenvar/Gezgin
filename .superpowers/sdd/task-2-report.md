# Task 2 report: exact restore-key namespaces

## Status

`GREEN`

## Scope

- Kept both public `rememberNavigator` overload descriptors unchanged.
- Added one internal, length-prefixed String payload envelope carrying the exact `restoreKey`.
- Android now writes that envelope for both the per-call-site UUID token and the live navigator snapshot.
- JVM/Desktop now writes the same envelope around the navigator Saver snapshot.
- Every restore path compares the payload namespace exactly and returns `null` on a missing, malformed, or
  different namespace so Compose initializes fresh state.
- Added Android and JVM production-composable coverage for the real `"Aa" -> "BB"` String-hash collision.
- Added Android coverage using `ComponentActivity`'s actual `SavedStateRegistry` Bundle export/import with a
  fresh Activity and `ViewModelStoreOwner`.
- Added two simultaneous Android navigator call sites under one owner with one business key; distinct
  navigator instances prove distinct token/holder resolution, and independent stacks survive save/recreate.
- Added JVM tests that execute the production `rememberNavigator` and `rememberRawNavigatorInstance` paths
  with a real `SaveableStateRegistry`.

No Task 3, ZAD, or Fragment interop source was changed.

## Red evidence

Before the payload fix, the new JVM production-path collision test failed while the same-key production-path
restore test passed:

```bash
./gradlew :gezgin-core:jvmTest --tests '*RememberNavigatorJvmSaveableRegistryTest*' --rerun-tasks
```

```text
RememberNavigatorJvmSaveableRegistryTest[jvm] > rememberRawNavigatorInstance rejects Aa payload for BB composite hash collision[jvm] FAILED
java.lang.AssertionError at RememberNavigatorJvmSaveableRegistryTest.kt:62
2 tests completed, 1 failed
```

Before the payload fix, the Android focused suite compiled and failed in the two collision-sensitive paths.
The same-key fresh-owner registry restore and simultaneous-call-site save/recreate tests passed:

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:testDebugUnitTest --tests '*RememberNavigatorAndroidIdentityTest*' --rerun-tasks
```

```text
retained owner rejects colliding changed namespace token while same namespace keeps holder FAILED
java.lang.AssertionError at RememberNavigatorAndroidIdentityTest.kt:63

saved state registry rejects Aa snapshot for fresh BB activity despite composite hash collision FAILED
java.lang.AssertionError at RememberNavigatorAndroidIdentityTest.kt:110
```

The internal envelope's corrupt-length test was also observed RED before its bounds guard:

```text
RememberNavigatorSaverTest[jvm] > corrupt namespaced payload length resets instead of throwing[jvm] FAILED
java.lang.StringIndexOutOfBoundsException at RememberNavigatorSaverTest.kt:71
1 test completed, 1 failed
```

## Green evidence

All original Task 2 commands were rerun with task outputs forced fresh:

```bash
./gradlew :gezgin-core:jvmTest --tests '*RememberNavigator*' --tests '*NavigatorIdentityRestoreTest*' --rerun-tasks
```

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:testDebugUnitTest --tests '*RememberNavigatorAndroidIdentityTest*' --rerun-tasks
```

```bash
./gradlew :gezgin-core:jvmTest --tests '*SavedStateTest*' --tests '*ResultBusTest*' --tests '*NavResultTest*' --rerun-tasks
```

The combined fresh run reported:

```text
FINAL_TASK2_JVM_EXIT=0
FINAL_ANDROID_SDK_EXIT=0
FINAL_TASK2_ANDROID_EXIT=0
FINAL_TASK2_REGRESSION_EXIT=0
FINAL_DIFF_CHECK_EXIT=0
FINAL_FRAGMENT_DIFF_COMMAND_EXIT=0
FINAL_FRAGMENT_DIFF_EMPTY_EXIT=0
FINAL_OLD_DESCRIPTOR_EXIT=0
```

The malformed-envelope regression was separately rerun after its bounds guard and passed:

```bash
./gradlew :gezgin-core:jvmTest \
  --tests '*RememberNavigatorSaverTest.corrupt namespaced payload length resets instead of throwing*' \
  --rerun-tasks
```

`javap` still shows the original public overload descriptor:

```text
rememberNavigator(Route, GezginTopology, Json, Function0<Unit>, Composer, int, int)
```

`git diff --check` was clean. A Fragment-path diff from `5d43f26` produced no files.

## Files changed

- `.superpowers/sdd/task-2-report.md`
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/RememberNavigator.kt`
- `gezgin-core/src/androidMain/kotlin/dev/gezgin/core/compose/RememberNavigator.android.kt`
- `gezgin-core/src/jvmMain/kotlin/dev/gezgin/core/compose/RememberNavigator.jvm.kt`
- `gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/RememberNavigatorSaverTest.kt`
- `gezgin-core/src/androidUnitTest/kotlin/dev/gezgin/core/compose/RememberNavigatorAndroidIdentityTest.kt`
- `gezgin-core/src/jvmTest/kotlin/dev/gezgin/core/compose/RememberNavigatorJvmSaveableRegistryTest.kt`

## Residual risks

- Robolectric proves framework `SavedStateRegistry` Bundle export/import across fresh Activity/owner instances;
  it is not a device process-death test and this report makes no device process-death claim.
- Payloads written by the earlier unnamespaced Task 2 implementation are deliberately rejected as malformed.
  Updating from that unreleased branch state causes a one-time fresh navigator start instead of adopting an
  ambiguously namespaced stack.
- Task 2 does not run the repository-wide Phase A matrix; evidence remains scoped to the requested Task 2
  restore, saved-state, result, descriptor, whitespace, and Fragment-boundary gates.
- Tracked API dumps remain owned by the later Phase A public-artifact task; this review fix does not expand
  the public API.
