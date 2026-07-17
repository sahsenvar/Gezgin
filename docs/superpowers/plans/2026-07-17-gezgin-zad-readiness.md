# Gezgin ZAD Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a tested, documented, locally publishable Gezgin compatibility artifact that satisfies the locked ZAD integration contract without changing ZAD source.

**Architecture:** Permanent navigation changes stay in gezgin-core; route-bound MVI binding and temporary migration chrome stay in gezgin-mvi; symbol association and generation stay in gezgin-processor. The Gezgin root keeps Gradle 8.14. A standalone Android consumer fixture inside Gezgin owns a separate Gradle 9.4.1 wrapper and proves the exact ZAD Kotlin, Koin KCP, AGP, JDK, and AndroidX Navigation 3 family only from pinned Maven Local artifacts, never through composite substitution. Work stops at verified local publication and a handoff contract for a separate ZAD repository/session.

**Tech Stack:** Kotlin Multiplatform 2.3.21, KSP 2.3.9, Compose Multiplatform, AndroidX Navigation 3 1.0.0, lifecycle Navigation 3 2.10.0, JetBrains desktop Navigation 3 artifacts, Koin 4.2.2 KCP, AGP 9.2.1 consumer fixture, JDK 21, KotlinPoet, Kotlin Compile Testing, Gradle Maven Publish.

## Global constraints

- [ ] Work only in the Gezgin repository on codex/zad-integration-readiness.
- [ ] Do not edit or execute source changes in a ZAD checkout. compatibility/zad-consumer is a Gezgin-owned compatibility fixture.
- [ ] Keep the Gezgin root wrapper on Gradle 8.14. Give compatibility/zad-consumer its own Gradle 9.4.1 wrapper and run every consumer command through that wrapper.
- [ ] Never use includeBuild, composite substitution, projectDir, or another source dependency for the consumer. Publish the exact Gezgin artifact version to Maven Local first, then resolve that version from the consumer.
- [ ] Preserve Fragment interop as screen-only; add no DialogFragment or BottomSheetDialogFragment support.
- [ ] Treat existing real @FragmentScreen process-death support as complete. Keep regression checks; do not create a second cold-process-death implementation.
- [ ] Do not add a distributed graph registry, multiple stacks, generic Throwable serialization, step-count back APIs, deep-link dispatch, or permanent chrome/container APIs.
- [ ] Keep @TopBar and @BottomBar in gezgin-mvi and isolate their models/codegen for later removal.
- [ ] Use red-green-refactor sequencing. Every behavior task begins with the focused failing proof listed below.
- [ ] Preserve the existing rememberNavigator positional and trailing-lambda call shapes while adding restoreKey.
- [ ] Fail processor compilation on ambiguous or conflicting bindings; diagnostics name the route and declaration.
- [ ] Do not commit unless a later execution session receives separate authorization.

---

## Task 1: Add a real ZAD-shaped consumer and resolve the Kotlin/KSP/Nav3 boundary

**Files**

- Modify: gradle/libs.versions.toml
- Modify: gezgin-core/build.gradle.kts
- Modify: gezgin-mvi/build.gradle.kts
- Modify: gezgin-processor/build.gradle.kts
- Create: compatibility/zad-consumer/settings.gradle.kts
- Create: compatibility/zad-consumer/build.gradle.kts
- Create: compatibility/zad-consumer/gradle.properties
- Create: compatibility/zad-consumer/gradlew
- Create: compatibility/zad-consumer/gradlew.bat
- Create: compatibility/zad-consumer/gradle/wrapper/gradle-wrapper.jar
- Create: compatibility/zad-consumer/gradle/wrapper/gradle-wrapper.properties
- Create: compatibility/zad-consumer/src/main/AndroidManifest.xml
- Create: compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityGraph.kt
- Create: compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityScreen.kt
- Create: compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityViewModel.kt
- Test: gezgin-processor/src/test/kotlin/dev/gezgin/processor/Faz8SpikeTest.kt

### Red: establish the consumer failure

