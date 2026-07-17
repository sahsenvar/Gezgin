# Task 2 report: restore-key namespaces

## Scope

- Added the source-compatible `rememberNavigator` overload with `restoreKey`.
- Kept the original positional and trailing-`onRootBack` call shape on a stable legacy namespace.
- Namespaced Android holder/snapshot state and Desktop saveable state by `restoreKey`.
- Added common/JVM namespace and validation coverage plus a Robolectric retained-owner/saved-registry test.

## Red evidence

Before production changes, the focused JVM command failed in `:gezgin-core:compileTestKotlinJvm` because the new namespace contract did not exist:

```text
Unresolved reference 'LEGACY_REMEMBER_NAVIGATOR_RESTORE_KEY'
Unresolved reference 'restoreNamespace'
```

Command:

```bash
./gradlew :gezgin-core:jvmTest --tests '*RememberNavigator*' --tests '*NavigatorIdentityRestoreTest*' --rerun-tasks
```

The Android-focused command was then attempted with the new keyed actual call in place; it reached the shared missing namespace contract first, so compilation stopped before runtime execution. After the production contract was added, the Android test compiled and exposed a test-harness-only second-`setContent` failure. The test now recreates a `ComposeView` with the retained owner and saved registry instead of calling the rule's `setContent` twice.

## Green evidence

Focused JVM verification passed:

```bash
./gradlew :gezgin-core:jvmTest --tests '*RememberNavigator*' --tests '*NavigatorIdentityRestoreTest*' --rerun-tasks
```

Focused Android verification passed:

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:testDebugUnitTest --tests '*RememberNavigatorAndroidIdentityTest*' --rerun-tasks
```

Final focused regression verification passed:

```bash
./gradlew :gezgin-core:jvmTest --tests '*SavedStateTest*' --tests '*ResultBusTest*' --tests '*NavResultTest*' --rerun-tasks
```

The final rerun-task results were JVM restore coverage (exit 0), Android identity coverage (exit 0), and
SavedState/ResultBus/NavResult coverage (exit 0). `git diff --check` was clean, and no Fragment interop
production path changed.

## Residual risks

- The Android test recreates the Compose host with a retained `ViewModelStoreOwner` and restored
  `SaveableStateRegistry`; it does not simulate a full framework Activity recreation or device process death.
- Task 2 does not run the repository-wide Phase A matrix; its evidence is limited to the specified focused
  JVM/Android restore and core saved-state/result tests.
- `:gezgin-core:apiCheck` detects the new public overload in generated Android API output. The tracked API
  dumps are intentionally not changed here because Task 2's authorized file list excludes them; the Phase A
  public-artifact task owns that refresh.
