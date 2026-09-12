# Generated Route Serializers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the obligation to write `@Serializable` on every route inside a `@NavGraph`, by generating each route's `KSerializer` and registering it explicitly.

**Architecture:** `ModelReader` classifies every persisted type (route constructor parameters and result payloads) into a `SerialKind` while the KSP `Resolver` is still in hand. A shared `SerializerRef` turns a kind into the KotlinPoet code that references that type's serializer — builtins from `kotlinx.serialization.builtins`, `@Serializable` classes through their companion, enums through a generated name-based serializer. `RouteSerializerCodegen` emits one `KSerializer` per route and one per bare enum; `TopologyCodegen` registers those in the polymorphic module and stops using the reflection-capable `serializer<T>()` for result edges.

**Tech Stack:** Kotlin 2.3.21, KSP 2.3.9, KotlinPoet, kotlinx-serialization 1.9.x, kctfork for processor tests, Gradle 9.0.0.

**Spec:** `docs/superpowers/specs/2026-09-12-gezgin-generated-route-serializers-design.md`

## Global Constraints

- Branch: continue on `design/screen-wrapper-de-opinionation` (baseline `30c3c9d`). The 0.3.0 wrapper work is already there and is not affected by this plan.
- JVM toolchain 17 everywhere. `gezgin-core` and `gezgin-processor` declare `explicitPublicApi()`; every new public declaration needs KDoc with `@author @sahsenvar` or `:gezgin-core:checkPublicApiKDoc` fails.
- Everything added in `gezgin-processor` is `internal`. The processor must gain no new public surface, so `apiDump` should show no processor change.
- Run `./gradlew spotlessApply` before every commit; `spotlessCheck` is part of `build`.
- **No production comment may contain a bare token matching `[A-Z]{2,3}[0-9]+`** (e.g. `KSP2`). `MaintainedProductionCommentContractTest` fails on it. Write `KSP`.
- After any change to `build.gradle.kts` module lists or published API, run `./gradlew -p buildSrc test` — the contract tests there encode the published-module set and the README coordinates.
- Processor tests run under kctfork with no Compose compiler plugin. Use `CompileHarness.compileGezgin` / `compileGezginModule`; add `kspArgs = mapOf("gezgin.emitEntries" to "false")` when a fixture would otherwise emit an entry body the bare JVM backend cannot inline.
- New error codes use the `SZ` prefix, emitted as `logger.error("[SZn] message")`.
- Verify with `nohup ./gradlew … > /tmp/x.log 2>&1 &` plus a `sleep` and a `grep`; a long foreground Gradle run kills the context-mode MCP connection.

---

## Wire-format decision (read before Task 3)

Gezgin's `Json` uses kotlinx's default `encodeDefaults = false`. A route parameter that has a Kotlin
default is therefore **absent** from today's encoded form:

```kotlin
data class WelcomeScreenRoute(val name: String? = null) : HomeGraph
// today encodes as {"type":"…WelcomeScreenRoute"} — no "name" element
```

A generated serializer cannot reproduce that, because KSP exposes `hasDefault` but not the default
*expression*, so it cannot reconstruct the value when the element is missing, and it cannot
conditionally omit a constructor argument without branching over every subset of parameters.

**Decision: the generated serializer always writes every element and requires every element on
read.** Consequences, both accepted:

- The encoded form gains the previously-omitted defaulted fields. This is forward-compatible: a new
  snapshot decodes fine.
- A snapshot written by 0.2.x for a route that *has* a defaulted parameter fails to decode and falls
  back to a fresh start — the documented behaviour for an incompatible snapshot
  (`decodeNavigatorStateOrNull`). Routes without defaulted parameters decode unchanged.

This narrows the spec's §4.1 claim, and Task 10 amends the spec to say so.

---

## File Structure

**New in `gezgin-processor`:**
- `serial/SerialKind.kt` — the classification of a persisted type.
- `serial/SerialTypeClassifier.kt` — `KSType` → `SerialKind`, run by `ModelReader`.
- `codegen/SerializerRef.kt` — `SerialKind` + `TypeName` → the `CodeBlock` that references a serializer. Shared by route serializers and topology edges.
- `codegen/RouteSerializerCodegen.kt` — emits `GezginRouteSerializers.kt`: one `KSerializer` per route that needs one, plus one per bare enum.

**Modified:**
- `model/GraphModel.kt` — `ParamModel` gains `kind`; `RouteModel` and `GraphModelNode` gain `resultTypeKind`.
- `model/ModelReader.kt` — classify at read time.
- `codegen/TopologyCodegen.kt` — `generateSerializers` registers explicit serializers; result edges use `SerializerRef`.
- `Validation.kt` — `SZ1`.
- Every graph in `sample/`, plus `sample/navigation/build.gradle.kts`.

---