- [ ] Create an independent Gradle 9.4.1 build under compatibility/zad-consumer with google(), mavenCentral(), and mavenLocal().
- [ ] Keep the root Gradle 8.14 wrapper unchanged and generate/commit the consumer's independent Gradle 9.4.1 wrapper files.
- [ ] Do not add includeBuild, composite substitution, projectDir, or any source dependency. Pin dev.gezgin:gezgin-core, dev.gezgin:gezgin-mvi, and dev.gezgin:gezgin-processor to one exact Maven Local artifact version.
- [ ] Pin Kotlin/serialization/Compose 2.3.21, KSP 2.3.9, AGP 9.2.1, Koin compiler plugin 1.0.1, Koin 4.2.2, compile/target SDK 37, min SDK 26, and Java/Kotlin toolchain 21.
- [ ] Apply io.insert-koin.compiler.plugin. Do not add the Koin KSP compiler.
- [ ] Add AndroidX navigation3-runtime:1.0.0, navigation3-ui:1.0.0, and lifecycle-viewmodel-navigation3:2.10.0.
- [ ] Add a serializable route, @Screen, and real @KoinViewModel with @InjectedParam route.
- [ ] Run and preserve the first compatibility-boundary failure:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:publishToMavenLocal :gezgin-mvi:publishToMavenLocal :gezgin-processor:publishToMavenLocal
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer clean compileDebugKotlin --stacktrace
~~~

- [ ] If the failure is fixture syntax rather than library compatibility, fix only the fixture and rerun until it reaches the Kotlin/KSP or Navigation 3 mismatch.

### Green: align Gezgin by platform

- [ ] Set the Gezgin Kotlin family to 2.3.21 and KSP to 2.3.9.
- [ ] Define separate catalog aliases for AndroidX Navigation 3 runtime/UI 1.0.0, AndroidX lifecycle Navigation 3 2.10.0, and the JetBrains UI/lifecycle artifacts retained for Desktop.
- [ ] In gezgin-core, export AndroidX UI/lifecycle from androidMain and JetBrains UI/lifecycle from jvmMain. Keep only the minimum shared compile surface in commonMain.
- [ ] Apply the same Android/JVM lifecycle split in gezgin-mvi.
- [ ] Update processor compile-test dependencies for Kotlin 2.3.21/KSP 2.3.9 without adding a production dependency on gezgin-mvi.
- [ ] Do not raise Gezgin's own Android compile/min SDK unless compilation proves it is required; AGP 9/SDK 37 belongs to the consumer fixture.

### Verify

- [ ] Run:

~~~bash
./gradlew :gezgin-processor:test --tests dev.gezgin.processor.Faz8SpikeTest
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:publishToMavenLocal :gezgin-mvi:publishToMavenLocal :gezgin-processor:publishToMavenLocal
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer clean compileDebugKotlin --stacktrace
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency navigation3
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency lifecycle-viewmodel-navigation3
~~~

- [ ] Require AndroidX 1.0.0/2.10.0 in Android runtime resolution and no org.jetbrains.androidx.navigation3 or JetBrains lifecycle Navigation 3 runtime artifact.
- [ ] Run:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:compileKotlinJvm :gezgin-core:compileDebugKotlinAndroid :gezgin-mvi:compileKotlinJvm :gezgin-mvi:compileDebugKotlinAndroid :gezgin-processor:test
~~~

---

## Task 2: Namespace saved snapshots and Android holder identity with restoreKey

**Files**

- Modify: gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/RememberNavigator.kt
- Modify: gezgin-core/src/androidMain/kotlin/dev/gezgin/core/compose/RememberNavigator.android.kt
- Modify: gezgin-core/src/jvmMain/kotlin/dev/gezgin/core/compose/RememberNavigator.jvm.kt
- Modify: gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/RememberNavigatorSaverTest.kt
- Modify: gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/NavigatorIdentityRestoreTest.kt
- Create: gezgin-core/src/androidUnitTest/kotlin/dev/gezgin/core/compose/RememberNavigatorAndroidIdentityTest.kt
- Modify: gezgin-core/build.gradle.kts

### Red: prove namespace semantics

