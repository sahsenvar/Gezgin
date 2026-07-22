# Task 2 Report — Public KDoc and enforcement

## Status

COMPLETE. The handwritten consumer-visible public API in `gezgin-core`, `gezgin-mvi`,
`gezgin-processor`, and `gezgin-test` is documented in English. Public top-level type declarations carry
the exact `@author @sahsenvar` tag. The deterministic Gradle inventory gate and strict Dokka configuration
are green.

## Commit

- Pending at report creation; update after the intentional Task 2 commit.

## Files

Enforcement and Dokka configuration:

- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `gezgin-core/build.gradle.kts`
- `gezgin-mvi/build.gradle.kts`
- `gezgin-processor/build.gradle.kts`
- `gezgin-test/build.gradle.kts`

Public KDoc and Dokka-link cleanup:

- `gezgin-core/src/androidMain/kotlin/dev/gezgin/core/fragment/FragmentBinding.android.kt`
- `gezgin-core/src/androidMain/kotlin/dev/gezgin/core/fragment/FragmentRouteBundle.android.kt`
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/{Contracts,ExperimentalGezginMigrationApi,GezginInternalApi,Markers,NavEvent,NavResult,RawNavigator,Route,Topology}.kt`
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/annotation/{BackEdgeAnnotations,ForwardEdgeAnnotations,FragmentScreen,GezginNavigatorFor,GraphAnnotations,KindAnnotations}.kt`
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/{BottomSheetScene,DialogScene,EntryAdapter,GezginDisplay,GezginEntryScope,GezginLocals,GezginTransition,RememberNavigator}.kt`
- `gezgin-mvi/src/commonMain/kotlin/dev/gezgin/mvi/{GezginEffects,GezginMvi,ObserveEffects}.kt`
- `gezgin-mvi/src/commonMain/kotlin/dev/gezgin/mvi/annotation/{EffectHandler,MviViewModel,ScreenChrome}.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/GezginProcessorProvider.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/{MviEntryCodegen,NavigatorProbe,TopologyCodegen}.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/{EntryModel,EntryModelReader}.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/model/ModelReader.kt`
- `gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/{ViewModelModel,ViewModelModelReader,VmDiClassifier}.kt`
- `gezgin-test/src/commonMain/kotlin/dev/gezgin/test/GezginTestNavigator.kt`

## TDD evidence

RED command:

```text
./gradlew checkPublicApiKDoc --no-daemon
```

RED result: exit 1; `Public API KDoc check failed (74 declaration(s))`; 33 missing KDoc matches and
44 missing exact-author matches (three declarations had both reasons). Full log:
`.superpowers/sdd/task-2-red.log`.

GREEN command:

```text
ANDROID_HOME=/Users/sahansenvar/Library/Android/sdk ./gradlew checkPublicApiKDoc \
  :gezgin-core:dokkaGenerate :gezgin-mvi:dokkaGenerate \
  :gezgin-processor:dokkaGenerate :gezgin-test:dokkaGenerate \
  :gezgin-core:jvmTest :gezgin-mvi:jvmTest :gezgin-processor:test :gezgin-test:jvmTest \
  :gezgin-core:compileDebugKotlinAndroid :gezgin-mvi:compileDebugKotlinAndroid \
  :gezgin-test:compileDebugKotlinAndroid apiCheck --no-daemon
```

GREEN result: exit 0, zero warning lines, `BUILD SUCCESSFUL`. Full log:
`.superpowers/sdd/task-2-final-verification.log`.

## Inventory

The inventory is frozen in `expectedPublicApiKDocInventory`; any declaration-count drift fails the task
until reviewed and accepted intentionally.

| Module | Included | Excluded |
|---|---:|---:|
| gezgin-core | 101 | 16 |
| gezgin-mvi | 14 | 0 |
| gezgin-processor | 1 | 0 |
| gezgin-test | 12 | 1 |
| **Total** | **128** | **17** |

## Deterministic exclusions

- Generated Kotlin is outside the four handwritten `src/*/kotlin` roots scanned by `checkPublicApiKDoc`.
- Dokka has `suppressGeneratedFiles=true` in every documented module.
- Explicit `public override` declarations are excluded by the inventory task.
- Declarations directly marked `@GezginInternalApi`, including inline constructor markers and immediately
  preceding marker annotations, are excluded by the inventory task.
- `GezginInternalApi` declarations that remain visible to Dokka are documented, so strict Dokka generation
  is warning-free without hiding other consumer-visible API.

## Concerns

- The inventory is intentionally syntax-based and relies on these published modules retaining
  `explicitApi()`, so consumer-visible declarations continue to spell `public` explicitly.
- Android-backed Dokka generation needs a valid SDK location (`ANDROID_HOME` or `local.properties`); the
  isolated release worktree has no `local.properties`, so verification supplied `ANDROID_HOME` explicitly.
- No publishing repository, signing, CI, tag, push, or release action was added or performed.