## Task 1: SerialKind and the classifier

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/serial/SerialKind.kt`
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/serial/SerialTypeClassifier.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/model/GraphModel.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/model/ModelReader.kt:235-245`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/SerialTypeClassifierTest.kt`

**Interfaces:**
- Produces: `internal sealed interface SerialKind` with `Builtin(val fq: String)`, `SerializableClass`, `BareEnum`, `ListOf(val element: SerialKind)`, `Unsupported(val reason: String)`; `internal object SerialTypeClassifier { fun classify(type: KSType): SerialKind }`; `ParamModel.kind: SerialKind`; `RouteModel.resultTypeKind: SerialKind?`; `GraphModelNode.resultTypeKind: SerialKind?`.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import kotlin.test.Test
import kotlin.test.assertContains
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class SerialTypeClassifierTest {

  private val fixture =
    SourceFile.kotlin(
      "Fixture.kt",
      """
      package app

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import kotlinx.serialization.Serializable

      @Serializable data class Filter(val query: String)

      enum class Sort { A, B }

      @Serializable enum class AnnotatedSort { A, B }

      class Opaque(val x: Int)

      @NavGraph
      sealed interface AppGraph : Route {
        @GoTo(Kinds::class) data object Start : AppGraph

        data class Kinds(
          val text: String,
          val maybe: String?,
          val count: Int,
          val sort: Sort,
          val annotated: AnnotatedSort,
          val filter: Filter,
          val tags: List<String>,
        ) : AppGraph
      }
      """
        .trimIndent(),
    )

  @Test
  fun `each parameter type is classified by its persisted shape`() {
    compileGezgin(
      fixture,
      kspArgs = mapOf("gezgin.dumpModel" to "true", "gezgin.emitEntries" to "false"),
    )

    val dump = findGeneratedResource("GezginModelDump.txt")!!.readText()
    assertContains(dump, "text: Builtin(kotlin.String)")
    assertContains(dump, "maybe: Builtin(kotlin.String)?")
    assertContains(dump, "count: Builtin(kotlin.Int)")
    assertContains(dump, "sort: BareEnum")
    assertContains(dump, "annotated: SerializableClass")
    assertContains(dump, "filter: SerializableClass")
    assertContains(dump, "tags: ListOf(Builtin(kotlin.String))")
  }

  @Test
  fun `a type that cannot be persisted is classified Unsupported`() {
    val withOpaque =
      SourceFile.kotlin(
        "Opaque.kt",
        """
        package app

        import dev.gezgin.core.Route

        data class OpaqueRoute(val opaque: Opaque) : AppGraph
        """
          .trimIndent(),
      )

    compileGezgin(
      fixture,
      withOpaque,
      kspArgs = mapOf("gezgin.dumpModel" to "true", "gezgin.emitEntries" to "false"),
    )

    val dump = findGeneratedResource("GezginModelDump.txt")!!.readText()
    assertContains(dump, "opaque: Unsupported")
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.SerialTypeClassifierTest" --console=plain --no-problems-report > /tmp/t1.log 2>&1 &` then `sleep 120; grep -aE "BUILD|^e: " /tmp/t1.log | head`
Expected: FAIL — the dump has no `: Builtin(` lines.

- [ ] **Step 3: Create `SerialKind.kt`**

```kotlin
package dev.gezgin.processor.serial

/**
 * How a persisted type — a route constructor parameter or a result payload — reaches its
 * serializer. Classified while the KSP `Resolver` is in hand; the codegen only reads it.
 */
internal sealed interface SerialKind {
  /** A Kotlin primitive with a `kotlinx.serialization.builtins` serializer extension. */
  data class Builtin(val fq: String) : SerialKind

  /** A non-generic class (or enum) carrying `@Serializable`; reached through its companion. */
  data object SerializableClass : SerialKind

  /** An enum WITHOUT `@Serializable`; Gezgin generates a name-based serializer for it. */
  data object BareEnum : SerialKind

  data class ListOf(val element: SerialKind) : SerialKind

  /** No serializer can be referenced; reported as `SZ1`. */
  data class Unsupported(val reason: String) : SerialKind
}
```

- [ ] **Step 4: Create `SerialTypeClassifier.kt`**

```kotlin
package dev.gezgin.processor.serial

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

private const val SERIALIZABLE_FQ = "kotlinx.serialization.Serializable"
private const val LIST_FQ = "kotlin.collections.List"

private val BUILTIN_FQS =
  setOf(
    "kotlin.String",
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Boolean",
    "kotlin.Float",
    "kotlin.Double",
    "kotlin.Short",
    "kotlin.Byte",
    "kotlin.Char",
  )

/**
 * Decides how a persisted type reaches its serializer. The order matters: a builtin is never
 * `@Serializable`, and an annotated enum is reached through its companion rather than a generated
 * serializer.
 */
internal object SerialTypeClassifier {

  fun classify(type: KSType): SerialKind {
    val declaration = type.declaration as? KSClassDeclaration
      ?: return SerialKind.Unsupported("not a class")
    val fq = declaration.qualifiedName?.asString()
      ?: return SerialKind.Unsupported("no qualified name")

    if (fq in BUILTIN_FQS) return SerialKind.Builtin(fq)

    if (fq == LIST_FQ) {
      val element = type.arguments.singleOrNull()?.type?.resolve()
        ?: return SerialKind.Unsupported("List without a resolvable element type")
      val elementKind = classify(element)
      return if (elementKind is SerialKind.Unsupported) {
        SerialKind.Unsupported("List element: ${elementKind.reason}")
      } else {
        SerialKind.ListOf(elementKind)
      }
    }

    if (declaration.typeParameters.isNotEmpty()) {
      return SerialKind.Unsupported("generic types are not supported")
    }

    val annotated =
      declaration.annotations.any {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIALIZABLE_FQ
      }
    if (annotated) return SerialKind.SerializableClass
    if (declaration.classKind == ClassKind.ENUM_CLASS) return SerialKind.BareEnum

    return SerialKind.Unsupported("not @Serializable and not an enum")
  }
}
```