- [ ] Add tests proving the same restoreKey restores a pushed stack and a changed restoreKey starts at the supplied start route.
- [ ] Add JVM coverage proving saver/navigator identity resets only when restoreKey changes.
- [ ] Configure androidUnitTest with the existing Compose/ViewModel stack and Robolectric.
- [ ] Add an Android test with a retained ViewModelStoreOwner and saved-state registry proving:
  - same owner plus same restoreKey returns the same holder/navigator across recreation;
  - same owner plus changed restoreKey returns a different holder/navigator;
  - the changed key does not adopt the old key's snapshot.
- [ ] Run and confirm the focused tests fail because the keyed API does not exist:

~~~bash
./gradlew :gezgin-core:jvmTest --tests '*RememberNavigator*' --tests '*NavigatorIdentityRestoreTest*'
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:testDebugUnitTest --tests '*RememberNavigatorAndroidIdentityTest*'
~~~

### Green: add a source-compatible keyed overload

- [ ] Keep the current overload unchanged:

~~~kotlin
@Composable
fun rememberNavigator(
    start: Route,
    topology: GezginTopology,
    json: Json,
    onRootBack: () -> Unit = platformDefaultRootBack(),
): RawNavigator
~~~

- [ ] Add the keyed overload:

~~~kotlin
@Composable
fun rememberNavigator(
    start: Route,
    topology: GezginTopology,
    json: Json,
    restoreKey: String,
    onRootBack: () -> Unit = platformDefaultRootBack(),
): RawNavigator
~~~

- [ ] Delegate the old overload to one stable legacy namespace and reject blank restoreKey with a message containing rememberNavigator and restoreKey.
- [ ] Add restoreKey to rememberRawNavigatorInstance expect/actual declarations.
- [ ] Preserve the Android per-call-site UUID token so two navigators using the same business key in one owner remain distinct.
- [ ] Regenerate that token when restoreKey changes, and compose the ViewModel holder key from restoreKey plus the call-site token.
- [ ] Key the Android saved snapshot by restoreKey. A changed session/mode key must create a new holder and fresh adoptChecked state.
- [ ] Key Desktop saver creation and rememberSaveable by restoreKey.

### Verify

- [ ] Run:

~~~bash
./gradlew :gezgin-core:jvmTest --tests '*RememberNavigator*' --tests '*NavigatorIdentityRestoreTest*'
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:testDebugUnitTest --tests '*RememberNavigatorAndroidIdentityTest*'
./gradlew :gezgin-core:jvmTest --tests '*SavedStateTest*' --tests '*ResultBusTest*' --tests '*NavResultTest*'
~~~

- [ ] Confirm no Fragment interop production file changed in this task.

---

## Task 3: Make @Screen repeatable and replace inferred effects with @EffectHandler(route)

**Files**

- Modify: gezgin-core/src/commonMain/kotlin/dev/gezgin/core/annotation/KindAnnotations.kt
- Create: gezgin-mvi/src/commonMain/kotlin/dev/gezgin/mvi/annotation/EffectHandler.kt
- Modify: gezgin-mvi/src/commonMain/kotlin/dev/gezgin/mvi/annotation/ScreenEffect.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModel.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModelReader.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/ViewModelModel.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/ViewModelModelReader.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/MviDump.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/fixtures/MviSource.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviModelReaderTest.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/ValidationTest.kt

### Red: model and diagnostics

- [ ] Add a fixture where one composable has @Screen(RouteA::class) and @Screen(RouteB::class), with separate ViewModels and compatible State/Intent types. Expect two entry models.
- [ ] Add @EffectHandler(RouteA::class) and @EffectHandler(RouteB::class) using different Effect and typed Navigator types. Expect route-local association.
- [ ] Add failures for duplicate explicit handlers, handler route with no screen, per-route State mismatch, per-route Intent mismatch, Effect mismatch, zero-match legacy @ScreenEffect, multi-match legacy @ScreenEffect, and explicit/legacy overlap.
- [ ] Require every diagnostic to name the route and conflicting function.
- [ ] Run and confirm red:

~~~bash
./gradlew :gezgin-processor:test --tests dev.gezgin.processor.MviModelReaderTest --tests dev.gezgin.processor.ValidationTest
~~~

