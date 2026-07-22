# Gezgin Phase A — Task 1 report

## Status

`GREEN`

Task 1 is complete from `dc09eb6` plus the preserved fixture/build work. The independent
ZAD-shaped Android consumer resolves only the published Maven Local artifacts, compiles with its
own Gradle 9.4.1 wrapper, and uses AndroidX Navigation 3 `1.0.0` plus lifecycle Navigation 3
`2.10.0` at Android runtime.

## Production and publication alignment

- `PlatformDisplay.android.kt` now adapts Gezgin's already-decorated entries to AndroidX Navigation
  3 `1.0.0`'s `backStack`/`entryProvider`/singular `sceneStrategy` API. The private route wrapper
  holds `contentKey: Any` directly and adds a stable per-list occurrence discriminator. Its
  `mapIndexed` construction preserves duplicate equal opaque keys as distinct, ordered entries
  without narrowing the Nav3 key to `Long`; every wrapped entry retains the exact original
  content-key instance, metadata, and content resolution. The small internal
  `adaptAndroidNavDisplayEntries` helper makes that adapter contract directly testable;
  `entryDecorators = emptyList()` still prevents a second decoration pass.
- `gezgin-core` and `gezgin-mvi` publish Android release variants to Maven Local. The Android
  source sets export AndroidX Navigation 3/lifecycle artifacts while JVM source sets retain the
  JetBrains desktop family.
- `gezgin-mvi` common compilation uses AndroidX lifecycle compile-only dependencies; JetBrains
  lifecycle runtime dependencies remain JVM-only.
- The consumer retains real Koin `@KoinViewModel`/`@InjectedParam`, KCP, and KSP. Its narrow
  dependency substitutions map Koin/Compose's general JetBrains lifecycle compatibility
  coordinates to AndroidX `2.10.0`; its component-selection guard rejects JetBrains Navigation 3
  and lifecycle Navigation 3 artifacts.

## Files changed

- `gradle/libs.versions.toml`
- `gezgin-core/build.gradle.kts`
- `gezgin-core/src/androidMain/kotlin/dev/gezgin/core/compose/PlatformDisplay.android.kt`
- `gezgin-core/src/androidUnitTest/kotlin/dev/gezgin/core/compose/AndroidNavDisplayAdapterTest.kt`
- `gezgin-mvi/build.gradle.kts`
- `gezgin-processor/build.gradle.kts`
- `compatibility/zad-consumer/settings.gradle.kts`
- `compatibility/zad-consumer/build.gradle.kts`
- `compatibility/zad-consumer/gradle.properties`
- `compatibility/zad-consumer/gradlew`
- `compatibility/zad-consumer/gradlew.bat`
- `compatibility/zad-consumer/gradle/wrapper/gradle-wrapper.jar`
- `compatibility/zad-consumer/gradle/wrapper/gradle-wrapper.properties`
- `compatibility/zad-consumer/src/main/AndroidManifest.xml`
- `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityGraph.kt`
- `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityScreen.kt`
- `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityViewModel.kt`
- `.superpowers/sdd/task-1-report.md`

No ZAD checkout was opened, read, or edited. No Task 2 source was changed.

## Red proof

The captured deterministic red proof remains valid: with AndroidX Navigation 3 `1.0.0`, the
previous Android production call used the newer `NavDisplay(entries = ..., sceneStrategies = ...)
` API. The exact 1.0.0 source exposes `backStack`, `entryProvider`, singular `sceneStrategy`, and
`onBack`; the targeted production adaptation above is the required green fix.

## Review-fix coverage

- `AndroidNavDisplayAdapterTest` is an Android local Robolectric/Compose test run by
  `:gezgin-core:testDebugUnitTest`; it uses a non-`Long` data-class content key and proves the
  adapter preserves that exact key instance, metadata instances, input order, and the original
  entry's resolved composable content. Its duplicate-key case gives two entries the same opaque
  key and proves count/order, both original keys, isolated metadata, and both rendered contents.
  It adds no application or custom Activity harness.
- Re-review TDD evidence: before the production change,
  `./gradlew :gezgin-core:testDebugUnitTest --tests dev.gezgin.core.compose.AndroidNavDisplayAdapterTest --rerun-tasks`
  failed with `adapter preserves duplicate opaque content keys as separate ordered entries` at the
  `count == 2` assertion, reproducing `associate` collapsing the first equal key. After adding the
  occurrence discriminator and indexed list construction, the same command exited `0` with
  `BUILD SUCCESSFUL`.
- Existing desktop Compose scene tests remain the overlay coverage: `GezginDisplaySceneTest` proves
  dialog/fullscreen overlays retain the underlaid screen and dismiss through back, while
  `GezginBottomSheetSceneTest` covers the bottom-sheet overlay/dismiss path.
- Residual integration risk: this focused Android local test exercises the Android adapter and its
  content resolution, but it does not execute a device/instrumentation end-to-end overlay scene
  through AndroidX Navigation 3 `NavDisplay`. Task 1's Android compile, Maven Local publication,
  independent consumer compile, and runtime dependency inspections cover the remaining binary and
  dependency-family boundary; the existing desktop scene tests cover overlay behavior.

## Fresh verification

All commands below exited `0` on the final working tree. Android SDK preflight passed with
`ANDROID_HOME_EXISTS=0`.

| Command | Result |
|---|---:|
| `./gradlew :gezgin-core:testDebugUnitTest --tests dev.gezgin.core.compose.AndroidNavDisplayAdapterTest --rerun-tasks` | `0` (2 tests, 0 failures) |
| `./gradlew :gezgin-processor:test --tests dev.gezgin.processor.Faz8SpikeTest` | `0` |
| `./gradlew :gezgin-core:publishToMavenLocal :gezgin-mvi:publishToMavenLocal :gezgin-processor:publishToMavenLocal` | `0` |
| `./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer clean compileDebugKotlin --stacktrace` | `0` |
| `./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency navigation3` | `0` |
| `./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency lifecycle-viewmodel-navigation3` | `0` |
| `./gradlew :gezgin-core:compileKotlinJvm :gezgin-core:compileDebugKotlinAndroid :gezgin-mvi:compileKotlinJvm :gezgin-mvi:compileDebugKotlinAndroid :gezgin-processor:test` | `0` |

The Maven Local publish run executed `publishAndroidReleasePublicationToMavenLocal` and JVM/KMP
publications for both `gezgin-core` and `gezgin-mvi`, plus the processor Maven publication. The
independent consumer ran both `:kspDebugKotlin` and `:compileDebugKotlin` successfully.

## Dependency-resolution proof

- Root wrapper: Gradle `8.14`.
- Consumer wrapper: Gradle `9.4.1`.
- Consumer runtime insight resolves `androidx.navigation3` runtime/UI Android artifacts at `1.0.0`.
- Consumer runtime insight resolves `androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0`.
- A final scan of both insight reports found `NONE` for
  `org.jetbrains.androidx.navigation3` and
  `org.jetbrains.androidx.lifecycle:*navigation3`.
- `compatibility/zad-consumer` has no `includeBuild`, `projectDir`, project dependency, or composite
  source substitution. It consumes only the exact `dev.gezgin` Maven Local version `0.1.0-alpha01`.

## Final audit and concerns

`git diff --check` passes. The consumer's AGP 9 compatibility settings emit deprecation warnings
for the temporary built-in Kotlin opt-out and legacy variant API; they do not affect compilation.
The Android adapter's remaining end-to-end scene-runtime risk is documented above; no unresolved
Task 1 compilation, publication, or dependency-family compatibility blocker remains.