- [ ] **Step 5: Carry the kind on the model**

In `model/GraphModel.kt`, add `val kind: SerialKind` as the last property of `ParamModel`, and add
`val resultTypeKind: SerialKind? = null` to both `RouteModel` and `GraphModelNode` immediately after
their existing `resultTypeFq`. Import `dev.gezgin.processor.serial.SerialKind`.

In `model/ModelReader.kt:235-245`, classify while resolving:

```kotlin
  private fun ctorParamsOf(decl: KSClassDeclaration): List<ParamModel> =
    decl.primaryConstructor?.parameters.orEmpty().map { param ->
      val resolved = param.type.resolve()
      ParamModel(
        name = param.name?.asString().orEmpty(),
        typeFq = resolved.declaration.qualifiedName?.asString() ?: resolved.toString(),
        typeName = resolved.toTypeName(),
        isNullable = resolved.isMarkedNullable,
        hasDefault = param.hasDefault,
        kind = SerialTypeClassifier.classify(resolved),
      )
    }
```

Set `resultTypeKind` wherever `resultTypeFq` is set (`ModelReader.kt:174` for graphs and the
matching route path) by classifying the same resolved `KSType`.

- [ ] **Step 6: Show the kind in the model dump**

In `model/ModelDump.kt`, extend the parameter line so the test above can assert on it: print each
parameter as `<name>: <kind><"?" when isNullable>`, where `Builtin` renders as `Builtin(<fq>)`.

- [ ] **Step 7: Run tests to verify they pass**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.SerialTypeClassifierTest" --console=plain --no-problems-report > /tmp/t1.log 2>&1 &` then `sleep 120; grep -aE "BUILD" /tmp/t1.log`
Expected: BUILD SUCCESSFUL, 2 tests.

- [ ] **Step 8: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add gezgin-processor
git commit -m "feat(processor): classify every persisted type into a SerialKind"
```

---

## Task 2: SerializerRef

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/SerializerRef.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/SerializerRefTest.kt`

**Interfaces:**
- Consumes: `SerialKind` from Task 1.
- Produces: `internal object SerializerRef { fun of(kind: SerialKind, typeName: TypeName, isNullable: Boolean): CodeBlock; fun enumSerializerName(typeName: TypeName): ClassName }`.