### Green: explicit, repeatable association

- [ ] Add Kotlin @Repeatable to @Screen without changing its target or retention.
- [ ] Add repeatable @EffectHandler(val route: KClass<out Route>) with function target and binary retention.
- [ ] Keep no-argument @ScreenEffect source-compatible, mark it deprecated, and point its replacement to @EffectHandler(Route::class).
- [ ] Keep processor recognition by annotation FQN strings; do not add a processor production dependency on gezgin-mvi.
- [ ] Replace the one-function/first-annotation assumption: create one entry candidate for every @Screen(route) while retaining the shared composable symbol.
- [ ] Index ViewModels and explicit handlers by route.
- [ ] Validate State and Intent independently for every route. Permit route-specific Effect and Navigator types to differ.
- [ ] Resolve legacy @ScreenEffect only when its Flow Effect maps to exactly one unoccupied route.
- [ ] Fail on zero matches, several matches, duplicate handlers, or legacy/explicit conflict.
- [ ] Preserve current same-package graph enforcement.

### Verify

- [ ] Run:

~~~bash
./gradlew :gezgin-processor:test --tests dev.gezgin.processor.MviModelReaderTest --tests dev.gezgin.processor.ValidationTest
./gradlew :gezgin-processor:test
~~~

- [ ] Keep one compile fixture proving an unambiguous legacy @ScreenEffect still compiles with a deprecation warning.

---

## Task 4: Generate route-specific entries and temporary MVI chrome

**Files**

- Create: gezgin-mvi/src/commonMain/kotlin/dev/gezgin/mvi/annotation/ScreenChrome.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModel.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModelReader.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/MviEntryCodegen.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/MviDump.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/fixtures/MviCodegenSource.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviEntryCodegenTest.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviModelReaderTest.kt
- Modify: compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityGraph.kt
- Modify: compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityScreen.kt
- Modify: compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityViewModel.kt

### Red: route-local codegen and wrapper shape

- [ ] Add a golden test for one shared composable bound to RouteA and RouteB. Expect two uniquely named provide-entry functions with separate ViewModels, handlers, and typed navigators.
- [ ] Give RouteA and RouteB different Effect and Navigator types and assert imports/calls do not cross routes.
- [ ] Add golden cases for top+content+bottom, top-only, bottom-only, and content-only.
- [ ] Assert top/content/bottom order, content Modifier.fillMaxWidth().weight(1f), and ColumnScope invocation.
- [ ] Assert IME observation/imports exist only when a bottom bar exists.
- [ ] Add failures for duplicate top bars, duplicate bottom bars, unknown routes, and provider State/Intent mismatch.
- [ ] Run and confirm red:

~~~bash
./gradlew :gezgin-processor:test --tests dev.gezgin.processor.MviEntryCodegenTest --tests dev.gezgin.processor.MviModelReaderTest
~~~

### Green: isolated temporary chrome

- [ ] Add repeatable @TopBar(route) and @BottomBar(route) to gezgin-mvi/.../annotation/ScreenChrome.kt.
- [ ] Document both as migration-only route bindings, not permanent container contracts.
- [ ] Add optional route-bound top/bottom provider symbols to the MVI entry model and resolve them by explicit route.
- [ ] Generate a route-unique entry for every repeated @Screen occurrence.
- [ ] Generate this MVI-only shape:

~~~kotlin
Column {
    RouteTopBar(state, viewModel::onIntent)
    Column(Modifier.fillMaxWidth().weight(1f)) {
        SharedScreen(state, viewModel::onIntent)
    }
    if (!imeVisible) {
        RouteBottomBar(state, viewModel::onIntent)
    }
}
~~~

- [ ] Omit missing providers. Compute WindowInsets.ime visibility only when a bottom bar is present.
- [ ] Keep non-MVI entry generation unchanged.
- [ ] Use the same collected state and viewModel::onIntent for screen and chrome.
- [ ] Keep typed navigators in handlers; generated Koin ViewModels receive @InjectedParam route but no navigator.

### Consumer proof and verification

