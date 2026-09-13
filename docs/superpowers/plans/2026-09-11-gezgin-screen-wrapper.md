# Gezgin Screen Wrapper and MVI De-opinionation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Gezgin's dictated MVI layer with application-defined `@ScreenWrapper` functions whose slots the processor fills, then delete `gezgin-mvi`.

**Architecture:** Three new `gezgin-core` annotations (`@ScreenWrapper`, `@ScreenSlot`, `@FilledBy`) let an application declare a wrapper composable with named slots and its own marker annotations. The processor discovers wrappers and markers (in-module by annotation, cross-module by package enumeration), collects per-route slot providers, matches provider signatures against slot function types by shallow unification, and emits one wrapper call per entry with explicit type arguments. Gezgin supplies the typed route and navigator to providers as closure-captured roles.

**Tech Stack:** Kotlin 2.3.21, KSP 2.3.9, KotlinPoet, kctfork (kotlin-compile-testing) for processor tests, Gradle 9.0.0, AGP 8.13.2, Compose Multiplatform 1.11.0.

**Spec:** `docs/superpowers/specs/2026-09-11-gezgin-screen-wrapper-design.md`

## Global Constraints

- Target release `0.3.0`; breaking, no deprecation window.
- JVM toolchain 17 on every module. Gradle daemon JDK is whatever the machine has; do not pin it.
- `gezgin-core` and `gezgin-processor` declare `explicitPublicApi()`. Every new public declaration needs KDoc with an `@author @sahsenvar` tag, or `:gezgin-core:checkPublicApiKDoc` fails.
- Binary Compatibility Validator runs on every non-ignored module. After any public API change run `./gradlew apiDump` and commit the `.api` diff.
- **Any new Kotlin-Android module must be added to `apiValidation.ignoredProjects` in the root `build.gradle.kts`.** BCV otherwise queries `KotlinAndroidProjectExtension.getTarget` during plugin application and fails the entire configuration phase with `Future was not completed yet` — including for unrelated modules.
- The processor must not gain a compile dependency on `gezgin-mvi` or on any application type. All annotation and type references are string FQNs.
- Run `./gradlew spotlessApply` before every commit; ktfmt formatting is enforced by `spotlessCheck`.
- Processor tests run under kctfork without the Compose compiler plugin. `CompileHarness` already passes `-Xlambdas=class -Xsam-conversions=class`; keep using it rather than constructing `KotlinCompilation` by hand.
- Error codes use the `SW` prefix, emitted as `logger.error("[SWn] message")` through `EntryModelReader.error(code, message)`'s pattern.
- `getDeclarationsFromPackage` is `@KspExperimental`; opt in at the single call site only.

---

## File Structure

**New in `gezgin-core`:**
- `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/annotation/WrapperAnnotations.kt` — `@ScreenWrapper`, `@ScreenSlot`, `@FilledBy`.

**New in `gezgin-processor`:**
- `wrapper/WrapperModel.kt` — `SlotType`, `WrapperSlotModel`, `WrapperModel`, `SlotMarkerModel`, `SlotProviderModel`, `ProviderParam`, `ProviderRole`, `WrapperBindingModel`.
- `wrapper/WrapperModelReader.kt` — discovery of wrappers and slot markers.
- `wrapper/SlotProviderReader.kt` — discovery of per-route providers.
- `wrapper/SlotUnifier.kt` — shallow unification of a `SlotType` against a concrete `TypeName`.
- `wrapper/WrapperBinder.kt` — candidate selection, slot filling, type-argument resolution.
- `wrapper/WrapperDump.kt` — deterministic text dump behind `gezgin.dumpWrapper`.
- `codegen/WrapperEntryCodegen.kt` — emits `provideXEntry` with the wrapper call.

**Deleted:**
- The whole `gezgin-mvi` module, `gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/`, `codegen/MviEntryCodegen.kt`, and `MviEntryModel`/`MviExtraParam`/`MviChromeProviderModel` from `entry/EntryModel.kt`.

**Modified:**
- `entry/EntryModel.kt` — `EntryFunctionModel.mvi: MviEntryModel?` becomes `wrapper: WrapperBindingModel?`.
- `entry/EntryModelReader.kt` — MVI-mode branch deleted; every entry is read the same way, with wrapper binding attached afterwards.
- `GezginProcessor.kt` — MVI readers replaced by the wrapper pipeline.
- `codegen/EntryCodegen.kt` — becomes the no-wrapper path only.

---

## Task 1: Core annotations

**Files:**
- Create: `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/annotation/WrapperAnnotations.kt`
- Modify: `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/annotation/KindAnnotations.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/WrapperAnnotationsTest.kt`

**Interfaces:**
- Produces: `dev.gezgin.core.annotation.ScreenWrapper`, `dev.gezgin.core.annotation.ScreenSlot`, `dev.gezgin.core.annotation.FilledBy(marker: KClass<out Annotation>)`. `dev.gezgin.core.annotation.Screen` additionally carries `@ScreenSlot`.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperAnnotationsTest {

  @Test
  fun `an application can declare a slot marker and a wrapper`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "AppVocabulary.kt",
          """
          package app

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.FilledBy
          import dev.gezgin.core.annotation.Screen
          import dev.gezgin.core.annotation.ScreenSlot
          import dev.gezgin.core.annotation.ScreenWrapper
          import kotlin.reflect.KClass

          @ScreenSlot
          @Repeatable
          annotation class TopBar(val route: KClass<out Route>)

          @ScreenWrapper
          fun appRoot(
            @FilledBy(TopBar::class) topBar: () -> Unit = {},
            @FilledBy(Screen::class) content: () -> Unit,
          ) {
            topBar()
            content()
          }
          """
            .trimIndent(),
        )
      )

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperAnnotationsTest" --console=plain`
Expected: FAIL — `Unresolved reference: ScreenWrapper`.

- [ ] **Step 3: Create the annotations**

```kotlin
package dev.gezgin.core.annotation

import kotlin.reflect.KClass

/**
 * Marks a composable that wraps screen content. The processor fills each parameter annotated with
 * [FilledBy] from the declarations carrying that slot marker, and calls this function in place of
 * the screen content in the generated entry.
 *
 * A wrapper must declare exactly one slot filled by [Screen]; that slot receives the screen body.
 * Every other slot is optional when the parameter has a Kotlin default.
 *
 * @author @sahsenvar
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class ScreenWrapper

/**
 * Marks an application-defined annotation as a slot marker. The marked annotation must declare
 * exactly one `KClass<out Route>` parameter naming the route its providers serve; any further
 * parameters are ignored by Gezgin. Mark it `@Repeatable` to bind one provider to several routes.
 *
 * @author @sahsenvar
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class ScreenSlot

/**
 * Binds one [ScreenWrapper] parameter to the slot marker whose providers fill it.
 *
 * @property marker the [ScreenSlot]-annotated annotation whose providers fill this parameter
 * @author @sahsenvar
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
public annotation class FilledBy(public val marker: KClass<out Annotation>)
```

- [ ] **Step 4: Add `@ScreenSlot` to `@Screen`**

In `KindAnnotations.kt`, change the `Screen` declaration to:

```kotlin
@ScreenSlot
@Target(AnnotationTarget.FUNCTION)
@Repeatable
public annotation class Screen(val route: KClass<out Route>)
```

Leave `Dialog`, `BottomSheet` and `FullscreenModal` unannotated for now; Task 9 adds `@ScreenSlot` to each once the binder handles all kinds.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperAnnotationsTest" --console=plain`
Expected: PASS.

- [ ] **Step 6: Refresh the API dump and commit**

```bash
./gradlew apiDump spotlessApply
git add gezgin-core/ gezgin-processor/src/test/kotlin/dev/gezgin/processor/WrapperAnnotationsTest.kt
git commit -m "feat(core): add @ScreenWrapper, @ScreenSlot and @FilledBy"
```

---

## Task 2: Wrapper model types

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/wrapper/WrapperModel.kt`
- Test: none of its own — Task 3 is the first consumer and tests it through behaviour.

**Interfaces:**
- Produces: the types every later task refers to. Exact declarations below; do not rename.

- [ ] **Step 1: Create the model file**

```kotlin
package dev.gezgin.processor.wrapper

import com.squareup.kotlinpoet.TypeName

/**
 * A slot's declared type, in the only shape [SlotUnifier] understands: a concrete type, one of the
 * wrapper's type parameters, or a function type over those.
 */
internal sealed interface SlotType {
  data class Concrete(val fq: String, val typeName: TypeName) : SlotType

  data class Variable(val name: String) : SlotType

  data class Lambda(val parameters: List<SlotType>, val returnType: SlotType) : SlotType
}

/** An application annotation carrying `@ScreenSlot`. */
internal data class SlotMarkerModel(val annotationFq: String, val routeParamName: String)

/** One `@FilledBy` parameter of a `@ScreenWrapper` function. */
internal data class WrapperSlotModel(
  val parameterName: String,
  val markerFq: String,
  val hasDefault: Boolean,
  val receiver: SlotType?,
  val parameters: List<SlotType>,
)

/** One `@ScreenWrapper` function. */
internal data class WrapperModel(
  val functionSimpleName: String,
  val packageName: String,
  val typeParameterNames: List<String>,
  val slots: List<WrapperSlotModel>,
) {
  val contentSlot: WrapperSlotModel?
    get() = slots.firstOrNull { it.markerFq == "dev.gezgin.core.annotation.Screen" }
}

/** A value Gezgin supplies to a provider parameter rather than matching it against a slot. */
internal enum class ProviderRole {
  ROUTE,
  NAVIGATOR,
  SHEET_CONTROLLER,
}

internal data class ProviderParam(val name: String, val typeName: TypeName)

internal data class ProviderRoleParam(val name: String, val role: ProviderRole)

/** One declaration annotated with a slot marker, resolved against one route. */
internal data class SlotProviderModel(
  val functionSimpleName: String,
  val packageName: String,
  val markerFq: String,
  val routeFq: String,
  val receiverTypeName: TypeName?,
  val slotParams: List<ProviderParam>,
  val roleParams: List<ProviderRoleParam>,
)