This is a pure function over KotlinPoet types, so it is unit-tested with no compilation.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.gezgin.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dev.gezgin.processor.codegen.SerializerRef
import dev.gezgin.processor.serial.SerialKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializerRefTest {

  @Test
  fun `a builtin resolves through the builtins extension`() {
    val ref = SerializerRef.of(SerialKind.Builtin("kotlin.String"), STRING, isNullable = false)

    assertEquals("kotlin.String.serializer()", ref.toString())
  }

  @Test
  fun `a nullable type wraps the reference`() {
    val ref = SerializerRef.of(SerialKind.Builtin("kotlin.Int"), INT, isNullable = true)

    assertEquals("kotlin.Int.serializer().nullable", ref.toString())
  }

  @Test
  fun `a serializable class resolves through its companion`() {
    val filter = ClassName("app", "Filter")

    val ref = SerializerRef.of(SerialKind.SerializableClass, filter, isNullable = false)

    assertEquals("app.Filter.serializer()", ref.toString())
  }

  @Test
  fun `a bare enum resolves to its generated serializer`() {
    val sort = ClassName("app", "Sort")

    val ref = SerializerRef.of(SerialKind.BareEnum, sort, isNullable = false)

    assertEquals("app.SortGezginSerializer", ref.toString())
  }

  @Test
  fun `a list wraps its element reference`() {
    val ref =
      SerializerRef.of(
        SerialKind.ListOf(SerialKind.Builtin("kotlin.String")),
        LIST.parameterizedBy(STRING),
        isNullable = false,
      )

    assertEquals("kotlinx.serialization.builtins.ListSerializer(kotlin.String.serializer())", ref.toString())
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.SerializerRefTest" --console=plain --no-problems-report > /tmp/t2.log 2>&1 &` then `sleep 120; grep -aE "BUILD|^e: " /tmp/t2.log | head`
Expected: FAIL — `Unresolved reference: SerializerRef`.

- [ ] **Step 3: Implement `SerializerRef.kt`**

```kotlin
package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import dev.gezgin.processor.serial.SerialKind

private const val BUILTINS_PKG = "kotlinx.serialization.builtins"

private val BUILTIN_SERIALIZER = MemberName(BUILTINS_PKG, "serializer")
private val LIST_SERIALIZER = MemberName(BUILTINS_PKG, "ListSerializer")
private val NULLABLE = MemberName(BUILTINS_PKG, "nullable")

/**
 * The code that references a type's serializer. Deliberately explicit: the reified
 * `kotlinx.serialization.serializer<T>()` would resolve too, but without the serialization compiler
 * plugin it falls back to reflection, which is JVM-only and would break both multiplatform support
 * and a graph module's ability to drop the plugin.
 */
internal object SerializerRef {

  fun of(kind: SerialKind, typeName: TypeName, isNullable: Boolean): CodeBlock {
    val bare = bareOf(kind, typeName)
    return if (isNullable) CodeBlock.of("%L.%M", bare, NULLABLE) else bare
  }

  /** `app.Sort` -> `app.SortGezginSerializer`, the object emitted by RouteSerializerCodegen. */
  fun enumSerializerName(typeName: TypeName): ClassName {
    val className = typeName.copy(nullable = false) as ClassName
    return ClassName(className.packageName, className.simpleNames.joinToString("") + "GezginSerializer")
  }

  private fun bareOf(kind: SerialKind, typeName: TypeName): CodeBlock =
    when (kind) {
      is SerialKind.Builtin -> CodeBlock.of("%T.%M()", typeName.copy(nullable = false), BUILTIN_SERIALIZER)
      SerialKind.SerializableClass ->
        CodeBlock.of("%T.serializer()", typeName.copy(nullable = false))
      SerialKind.BareEnum -> CodeBlock.of("%T", enumSerializerName(typeName))
      is SerialKind.ListOf -> {
        val element = (typeName.copy(nullable = false) as ParameterizedTypeName).typeArguments.single()
        CodeBlock.of("%M(%L)", LIST_SERIALIZER, bareOf(kind.element, element))
      }
      is SerialKind.Unsupported ->
        error("SerializerRef.of called for an unsupported kind: ${kind.reason}")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.SerializerRefTest" --console=plain --no-problems-report > /tmp/t2.log 2>&1 &` then `sleep 120; grep -aE "BUILD" /tmp/t2.log`
Expected: BUILD SUCCESSFUL, 5 tests.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add gezgin-processor
git commit -m "feat(processor): emit explicit serializer references instead of reified lookups"
```

---

## Task 3: Route and enum serializer codegen

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/RouteSerializerCodegen.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/GezginProcessor.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/RouteSerializerCodegenTest.kt`

**Interfaces:**
- Consumes: `SerializerRef`, `SerialKind`, `RouteModel.ctorParams`.
- Produces: `internal object RouteSerializerCodegen { fun generate(model: GraphModel, packageName: String): FileSpec? }` writing `GezginRouteSerializers.kt`; a route's serializer object is named `<RouteSimpleName>GezginSerializer` in `packageName`; an enum's is `<EnumSimpleNames joined>GezginSerializer` in the enum's own package.

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
class RouteSerializerCodegenTest {

  private val fixture =
    SourceFile.kotlin(
      "Fixture.kt",
      """
      package app

      import dev.gezgin.core.Route
      import dev.gezgin.core.annotation.GoTo
      import dev.gezgin.core.annotation.NavGraph
      import kotlinx.serialization.Serializable

      @Serializable data class Filter(val query: String)

      enum class Sort { A, B }

      @NavGraph
      sealed interface AppGraph : Route {
        @GoTo(Detail::class) data object Start : AppGraph

        data class Detail(
          val id: String,
          val name: String?,
          val sort: Sort,
          val filter: Filter,
        ) : AppGraph
      }
      """
        .trimIndent(),
    )

  @Test
  fun `a route with no @Serializable gets a generated serializer`() {
    val result = compileGezgin(fixture, kspArgs = mapOf("gezgin.emitEntries" to "false"))
    val generated = result.generatedSourceFor("GezginRouteSerializers.kt")

    assertNotNull(generated, "GezginRouteSerializers.kt missing: ${result.messages}")
    val text = generated.readText()
    assertContains(text, "internal object DetailGezginSerializer : KSerializer<AppGraph.Detail>")
    assertContains(text, """buildClassSerialDescriptor("app.AppGraph.Detail")""")
    assertContains(text, "kotlin.String.serializer()")
    assertContains(text, "kotlin.String.serializer().nullable")
    assertContains(text, "SortGezginSerializer")
    assertContains(text, "Filter.serializer()")
  }

  @Test
  fun `a parameterless route gets an empty descriptor`() {
    val text =
      compileGezgin(fixture, kspArgs = mapOf("gezgin.emitEntries" to "false"))
        .generatedSourceFor("GezginRouteSerializers.kt")!!
        .readText()

    assertContains(text, "internal object StartGezginSerializer : KSerializer<AppGraph.Start>")
    assertContains(text, """buildClassSerialDescriptor("app.AppGraph.Start")""")
  }

  @Test
  fun `a bare enum gets a name-based serializer`() {
    val text =
      compileGezgin(fixture, kspArgs = mapOf("gezgin.emitEntries" to "false"))
        .generatedSourceFor("GezginRouteSerializers.kt")!!
        .readText()

    assertContains(text, "internal object SortGezginSerializer : KSerializer<Sort>")
    assertContains(text, "encoder.encodeString(value.name)")
    assertContains(text, "Sort.valueOf(decoder.decodeString())")
  }

  @Test
  fun `a route that still declares @Serializable gets no generated serializer`() {
    val annotated =
      SourceFile.kotlin(
        "Annotated.kt",
        """
        package app2

        import dev.gezgin.core.Route
        import dev.gezgin.core.annotation.GoTo
        import dev.gezgin.core.annotation.NavGraph
        import kotlinx.serialization.Serializable

        @NavGraph
        sealed interface OldGraph : Route {
          @GoTo(Second::class) @Serializable data object First : OldGraph

          @Serializable data class Second(val id: String) : OldGraph
        }
        """
          .trimIndent(),
      )

    val text =
      compileGezgin(annotated, kspArgs = mapOf("gezgin.emitEntries" to "false"))
        .generatedSourceFor("GezginRouteSerializers.kt")
        ?.readText()
        .orEmpty()

    kotlin.test.assertFalse(text.contains("SecondGezginSerializer"), text)
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.RouteSerializerCodegenTest" --console=plain --no-problems-report > /tmp/t3.log 2>&1 &` then `sleep 130; grep -aE "BUILD|^e: " /tmp/t3.log | head`
Expected: FAIL — `GezginRouteSerializers.kt missing`.

- [ ] **Step 3: Implement `RouteSerializerCodegen.kt`**

Emit one file per graph package. For every route that does NOT carry `@Serializable`, emit:

```kotlin
internal object DetailGezginSerializer : KSerializer<AppGraph.Detail> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("app.AppGraph.Detail") {
      element("id", kotlin.String.serializer().descriptor)
      element("name", kotlin.String.serializer().nullable.descriptor)
      element("sort", SortGezginSerializer.descriptor)
      element("filter", Filter.serializer().descriptor)
    }

  override fun serialize(encoder: Encoder, value: AppGraph.Detail) {
    encoder.encodeStructure(descriptor) {
      encodeSerializableElement(descriptor, 0, kotlin.String.serializer(), value.id)
      encodeSerializableElement(descriptor, 1, kotlin.String.serializer().nullable, value.name)
      encodeSerializableElement(descriptor, 2, SortGezginSerializer, value.sort)
      encodeSerializableElement(descriptor, 3, Filter.serializer(), value.filter)
    }
  }

  override fun deserialize(decoder: Decoder): AppGraph.Detail =
    decoder.decodeStructure(descriptor) {
      var id: String? = null
      var seen0 = false
      var name: String? = null
      var seen1 = false
      var sort: Sort? = null
      var seen2 = false
      var filter: Filter? = null
      var seen3 = false
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> { id = decodeSerializableElement(descriptor, 0, kotlin.String.serializer()); seen0 = true }
          1 -> { name = decodeSerializableElement(descriptor, 1, kotlin.String.serializer().nullable); seen1 = true }
          2 -> { sort = decodeSerializableElement(descriptor, 2, SortGezginSerializer); seen2 = true }
          3 -> { filter = decodeSerializableElement(descriptor, 3, Filter.serializer()); seen3 = true }
          -1 -> break
          else -> throw SerializationException("unexpected index $index")
        }
      }
      if (!seen0) throw SerializationException("app.AppGraph.Detail: missing 'id'")
      if (!seen1) throw SerializationException("app.AppGraph.Detail: missing 'name'")
      if (!seen2) throw SerializationException("app.AppGraph.Detail: missing 'sort'")
      if (!seen3) throw SerializationException("app.AppGraph.Detail: missing 'filter'")
      AppGraph.Detail(id = id as String, name = name, sort = sort as Sort, filter = filter as Filter)
    }
}
```

The `seen` flags are why a nullable parameter still needs one: `null` is a legal decoded value, so
absence cannot be inferred from the holder. A parameterless route emits an empty
`buildClassSerialDescriptor("<fq>")`, a `serialize` that opens and closes an empty structure, and a
`deserialize` that drains the structure and returns the object.

For each distinct `SerialKind.BareEnum` type reachable from any route parameter or result type,
emit once, into the ENUM's own package:

```kotlin
internal object SortGezginSerializer : KSerializer<Sort> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("app.Sort", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Sort) = encoder.encodeString(value.name)

  override fun deserialize(decoder: Decoder): Sort = Sort.valueOf(decoder.decodeString())
}
```

A route is skipped when its declaration carries `kotlinx.serialization.Serializable`; add
`val isSerializable: Boolean` to `RouteModel`, set by `ModelReader` from the same annotation check
the classifier uses.

- [ ] **Step 4: Wire into the processor**

In `GezginProcessor.kt`, inside the `if (model.graphs.isNotEmpty())` block and immediately before
the `generateSerializers` call:

```kotlin
RouteSerializerCodegen.generate(model, packageName)
  ?.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
```

Emission is gated by the same `emitSerializers` option that already gates `generateSerializers`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.RouteSerializerCodegenTest" --console=plain --no-problems-report > /tmp/t3.log 2>&1 &` then `sleep 130; grep -aE "BUILD" /tmp/t3.log`
Expected: BUILD SUCCESSFUL, 4 tests.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add gezgin-processor
git commit -m "feat(processor): generate a KSerializer per route and per bare enum"
```

---

## Task 4: Register the generated serializers

**Files:**
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/TopologyCodegen.kt:139-164`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/TopologyCodegenTest.kt`

**Interfaces:**
- Consumes: `RouteSerializerCodegen`'s naming (`<RouteSimpleName>GezginSerializer`), `RouteModel.isSerializable`.

- [ ] **Step 1: Write the failing test**

Add to the existing `TopologyCodegenTest`:

```kotlin
  @Test
  fun `the module registers a generated serializer for a route without @Serializable`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Mixed.kt",
          """
          package app

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.GoTo
          import dev.gezgin.core.annotation.NavGraph
          import kotlinx.serialization.Serializable

          @NavGraph
          sealed interface AppGraph : Route {
            @GoTo(Plain::class) @Serializable data object Annotated : AppGraph

            data class Plain(val id: String) : AppGraph
          }
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    val text = result.generatedSourceFor("GezginSerializers.kt")!!.readText()
    assertContains(text, "subclass(AppGraph.Plain::class, PlainGezginSerializer)")
    assertContains(text, "subclass(AppGraph.Annotated::class)")
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.TopologyCodegenTest" --console=plain --no-problems-report > /tmp/t4.log 2>&1 &` then `sleep 130; grep -aE "BUILD|expected" /tmp/t4.log | head`
Expected: FAIL — the module emits the bare `subclass(AppGraph.Plain::class)`.

- [ ] **Step 3: Implement**

In `generateSerializers`, replace the single `addStatement` with a branch:

```kotlin
    model.routes.forEach { route ->
      val routeClass = ClassName.bestGuess(route.fqName)
      if (route.isSerializable) {
        polymorphicBody.addStatement("%M(%T::class)", SUBCLASS, routeClass)
      } else {
        polymorphicBody.addStatement(
          "%M(%T::class, %T)",
          SUBCLASS,
          routeClass,
          RouteSerializerCodegen.serializerName(route, packageName),
        )
      }
    }
```

Add `internal fun serializerName(route: RouteModel, packageName: String): ClassName` to
`RouteSerializerCodegen`, returning `ClassName(packageName, route.simpleName + "GezginSerializer")`,
so the two codegens cannot disagree on the name.

- [ ] **Step 4: Run test to verify it passes**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.TopologyCodegenTest" --console=plain --no-problems-report > /tmp/t4.log 2>&1 &` then `sleep 130; grep -aE "BUILD" /tmp/t4.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add gezgin-processor
git commit -m "feat(processor): register generated route serializers in the polymorphic module"
```

---

## Task 5: Result edges stop using the reified lookup

**Files:**
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/TopologyCodegen.kt:29-36,205-230`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/TopologyCodegenTest.kt`

**Interfaces:**
- Consumes: `SerializerRef.of`, `RouteModel.resultTypeKind` / `GraphModelNode.resultTypeKind`.

- [ ] **Step 1: Write the failing test**

```kotlin
  @Test
  fun `a result edge references an explicit serializer rather than the reified lookup`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Results.kt",
          """
          package app

          import dev.gezgin.core.ResultRoute
          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.GoForResult
          import dev.gezgin.core.annotation.NavGraph

          enum class Sort { A, B }

          @NavGraph
          sealed interface AppGraph : Route {
            @GoForResult(Picker::class, name = "pickSort") data object Home : AppGraph

            data class Picker(val current: String) : AppGraph, ResultRoute<Sort>
          }
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    val text = result.generatedSourceFor("GezginGenerated.kt")!!.readText()
    assertContains(text, "SortGezginSerializer")
    kotlin.test.assertFalse(text.contains("serializer<"), text)
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.TopologyCodegenTest" --console=plain --no-problems-report > /tmp/t5.log 2>&1 &` then `sleep 130; grep -aE "BUILD|expected" /tmp/t5.log | head`
Expected: FAIL — the emitted edge still reads `serializer<Sort>()`.

- [ ] **Step 3: Implement**

Delete the `SERIALIZER` `MemberName` and its KDoc at `TopologyCodegen.kt:29-36`. At the edge-emission
site (around line 226), replace the `serializer<T>()` reference with:

```kotlin
SerializerRef.of(resultTypeKind, ClassName.bestGuess(resultTypeFq), isNullable = false)
```

carrying `resultTypeKind` from whichever of `graphsByFq[edge.targetFq]` or
`routesByFq[edge.targetFq]` supplied `resultTypeFq`.

- [ ] **Step 4: Run test to verify it passes**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.TopologyCodegenTest" --console=plain --no-problems-report > /tmp/t5.log 2>&1 &` then `sleep 130; grep -aE "BUILD" /tmp/t5.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add gezgin-processor
git commit -m "feat(processor): reference result serializers explicitly, never reflectively"
```

---

## Task 6: SZ1 validation

**Files:**
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/Validation.kt`
- Test: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/ValidationTest.kt`

**Interfaces:**
- Consumes: `SerialKind.Unsupported`.

- [ ] **Step 1: Write the failing test**

```kotlin
  @Test
  fun `SZ1 — a route parameter with no serializer is rejected`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Opaque.kt",
          """
          package app

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.GoTo
          import dev.gezgin.core.annotation.NavGraph

          class Opaque(val x: Int)

          @NavGraph
          sealed interface AppGraph : Route {
            @GoTo(Detail::class) data object Start : AppGraph

            data class Detail(val opaque: Opaque) : AppGraph
          }
          """
            .trimIndent(),
        ),
        kspArgs = mapOf("gezgin.emitEntries" to "false"),
      )

    assertContains(result.messages, "[SZ1]")
    assertContains(result.messages, "opaque")
    assertContains(result.messages, "app.Opaque")
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.ValidationTest" --console=plain --no-problems-report > /tmp/t6.log 2>&1 &` then `sleep 130; grep -aE "BUILD|expected" /tmp/t6.log | head`
Expected: FAIL — no `[SZ1]` in the messages.

- [ ] **Step 3: Implement**

In `GezginValidator`, add a rule that walks every route's `ctorParams` and every non-null
`resultTypeKind`, and for each `SerialKind.Unsupported` reports:

```kotlin
error(
  "SZ1",
  "${route.fqName}: parameter '${param.name}' of type ${param.typeFq} cannot be persisted " +
    "(${(param.kind as SerialKind.Unsupported).reason}). A route parameter must be a Kotlin " +
    "primitive, an enum, a List of those, or a non-generic class annotated @Serializable.",
)
```

Skip routes that carry `@Serializable` themselves — their serializer comes from the compiler plugin,
which reports its own error.

- [ ] **Step 4: Run test to verify it passes**

Run: `nohup ./gradlew :gezgin-processor:test --tests "dev.gezgin.processor.ValidationTest" --console=plain --no-problems-report > /tmp/t6.log 2>&1 &` then `sleep 130; grep -aE "BUILD" /tmp/t6.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add gezgin-processor
git commit -m "feat(processor): reject a route parameter that cannot be persisted (SZ1)"
```

---

## Task 7: Round-trip and compatibility tests in core

**Files:**
- Create: `gezgin-core/src/commonTest/kotlin/dev/gezgin/core/GeneratedRouteSerializerRoundTripTest.kt`

**Interfaces:**
- Consumes: `encodeNavigatorState` / `decodeNavigatorState` (both `internal` to `gezgin-core`).

This is the test the design's §9 probe became. It uses hand-written serializers shaped exactly like
Task 3's output, because `gezgin-core` has no KSP round of its own.

- [ ] **Step 1: Write the test**

Port the probe verbatim: a `sealed interface SpikeGraph : Route` with **no** `@Serializable`, a
`data object` route and a `data class` route whose parameters are `String`, `String?`, a bare enum
and an `@Serializable` data class; hand-written `KSerializer` objects in Task 3's shape; a
`SerializersModule` registering them with `subclass(X::class, XSerializer)`; then:

```kotlin
  @Test
  fun `a route with no @Serializable round-trips through the real save and restore path`() {
    val topology = GezginTopology(emptyMap(), emptyMap(), emptyMap())
    val navigator = RawNavigator(start = SpikeGraph.ListRoute, topology = topology, onRootBack = {})
    val detail =
      SpikeGraph.DetailRoute("item-42", null, SortOrder.PRICE_ASC, ComplexFilter("shoes", 3))
    navigator.navigate(detail, singleTop = true)

    val encoded = encodeNavigatorState(navigator, spikeJson)
    val restored =
      decodeNavigatorState(encoded, SpikeGraph.ListRoute, topology, spikeJson, onRootBack = {})

    assertEquals(listOf<Route>(SpikeGraph.ListRoute, detail), restored.backStack.value)
  }
```

- [ ] **Step 2: Add the serial-name compatibility test**

```kotlin
  @Test
  fun `a snapshot written by an @Serializable route decodes with the generated serializer`() {
    // Produced by kotlinx for the same route shape; the discriminator is the FQ name.
    val legacy =
      """{"keys":[{"route":{"type":"dev.gezgin.core.XGraph.Detail","id":"a","name":null,""" +
        """"order":"RELEVANCE","filter":{"query":"q","page":1}},"id":0}],""" +
        """"nextId":1,"pendingSlots":[]}"""

    val restored =
      decodeNavigatorState(legacy, SpikeGraph.ListRoute, topology, spikeJson, onRootBack = {})

    assertEquals(1, restored.backStack.value.size)
  }
```

Name the hand-written descriptors `dev.gezgin.core.XGraph.Detail` so this literal matches; that is
the point of the test — the serial name is the FQ name and nothing else.

- [ ] **Step 3: Run tests**

Run: `nohup ./gradlew :gezgin-core:jvmTest --tests "dev.gezgin.core.GeneratedRouteSerializerRoundTripTest" --console=plain --no-problems-report > /tmp/t7.log 2>&1 &` then `sleep 140; grep -aE "BUILD" /tmp/t7.log`
Expected: BUILD SUCCESSFUL, 2 tests.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add gezgin-core
git commit -m "test(core): lock the generated-serializer round trip and its serial names"
```

---

## Task 8: Drop the annotation from sample/hello

**Files:**
- Modify: `sample/hello/src/main/kotlin/dev/gezgin/sample/hello/nav/HelloGraph.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–6.

`sample/hello` is the smallest graph and the first end-to-end proof that a real Gradle build
produces compilable serializers.

- [ ] **Step 1: Remove the annotations**

Delete `@Serializable` from the `HelloGraph` interface and from both routes, and delete the
`import kotlinx.serialization.Serializable`.

- [ ] **Step 2: Build**

Run: `nohup ./gradlew :sample:hello:assembleDebug --console=plain --no-problems-report > /tmp/t8.log 2>&1 &` then `sleep 150; grep -aE "BUILD|^e: |\[SZ" /tmp/t8.log | head`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Read the generated serializer**

```bash
cat sample/hello/build/generated/ksp/debug/kotlin/dev/gezgin/sample/hello/nav/GezginRouteSerializers.kt
```

Expected: `ContactDetailScreenRouteGezginSerializer` with a `contactId` element, and
`GezginSerializers.kt` registering both routes with their generated serializers.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add sample/hello
git commit -m "refactor(sample): drop @Serializable from the hello graph"
```

---

## Task 9: Drop the annotation from every remaining sample

**Files:**
- Modify: `sample/navigation/src/main/kotlin/dev/gezgin/sample/navigation/{AuthGraph,HomeGraph,ProfileGraph,AvatarFlow,SignUpFlow}.kt`
- Modify: `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/nav/ShopGraph.kt`
- Modify: `sample/domain/src/main/kotlin/dev/gezgin/sample/domain/model/{SortOrder,NotificationLevel}.kt`
- Modify: `sample/navigation/build.gradle.kts`

- [ ] **Step 1: Remove the annotations from the graphs**

Delete `@Serializable` from every `@NavGraph`/`@FlowGraph` interface and every route inside them,
plus the now-unused imports. Leave `AvatarChoice` and `OrderId` annotated — they are parameter and
result types, which is where the annotation belongs.

- [ ] **Step 2: Remove `@Serializable` from the two enums**

`SortOrder` and `NotificationLevel` are used only as a route parameter and result types, so the
generated enum serializer covers them.

- [ ] **Step 3: Drop the serialization plugin from the graph module**

In `sample/navigation/build.gradle.kts`, delete `alias(libs.plugins.kotlin.serialization)`. The
module now declares no `@Serializable` type of its own; its parameter types live in `:sample:domain`.

- [ ] **Step 4: Build everything**

Run: `nohup ./gradlew build --console=plain --no-problems-report > /tmp/t9.log 2>&1 &` then `sleep 290; grep -aE "BUILD|^e: |\[SZ" /tmp/t9.log | head`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Confirm the plugin removal actually held**

```bash
grep -c serialization sample/navigation/build.gradle.kts
```
Expected: `0`.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add sample
git commit -m "refactor(sample): drop @Serializable from every graph and the navigation module's plugin"
```

---

## Task 10: Documentation

**Files:**
- Modify: `README.md`, `README.tr.md`, `sample/README.md`, `CHANGELOG.md`
- Modify: `docs/superpowers/specs/2026-09-12-gezgin-generated-route-serializers-design.md`

- [ ] **Step 1: Update the graph examples in both READMEs**

Every `@Serializable`-on-a-route in a README code block loses the annotation. The installation
snippet keeps `kotlin("plugin.serialization")` but gains a sentence: it is required only in modules
that declare `@Serializable` types — a graph module that only references them does not need it.

- [ ] **Step 2: Add the CHANGELOG entry**

Under the existing `## [0.3.0]`, in `### Removed`, add: a route inside a `@NavGraph` no longer needs
`@Serializable`, nor does an enum used only as a route parameter or result type, nor does a graph
module that declares no `@Serializable` type of its own need the serialization Gradle plugin.
In `### Added`, add `SZ1`.

- [ ] **Step 3: Amend the spec's §4.1**

Replace the compatibility paragraph with the wire-format decision from this plan's preamble: the
generated serializer always writes every element, so a 0.2.x snapshot of a route that has a
defaulted parameter falls back to a fresh start, while routes without defaults decode unchanged.

- [ ] **Step 4: Verify no stale text remains**

```bash
grep -rn "@Serializable" README.md README.tr.md sample/README.md | grep -v "parameter\|payload\|ComplexFilter\|AvatarChoice"
```
Expected: no line showing `@Serializable` on a route declaration.

- [ ] **Step 5: Full verification**

Run: `nohup ./gradlew clean build --console=plain --no-problems-report > /tmp/t10.log 2>&1 &` then `sleep 295; grep -aE "BUILD" /tmp/t10.log` and `nohup ./gradlew -p buildSrc test --console=plain > /tmp/t10b.log 2>&1 &` then `sleep 120; grep -aE "BUILD" /tmp/t10b.log`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply --console=plain -q
git add -A
git commit -m "docs: a route no longer needs @Serializable"
```

---

## Self-Review Notes

**Spec coverage.** §3 user-facing change → Tasks 8, 9, 10. §4.1 route serializer → Task 3. §4.2 enum
serializer → Task 3. §4.3 module registration → Task 4. §4.4 topology edges → Task 5. §5 type
resolution → Tasks 1 and 2. §6 `@Serializable` stays legal → Task 3 step 1's fourth test and Task 4.
§7 unchanged behaviour → Task 7. §8 validation → Task 6. §10 migration → Tasks 8, 9. §11 testing →
distributed, with the cross-module case covered by Task 9 (`:sample:navigation` parameter types live
in `:sample:domain`).

**Deviation from the spec, deliberate and documented.** The spec's §4.1 implies full wire
compatibility. This plan's preamble narrows it: routes with defaulted parameters lose their 0.2.x
snapshots. Task 10 step 3 amends the spec so the two documents agree.

**Type consistency.** The generated object name is `<SimpleName>GezginSerializer` in Tasks 2, 3 and
4, produced by the single helper `RouteSerializerCodegen.serializerName` so the two codegens cannot
drift. `SerialKind` variant names (`Builtin`, `SerializableClass`, `BareEnum`, `ListOf`,
`Unsupported`) are identical in Tasks 1, 2, 5 and 6.