- [ ] Expand the consumer fixture to two routes, one repeated screen, separate @KoinViewModel classes, compatible shared State/Intent, different Effects, route-bound @EffectHandler functions, and one @TopBar/@BottomBar pair.
- [ ] Run:

~~~bash
./gradlew :gezgin-processor:test --tests dev.gezgin.processor.MviEntryCodegenTest --tests dev.gezgin.processor.MviModelReaderTest
./gradlew :gezgin-processor:test :gezgin-mvi:jvmTest
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:publishToMavenLocal :gezgin-mvi:publishToMavenLocal :gezgin-processor:publishToMavenLocal
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer clean compileDebugKotlin --stacktrace
~~~

- [ ] Inspect generated fixture source and confirm a typed or raw navigator is never passed into either ViewModel constructor.

---

## Task 5: Add BottomSheetContract.sheetGesturesEnabled and revise no-back safety

**Files**

- Modify: gezgin-core/src/commonMain/kotlin/dev/gezgin/core/Contracts.kt
- Modify: gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/EntryAdapter.kt
- Modify: gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/BottomSheetScene.kt
- Modify: gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/GezginBottomSheetContractTest.kt
- Modify: gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/GezginBottomSheetDismissTest.kt
- Modify: gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/GezginEntryMetadataTest.kt
- Modify: gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/GezginModalRootGuardTest.kt
- Modify: gezgin-core/src/jvmTest/kotlin/dev/gezgin/core/compose/GezginBottomSheetSceneTest.kt
- Modify: gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModelReader.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/EntryCodegenTest.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/ValidationTest.kt
- Modify: gezgin-processor/src/test/kotlin/dev/gezgin/processor/fixtures/EntrySource.kt

### Red: contract, metadata, scene, and guards

- [ ] Test default sheetGesturesEnabled == true.
- [ ] Test metadata/equality changes when only sheetGesturesEnabled differs.
- [ ] Test true and false values reach Material3 ModalBottomSheet directly.
- [ ] Add no-back combinations:
  - default contract fails;
  - back=false with gestures=true fails;
  - back=true with gestures=false fails;
  - back=false with gestures=false passes;
  - outside dismissal remains independently represented.
- [ ] Add processor coverage: @NoBack bottom sheet without BottomSheetContract fails statically; a contract-bearing route reaches the runtime value guard.
- [ ] Run and confirm red:

~~~bash
./gradlew :gezgin-core:jvmTest --tests '*BottomSheet*' --tests '*ModalRootGuardTest*'
./gradlew :gezgin-processor:test --tests dev.gezgin.processor.EntryCodegenTest --tests dev.gezgin.processor.ValidationTest
~~~

### Green: propagate all dismissal dimensions

- [ ] Add exactly:

~~~kotlin
val sheetGesturesEnabled: Boolean get() = true
~~~

- [ ] Add the property to GezginBottomSheetProps, contract resolution, metadata, equality, and KDoc.
- [ ] Pass it as ModalBottomSheet(sheetGesturesEnabled = props.sheetGesturesEnabled).
- [ ] Replace unconditional processor SC7 behavior with:
  - no explicit contract over @NoBack fails statically because defaults are unsafe;
  - a contract-bearing route compiles and is checked from resolved runtime values.
- [ ] At runtime, require dismissOnBackPress == false and sheetGesturesEnabled == false for a no-back bottom sheet.
- [ ] Keep dismissOnClickOutside independent. Document that ZAD preventDismiss sets all three values false.

### Verify

- [ ] Run:

~~~bash
./gradlew :gezgin-core:jvmTest --tests '*BottomSheet*' --tests '*ModalRootGuardTest*' --tests '*EntryMetadataTest*'
./gradlew :gezgin-processor:test --tests dev.gezgin.processor.EntryCodegenTest --tests dev.gezgin.processor.ValidationTest
./gradlew :gezgin-core:jvmTest --tests '*Dialog*' --tests '*OnBackTest*' --tests '*StackedOverlay*'
~~~

- [ ] Confirm no Fragment modal annotation, adapter, class, or dependency was added.

---

## Task 6: Update samples to prove strict MVI and the new contracts

**Files**