/** The wrapper chosen for one route, with every slot filled and every type argument bound. */
internal data class WrapperBindingModel(
  val wrapper: WrapperModel,
  /** In wrapper type-parameter declaration order. */
  val typeArguments: List<TypeName>,
  /** Wrapper parameter name to the provider that fills it. Omitted slots use their Kotlin default. */
  val filledSlots: Map<String, SlotProviderModel>,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :gezgin-processor:compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessApply
git add gezgin-processor/src/main/kotlin/dev/gezgin/processor/wrapper/WrapperModel.kt
git commit -m "feat(processor): add screen-wrapper model types"
```

---

## Task 3: Wrapper and slot-marker discovery

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/wrapper/WrapperModelReader.kt`
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/wrapper/WrapperDump.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/GezginProcessor.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/WrapperModelReaderTest.kt`

**Interfaces:**
- Consumes: `WrapperModel`, `SlotMarkerModel`, `SlotType` from Task 2.
- Produces: `WrapperModelReader(resolver, logger, options).read(): Pair<WrapperReadResult, Boolean>` where `WrapperReadResult(val wrappers: List<WrapperModel>, val markers: List<SlotMarkerModel>)`; and `dumpWrapperText(result: WrapperReadResult, providers: List<SlotProviderModel>, bindings: Map<String, WrapperBindingModel>): String`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.compileGezginModule
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperModelReaderTest {

  private val vocabulary =
    SourceFile.kotlin(
      "AppVocabulary.kt",
      """
      package app

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FilledBy
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.core.annotation.ScreenSlot
      import dev.gezgin.core.annotation.ScreenWrapper
      import kotlin.reflect.KClass

      @ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

      @ScreenWrapper
      fun appRoot(
        @FilledBy(TopBar::class) topBar: () -> Unit = {},
        @FilledBy(Screen::class) content: () -> Unit,
      ) {
        topBar()
        content()
      }
      """
        .trimIndent(),
    )

  @Test
  fun `in-module wrapper and marker are discovered`() {
    compileGezgin(vocabulary, kspArgs = mapOf("gezgin.dumpWrapper" to "true"))

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "wrapper app.appRoot")
    assertContains(dump, "slot topBar marker=app.TopBar default=true")
    assertContains(dump, "slot content marker=dev.gezgin.core.annotation.Screen default=false")
    assertContains(dump, "marker app.TopBar route=route")
  }

  @Test
  fun `a wrapper on the classpath is discovered through gezgin wrapperPackages`() {
    val lib = compileGezginModule(vocabulary)

    val feature =
      compileGezginModule(
        SourceFile.kotlin(
          "Feature.kt",
          """
          package feature

          object Placeholder
          """
            .trimIndent(),
        ),
        kspArgs =
          mapOf("gezgin.dumpWrapper" to "true", "gezgin.wrapperPackages" to "app"),
        extraClasspath = listOf(lib.outputDirectory),
      )

    assertTrue(feature.messages.none { false })
    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "wrapper app.appRoot")
    assertContains(dump, "marker app.TopBar route=route")
  }

  @Test
  fun `SW9 fires when a configured package yields nothing`() {
    val result =
      compileGezgin(vocabulary, kspArgs = mapOf("gezgin.wrapperPackages" to "does.not.exist"))

    assertContains(result.messages, "[SW9]")
    assertContains(result.messages, "does.not.exist")
  }

  @Test
  fun `SW3 fires when a slot marker has no route parameter`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "BadMarker.kt",
          """
          package app

          import dev.gezgin.core.annotation.ScreenSlot

          @ScreenSlot annotation class Broken(val name: String)
          """
            .trimIndent(),
        )
      )

    assertContains(result.messages, "[SW3]")
    assertContains(result.messages, "app.Broken")
  }

  @Test
  fun `SW1 fires when a wrapper has no Screen slot`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "NoContent.kt",
          """
          package app

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.FilledBy
          import dev.gezgin.core.annotation.ScreenSlot
          import dev.gezgin.core.annotation.ScreenWrapper
          import kotlin.reflect.KClass

          @ScreenSlot annotation class TopBar(val route: KClass<out Route>)

          @ScreenWrapper
          fun appRoot(@FilledBy(TopBar::class) topBar: () -> Unit) { topBar() }
          """
            .trimIndent(),
        )
      )

    assertContains(result.messages, "[SW1]")
    assertContains(result.messages, "app.appRoot")
  }

  @Test
  fun `SW2 fires when a FilledBy parameter is not a function type`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "BadSlot.kt",
          """
          package app

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.FilledBy
          import dev.gezgin.core.annotation.Screen
          import dev.gezgin.core.annotation.ScreenSlot
          import dev.gezgin.core.annotation.ScreenWrapper
          import kotlin.reflect.KClass

          @ScreenSlot annotation class TopBar(val route: KClass<out Route>)

          @ScreenWrapper
          fun appRoot(
            @FilledBy(TopBar::class) topBar: String,
            @FilledBy(Screen::class) content: () -> Unit,
          ) { content() }
          """
            .trimIndent(),
        )
      )

    assertContains(result.messages, "[SW2]")
    assertContains(result.messages, "topBar")
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperModelReaderTest" --console=plain`
Expected: FAIL — `GezginWrapperDump.txt` is null and no `[SWn]` messages appear.

- [ ] **Step 3: Implement the reader**

```kotlin
package dev.gezgin.processor.wrapper

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ksp.toTypeName

internal const val SCREEN_WRAPPER_FQ = "dev.gezgin.core.annotation.ScreenWrapper"
internal const val SCREEN_SLOT_FQ = "dev.gezgin.core.annotation.ScreenSlot"
internal const val FILLED_BY_FQ = "dev.gezgin.core.annotation.FilledBy"
internal const val SCREEN_FQ = "dev.gezgin.core.annotation.Screen"
internal const val ROUTE_FQ = "dev.gezgin.core.Route"

internal data class WrapperReadResult(
  val wrappers: List<WrapperModel>,
  val markers: List<SlotMarkerModel>,
)

/**
 * Discovers every `@ScreenWrapper` function and every `@ScreenSlot` annotation visible to this KSP
 * round. In-module declarations are found by annotation; declarations compiled into a dependency
 * are found by enumerating the packages named in `gezgin.wrapperPackages`, because KSP cannot
 * enumerate classpath declarations by annotation.
 */
internal class WrapperModelReader(
  private val resolver: Resolver,
  private val logger: KSPLogger,
  private val options: Map<String, String>,
) {

  private var ok = true

  fun read(): Pair<WrapperReadResult, Boolean> {
    val wrapperDecls = mutableListOf<KSFunctionDeclaration>()
    val markerDecls = mutableListOf<KSClassDeclaration>()

    resolver.getSymbolsWithAnnotation(SCREEN_WRAPPER_FQ).filterIsInstance<KSFunctionDeclaration>()
      .forEach { wrapperDecls += it }
    resolver.getSymbolsWithAnnotation(SCREEN_SLOT_FQ).filterIsInstance<KSClassDeclaration>()
      .forEach { markerDecls += it }

    configuredPackages().forEach { pkg ->
      val (pkgWrappers, pkgMarkers) = enumeratePackage(pkg)
      if (pkgWrappers.isEmpty() && pkgMarkers.isEmpty()) {
        error(
          "SW9",
          "gezgin.wrapperPackages names '$pkg' but it declares no @ScreenWrapper function and " +
            "no @ScreenSlot annotation; remove it or fix the package name",
        )
      }
      wrapperDecls += pkgWrappers
      markerDecls += pkgMarkers
    }

    // `@Screen` is itself a slot marker but lives in gezgin-core, always on the classpath.
    val screenMarker = resolver.getClassDeclarationByName(resolver.getKSNameFromString(SCREEN_FQ))
    if (screenMarker != null) markerDecls += screenMarker

    val markers =
      markerDecls
        .distinctBy { it.qualifiedName?.asString() }
        .mapNotNull(::readMarker)
        .sortedBy { it.annotationFq }
    val wrappers =
      wrapperDecls
        .distinctBy { "${it.packageName.asString()}.${it.simpleName.asString()}" }
        .map { readWrapper(it) }
        .sortedBy { "${it.packageName}.${it.functionSimpleName}" }

    return WrapperReadResult(wrappers, markers) to ok
  }

  private fun configuredPackages(): List<String> =
    options["gezgin.wrapperPackages"]
      ?.split(',')
      ?.map(String::trim)
      ?.filter(String::isNotEmpty)
      .orEmpty()

  @OptIn(KspExperimental::class)
  private fun enumeratePackage(
    pkg: String
  ): Pair<List<KSFunctionDeclaration>, List<KSClassDeclaration>> {
    val declarations = resolver.getDeclarationsFromPackage(pkg).toList()
    val wrappers =
      declarations.filterIsInstance<KSFunctionDeclaration>().filter { it.hasAnnotation(SCREEN_WRAPPER_FQ) }
    val markers =
      declarations.filterIsInstance<KSClassDeclaration>().filter { it.hasAnnotation(SCREEN_SLOT_FQ) }
    return wrappers to markers
  }

  private fun readMarker(declaration: KSClassDeclaration): SlotMarkerModel? {
    val fq = declaration.qualifiedName?.asString() ?: return null
    val routeParams =
      declaration.primaryConstructor
        ?.parameters
        .orEmpty()
        .filter { it.type.resolve().isRouteKClass() }
    if (routeParams.size != 1) {
      error(
        "SW3",
        "@ScreenSlot annotation $fq must declare exactly one KClass<out Route> parameter " +
          "(found ${routeParams.size}); other parameters are allowed and ignored",
      )
      return null
    }
    return SlotMarkerModel(fq, routeParams.single().name!!.asString())
  }

  private fun readWrapper(declaration: KSFunctionDeclaration): WrapperModel {
    val packageName = declaration.packageName.asString()
    val simpleName = declaration.simpleName.asString()
    val typeParameterNames = declaration.typeParameters.map { it.name.asString() }
    val slots =
      declaration.parameters.mapNotNull { parameter ->
        val markerFq = parameter.filledByMarkerFq() ?: return@mapNotNull null
        val type = parameter.type.resolve()
        if (!type.isFunctionType) {
          error(
            "SW2",
            "@FilledBy parameter '${parameter.name?.asString()}' of $packageName.$simpleName " +
              "must be a function type; it is ${type.declaration.qualifiedName?.asString()}",
          )
          return@mapNotNull null
        }
        WrapperSlotModel(
          parameterName = parameter.name!!.asString(),
          markerFq = markerFq,
          hasDefault = parameter.hasDefault,
          receiver = type.functionReceiver()?.toSlotType(typeParameterNames),
          parameters = type.functionParameters().map { it.toSlotType(typeParameterNames) },
        )
      }
    val model = WrapperModel(simpleName, packageName, typeParameterNames, slots)
    if (model.contentSlot == null) {
      error(
        "SW1",
        "@ScreenWrapper $packageName.$simpleName declares no @FilledBy(Screen::class) slot; " +
          "one slot must receive the screen content",
      )
    }
    return model
  }

  private fun error(code: String, message: String) {
    logger.error("[$code] $message")
    ok = false
  }
}
```

Add these helpers in the same file. `KSType.functionReceiver()` reads the `ExtensionFunctionType`
annotation KSP puts on receiver-carrying function types; the receiver is then the first type
argument.

```kotlin
private fun KSAnnotated.hasAnnotation(fq: String): Boolean =
  annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == fq }

private fun KSValueParameter.filledByMarkerFq(): String? =
  annotations
    .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == FILLED_BY_FQ }
    ?.arguments
    ?.firstOrNull()
    ?.let { (it.value as? KSType)?.declaration?.qualifiedName?.asString() }

private fun KSType.isRouteKClass(): Boolean {
  if (declaration.qualifiedName?.asString() != "kotlin.reflect.KClass") return false
  val argument = arguments.firstOrNull()?.type?.resolve() ?: return false
  return argument.declaration.qualifiedName?.asString() == ROUTE_FQ ||
    (argument.declaration as? KSClassDeclaration)
      ?.getAllSuperTypes()
      ?.any { it.declaration.qualifiedName?.asString() == ROUTE_FQ } == true
}

private fun KSType.isExtensionFunctionType(): Boolean =
  annotations.any {
    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
      "kotlin.ExtensionFunctionType"
  }

private fun KSType.functionReceiver(): KSType? =
  if (isExtensionFunctionType()) arguments.firstOrNull()?.type?.resolve() else null

/** Function-type arguments are `[receiver?] + params + returnType`. */
private fun KSType.functionParameters(): List<KSType> {
  val all = arguments.mapNotNull { it.type?.resolve() }
  val withoutReturn = all.dropLast(1)
  return if (isExtensionFunctionType()) withoutReturn.drop(1) else withoutReturn
}

private fun KSType.toSlotType(typeParameterNames: List<String>): SlotType {
  val name = declaration.simpleName.asString()
  if (name in typeParameterNames && declaration.qualifiedName == null) return SlotType.Variable(name)
  if (isFunctionType) {
    return SlotType.Lambda(
      parameters = functionParameters().map { it.toSlotType(typeParameterNames) },
      returnType =
        arguments.last().type!!.resolve().toSlotType(typeParameterNames),
    )
  }
  return SlotType.Concrete(declaration.qualifiedName?.asString() ?: name, toTypeName())
}
```

- [ ] **Step 4: Implement the dump**

```kotlin
package dev.gezgin.processor.wrapper

internal fun dumpWrapperText(
  result: WrapperReadResult,
  providers: List<SlotProviderModel>,
  bindings: Map<String, WrapperBindingModel>,
): String = buildString {
  result.markers.forEach { marker ->
    appendLine("marker ${marker.annotationFq} route=${marker.routeParamName}")
  }
  result.wrappers.forEach { wrapper ->
    appendLine("wrapper ${wrapper.packageName}.${wrapper.functionSimpleName}")
    wrapper.typeParameterNames.forEach { appendLine("  typeParam $it") }
    wrapper.slots.forEach { slot ->
      appendLine(
        "  slot ${slot.parameterName} marker=${slot.markerFq} default=${slot.hasDefault} " +
          "receiver=${slot.receiver} params=${slot.parameters}"
      )
    }
  }
  providers.sortedBy { "${it.routeFq}|${it.markerFq}" }.forEach { provider ->
    appendLine(
      "provider ${provider.packageName}.${provider.functionSimpleName} " +
        "marker=${provider.markerFq} route=${provider.routeFq} " +
        "slotParams=${provider.slotParams.map { it.name }} " +
        "roles=${provider.roleParams.map { "${it.name}:${it.role}" }}"
    )
  }
  bindings.toSortedMap().forEach { (routeFq, binding) ->
    appendLine(
      "binding $routeFq wrapper=${binding.wrapper.packageName}.${binding.wrapper.functionSimpleName} " +
        "typeArgs=${binding.typeArguments} filled=${binding.filledSlots.keys.sorted()}"
    )
  }
}
```

- [ ] **Step 5: Wire the reader and dump into the processor**

In `GezginProcessor.process`, immediately after the `ViewModelModelReader` call (it is still present at this point and is removed in Task 9), insert:

```kotlin
val (wrapperResult, wrapperOk) =
  WrapperModelReader(resolver, environment.logger, environment.options).read()

if (environment.options["gezgin.dumpWrapper"].toBoolean()) {
  environment.codeGenerator
    .createNewFile(
      dependencies = Dependencies.ALL_FILES,
      packageName = "",
      fileName = "GezginWrapperDump",
      extensionName = "txt",
    )
    .use { it.write(dumpWrapperText(wrapperResult, emptyList(), emptyMap()).toByteArray()) }
}
```

Add `wrapperOk` to the `emitEntries && vmOk && entriesOk && fragOk` gate.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperModelReaderTest" --console=plain`
Expected: PASS, all six tests.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add gezgin-processor/
git commit -m "feat(processor): discover @ScreenWrapper functions and @ScreenSlot markers"
```

---

## Task 4: Slot provider discovery

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/wrapper/SlotProviderReader.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/GezginProcessor.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/SlotProviderReaderTest.kt`

**Interfaces:**
- Consumes: `WrapperReadResult` from Task 3; `SlotProviderModel`, `ProviderParam`, `ProviderRoleParam`, `ProviderRole` from Task 2.
- Produces: `SlotProviderReader(resolver, logger, markers, graphModel).read(): Pair<List<SlotProviderModel>, Boolean>`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import kotlin.test.Test
import kotlin.test.assertContains
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class SlotProviderReaderTest {

  private fun graphAndVocabulary(extra: String) =
    SourceFile.kotlin(
      "Fixture.kt",
      """
      package app

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FilledBy
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.core.annotation.ScreenSlot
      import dev.gezgin.core.annotation.ScreenWrapper
      import kotlin.reflect.KClass
      import kotlinx.serialization.Serializable

      @NavGraph
      @Serializable
      sealed interface AppGraph : Route {
        @GoTo(DetailRoute::class) @Serializable data object ListRoute : AppGraph

        @Serializable data class DetailRoute(val id: String) : AppGraph
      }

      @ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

      @ScreenWrapper
      fun appRoot(
        @FilledBy(TopBar::class) topBar: () -> Unit = {},
        @FilledBy(Screen::class) content: () -> Unit,
      ) { topBar(); content() }

      $extra
      """
        .trimIndent(),
    )

  @Test
  fun `a provider is bound to its route and its role parameters are classified`() {
    compileGezgin(
      graphAndVocabulary(
        """
        @TopBar(AppGraph.DetailRoute::class)
        fun detailTopBar(route: AppGraph.DetailRoute, nav: ListNavigator) = Unit
        """
          .trimIndent()
      ),
      kspArgs = mapOf("gezgin.dumpWrapper" to "true", "gezgin.emitEntries" to "false"),
    )

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "provider app.detailTopBar marker=app.TopBar route=app.AppGraph.DetailRoute")
    assertContains(dump, "roles=[route:ROUTE]")
  }

  @Test
  fun `SW4 fires when two providers claim the same slot for one route`() {
    val result =
      compileGezgin(
        graphAndVocabulary(
          """
          @TopBar(AppGraph.DetailRoute::class) fun firstTopBar() = Unit

          @TopBar(AppGraph.DetailRoute::class) fun secondTopBar() = Unit
          """
            .trimIndent()
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW4]")
    assertContains(result.messages, "firstTopBar")
    assertContains(result.messages, "secondTopBar")
  }

  @Test
  fun `a repeatable marker binds one provider to several routes`() {
    compileGezgin(
      graphAndVocabulary(
        """
        @TopBar(AppGraph.ListRoute::class)
        @TopBar(AppGraph.DetailRoute::class)
        fun sharedTopBar() = Unit
        """
          .trimIndent()
      ),
      kspArgs = mapOf("gezgin.dumpWrapper" to "true", "gezgin.emitEntries" to "false"),
    )

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "route=app.AppGraph.ListRoute")
    assertContains(dump, "route=app.AppGraph.DetailRoute")
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.SlotProviderReaderTest" --console=plain`
Expected: FAIL — no `provider ` lines in the dump.

- [ ] **Step 3: Implement the reader**

```kotlin
package dev.gezgin.processor.wrapper

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import dev.gezgin.processor.codegen.NavigatorCodegen

private const val SHEET_CONTROLLER_FQ = "dev.gezgin.core.compose.GezginSheetController"

/**
 * Finds every declaration annotated with one of the discovered slot markers and resolves it against
 * the route each annotation instance names. A provider's parameters split into Gezgin-supplied
 * roles (the exact route type, the route's typed navigator, the sheet controller) and slot
 * parameters, which [SlotUnifier] later matches against the slot's function type.
 */
internal class SlotProviderReader(
  private val resolver: Resolver,
  private val logger: KSPLogger,
  private val markers: List<SlotMarkerModel>,
) {

  private var ok = true

  fun read(): Pair<List<SlotProviderModel>, Boolean> {
    val providers = mutableListOf<SlotProviderModel>()

    markers.forEach { marker ->
      resolver
        .getSymbolsWithAnnotation(marker.annotationFq)
        .filterIsInstance<KSFunctionDeclaration>()
        .forEach { declaration ->
          declaration.annotations
            .filter {
              it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                marker.annotationFq
            }
            .forEach { annotation ->
              val routeFq =
                annotation.arguments
                  .firstOrNull { it.name?.asString() == marker.routeParamName }
                  ?.let { (it.value as? KSType)?.declaration?.qualifiedName?.asString() }
                  ?: annotation.arguments
                    .firstOrNull()
                    ?.let { (it.value as? KSType)?.declaration?.qualifiedName?.asString() }
              if (routeFq != null) {
                providers += readProvider(declaration, marker.annotationFq, routeFq)
              }
            }
        }
    }

    providers
      .groupBy { it.routeFq to it.markerFq }
      .filterValues { it.size > 1 }
      .forEach { (key, duplicates) ->
        error(
          "SW4",
          "route ${key.first} has ${duplicates.size} providers for slot marker ${key.second}: " +
            duplicates.joinToString { "${it.packageName}.${it.functionSimpleName}" } +
            "; exactly one is allowed",
        )
      }

    return providers.sortedBy { "${it.routeFq}|${it.markerFq}" } to ok
  }

  private fun readProvider(
    declaration: KSFunctionDeclaration,
    markerFq: String,
    routeFq: String,
  ): SlotProviderModel {
    val navigatorFq = navigatorFqFor(routeFq)
    val slotParams = mutableListOf<ProviderParam>()
    val roleParams = mutableListOf<ProviderRoleParam>()

    declaration.parameters.forEach { parameter ->
      val type = parameter.type.resolve()
      val typeFq = type.declaration.qualifiedName?.asString()
      val name = parameter.name!!.asString()
      when (typeFq) {
        routeFq -> roleParams += ProviderRoleParam(name, ProviderRole.ROUTE)
        navigatorFq -> roleParams += ProviderRoleParam(name, ProviderRole.NAVIGATOR)
        SHEET_CONTROLLER_FQ -> roleParams += ProviderRoleParam(name, ProviderRole.SHEET_CONTROLLER)
        else -> slotParams += ProviderParam(name, type.toTypeName())
      }
    }

    return SlotProviderModel(
      functionSimpleName = declaration.simpleName.asString(),
      packageName = declaration.packageName.asString(),
      markerFq = markerFq,
      routeFq = routeFq,
      receiverTypeName = declaration.extensionReceiver?.resolve()?.toTypeName(),
      slotParams = slotParams,
      roleParams = roleParams,
    )
  }

  /** `app.AppGraph.DetailRoute` -> `app.DetailNavigator`, matching NavigatorCodegen's naming. */
  private fun navigatorFqFor(routeFq: String): String {
    val simple = routeFq.substringAfterLast('.')
    val packageName = routeFq.substringBefore(".${routeFq.substringAfter('.')}", "")
    val x = NavigatorCodegen.xOf(simple)
    val routePackage =
      (resolver.getClassDeclarationByName(resolver.getKSNameFromString(routeFq))
          as? KSClassDeclaration)
        ?.packageName
        ?.asString() ?: packageName
    return "$routePackage.${x}Navigator"
  }

  private fun error(code: String, message: String) {
    logger.error("[$code] $message")
    ok = false
  }
}
```

`NavigatorCodegen.xOf` does not exist yet. Add it as a thin `internal` wrapper over the existing
`stripSuffix` helper used at `NavigatorCodegen.kt:128` (it turns `DetailScreenRoute` into
`Detail`); `NavigatorNamingTest` already locks that behaviour, so reuse it rather than
reimplementing. `NavigatorCodegen.rawFactoryFunName(x)` (line 84) is already `internal` and is used
unchanged by Task 7.

- [ ] **Step 4: Wire into the processor**

Immediately after the `WrapperModelReader` call added in Task 3:

```kotlin
val (slotProviders, providersOk) =
  SlotProviderReader(resolver, environment.logger, wrapperResult.markers).read()
```

Pass `slotProviders` into the `dumpWrapperText` call and add `providersOk` to the emit gate.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.SlotProviderReaderTest" --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add gezgin-processor/
git commit -m "feat(processor): read slot providers and classify their role parameters"
```

---

## Task 5: Shallow unification

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/wrapper/SlotUnifier.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/SlotUnifierTest.kt`

**Interfaces:**
- Consumes: `SlotType` from Task 2.
- Produces: `SlotUnifier.unify(slot: SlotType, concrete: TypeName, bindings: MutableMap<String, TypeName>): Boolean`.

This is a pure function over KotlinPoet types, so it is unit-tested directly with no compilation.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.gezgin.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.UNIT
import dev.gezgin.processor.wrapper.SlotType
import dev.gezgin.processor.wrapper.SlotUnifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotUnifierTest {

  private val detailState = ClassName("app", "DetailUiState")
  private val detailIntent = ClassName("app", "DetailIntent")

  @Test
  fun `a concrete slot type matches an equal concrete type`() {
    val bindings = mutableMapOf<String, com.squareup.kotlinpoet.TypeName>()
    assertTrue(
      SlotUnifier.unify(SlotType.Concrete("app.DetailUiState", detailState), detailState, bindings)
    )
    assertTrue(bindings.isEmpty())
  }

  @Test
  fun `a concrete slot type rejects a different concrete type`() {
    assertFalse(
      SlotUnifier.unify(
        SlotType.Concrete("app.DetailUiState", detailState),
        detailIntent,
        mutableMapOf(),
      )
    )
  }

  @Test
  fun `a type variable binds to the concrete type`() {
    val bindings = mutableMapOf<String, com.squareup.kotlinpoet.TypeName>()
    assertTrue(SlotUnifier.unify(SlotType.Variable("S"), detailState, bindings))
    assertEquals(mapOf("S" to detailState), bindings)
  }

  @Test
  fun `a type variable already bound to a different type fails`() {
    val bindings = mutableMapOf<String, com.squareup.kotlinpoet.TypeName>("S" to detailState)
    assertFalse(SlotUnifier.unify(SlotType.Variable("S"), detailIntent, bindings))
  }

  @Test
  fun `a lambda slot binds its parameter variable`() {
    val bindings = mutableMapOf<String, com.squareup.kotlinpoet.TypeName>()
    val slot = SlotType.Lambda(listOf(SlotType.Variable("I")), SlotType.Concrete("kotlin.Unit", UNIT))
    val concrete =
      LambdaTypeName.get(parameters = listOf(ParameterSpec.unnamed(detailIntent)), returnType = UNIT)

    assertTrue(SlotUnifier.unify(slot, concrete, bindings))
    assertEquals(mapOf("I" to detailIntent), bindings)
  }

  @Test
  fun `a lambda slot rejects a different arity`() {
    val slot = SlotType.Lambda(listOf(SlotType.Variable("I")), SlotType.Concrete("kotlin.Unit", UNIT))
    val concrete = LambdaTypeName.get(parameters = emptyList(), returnType = UNIT)

    assertFalse(SlotUnifier.unify(slot, concrete, mutableMapOf()))
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.SlotUnifierTest" --console=plain`
Expected: FAIL — `Unresolved reference: SlotUnifier`.

- [ ] **Step 3: Implement the unifier**

```kotlin
package dev.gezgin.processor.wrapper

import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.TypeName

/**
 * Matches a slot's declared type against a provider's concrete type, binding the wrapper's type
 * parameters as it goes. Deliberately shallow: no variance, no subtyping, no nested generic
 * decomposition beyond function types. A pair that does not match is a plain mismatch the caller
 * reports as `SW8`.
 */
internal object SlotUnifier {

  fun unify(
    slot: SlotType,
    concrete: TypeName,
    bindings: MutableMap<String, TypeName>,
  ): Boolean =
    when (slot) {
      is SlotType.Concrete -> slot.typeName.copy(annotations = emptyList()) == concrete.copy(annotations = emptyList())

      is SlotType.Variable -> {
        val existing = bindings[slot.name]
        if (existing == null) {
          bindings[slot.name] = concrete
          true
        } else {
          existing == concrete
        }
      }

      is SlotType.Lambda -> {
        val lambda = concrete as? LambdaTypeName
        when {
          lambda == null -> false
          lambda.parameters.size != slot.parameters.size -> false
          else ->
            slot.parameters.zip(lambda.parameters).all { (slotParam, concreteParam) ->
              unify(slotParam, concreteParam.type, bindings)
            } && unify(slot.returnType, lambda.returnType, bindings)
        }
      }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.SlotUnifierTest" --console=plain`
Expected: PASS, all six tests.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add gezgin-processor/
git commit -m "feat(processor): add shallow slot-type unification"
```

---

## Task 6: Wrapper binding

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/wrapper/WrapperBinder.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/GezginProcessor.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/WrapperBinderTest.kt`

**Interfaces:**
- Consumes: `WrapperReadResult`, `List<SlotProviderModel>`, `SlotUnifier`.
- Produces: `WrapperBinder(logger).bind(wrappers, providers, routesWithScreens: Set<String>): Pair<Map<String, WrapperBindingModel>, Boolean>` keyed by route FQ.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import kotlin.test.Test
import kotlin.test.assertContains
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperBinderTest {

  private fun fixture(vocabulary: String, feature: String) =
    SourceFile.kotlin(
      "Fixture.kt",
      """
      package app

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FilledBy
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.core.annotation.ScreenSlot
      import dev.gezgin.core.annotation.ScreenWrapper
      import kotlin.reflect.KClass
      import kotlinx.serialization.Serializable

      @NavGraph
      @Serializable
      sealed interface AppGraph : Route {
        @GoTo(DetailRoute::class) @Serializable data object ListRoute : AppGraph

        @Serializable data class DetailRoute(val id: String) : AppGraph
      }

      data class DetailUiState(val title: String)

      sealed interface DetailIntent

      $vocabulary

      $feature
      """
        .trimIndent(),
    )

  private val genericWrapper =
    """
    @ScreenSlot annotation class ViewModelOf(val route: KClass<out Route>)

    @ScreenWrapper
    @Composable
    fun <S, I> appRoot(
      @FilledBy(ViewModelOf::class) viewModel: @Composable () -> S,
      @FilledBy(Screen::class) content: @Composable (S, (I) -> Unit) -> Unit,
    ) = Unit
    """
      .trimIndent()

  @Test
  fun `type arguments bind from the content slot and a sibling slot`() {
    compileGezgin(
      fixture(
        genericWrapper,
        """
        @ViewModelOf(AppGraph.DetailRoute::class)
        @Composable fun detailViewModel(): DetailUiState = DetailUiState("x")

        @Screen(AppGraph.DetailRoute::class)
        @Composable fun detailScreen(state: DetailUiState, onIntent: (DetailIntent) -> Unit) = Unit
        """
          .trimIndent(),
      ),
      kspArgs = mapOf("gezgin.dumpWrapper" to "true", "gezgin.emitEntries" to "false"),
    )

    val dump = findGeneratedResource("GezginWrapperDump.txt")!!.readText()
    assertContains(dump, "binding app.AppGraph.DetailRoute wrapper=app.appRoot")
    assertContains(dump, "typeArgs=[app.DetailUiState, app.DetailIntent]")
  }

  @Test
  fun `SW5 fires when a slot without a default has no provider`() {
    val result =
      compileGezgin(
        fixture(
          genericWrapper,
          """
          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState, onIntent: (DetailIntent) -> Unit) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW5]")
    assertContains(result.messages, "viewModel")
  }

  @Test
  fun `SW7 fires when a type parameter is bound by no filled slot`() {
    val result =
      compileGezgin(
        fixture(
          """
          @ScreenSlot annotation class Unused(val route: KClass<out Route>)

          @ScreenWrapper
          @Composable
          fun <S, E> appRoot(
            @FilledBy(Unused::class) onEffect: (E) -> Unit = {},
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
            .trimIndent(),
          """
          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW7]")
    assertContains(result.messages, "E")
  }

  @Test
  fun `SW6 fires when two wrappers are candidates for one route`() {
    val result =
      compileGezgin(
        fixture(
          """
          @ScreenWrapper
          @Composable
          fun firstRoot(@FilledBy(Screen::class) content: @Composable (DetailUiState) -> Unit) = Unit

          @ScreenWrapper
          @Composable
          fun secondRoot(@FilledBy(Screen::class) content: @Composable (DetailUiState) -> Unit) = Unit
          """
            .trimIndent(),
          """
          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SW6]")
    assertContains(result.messages, "firstRoot")
    assertContains(result.messages, "secondRoot")
  }

  @Test
  fun `no wrapper in scope is not an error`() {
    val result =
      compileGezgin(
        fixture(
          "",
          """
          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "Gezgin processor alive")
    kotlin.test.assertFalse(result.messages.contains("[SW6]"))
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperBinderTest" --console=plain`
Expected: FAIL — no `binding ` lines, no `[SWn]` messages.

- [ ] **Step 3: Implement the binder**

```kotlin
package dev.gezgin.processor.wrapper

import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.TypeName

/**
 * Chooses one wrapper per route and resolves everything the codegen needs. A wrapper is a candidate
 * when its content slot unifies with the route's `@Screen` provider and every slot without a Kotlin
 * default has a provider. Exactly one candidate must survive.
 */
internal class WrapperBinder(private val logger: KSPLogger) {

  private var ok = true

  fun bind(
    wrappers: List<WrapperModel>,
    providers: List<SlotProviderModel>,
    routesWithScreens: Set<String>,
  ): Pair<Map<String, WrapperBindingModel>, Boolean> {
    if (wrappers.isEmpty()) return emptyMap<String, WrapperBindingModel>() to true

    val providersByRoute = providers.groupBy { it.routeFq }
    val bindings = mutableMapOf<String, WrapperBindingModel>()

    routesWithScreens.sorted().forEach { routeFq ->
      val routeProviders = providersByRoute[routeFq].orEmpty().associateBy { it.markerFq }
      val candidates = wrappers.mapNotNull { tryBind(it, routeFq, routeProviders) }
      when (candidates.size) {
        1 -> bindings[routeFq] = candidates.single()
        0 ->
          error(
            "SW6",
            "route $routeFq matches none of the @ScreenWrapper functions in scope " +
              "(${wrappers.joinToString { "${it.packageName}.${it.functionSimpleName}" }}); " +
              "check the screen's receiver and parameter types against each wrapper's content slot",
          )
        else ->
          error(
            "SW6",
            "route $routeFq matches ${candidates.size} @ScreenWrapper functions " +
              "(${candidates.joinToString { "${it.wrapper.packageName}.${it.wrapper.functionSimpleName}" }}); " +
              "give them distinct content-slot signatures",
          )
      }
    }

    providers
      .filter { it.routeFq in routesWithScreens }
      .filterNot { provider ->
        wrappers.any { wrapper -> wrapper.slots.any { it.markerFq == provider.markerFq } }
      }
      .forEach { provider ->
        error(
          "SW10",
          "${provider.packageName}.${provider.functionSimpleName} is marked ${provider.markerFq} " +
            "for route ${provider.routeFq}, but no @ScreenWrapper declares a @FilledBy slot for " +
            "that marker",
        )
      }

    return bindings to ok
  }

  private fun tryBind(
    wrapper: WrapperModel,
    routeFq: String,
    routeProviders: Map<String, SlotProviderModel>,
  ): WrapperBindingModel? {
    val contentSlot = wrapper.contentSlot ?: return null
    val screenProvider = routeProviders[contentSlot.markerFq] ?: return null

    val bindings = mutableMapOf<String, TypeName>()
    val filled = mutableMapOf<String, SlotProviderModel>()

    if (!unifySlot(contentSlot, screenProvider, bindings)) return null
    filled[contentSlot.parameterName] = screenProvider

    wrapper.slots.filter { it !== contentSlot }.forEach { slot ->
      val provider = routeProviders[slot.markerFq]
      when {
        provider != null -> {
          if (!unifySlot(slot, provider, bindings)) {
            error(
              "SW8",
              "${provider.packageName}.${provider.functionSimpleName} does not match slot " +
                "'${slot.parameterName}' of ${wrapper.packageName}.${wrapper.functionSimpleName}: " +
                "slot expects receiver=${slot.receiver} params=${slot.parameters}, provider has " +
                "receiver=${provider.receiverTypeName} params=${provider.slotParams.map { it.typeName }}",
            )
            return null
          }
          filled[slot.parameterName] = provider
        }
        !slot.hasDefault -> {
          error(
            "SW5",
            "route $routeFq has no provider marked ${slot.markerFq} for required slot " +
              "'${slot.parameterName}' of ${wrapper.packageName}.${wrapper.functionSimpleName}; " +
              "add a provider or give the parameter a default value",
          )
          return null
        }
      }
    }

    val unbound = wrapper.typeParameterNames.filterNot { it in bindings }
    if (unbound.isNotEmpty()) {
      error(
        "SW7",
        "type parameter(s) ${unbound.joinToString()} of " +
          "${wrapper.packageName}.${wrapper.functionSimpleName} are bound by no filled slot for " +
          "route $routeFq; surface them in a slot's signature so the processor can resolve them",
      )
      return null
    }

    return WrapperBindingModel(
      wrapper = wrapper,
      typeArguments = wrapper.typeParameterNames.map { bindings.getValue(it) },
      filledSlots = filled,
    )
  }

  private fun unifySlot(
    slot: WrapperSlotModel,
    provider: SlotProviderModel,
    bindings: MutableMap<String, TypeName>,
  ): Boolean {
    val receiverOk =
      when {
        slot.receiver == null && provider.receiverTypeName == null -> true
        slot.receiver != null && provider.receiverTypeName != null ->
          SlotUnifier.unify(slot.receiver, provider.receiverTypeName, bindings)
        else -> false
      }
    if (!receiverOk) return false
    if (slot.parameters.size != provider.slotParams.size) return false
    return slot.parameters.zip(provider.slotParams).all { (slotType, providerParam) ->
      SlotUnifier.unify(slotType, providerParam.typeName, bindings)
    }
  }

  private fun error(code: String, message: String) {
    logger.error("[$code] $message")
    ok = false
  }
}
```

Note that `tryBind` reports `SW5`, `SW7` and `SW8` before returning null, so a single-wrapper
application gets the precise cause rather than only `SW6`. With several wrappers in scope this can
report a rejection for a wrapper the author never intended; that is accepted — the messages name the
wrapper, and `SW6`'s own message lists every candidate.

- [ ] **Step 4: Wire into the processor**

After `SlotProviderReader`:

```kotlin
val routesWithScreens = entries.map { it.routeFq }.toSet()
val (wrapperBindings, bindOk) =
  WrapperBinder(environment.logger).bind(wrapperResult.wrappers, slotProviders, routesWithScreens)
```

Pass `wrapperBindings` into `dumpWrapperText` and add `bindOk` to the emit gate.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperBinderTest" --console=plain`
Expected: PASS, all five tests.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add gezgin-processor/
git commit -m "feat(processor): bind one wrapper per route and resolve its type arguments"
```

---

## Task 7: Wrapper entry codegen

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/WrapperEntryCodegen.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModel.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/GezginProcessor.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/WrapperEntryCodegenTest.kt`

**Interfaces:**
- Consumes: `WrapperBindingModel`, `EntryFunctionModel`.
- Produces: `WrapperEntryCodegen.generate(entries: List<EntryFunctionModel>): List<FileSpec>`, writing `GezginWrapperEntries.kt` per package. `EntryFunctionModel` gains `val wrapper: WrapperBindingModel?`.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperEntryCodegenTest {

  private val fixture =
    SourceFile.kotlin(
      "Fixture.kt",
      """
      package app

      import androidx.compose.runtime.Composable
      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.FilledBy
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import dev.gezgin.core.annotation.Screen
      import dev.gezgin.core.annotation.ScreenSlot
      import dev.gezgin.core.annotation.ScreenWrapper
      import kotlin.reflect.KClass
      import kotlinx.serialization.Serializable

      @NavGraph
      @Serializable
      sealed interface AppGraph : Route {
        @GoTo(DetailRoute::class) @Serializable data object ListRoute : AppGraph

        @Serializable data class DetailRoute(val id: String) : AppGraph
      }

      data class DetailUiState(val title: String)

      sealed interface DetailIntent

      sealed interface DetailEffect

      @ScreenSlot annotation class ViewModelOf(val route: KClass<out Route>)

      @ScreenSlot annotation class Effects(val route: KClass<out Route>)

      @ScreenSlot annotation class TopBar(val route: KClass<out Route>)

      @ScreenWrapper
      @Composable
      fun <S, I, E> appRoot(
        @FilledBy(ViewModelOf::class) viewModel: @Composable () -> S,
        @FilledBy(Effects::class) onEffect: (E) -> Unit,
        @FilledBy(TopBar::class) topBar: @Composable (S) -> Unit = {},
        @FilledBy(Screen::class) content: @Composable (S, (I) -> Unit) -> Unit,
      ) = Unit

      @ViewModelOf(AppGraph.DetailRoute::class)
      @Composable fun detailViewModel(route: AppGraph.DetailRoute): DetailUiState =
        DetailUiState(route.id)

      @Effects(AppGraph.DetailRoute::class)
      fun handleDetailEffect(effect: DetailEffect, nav: DetailNavigator) = Unit

      @Screen(AppGraph.DetailRoute::class)
      @Composable fun detailScreen(state: DetailUiState, onIntent: (DetailIntent) -> Unit) = Unit
      """
        .trimIndent(),
    )

  @Test
  fun `the generated entry calls the wrapper with explicit type arguments and role wiring`() {
    val result = compileGezgin(fixture)
    val generated = result.generatedSourceFor("GezginWrapperEntries.kt")

    assertNotNull(generated, "GezginWrapperEntries.kt was not generated")
    val text = generated.readText()
    assertContains(text, "public fun GezginEntryScope.provideDetailEntry()")
    assertContains(text, "val nav = LocalGezginRawNavigator.current.detailNavigator(")
    assertContains(text, "appRoot<DetailUiState, DetailIntent, DetailEffect>(")
    assertContains(text, "viewModel = { detailViewModel(route = route) }")
    assertContains(text, "onEffect = { effect -> handleDetailEffect(effect = effect, nav = nav) }")
    assertContains(text, "detailScreen(state = state, onIntent = onIntent)")
  }

  @Test
  fun `an omitted optional slot does not appear in the call`() {
    val result = compileGezgin(fixture)
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText()

    kotlin.test.assertFalse(text.contains("topBar ="), "optional unfilled slot must be omitted")
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperEntryCodegenTest" --console=plain`
Expected: FAIL — `GezginWrapperEntries.kt was not generated`.

- [ ] **Step 3: Add the field to `EntryFunctionModel`**

In `entry/EntryModel.kt`, add to `EntryFunctionModel`:

```kotlin
/** The wrapper chosen for this entry's route, or null when no `@ScreenWrapper` is in scope. */
val wrapper: WrapperBindingModel? = null,
```

Keep `mvi: MviEntryModel?` for now; Task 9 removes it.

- [ ] **Step 4: Implement the codegen**

```kotlin
package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import dev.gezgin.processor.entry.EntryFunctionModel
import dev.gezgin.processor.wrapper.ProviderRole
import dev.gezgin.processor.wrapper.SlotProviderModel
import dev.gezgin.processor.wrapper.WrapperBindingModel

private const val COMPOSE_PKG = "dev.gezgin.core.compose"

private val ENTRY_SCOPE = ClassName(COMPOSE_PKG, "GezginEntryScope")
private val ENTRY_KIND = ClassName(COMPOSE_PKG, "EntryKind")
private val LOCAL_ENTRY_ID = MemberName(COMPOSE_PKG, "LocalGezginEntryId")
private val LOCAL_RAW_NAVIGATOR = MemberName(COMPOSE_PKG, "LocalGezginRawNavigator")
private val LOCAL_SHEET_CONTROLLER = MemberName(COMPOSE_PKG, "LocalGezginSheetController")

/**
 * Emits `fun GezginEntryScope.provideXEntry()` for every entry whose route bound a
 * `@ScreenWrapper`. The wrapper call carries explicit type arguments because Kotlin cannot infer a
 * wrapper's type parameters from lambda parameter types; each slot is a lambda that closes over the
 * `register` body's `route` and `nav` locals, which is how a generic wrapper reaches a fully typed
 * navigator without naming its type.
 */
internal object WrapperEntryCodegen {

  fun generate(entries: List<EntryFunctionModel>): List<FileSpec> =
    entries
      .filter { it.wrapper != null }
      .sortedWith(compareBy({ it.packageName }, { it.routeFq }))
      .groupBy { it.packageName }
      .map { (packageName, group) ->
        FileSpec.builder(packageName, "GezginWrapperEntries")
          .apply { if (group.any { navWired(it) }) optInGezginInternalApi() }
          .apply { group.forEach { addFunction(provideEntryFun(it)) } }
          .build()
      }

  private fun navWired(entry: EntryFunctionModel): Boolean =
    entry.wrapper!!.filledSlots.values.any { provider ->
      provider.roleParams.any { it.role == ProviderRole.NAVIGATOR }
    }

  private fun provideEntryFun(entry: EntryFunctionModel): FunSpec {
    val binding = entry.wrapper!!
    val routeClass = ClassName.bestGuess(entry.routeFq)
    val body =
      CodeBlock.builder()
        .add(
          "register<%T>(kind = %T.%L, noBack = %L) { route ->\n",
          routeClass,
          ENTRY_KIND,
          entry.kind.name,
          entry.noBack,
        )
        .indent()

    if (navWired(entry)) {
      val factoryFun =
        MemberName(entry.routePackageName, NavigatorCodegen.rawFactoryFunName(entry.x))
      body.add(
        "val nav = %M.current.%M(%M.current)\n",
        LOCAL_RAW_NAVIGATOR,
        factoryFun,
        LOCAL_ENTRY_ID,
      )
    }

    val wrapperFun = MemberName(binding.wrapper.packageName, binding.wrapper.functionSimpleName)
    val typeArgs = binding.typeArguments.joinToString(", ") { it.toString() }
    body.add("%M<%L>(\n", wrapperFun, typeArgs).indent()

    val contentSlotName = binding.wrapper.contentSlot!!.parameterName
    binding.wrapper.slots
      .filter { it.parameterName != contentSlotName }
      .mapNotNull { slot -> binding.filledSlots[slot.parameterName]?.let { slot to it } }
      .forEach { (slot, provider) ->
        body.add("%L = %L,\n", slot.parameterName, slotLambda(slot.parameterName, provider, binding))
      }

    body.unindent().add(") { ")
    val contentProvider = binding.filledSlots.getValue(contentSlotName)
    val contentLambdaParams = contentProvider.slotParams.joinToString(", ") { it.name }
    if (contentLambdaParams.isNotEmpty()) body.add("%L ->\n", contentLambdaParams) else body.add("\n")
    body.indent()
    body.add("%L\n", callProvider(contentProvider, contentProvider.slotParams.map { it.name }))
    body.unindent().add("}\n")

    body.unindent().add("}\n")

    return FunSpec.builder("provide${entry.x}Entry")
      .receiver(ENTRY_SCOPE)
      .addCode(body.build())
      .build()
  }

  /** `{ a, b -> provider(a = a, b = b, role = <role value>) }` for one non-content slot. */
  private fun slotLambda(
    slotName: String,
    provider: SlotProviderModel,
    binding: WrapperBindingModel,
  ): CodeBlock {
    val slot = binding.wrapper.slots.first { it.parameterName == slotName }
    val lambdaParams = provider.slotParams.map { it.name }
    val header = if (lambdaParams.isEmpty()) "" else "${lambdaParams.joinToString(", ")} -> "
    return CodeBlock.of("{ %L%L }", header, callProvider(provider, lambdaParams))
      .also { check(slot.parameters.size == lambdaParams.size) }
  }

  /** Every argument named, so a provider's declared parameter order never matters. */
  private fun callProvider(provider: SlotProviderModel, lambdaParams: List<String>): CodeBlock {
    val args = CodeBlock.builder()
    provider.slotParams.forEachIndexed { index, param ->
      if (index > 0) args.add(", ")
      args.add("%L = %L", param.name, lambdaParams[index])
    }
    provider.roleParams.forEach { role ->
      if (args.isNotEmpty()) args.add(", ")
      when (role.role) {
        ProviderRole.ROUTE -> args.add("%L = route", role.name)
        ProviderRole.NAVIGATOR -> args.add("%L = nav", role.name)
        ProviderRole.SHEET_CONTROLLER ->
          args.add("%L = %M.current", role.name, LOCAL_SHEET_CONTROLLER)
      }
    }
    return CodeBlock.of(
      "%M(%L)",
      MemberName(provider.packageName, provider.functionSimpleName),
      args.build(),
    )
  }
}
```

- [ ] **Step 5: Wire into the processor**

In the `emitEntries` block, attach bindings to entries and emit:

```kotlin
val boundEntries = entries.map { it.copy(wrapper = wrapperBindings[it.routeFq]) }
val wrappedEntries = boundEntries.filter { it.wrapper != null }
if (wrappedEntries.isNotEmpty()) {
  WrapperEntryCodegen.generate(wrappedEntries).forEach {
    it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
  }
}
```

Exclude wrapped entries from the existing `EntryCodegen` / `MviEntryCodegen` filters so no route is
registered twice:

```kotlin
val coreEntries = boundEntries.filter { it.mvi == null && it.wrapper == null }
val mviEntries = boundEntries.filter { it.mvi != null && it.wrapper == null }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperEntryCodegenTest" --console=plain`
Expected: PASS, both tests.

- [ ] **Step 7: Run the whole processor suite for regressions**

Run: `./gradlew :gezgin-processor:test --console=plain`
Expected: PASS. Existing MVI and core tests are untouched because wrapped entries are a disjoint set.

- [ ] **Step 8: Commit**

```bash
./gradlew spotlessApply
git add gezgin-processor/
git commit -m "feat(processor): emit wrapper-based entries"
```

---

## Task 8: End-to-end proof in sample/hello

**Files:**
- Create: `sample/hello/src/main/kotlin/dev/gezgin/sample/hello/ui/AppMvi.kt`
- Create: `sample/hello/src/main/kotlin/dev/gezgin/sample/hello/ui/AppScreenRoot.kt`
- Modify: every file under `sample/hello/src/main/kotlin/dev/gezgin/sample/hello/screen_contact_list/` and `screen_contact_detail/`
- Modify: `sample/hello/build.gradle.kts`

**Interfaces:**
- Consumes: everything from Tasks 1–7.
- Produces: the canonical two-screen example the spec is written against.

- [ ] **Step 1: Write the application's MVI base and wrapper**

`ui/AppMvi.kt`:

```kotlin
package dev.gezgin.sample.hello.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UiState

interface UiIntent

interface UiEvent

abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEvent> : ViewModel() {
  abstract val uiState: StateFlow<S>
  abstract val effects: Flow<E>

  abstract fun onIntent(intent: I)
}
```

`ui/AppScreenRoot.kt`:

```kotlin
package dev.gezgin.sample.hello.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.FilledBy
import dev.gezgin.core.annotation.Screen
import dev.gezgin.core.annotation.ScreenSlot
import dev.gezgin.core.annotation.ScreenWrapper
import kotlin.reflect.KClass

@ScreenSlot @Repeatable annotation class ViewModelOf(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class Effects(val route: KClass<out Route>)

@ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

@ScreenWrapper
@Composable
fun <S : UiState, I : UiIntent, E : UiEvent> AppScreenRoot(
  @FilledBy(ViewModelOf::class) viewModel: @Composable () -> BaseViewModel<S, I, E>,
  @FilledBy(Effects::class) onEffect: (E) -> Unit,
  @FilledBy(TopBar::class) topBar: @Composable (S, (I) -> Unit) -> Unit = { _, _ -> },
  @FilledBy(Screen::class) content: @Composable ColumnScope.(S, (I) -> Unit) -> Unit,
) {
  val vm = viewModel()
  val state by vm.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(vm) { vm.effects.collect(onEffect) }
  Scaffold(topBar = { topBar(state, vm::onIntent) }) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) { content(state, vm::onIntent) }
  }
}
```

- [ ] **Step 2: Migrate both ViewModels off `GezginMvi`**

In `ContactListViewModel.kt`, replace the `@MviViewModel` annotation and `GezginMvi` supertype with
the application base; keep the body:

```kotlin
class ContactListViewModel : BaseViewModel<ContactListUiState, ContactListIntent, ContactListEffect>() {

  private val _uiState = MutableStateFlow(ContactListUiState(contacts = CONTACTS))
  override val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

  private val _effects = MutableSharedFlow<ContactListEffect>(extraBufferCapacity = 8)
  override val effects: Flow<ContactListEffect> = _effects.asSharedFlow()

  override fun onIntent(intent: ContactListIntent) {
    when (intent) {
      is ContactListIntent.OpenContact -> _effects.tryEmit(ContactListEffect.OpenContact(intent.id))
    }
  }
}
```

Apply the same shape to `ContactDetailViewModel`, whose constructor keeps `contactId: String` rather
than the route type — the provider projects it.

Mark the state/intent/effect types with the application interfaces:
`data class ContactListUiState(...) : UiState`, `sealed interface ContactListIntent : UiIntent`,
`sealed interface ContactListEffect : UiEvent`, and the same three for `ContactDetail`.

- [ ] **Step 3: Replace the effect handlers with `@Effects` providers**

`ContactDetailEffectHandler.kt` becomes:

```kotlin
@Effects(HelloGraph.ContactDetailScreenRoute::class)
fun handleContactDetailEffect(effect: ContactDetailEffect, nav: ContactDetailNavigator) {
  when (effect) {
    is ContactDetailEffect.ShowMessage -> Unit
    ContactDetailEffect.BackToList -> nav.backToContactList()
  }
}
```

The `Toast` case moves into the screen, which has a `LocalContext`; a non-composable provider has
none.

- [ ] **Step 4: Add the ViewModel providers**

```kotlin
@ViewModelOf(HelloGraph.ContactListScreenRoute::class)
@Composable
fun contactListViewModel(): ContactListViewModel = viewModel { ContactListViewModel() }

@ViewModelOf(HelloGraph.ContactDetailScreenRoute::class)
@Composable
fun contactDetailViewModel(route: HelloGraph.ContactDetailScreenRoute): ContactDetailViewModel =
  viewModel { ContactDetailViewModel(contactId = route.contactId) }
```

- [ ] **Step 5: Give both screens a `ColumnScope` receiver**

Change both `@Screen` functions to `fun ColumnScope.ContactListScreen(...)` and
`fun ColumnScope.ContactDetailScreen(...)`, and drop their own `Surface`/`Column` wrappers — the
wrapper supplies them now.

- [ ] **Step 6: Build and verify the generated output**

Run: `./gradlew :sample:hello:assembleDebug --console=plain --no-problems-report`
Expected: BUILD SUCCESSFUL.

Then inspect:

```bash
cat sample/hello/build/generated/ksp/debug/kotlin/dev/gezgin/sample/hello/screen_contact_detail/GezginWrapperEntries.kt
```

Expected: an `AppScreenRoot<ContactDetailUiState, ContactDetailIntent, ContactDetailEffect>(...)`
call with `viewModel = { contactDetailViewModel(route = route) }` and
`onEffect = { effect -> handleContactDetailEffect(effect = effect, nav = nav) }`.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add sample/hello/
git commit -m "feat(sample): move hello onto the screen-wrapper API"
```

---

## Task 9: Delete gezgin-mvi and the MVI processor path

**Files:**
- Delete: the `gezgin-mvi/` directory
- Delete: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi/`
- Delete: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/MviEntryCodegen.kt`
- Delete: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviEntryCodegenTest.kt`, `MviModelReaderTest.kt`, `ExperimentalGezginMigrationApiTest.kt`
- Modify: `settings.gradle.kts`, root `build.gradle.kts`, `gezgin-processor/build.gradle.kts`, `entry/EntryModel.kt`, `entry/EntryModelReader.kt`, `GezginProcessor.kt`, `gezgin-core/.../KindAnnotations.kt`

**Interfaces:**
- Produces: `EntryFunctionModel` without `mvi`; `GezginProcessor` with a single entry pipeline.

- [ ] **Step 1: Remove the module from the build**

In `settings.gradle.kts` delete `include(":gezgin-mvi")`. In the root `build.gradle.kts` remove
`":gezgin-mvi"` from `publishedProjectPaths`. In `gezgin-processor/build.gradle.kts` remove
`testImplementation(project(":gezgin-mvi"))`.

- [ ] **Step 2: Delete the sources**

```bash
git rm -r gezgin-mvi
git rm -r gezgin-processor/src/main/kotlin/dev/gezgin/processor/mvi
git rm gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/MviEntryCodegen.kt
git rm gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviEntryCodegenTest.kt \
       gezgin-processor/src/test/kotlin/dev/gezgin/processor/MviModelReaderTest.kt \
       gezgin-processor/src/test/kotlin/dev/gezgin/processor/ExperimentalGezginMigrationApiTest.kt
```

- [ ] **Step 3: Strip MVI from the entry model and reader**

Remove `MviEntryModel`, `MviExtraParam` and `MviChromeProviderModel` from `entry/EntryModel.kt`, and
remove the `mvi` property from `EntryFunctionModel`.

In `entry/EntryModelReader.kt`, delete the MVI-mode branch, the `TOP_BAR_FQ`/`BOTTOM_BAR_FQ`/
`EFFECT_HANDLER_FQ` constants, the `VmDiClassifier` import and every `MV*` validation. An entry is
now read once: kind, route, `noBack`, package, function name, and an optional `route`/`nav`
parameter for the no-wrapper path. Content parameters are no longer classified by the reader; the
binder matches them.

In `GezginProcessor.kt`, delete the `ViewModelModelReader` call, the `gezgin.dumpMvi` block, the
`vmOk` gate term and the `MviEntryCodegen` emission. The remaining gate is
`emitEntries && entriesOk && fragOk && wrapperOk && providersOk && bindOk`.

- [ ] **Step 4: Mark the remaining kind annotations as slot markers**

In `KindAnnotations.kt`, add `@ScreenSlot` to `Dialog`, `BottomSheet` and `FullscreenModal`, and
update `WrapperModel.contentSlot` to accept any of the four:

```kotlin
private val CONTENT_MARKER_FQS =
  setOf(
    "dev.gezgin.core.annotation.Screen",
    "dev.gezgin.core.annotation.Dialog",
    "dev.gezgin.core.annotation.BottomSheet",
    "dev.gezgin.core.annotation.FullscreenModal",
  )

val contentSlot: WrapperSlotModel?
  get() = slots.firstOrNull { it.markerFq in CONTENT_MARKER_FQS }
```

Delete the `BOTTOM_SHEET` wrapper-skipping branch; a sheet now binds like any other kind, with its
`GezginSheetController` parameter classified as a role.

- [ ] **Step 5: Run the full processor suite**

Run: `./gradlew :gezgin-processor:test --console=plain`
Expected: PASS. Tests that referenced MVI fixtures were deleted in Step 2; any remaining failure is
a real regression in `EntryModelReader`.

- [ ] **Step 6: Verify the whole build**

Run: `./gradlew build -x :sample:shopr:assembleDebug -x :sample:app:assembleDebug --console=plain --no-problems-report`
Expected: FAIL only in `sample/feature/*`, `sample/shopr` and `sample/app`, which Task 10 migrates.
`sample/hello`, `gezgin-core`, `gezgin-processor` and `gezgin-test` must be green.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply apiDump
git add -A
git commit -m "refactor!: delete gezgin-mvi and the MVI codegen path"
```

---

## Task 10: Migrate the remaining samples

**Files:**
- Modify: every file under `sample/feature/auth/`, `sample/feature/home/`, `sample/feature/profile/`, `sample/shopr/`, `sample/app/`
- Create: `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/ui/ShoprScreenRoot.kt`
- Create: `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/ui/ShowcaseScreenRoot.kt`

**Interfaces:**
- Consumes: the API proven in Task 8.

- [ ] **Step 1: Add one wrapper per sample application**

`sample/shopr` is single-module, so its wrapper lives beside its screens and needs no KSP option.
The `:sample:app` showcase spans `:sample:feature:*`, so its wrapper and markers go in
`:sample:navigation` (which every feature already depends on), and each feature module adds:

```kotlin
ksp { arg("gezgin.wrapperPackages", "dev.gezgin.sample.navigation.ui") }
```

- [ ] **Step 2: Migrate `sample/feature/auth`**

For each of `screen_login`, `screen_credentials`, `screen_profile_info`, `screen_terms`: drop the
`GezginMvi` supertype for the showcase base class, convert `@MviViewModel` to a `@ViewModelOf`
provider, convert `@EffectHandler` to an `@Effects` provider, and give the `@Screen` composable a
`ColumnScope` receiver.

Run: `./gradlew :sample:feature:auth:assembleDebug --console=plain --no-problems-report`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Migrate `sample/feature/home`**

Same conversion for `screen_dashboard`, `screen_item_detail`, `screen_welcome`. The dashboard's
result-intent plumbing (`resultIntentSink` / `resultIntentEffectFlow` in
`sample/feature/home/StrictMviResultIntents.kt`) is sample-local MVI glue, not library API: keep the
file, and have the `@Effects` provider for the dashboard collect `nav.pickSortResults` exactly as
`DashboardEffectHandler` does today.

`modal_image_viewer` and `sheet_filter` are core-mode entries and need only the `ColumnScope`
receiver if their wrapper supplies one.

Run: `./gradlew :sample:feature:home:assembleDebug --console=plain --no-problems-report`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Migrate `sample/feature/profile` and `sample/shopr`**

Same conversion. `sample/shopr`'s `@TopBar`/`@BottomBar` usages in `screen_feed/FeedChrome.kt` and
`ui/ScreenChrome.kt` become the sample's own `@TopBar`/`@BottomBar` markers filled by
`ShoprScreenRoot`'s slots.

Run: `./gradlew :sample:feature:profile:assembleDebug :sample:shopr:assembleDebug --console=plain --no-problems-report`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run every sample test**

Run: `./gradlew :sample:navigation:test :sample:shopr:test :sample:app:test :sample:feature:auth:test --console=plain`
Expected: PASS. `sample/feature/auth/src/test/.../StrictMviMigrationTest.kt` asserts the old MVI
shape; rewrite its assertions against the generated `GezginWrapperEntries.kt` or delete it if its
only subject was `GezginMvi`.

- [ ] **Step 6: Full build**

Run: `./gradlew build --console=plain --no-problems-report`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "refactor(sample): migrate every sample onto the screen-wrapper API"
```

---

## Task 11: Cross-module regression test

**Files:**
- Create: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/WrapperCrossModuleTest.kt`

**Interfaces:**
- Consumes: `CompileHarness.compileGezginModule`.

This locks the discovery behaviour the design depends on, so a KSP upgrade that breaks classpath
package enumeration fails here rather than silently stripping every screen's chrome.

- [ ] **Step 1: Write the test**

```kotlin
package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezginModule
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperCrossModuleTest {

  @Test
  fun `a wrapper compiled into a dependency binds a feature module's screen`() {
    val designSystem =
      compileGezginModule(
        SourceFile.kotlin(
          "DesignSystem.kt",
          """
          package design

          import androidx.compose.runtime.Composable
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.FilledBy
          import dev.gezgin.core.annotation.Screen
          import dev.gezgin.core.annotation.ScreenSlot
          import dev.gezgin.core.annotation.ScreenWrapper
          import kotlin.reflect.KClass

          @ScreenSlot @Repeatable annotation class TopBar(val route: KClass<out Route>)

          @ScreenWrapper
          @Composable
          fun <S> designRoot(
            @FilledBy(TopBar::class) topBar: @Composable (S) -> Unit = {},
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )
    assertEquals(KotlinCompilation.ExitCode.OK, designSystem.exitCode, designSystem.messages)

    val navigation =
      compileGezginModule(
        SourceFile.kotlin(
          "Nav.kt",
          """
          package navigation

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.GoTo
          import dev.gezgin.core.annotation.NavGraph
          import kotlinx.serialization.Serializable

          @NavGraph
          @Serializable
          sealed interface AppGraph : Route {
            @GoTo(DetailRoute::class) @Serializable data object ListRoute : AppGraph

            @Serializable data class DetailRoute(val id: String) : AppGraph
          }
          """
            .trimIndent(),
        )
      )
    assertEquals(KotlinCompilation.ExitCode.OK, navigation.exitCode, navigation.messages)

    val feature =
      compileGezginModule(
        SourceFile.kotlin(
          "Feature.kt",
          """
          package feature

          import androidx.compose.runtime.Composable
          import design.TopBar
          import dev.gezgin.core.annotation.Screen
          import navigation.AppGraph

          data class DetailUiState(val title: String)

          @TopBar(AppGraph.DetailRoute::class)
          @Composable fun detailTopBar(state: DetailUiState) = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable fun detailScreen(state: DetailUiState) = Unit
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.wrapperPackages" to "design", "gezgin.dumpWrapper" to "true"),
        extraClasspath = listOf(designSystem.outputDirectory, navigation.outputDirectory),
      )

    assertEquals(KotlinCompilation.ExitCode.OK, feature.exitCode, feature.messages)
    val generated =
      feature.sourcesGeneratedBySymbolProcessor.first { it.name == "GezginWrapperEntries.kt" }
    val text = generated.readText()
    assertContains(text, "designRoot<DetailUiState>(")
    assertContains(text, "topBar = { state -> detailTopBar(state = state) }")
    assertContains(text, "detailScreen(state = state)")
  }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.WrapperCrossModuleTest" --console=plain`
Expected: PASS. If discovery fails, the failure is in `WrapperModelReader.enumeratePackage` — the
verified fallback is resolving fully-qualified names via `getFunctionDeclarationsByName` and
`getClassDeclarationByName`, both stable API.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessApply
git add gezgin-processor/src/test/
git commit -m "test(processor): lock cross-module wrapper discovery"
```

---

## Task 12: Documentation and release

**Files:**
- Modify: `README.md`, `README.tr.md`, `CHANGELOG.md`, `gradle.properties`, `docs/gezgin-design.md`, `docs/gezgin-by-example.md`, `docs/gezgin-binder-location.md`, `docs/gezgin-zad-root-integration-spec.md`, `sample/README.md`

- [ ] **Step 1: Bump the version**

In `gradle.properties`, set `VERSION_NAME=0.3.0-SNAPSHOT`.

- [ ] **Step 2: Rewrite the module table and the MVI section in both READMEs**

The installation table loses the `gezgin-mvi` row. The "Strict MVI add-on" section is replaced by a
"Screen wrappers" section carrying the `AppScreenRoot` example from Task 8 verbatim, the slot
resolution rule, and the `gezgin.wrapperPackages` option. Add the option to the "KSP options" table:

| Option | Default | When to change |
|---|---|---|
| `gezgin.wrapperPackages` | empty | Comma-separated packages to scan for `@ScreenWrapper` functions and `@ScreenSlot` annotations that are compiled into a dependency rather than declared in this module. |

- [ ] **Step 3: Write the CHANGELOG entry**

Under a new `## 0.3.0` heading, list: `gezgin-mvi` removed; `@ScreenWrapper`/`@ScreenSlot`/
`@FilledBy` added; `@MviViewModel`, `@EffectHandler`, `@TopBar`, `@BottomBar`, `GezginMvi`,
`GezginEffects`, `ObserveEffects` and `@ExperimentalGezginMigrationApi` removed; the generated
double `Column` removed; DI detection removed. Include the five-step per-screen migration from spec
§11.

- [ ] **Step 4: Revise the ZAD integration spec**

`docs/gezgin-zad-root-integration-spec.md` §129–§145 and §244 describe the `@TopBar`/`@BottomBar`
contract as the migration path. Replace those sections with a pointer to
`docs/superpowers/specs/2026-09-11-gezgin-screen-wrapper-design.md` and a note that the audited 25
`ColumnScope` screens now map onto one `ColumnScope` wrapper, and the 14 top-bar / 7 bottom-bar
provider files onto ZAD's own `@TopBar` / `@BottomBar` markers.

- [ ] **Step 5: Verify docs do not reference deleted API**

Run: `grep -rn "gezgin-mvi\|GezginMvi\|@MviViewModel\|ObserveEffects\|ExperimentalGezginMigrationApi" README.md README.tr.md docs/ sample/ --include=*.md --include=*.kt | grep -v "docs/superpowers/"`
Expected: no output. Matches under `docs/superpowers/` are historical records and stay.

- [ ] **Step 6: Full build and commit**

```bash
./gradlew build --console=plain --no-problems-report
./gradlew spotlessApply
git add -A
git commit -m "docs: document screen wrappers and the 0.3.0 removal of gezgin-mvi"
```

---

## Self-Review Notes

**Spec coverage.** §3 removal inventory → Task 9 and Task 12. §4 public API → Task 1. §5 generated
output → Task 7, proven end-to-end in Task 8. §6 slot resolution → Tasks 3, 4, 6. §6.2 roles →
Task 4 (classification) and Task 7 (emission). §7 type parameter resolution → Tasks 5 and 6. §8
discovery → Task 3, locked by Task 11. §9 error catalog: SW1/SW2/SW3/SW9 in Task 3, SW4 in Task 4,
SW5/SW6/SW7/SW8/SW10 in Task 6. §10 entry kinds → Task 9 step 4. §11 migration → Tasks 8, 10, 12.
§12 testing → distributed across every task plus Task 11.

**Known gap accepted.** Spec §10 defers `@FragmentScreen` wrapping; no task covers it, matching the
spec.

**Type consistency.** `WrapperBindingModel.filledSlots` is keyed by wrapper *parameter* name
throughout (Tasks 2, 6, 7). `SlotProviderModel.slotParams` excludes role parameters in both the
reader (Task 4) and the codegen (Task 7). `WrapperModel.contentSlot` is a computed property in
Task 2 and is widened, not replaced, in Task 9.