- Modify: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/nav/ShopGraph.kt
- Modify: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedScreen.kt
- Modify: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedViewModel.kt
- Modify: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedEffect.kt
- Modify: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedEffectHandler.kt
- Create: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed/FeedChrome.kt
- Create: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_featured_feed/FeaturedFeedViewModel.kt
- Create: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_featured_feed/FeaturedFeedEffect.kt
- Create: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_featured_feed/FeaturedFeedEffectHandler.kt
- Modify: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_order_placed/OrderPlacedScreen.kt
- Modify: sample/shopr/src/test/kotlin/dev/gezgin/sample/shopr/ShopGraphTopologyTest.kt

### Red

- [ ] Add serializable HomeGraph.FeaturedFeed and a reachable edge without changing the existing start destination.
- [ ] Test that Feed and FeaturedFeed receive distinct generated entries/navigators.
- [ ] Test that the sample non-dismissible bottom sheet resolves back=false, outside=false, gestures=false.
- [ ] Run and confirm red:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :sample:shopr:testDebugUnitTest --tests dev.gezgin.sample.shopr.ShopGraphTopologyTest
~~~

### Green

- [ ] Bind FeedScreen to both HomeGraph.Feed and HomeGraph.FeaturedFeed.
- [ ] Share FeedUiState and FeedIntent.
- [ ] Remove FeedNavigator from FeedViewModel. Emit FeedEffect.NavigateToCatalog from handle/onIntent.
- [ ] Annotate FeedEffectHandler with @EffectHandler(HomeGraph.Feed::class), inject the generated FeedNavigator there, and navigate there.
- [ ] Add FeaturedFeedViewModel with no navigator field and a separate FeaturedFeedEffect.
- [ ] Add @EffectHandler(HomeGraph.FeaturedFeed::class) with a distinct navigation target and typed navigator.
- [ ] Add route-bound @TopBar and @BottomBar for HomeGraph.Feed in FeedChrome.kt.
- [ ] Keep the shared screen body in ColumnScope.
- [ ] Override the sample prevent-dismiss sheet with getter values false for back, outside, and gestures.

### Verify

- [ ] Run:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :sample:shopr:test :sample:shopr:compileDebugKotlin
./gradlew :sample:app:compileDebugKotlin :sample:feature:auth:compileDebugKotlin :sample:feature:home:compileDebugKotlin :sample:feature:profile:compileDebugKotlin
~~~

- [ ] Confirm the strict-MVI demonstration ViewModels contain no RawNavigator or typed navigator field.

---

## Task 7: Refresh maintained usage, API dumps, and documentation

**Files**

- Modify: README.md
- Modify: README.tr.md
- Modify: sample/README.md
- Modify: docs/gezgin-design.md
- Modify: docs/gezgin-design-notes.md
- Modify: docs/gezgin-binder-location.md
- Modify: docs/gezgin-on-device-checklist.md
- Modify: sample/app/src/main/kotlin/dev/gezgin/sample/app/MainActivity.kt
- Modify: sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/MainActivity.kt
- Modify: maintained *EffectHandler.kt files under sample/feature and sample/shopr
- Modify: gezgin-core/api/android/gezgin-core.api
- Modify: gezgin-core/api/jvm/gezgin-core.api
- Modify: gezgin-mvi/api/android/gezgin-mvi.api
- Modify: gezgin-mvi/api/jvm/gezgin-mvi.api

### Migrate maintained examples

- [ ] Replace maintained sample @ScreenEffect usage with @EffectHandler(ExactRoute::class).
- [ ] Move navigation from paired sample ViewModels into route-bound handlers.
- [ ] Retain deprecated @ScreenEffect only in compatibility tests and a clearly marked compatibility paragraph.
- [ ] Require no maintained sample usage:

~~~bash
rg -n '@ScreenEffect|ScreenEffect' sample --glob '*.kt' --glob '*.md'
~~~

### Document the exact contracts

- [ ] Document the keyed rememberNavigator overload and same-key restore/changed-key fresh semantics.
- [ ] Document repeatable @Screen, route-bound @EffectHandler, shared State/Intent compatibility, and route-specific Effect/Navigator types.
- [ ] Document @TopBar/@BottomBar as temporary gezgin-mvi APIs with Column, weight, and IME behavior.
- [ ] Document sheetGesturesEnabled and the three-false prevent-dismiss mapping.
- [ ] State that Fragment interop remains screen-only and real @FragmentScreen process-death support is already complete.
- [ ] State that multiple stacks, deep-link dispatch, generic Throwable serialization, and Fragment modal interop are outside this artifact.
- [ ] Update maintained design docs only; historical plan/review records remain historical.

### API red/green

- [ ] Run apiCheck and inspect the intentional failures:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew apiCheck
~~~

- [ ] Regenerate and verify:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew apiDump
./gradlew apiCheck
~~~

- [ ] Core dumps must show repeatable Screen, keyed rememberNavigator, and sheetGesturesEnabled.
- [ ] MVI dumps must show EffectHandler, deprecated ScreenEffect, TopBar, and BottomBar on Android and JVM.
- [ ] No Fragment modal type may enter public API dumps.

### Verify maintained paths

- [ ] Run:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:check :gezgin-mvi:check :gezgin-processor:test :sample:shopr:check :sample:app:assembleDebug
~~~

- [ ] Search maintained docs for stale architecture recommendations:

~~~bash
rg -n '@ScreenEffect|Channel\.UNLIMITED|StartUpHost' README.md README.tr.md sample/README.md docs/gezgin-design.md docs/gezgin-design-notes.md docs/gezgin-binder-location.md
~~~

- [ ] Permit @ScreenEffect only in the deprecated compatibility explanation; require no StartUpHost or unbounded-bus recommendation.

---

## Task 8: Full verification, local publication, and ZAD handoff

**Files**

- Create: docs/gezgin-zad-readiness-handoff.md
- Modify only when verification proves drift: files already listed in Tasks 1–7

### Full regression gates

- [ ] Confirm worktree scope and formatting:

~~~bash
git status --short --branch
git diff --check
git diff --stat
~~~

- [ ] Run focused and repository-wide verification:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-processor:test :gezgin-core:allTests :gezgin-mvi:allTests apiCheck
./gradlew :gezgin-core:publishToMavenLocal :gezgin-mvi:publishToMavenLocal :gezgin-processor:publishToMavenLocal
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer clean compileDebugKotlin --stacktrace
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency navigation3
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer dependencyInsight --configuration debugRuntimeClasspath --dependency lifecycle-viewmodel-navigation3
./gradlew clean check
./gradlew :sample:app:assembleDebug :sample:shopr:assembleDebug
~~~

- [ ] Save dependency reports under compatibility/zad-consumer/build/reports/zad-readiness and require no JetBrains Navigation 3 UI/lifecycle runtime artifact on Android.

### Existing Fragment PD regression only

- [ ] Run existing non-device Fragment regressions:

~~~bash
./gradlew :gezgin-processor:test --tests '*Fragment*'
./gradlew :gezgin-core:jvmTest --tests '*Fragment*'
~~~

- [ ] When the configured device/harness is available, run the existing script unchanged:

~~~bash
./maestro/run-15-fragment-pd.sh
~~~

- [ ] Record this as regression evidence. If it passes, do not introduce another cold-process-death implementation task.

### Boundary searches

- [ ] Run:

~~~bash
rg -n 'DialogFragment|BottomSheetDialogFragment' gezgin-core/src gezgin-mvi/src gezgin-processor/src
rg -n '@ScreenEffect' sample --glob '*.kt'
rg -n 'Channel\.UNLIMITED' compatibility/zad-consumer sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_feed sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/screen_featured_feed
~~~

- [ ] Require no new Fragment modal implementation, no maintained sample @ScreenEffect, and no unbounded bus in the strict-MVI proof.

### Publish and consume the artifact

- [ ] Publish required modules locally:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./gradlew :gezgin-core:publishToMavenLocal :gezgin-mvi:publishToMavenLocal :gezgin-processor:publishToMavenLocal
~~~

- [ ] Resolve the exact published Maven Local version with the consumer's Gradle 9.4.1 wrapper; composite substitution is forbidden:

~~~bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
test -d "$ANDROID_HOME"
./compatibility/zad-consumer/gradlew -p compatibility/zad-consumer clean compileDebugKotlin --refresh-dependencies --stacktrace
~~~

- [ ] Record SHA-256 values for published POMs and primary JAR/AAR/KMP metadata with shasum -a 256.

### Commit checkpoints, write the handoff, and stop

- [ ] After all implementation/source validations above are green, stop and request explicit permission for the implementation/source commit. Do not commit without that permission.
- [ ] After permission is granted, create the implementation/source commit and capture its immutable SHA with `git rev-parse HEAD`.
- [ ] Write docs/gezgin-zad-readiness-handoff.md against that source commit. The handoff must not identify an uncommitted or moving source tree.

- [ ] In docs/gezgin-zad-readiness-handoff.md record:
  - the immutable implementation/source commit SHA and the pre-handoff clean/dirty status;
  - exact Maven coordinates and SHA-256 values;
  - Kotlin 2.3.21, KSP 2.3.9, Koin 4.2.2 KCP/plugin 1.0.1, AGP 9.2.1, JDK 21, and AndroidX Navigation 3 matrix;
  - every verification command and result;
  - restoreKey same-key/change-key semantics;
  - deprecated @ScreenEffect ambiguity rules;
  - temporary gezgin-mvi chrome removal boundary;
  - confirmation that Fragment interop remains screen-only and existing true PD support was regression-tested, not reimplemented;
  - ZAD root-swap blockers: complete Fragment-to-serializable-Route mapping and zero production Fragment modal APIs;
  - future ZAD baseline 8e02471e1 over origin 69142a1bd.
- [ ] Require no unresolved marker or stale root name:

~~~bash
git diff --check
pattern="$(printf '%s' 'TB' 'D|TO' 'DO|<' 'version>|<' 'commit>|StartUpHost')"
rg -n "$pattern" docs/gezgin-zad-readiness-handoff.md docs/gezgin-zad-root-integration-spec.md docs/superpowers/plans/2026-07-17-gezgin-zad-readiness.md
~~~

- [ ] Stop and request a second, separate permission for the handoff commit. Do not reuse the implementation/source commit permission.
- [ ] After the second permission is granted, create the handoff commit.
- [ ] Only after the handoff commit exists, run final clean-worktree verification:

~~~bash
git diff --check
git status --short --branch
test -z "$(git status --porcelain)"
~~~

- [ ] Stop after reporting the tested/publishable Gezgin artifact, immutable source commit, handoff commit, handoff path, and final clean-worktree result. Do not open or edit the ZAD repository.

## Future ZAD handoff boundary — not Gezgin implementation tasks

The later ZAD session starts only after Task 8 is green. It must use local baseline 8e02471e1, move all graph/route declarations into one package in core:navigation, keep feature bindings in feature modules, create App/AppViewModel/AppUiState/AppIntent/AppEffect/AppEffectBus/AppEffectHandler, and create Gezgin only in Ready(startRoute, restoreKey).

The later session must complete Fragment-to-serializable-Route mapping and convert production Fragment modals to zero before root swap. It then uses one stack, replaceTo tab teardown, strict Intent -> handleIntent -> effect -> EffectHandler -> typed navigator, and deletes the temporary FragmentNavigation adapter after its last consumer. MainFragment is removed without an @FragmentScreen wrapper. Sub-FragNav hosts hoist; ViewPager wizard leaves carry synchronized code/markdown/PR/commit debt markers. ZAD @SaveState is removed only after Gezgin navigation PD parity is proven.

The future App-level unexpected-error flow remains ZAD-owned: transient AppEffect.ShowUnexpectedError(Throwable), Crashlytics before coalescing, no CancellationException dialog, serializable UnexpectedErrorUi, non-dismissible UnexpectedErrorDialogRoute, one-back Kapat, one-back-then-snackbar Bildir, bounded AppEffectBus, and no Throwable in routes or saved state. The future session creates docs/technical-debt/unexpected-error-dialog.md and records report transport, two-step recovery, ViewPager hoist, temporary chrome, and deep-link debt.
