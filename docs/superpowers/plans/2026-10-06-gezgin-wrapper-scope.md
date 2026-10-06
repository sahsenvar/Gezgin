# Gezgin wrapper scope (`GezginWrapperScope`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every `@ScreenWrapper` a mandatory `GezginWrapperScope` receiver exposing the route, its name and annotations, its nearest graph, and live back-stack state.

**Architecture:** `gezgin-core` gets the public `GezginWrapperScope`/`GezginGraph`/`GraphKind` and an internal implementation built by a `@GezginInternalApi` composable `rememberGezginWrapperScope`. `gezgin-processor` reads route and graph metadata from KSP declarations (not from `GraphModel`, so cross-module routes work), renders annotations as constructor calls, emits them as file-private constants in `GezginWrapperEntries.kt`, and wraps the wrapper call in `scope.Wrapper(...)`. Two new errors (`SW13`, `SW14`) and one warning (`SW15`).

**Tech Stack:** Kotlin 2.4.20, KSP 2.3.12, KotlinPoet (+ksp interop), kctfork 0.14.0 tests, Compose Multiplatform 1.11.1, kotlinx binary-compatibility-validator, Spotless.

**Spec:** `docs/superpowers/specs/2026-10-06-gezgin-wrapper-scope-design.md`

## Global Constraints

- Branch `feat/wrapper-scope`, based on `origin/main`; baseline spec commit `f3f8476`. Do not push or open a PR; the maintainer does that.
- `gezgin-core` uses `explicitApi()`: every public declaration needs an explicit `public` modifier.
- Every string literal in `gezgin-processor/src/main` and `gezgin-core/src/commonMain` is English (`ProductionErrorMessageLanguageTest`). Tests, docs in `docs/` and commit messages follow the repo's existing language habits (commits: Turkish without diacritics, conventional prefix).
- `kctfork` runs without the Compose compiler plugin: processor tests assert on generated SOURCE and on `result.messages`, never on a successful compile of the generated entries (`CompileHarness` documents why). Real compilation of generated code is proven by the sample modules in Task 5.
- Annotations reproduced at runtime exclude exactly the prefixes `kotlin.`, `kotlinx.serialization.`, `androidx.compose.runtime.`; Gezgin's own annotations and the application's custom ones are included.
- Scope members are exactly: `route`, `routeName`, `routeAnnotations`, `graph`, `canGoBack`, `isAloneInBackStack`, `isTop`. No `navigator`, no `sheetController`, no sub-scopes.
- Error codes: `[SW13]` (no receiver), `[SW14]` (unfillable parameter), `[SW15]` (warning, annotation not reproducible). Format `logger.error("[SWn] message")`.
- Version stays `1.0.0` in `gradle.properties`; the `1.1.0` bump belongs to the release PR. Changelog goes under `[Unreleased]`.
- Run Gradle through lean flags, never edit `gradle.properties`. Shorthand used below:
  `G="./gradlew --console=plain -q --warning-mode=summary --no-problems-report --offline --fail-fast"`
  Capture long output to a file and read slices (`> /tmp/g.log 2>&1; echo exit=$?; grep -nE '^e: |FAILED|^BUILD FAILED' /tmp/g.log | head`).
- Before every commit run `./gradlew spotlessApply --console=plain -q --offline` and re-stage.

## Review Focus

1. A route nested two graphs deep (a `@FlowGraph` inside a `@NavGraph`): `graph.parent` must chain and `GraphKind` must be right at each level. Test in Task 3.
2. Annotation arguments of every shape the renderer claims: string, int, enum, `KClass`, string array, nested annotation, and a `vararg KClass` (`@GoTo`). A wrong rendering breaks the generated file for the whole module. Tests in Task 3, real compile in Task 5.
3. A route that lives in another module (classpath declaration, no `GraphModel` entry): name, annotations and graph must still be emitted. Test in Task 3 (`WrapperCrossModuleTest`).
4. A non-`@FilledBy` wrapper parameter WITH a default value must stay legal (no `SW14`), and a wrapper with type parameters plus a receiver must still bind. Test in Task 3.
5. An annotation the generated file cannot reference (private to the file): must warn `[SW15]` and be omitted, never fail the build or emit an unresolved reference. Test in Task 3.
6. Two routes in one package sharing a graph: the graph constant is emitted once and the file has no duplicate top-level names. Test in Task 3.

---

## File Structure

| File | Responsibility |
|---|---|
| `gezgin-core/.../core/compose/GezginWrapperScope.kt` (new) | Public API: `GezginWrapperScope`, `GezginGraph`, `GraphKind` |
| `gezgin-core/.../core/compose/GezginWrapperScopeImpl.kt` (new) | Internal implementation + `@GezginInternalApi rememberGezginWrapperScope` |
| `gezgin-processor/.../model/GraphMembership.kt` (new) | Graph-membership logic extracted from `ModelReader` so two readers share it |
| `gezgin-processor/.../model/ModelReader.kt` | Delegates to `GraphMembership` (behavior unchanged) |
| `gezgin-processor/.../routemeta/RouteMetaModel.kt` (new) | `RouteMetaModel`, `GraphMetaModel` |
| `gezgin-processor/.../routemeta/AnnotationRenderer.kt` (new) | KSP annotation → constructor-call `CodeBlock`, exclusion list, `[SW15]` |
| `gezgin-processor/.../routemeta/RouteMetaReader.kt` (new) | Route declaration → `RouteMetaModel` (name, annotations, graph chain) |
| `gezgin-processor/.../wrapper/WrapperModelReader.kt` | `SW13`, `SW14` checks |
| `gezgin-processor/.../entry/EntryModel.kt`, `EntryModelReader.kt` | Carry `routeMeta` for wrapped entries |
| `gezgin-processor/.../codegen/WrapperEntryCodegen.kt` | Emit constants + `scope.Wrapper(...)` |
| Tests: `WrapperScopeCodegenTest.kt` (new), the 7 wrapper test files + fixture (migrated) | See Task 3 |
| 5 sample/compat wrappers, `CHANGELOG.md`, READMEs, 2 specs | Tasks 5–6 |

---

### Task 1: Public scope API and runtime implementation (`gezgin-core`)

**Files:**
- Create: `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/GezginWrapperScope.kt`
- Create: `gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/GezginWrapperScopeImpl.kt`
- Test: `gezgin-core/src/commonTest/kotlin/dev/gezgin/core/compose/GezginWrapperScopeTest.kt`
- Modify: `gezgin-core/api/jvm/*.api` and `gezgin-core/api/android/*.api` (generated by `apiDump`)

**Interfaces:**
- Produces (used by Task 3's generated code):
  - `public interface GezginWrapperScope { route: Route; routeName: String; routeAnnotations: List<Annotation>; graph: GezginGraph; canGoBack: Boolean; isAloneInBackStack: Boolean; isTop: Boolean }` in package `dev.gezgin.core.compose`
  - `public class GezginGraph(name: String, kind: GraphKind, annotations: List<Annotation>, parent: GezginGraph?)`
  - `public enum class GraphKind { Nav, Flow }`
  - `@GezginInternalApi @Composable public fun rememberGezginWrapperScope(route: Route, routeName: String, routeAnnotations: List<Annotation>, graph: GezginGraph, noBack: Boolean): GezginWrapperScope`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.gezgin.core.compose

import androidx.compose.runtime.mutableStateOf
import dev.gezgin.core.GezginKey
import dev.gezgin.core.fixtures.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GezginWrapperScopeTest {
  private val graph = GezginGraph("AppGraph", GraphKind.Nav, emptyList(), null)

  private fun scope(
    keys: androidx.compose.runtime.State<List<GezginKey>>,
    entryId: Long,
    noBack: Boolean = false,
  ) =
    GezginWrapperScopeImpl(
      route = Product("1"),
      routeName = "Product",
      routeAnnotations = emptyList(),
      graph = graph,
      noBack = noBack,
      entryId = entryId,
      keys = keys,
    )

  @Test
  fun aLoneEntryIsAloneCannotGoBackAndIsTop() {
    val keys = mutableStateOf(listOf(GezginKey(Product("1"), id = 1)))
    val scope = scope(keys, entryId = 1)

    assertTrue(scope.isAloneInBackStack)
    assertFalse(scope.canGoBack)
    assertTrue(scope.isTop)
  }

  @Test
  fun anEntryUnderAnotherIsNotTopAndCanGoBack() {
    val keys =
      mutableStateOf(listOf(GezginKey(Product("0"), id = 1), GezginKey(Product("1"), id = 2)))

    val top = scope(keys, entryId = 2)
    val below = scope(keys, entryId = 1)

    assertTrue(top.isTop)
    assertTrue(top.canGoBack)
    assertFalse(top.isAloneInBackStack)
    assertFalse(below.isTop)
  }

  @Test
  fun aNoBackEntryCannotGoBackEvenWhenNotAlone() {
    val keys =
      mutableStateOf(listOf(GezginKey(Product("0"), id = 1), GezginKey(Product("1"), id = 2)))

    val scope = scope(keys, entryId = 2, noBack = true)

    assertFalse(scope.isAloneInBackStack)
    assertFalse(scope.canGoBack)
  }

  @Test
  fun stateFollowsTheStackWhenAnEntryIsPushedOverIt() {
    val keys = mutableStateOf(listOf(GezginKey(Product("0"), id = 1)))
    val scope = scope(keys, entryId = 1)
    assertTrue(scope.isTop)

    keys.value = keys.value + GezginKey(Product("1"), id = 2)

    assertFalse(scope.isTop)
    assertFalse(scope.isAloneInBackStack)
  }

  @Test
  fun anEntryThatLeftTheStackIsNotTop() {
    val keys = mutableStateOf(listOf(GezginKey(Product("0"), id = 1)))
    val scope = scope(keys, entryId = 99)

    assertFalse(scope.isTop)
  }

  @Test
  fun graphParentChainIsReadableOutward() {
    val flow = GezginGraph("CheckoutFlow", GraphKind.Flow, emptyList(), graph)

    assertEquals(GraphKind.Flow, flow.kind)
    assertEquals("AppGraph", flow.parent?.name)
    assertNull(flow.parent?.parent)
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `$G :gezgin-core:jvmTest --tests "dev.gezgin.core.compose.GezginWrapperScopeTest" > /tmp/g.log 2>&1; echo exit=$?; grep -nE '^e: |FAILED' /tmp/g.log | head`
Expected: non-zero exit, `e: … Unresolved reference 'GezginWrapperScopeImpl'` (and `GezginGraph`). If `jvmTest` does not exist, list candidates with `./gradlew :gezgin-core:tasks --all --console=plain -q --offline | grep -iE 'test$'` and use the JVM one.

- [ ] **Step 3: Write the public API**

`GezginWrapperScope.kt`:

```kotlin
package dev.gezgin.core.compose

import dev.gezgin.core.Route

/**
 * What Gezgin hands a `@ScreenWrapper` function: the entry's route, its compile-time metadata and
 * a little live back-stack state. A wrapper is declared as an extension on this type:
 *
 * ```
 * @ScreenWrapper
 * @Composable
 * fun <S, I> GezginWrapperScope.AppScreenRoot(
 *   @FilledBy(Screen::class) content: @Composable (S, (I) -> Unit) -> Unit,
 * ) { … }
 * ```
 *
 * [canGoBack], [isAloneInBackStack] and [isTop] are observable: reading them while composing
 * recomposes the wrapper when the back stack changes.
 */
public interface GezginWrapperScope {
  /** The route instance of this entry. */
  public val route: Route

  /**
   * The route's name as declared (for example `OptionOrderChainScreenRoute`). A constant written
   * at compile time, so R8 cannot change it.
   */
  public val routeName: String

  /**
   * The route's annotations as instances: Gezgin's own and the application's custom ones. Compiler
   * and plugin annotations (`@Serializable`, …) are not included. Empty when there are none.
   */
  public val routeAnnotations: List<Annotation>

  /** The nearest enclosing graph, `@NavGraph` or `@FlowGraph`. */
  public val graph: GezginGraph

  /** `false` for a `@NoBack` route and for an entry that is alone on the stack. */
  public val canGoBack: Boolean

  /**
   * This entry is the only one on the stack. Differs from [canGoBack]: a `@NoBack` route need not
   * be alone, and a screen opened by a deep link is.
   */
  public val isAloneInBackStack: Boolean

  /**
   * This entry is the top of the stack. A screen under an open dialog or sheet is still composed
   * and RESUMED but is not on top.
   */
  public val isTop: Boolean
}

/** A graph a route belongs to, with the graphs enclosing it reachable through [parent]. */
public class GezginGraph(
  /** The graph interface's simple name, as declared. */
  public val name: String,
  public val kind: GraphKind,
  /** The graph's annotations as instances, with the same rules as [GezginWrapperScope.routeAnnotations]. */
  public val annotations: List<Annotation>,
  /** The enclosing graph (either kind), or `null` at the outermost graph. */
  public val parent: GezginGraph?,
)

/** Whether a [GezginGraph] is a `@NavGraph` or a `@FlowGraph`. */
public enum class GraphKind {
  Nav,
  Flow,
}
```

`GezginWrapperScopeImpl.kt`:

```kotlin
package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.GezginKey
import dev.gezgin.core.Route

internal class GezginWrapperScopeImpl(
  override val route: Route,
  override val routeName: String,
  override val routeAnnotations: List<Annotation>,
  override val graph: GezginGraph,
  private val noBack: Boolean,
  private val entryId: Long,
  private val keys: State<List<GezginKey>>,
) : GezginWrapperScope {
  override val isAloneInBackStack: Boolean
    get() = keys.value.size == 1

  override val isTop: Boolean
    get() = keys.value.lastOrNull()?.id == entryId

  override val canGoBack: Boolean
    get() = !isAloneInBackStack && !noBack
}

/**
 * Builds the scope a generated `@ScreenWrapper` call receives. Reads the entry id and raw
 * navigator that `toNavEntry` installs around every entry; the stack is collected as state so the
 * scope's flags recompose their readers.
 */
@GezginInternalApi
@Composable
public fun rememberGezginWrapperScope(
  route: Route,
  routeName: String,
  routeAnnotations: List<Annotation>,
  graph: GezginGraph,
  noBack: Boolean,
): GezginWrapperScope {
  val raw = LocalGezginRawNavigator.current
  val entryId = LocalGezginEntryId.current
  val keys = raw.keysState.collectAsState()
  return remember(route, routeName, routeAnnotations, graph, noBack, entryId, keys) {
    GezginWrapperScopeImpl(route, routeName, routeAnnotations, graph, noBack, entryId, keys)
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `$G :gezgin-core:jvmTest --tests "dev.gezgin.core.compose.GezginWrapperScopeTest" > /tmp/g.log 2>&1; echo exit=$?; tail -n 5 /tmp/g.log`
Expected: exit=0.

- [ ] **Step 5: Update the binary-compatibility dump**

Run: `$G :gezgin-core:apiDump > /tmp/g.log 2>&1; echo exit=$?; git --no-pager diff --stat -- gezgin-core/api`
Expected: exit=0; the diff only ADDS `GezginWrapperScope`, `GezginGraph`, `GraphKind`, `GezginWrapperScopeImplKt.rememberGezginWrapperScope` entries. Then `$G :gezgin-core:apiCheck` exits 0.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply --console=plain -q --offline
git add gezgin-core
git commit -m "$(cat <<'EOF'
feat(core): GezginWrapperScope, GezginGraph ve scope kurucu composable (#87)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Extract graph-membership logic from `ModelReader` (pure refactor)

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/model/GraphMembership.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/model/ModelReader.kt`

**Interfaces:**
- Produces (used by Task 3's `RouteMetaReader`): `internal class GraphMembership { fun membershipParent(decl: KSClassDeclaration): KSClassDeclaration?; fun enclosingGraphChain(decl: KSClassDeclaration): List<KSClassDeclaration>; fun directAnnotatedGraphSupertypes(decl: KSClassDeclaration): List<KSClassDeclaration> }`, `internal const val NAV_GRAPH_FQ`, `internal const val FLOW_GRAPH_FQ`, `internal fun KSClassDeclaration.isAnnotatedGraph(): Boolean`, `internal fun KSAnnotated.hasGezginAnnotation(fq: String): Boolean`.

No new test: the refactor is covered by the existing `FlatFileGraphTest`, `ValidationTest`, `TopologyCodegenTest`, `Faz8SpikeTest`, which must stay green. Establish the green baseline first.

- [ ] **Step 1: Baseline**

Run: `$G :gezgin-processor:test > /tmp/g.log 2>&1; echo exit=$?; grep -nE 'FAILED|tests completed' /tmp/g.log | head`
Expected: exit=0.

- [ ] **Step 2: Create `GraphMembership.kt`**

Move the bodies verbatim from `ModelReader.kt` (`membershipParent`, `computeMembershipParent`, `enclosingGraphChain`, `directAnnotatedGraphSupertypes`, `isAnnotatedGraph`, and the two FQ constants):

```kotlin
package dev.gezgin.processor.model

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

internal const val NAV_GRAPH_FQ = "dev.gezgin.core.annotation.NavGraph"
internal const val FLOW_GRAPH_FQ = "dev.gezgin.core.annotation.FlowGraph"

/**
 * Which annotated graph a declaration belongs to: the DIRECT annotated supertype is primary, the
 * lexically enclosing annotated graph the fallback. Shared by [ModelReader] and the wrapper
 * route-metadata reader so both attribute membership identically.
 */
internal class GraphMembership {
  /** Per-instance memoization; the chain walks would otherwise re-resolve supertypes. */
  private val parentCache = HashMap<String, KSClassDeclaration?>()

  fun membershipParent(decl: KSClassDeclaration): KSClassDeclaration? {
    val key = decl.qualifiedName?.asString() ?: return computeMembershipParent(decl)
    if (parentCache.containsKey(key)) return parentCache[key]
    return computeMembershipParent(decl).also { parentCache[key] = it }
  }

  private fun computeMembershipParent(decl: KSClassDeclaration): KSClassDeclaration? {
    val directAnnotated = directAnnotatedGraphSupertypes(decl)
    val lexParent =
      (decl.parentDeclaration as? KSClassDeclaration)?.takeIf { it.isAnnotatedGraph() }
    val lexFq = lexParent?.qualifiedName?.asString()
    return when {
      lexParent != null && directAnnotated.any { it.qualifiedName?.asString() == lexFq } ->
        lexParent
      directAnnotated.isNotEmpty() -> directAnnotated.first()
      lexParent != null -> lexParent
      else -> null
    }
  }

  /** Enclosing annotated graphs from outermost to innermost, excluding `decl` itself. */
  fun enclosingGraphChain(decl: KSClassDeclaration): List<KSClassDeclaration> {
    val chain = mutableListOf<KSClassDeclaration>()
    val seen = mutableSetOf<String>()
    var cur = membershipParent(decl)
    while (cur != null) {
      val fq = cur.qualifiedName?.asString()
      if (fq != null && !seen.add(fq)) break
      chain.add(0, cur)
      cur = membershipParent(cur)
    }
    return chain
  }

  fun directAnnotatedGraphSupertypes(decl: KSClassDeclaration): List<KSClassDeclaration> =
    decl.superTypes
      .map { it.resolve().declaration }
      .filterIsInstance<KSClassDeclaration>()
      .filter { it.isAnnotatedGraph() }
      .distinctBy { it.qualifiedName?.asString() }
      .toList()
}

internal fun KSClassDeclaration.isAnnotatedGraph(): Boolean =
  hasGezginAnnotation(NAV_GRAPH_FQ) || hasGezginAnnotation(FLOW_GRAPH_FQ)

internal fun KSAnnotated.hasGezginAnnotation(fq: String): Boolean =
  annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == fq }
```

- [ ] **Step 3: Delegate from `ModelReader`**

In `ModelReader.kt`:
1. Delete `private const val NAV_GRAPH_FQ` and `private const val FLOW_GRAPH_FQ` (now internal in the same package).
2. Delete the `parentCache` field, and the functions `membershipParent`, `computeMembershipParent`, `enclosingGraphChain`, `directAnnotatedGraphSupertypes` and the private `KSClassDeclaration.isAnnotatedGraph()` extension.
3. Add `private val membership = GraphMembership()` where `parentCache` was.
4. Replace every call `membershipParent(` → `membership.membershipParent(`, `enclosingGraphChain(` → `membership.enclosingGraphChain(`, `directAnnotatedGraphSupertypes(` → `membership.directAnnotatedGraphSupertypes(` (`implementedGraphFqsOf`, `buildGraphNode`, `buildRouteModel`, `read`).
5. Remove imports that became unused (`getAllSuperTypes` stays; check with the compiler).

- [ ] **Step 4: Verify behavior unchanged**

Run: `$G :gezgin-processor:test > /tmp/g.log 2>&1; echo exit=$?; grep -nE '^e: |FAILED' /tmp/g.log | head`
Expected: exit=0, same as the baseline.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply --console=plain -q --offline
git add gezgin-processor
git commit -m "$(cat <<'EOF'
refactor(processor): graph uyeligi mantigi GraphMembership'e tasindi (#87)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Receiver validation, route metadata and scope call generation (`gezgin-processor`)

This is the one task where validation, metadata and codegen land together: once `SW13` exists every existing wrapper fixture needs a receiver, and once a receiver exists the generated call must supply it, so a partial state cannot keep the suite green.

**Files:**
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/routemeta/RouteMetaModel.kt`
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/routemeta/AnnotationRenderer.kt`
- Create: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/routemeta/RouteMetaReader.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/wrapper/WrapperModelReader.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/entry/EntryModel.kt`, `.../entry/EntryModelReader.kt`
- Modify: `gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/WrapperEntryCodegen.kt`
- Create: `gezgin-processor/src/test/kotlin/dev/gezgin/processor/WrapperScopeCodegenTest.kt`
- Modify (migrate receivers): `WrapperBinderTest.kt`, `WrapperModelReaderTest.kt`, `WrapperEntryCodegenTest.kt`, `SlotProviderReaderTest.kt`, `WrapperCrossModuleTest.kt`, `WrapperAnnotationsTest.kt`, `CallbackModalEntryCodegenTest.kt`, `fixtures/MviCodegenSource.kt` (all under `gezgin-processor/src/test/kotlin/dev/gezgin/processor/`)

**Interfaces:**
- Consumes: Task 1 `GezginWrapperScope`, `GezginGraph`, `GraphKind`, `rememberGezginWrapperScope`; Task 2 `GraphMembership`, `hasGezginAnnotation`, `FLOW_GRAPH_FQ`.
- Produces: `internal data class RouteMetaModel(routeName: String, annotations: List<CodeBlock>, graph: GraphMetaModel?)`, `internal data class GraphMetaModel(fq: String, name: String, isFlow: Boolean, annotations: List<CodeBlock>, parent: GraphMetaModel?)`, `EntryFunctionModel.routeMeta: RouteMetaModel?`.

- [ ] **Step 1: Migrate every existing wrapper fixture to the receiver form**

Save this script as `$TMPDIR/migrate_wrappers.py` (scratchpad directory) — it edits only Kotlin source inside triple-quoted strings that contain `@ScreenWrapper`:

```python
import re, sys

FUN = re.compile(r'(@ScreenWrapper\s*(?:@\w+\s*)*?fun\s+(?:<[^\n]*?>\s*)?)(\w+)\(')
IMPORT = 'import dev.gezgin.core.compose.GezginWrapperScope'

def migrate_segment(seg: str) -> str:
    if '@ScreenWrapper' not in seg or 'GezginWrapperScope.' in seg:
        return seg
    seg = FUN.sub(lambda m: m.group(1) + 'GezginWrapperScope.' + m.group(2) + '(', seg, count=0)
    m = re.search(r'^([ \t]*)package [^\n]*\n', seg, re.M)
    if m and IMPORT not in seg:
        seg = seg[:m.end()] + m.group(1) + IMPORT + '\n' + seg[m.end():]
    return seg

for path in sys.argv[1:]:
    text = open(path, encoding='utf-8').read()
    parts = text.split('"""')
    for i in range(1, len(parts), 2):
        parts[i] = migrate_segment(parts[i])
    open(path, 'w', encoding='utf-8').write('"""'.join(parts))
```

Run from the repo root:

```bash
T=gezgin-processor/src/test/kotlin/dev/gezgin/processor
python3 "$TMPDIR/migrate_wrappers.py" $T/WrapperBinderTest.kt $T/WrapperModelReaderTest.kt $T/WrapperEntryCodegenTest.kt $T/SlotProviderReaderTest.kt $T/WrapperCrossModuleTest.kt $T/WrapperAnnotationsTest.kt $T/CallbackModalEntryCodegenTest.kt $T/fixtures/MviCodegenSource.kt
git --no-pager diff --stat
grep -rn "@ScreenWrapper" -A3 $T | grep -E "fun " | grep -v "GezginWrapperScope\." | head
```

Expected: the last command prints nothing (every wrapper `fun` now has the receiver). If a function slipped through (an unusual signature), edit it by hand: put `GezginWrapperScope.` immediately before the function name and make sure the source has the import. (Kotlin sources that are not inside `"""` blocks do not exist in these files; if the grep shows one, edit it by hand too.)

- [ ] **Step 2: Write the failing tests**

Create `WrapperScopeCodegenTest.kt`:

```kotlin
package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.compileGezginModule
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class WrapperScopeCodegenTest {

  private fun String.flat() = replace(Regex("\\s+"), " ")

  private val header =
    """
    package app

    import androidx.compose.runtime.Composable
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.FilledBy
    import dev.gezgin.core.annotation.FlowGraph
    import dev.gezgin.core.annotation.GoTo
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.NoBack
    import dev.gezgin.core.annotation.Screen
    import dev.gezgin.core.annotation.StartDestination
    import dev.gezgin.core.compose.GezginWrapperScope
    import kotlin.reflect.KClass
    """
      .trimIndent()

  private fun source(body: String) =
    SourceFile.kotlin("Scope.kt", header + "\n\n" + body.trimIndent())

  private val wrapperAndGraph =
    """
    import dev.gezgin.core.annotation.ScreenWrapper

    @NavGraph
    sealed interface AppGraph : Route {
      @GoTo(DetailRoute::class) data object ListRoute : AppGraph
      data class DetailRoute(val id: String) : AppGraph
    }

    @ScreenWrapper
    @Composable
    fun <S> GezginWrapperScope.appRoot(
      @FilledBy(Screen::class) content: @Composable (S) -> Unit,
    ) = Unit

    @Screen(AppGraph.DetailRoute::class)
    @Composable
    fun detailScreen(state: String) = Unit
    """

  // region Validation

  @Test
  fun `a wrapper without the scope receiver is rejected with SW13`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          @ScreenWrapper
          @Composable
          fun <S> plainRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
        )
      )

    assertContains(result.messages, "[SW13]")
    assertContains(result.messages, "GezginWrapperScope")
  }

  @Test
  fun `a wrapper with a different receiver type is also rejected with SW13`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          @ScreenWrapper
          @Composable
          fun <S> String.otherRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
        )
      )

    assertContains(result.messages, "[SW13]")
  }

  @Test
  fun `a parameter that is neither a slot nor defaulted is rejected with SW14`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.titledRoot(
            title: String,
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit
          """
        )
      )

    assertContains(result.messages, "[SW14]")
    assertContains(result.messages, "'title'")
    assertFalse(result.messages.contains("[SW13]"), result.messages)
  }

  @Test
  fun `a defaulted non-slot parameter stays legal and the wrapper still binds`() {
    val result =
      compileGezgin(
        source(
          wrapperAndGraph.replace(
            "fun <S> GezginWrapperScope.appRoot(",
            "fun <S> GezginWrapperScope.appRoot(\n  title: String = \"\",",
          )
        )
      )

    assertFalse(result.messages.contains("[SW14]"), result.messages)
    assertFalse(result.messages.contains("[SW13]"), result.messages)
    assertNotNull(result.generatedSourceFor("GezginWrapperEntries.kt"), result.messages)
  }

  // endregion

  // region Scope call and route metadata

  @Test
  fun `the wrapper is called on a scope built from the route's compile-time metadata`() {
    val result = compileGezgin(source(wrapperAndGraph))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "val scope = rememberGezginWrapperScope(")
    assertContains(text, "route = route,")
    assertContains(text, "routeName = \"DetailRoute\",")
    assertContains(text, "noBack = false,")
    assertContains(text, "scope.appRoot<String>(")
    assertContains(text, "@OptIn(GezginInternalApi::class)")
  }

  @Test
  fun `a route with no annotations of interest gets an empty annotation list`() {
    val result = compileGezgin(source(wrapperAndGraph.replace("@GoTo(DetailRoute::class) ", "")))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "routeAnnotations = emptyList(),")
  }

  @Test
  fun `custom and gezgin annotations are reproduced as constructor calls and plugin ones are not`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper
          import kotlinx.serialization.Serializable

          enum class Level { LOW, HIGH }

          annotation class Inner(val value: Int)

          annotation class Tracked(
            val name: String,
            val level: Level = Level.LOW,
            val tags: Array<String> = [],
            val target: KClass<*> = Any::class,
            val inner: Inner = Inner(0),
            val weight: Long = 0L,
          )

          @NavGraph
          sealed interface AppGraph : Route {
            @GoTo(DetailRoute::class) data object ListRoute : AppGraph

            @NoBack
            @Serializable
            @Tracked("detail", level = Level.HIGH, tags = ["a", "b"], target = String::class, inner = Inner(5), weight = 7L)
            data class DetailRoute(val id: String) : AppGraph
          }

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.appRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable
          fun detailScreen(state: String) = Unit
          """
        )
      )
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "NoBack()")
    assertContains(text, "Tracked(")
    assertContains(text, "name = \"detail\"")
    assertContains(text, "level = Level.HIGH")
    assertContains(text, "tags = arrayOf<String>(\"a\", \"b\")")
    assertContains(text, "target = String::class")
    assertContains(text, "inner = Inner(value = 5)")
    assertContains(text, "weight = 7L")
    assertFalse(text.contains("Serializable("), "plugin annotation leaked:\n$text")
    assertContains(text, "noBack = true,")
  }

  @Test
  fun `a vararg KClass argument such as GoTo target is reproduced with a typed array`() {
    val result = compileGezgin(source(wrapperAndGraph.replace("@Screen(AppGraph.DetailRoute::class)", "@Screen(AppGraph.ListRoute::class)")))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "GoTo(")
    assertContains(text, "*arrayOf<KClass<out Route>>(AppGraph.DetailRoute::class)")
  }

  @Test
  fun `an annotation the generated file cannot name is skipped with SW15 and the build goes on`() {
    val result =
      compileGezgin(
        source(
          """
          import dev.gezgin.core.annotation.ScreenWrapper

          private annotation class Hidden

          @NavGraph
          sealed interface AppGraph : Route {
            @Hidden data class DetailRoute(val id: String) : AppGraph
          }

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.appRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable
          fun detailScreen(state: String) = Unit
          """
        )
      )
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText()

    assertContains(result.messages, "[SW15]")
    assertContains(result.messages, "Hidden")
    assertFalse(text.contains("Hidden"), "skipped annotation must not be emitted:\n$text")
    assertFalse(result.messages.contains("[SW13]") || result.messages.contains("[SW14]"))
  }

  // endregion

  // region Graph chain

  private val nestedGraphs =
    """
    import dev.gezgin.core.annotation.ScreenWrapper

    annotation class Area(val name: String)

    @Area("profile")
    @NavGraph
    sealed interface ProfileGraph : Route {
      @GoTo(SettingsFlow::class) data object Profile : ProfileGraph

      @FlowGraph
      sealed interface SettingsFlow : ProfileGraph {
        @StartDestination data object Landing : SettingsFlow
        data object Other : SettingsFlow
      }
    }

    @ScreenWrapper
    @Composable
    fun <S> GezginWrapperScope.appRoot(
      @FilledBy(Screen::class) content: @Composable (S) -> Unit,
    ) = Unit

    @Screen(ProfileGraph.SettingsFlow.Landing::class)
    @Composable
    fun landingScreen(state: String) = Unit

    @Screen(ProfileGraph.SettingsFlow.Other::class)
    @Composable
    fun otherScreen(state: String) = Unit
    """

  @Test
  fun `a route in a flow nested in a nav graph gets the chain with the right kinds`() {
    val result = compileGezgin(source(nestedGraphs))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "graph = gezginGraph_app_ProfileGraph_SettingsFlow,")
    assertContains(text, "GezginGraph( name = \"SettingsFlow\", kind = GraphKind.Flow,")
    assertContains(text, "parent = gezginGraph_app_ProfileGraph")
    assertContains(text, "GezginGraph( name = \"ProfileGraph\", kind = GraphKind.Nav,")
    assertContains(text, "Area(name = \"profile\")")
    assertContains(text, "parent = null")
    assertTrue(
      text.indexOf("val gezginGraph_app_ProfileGraph =") <
        text.indexOf("val gezginGraph_app_ProfileGraph_SettingsFlow ="),
      "a parent constant must be declared before the constant that references it:\n$text",
    )
  }

  @Test
  fun `two routes sharing a graph emit its constant once`() {
    val result = compileGezgin(source(nestedGraphs))
    val text = result.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertTrue(
      Regex("val gezginGraph_app_ProfileGraph_SettingsFlow =").findAll(text).count() == 1,
      "the shared graph constant must be emitted exactly once:\n$text",
    )
  }

  // endregion

  // region Cross-module

  @Test
  fun `a route compiled into a dependency still gets its name annotations and graph`() {
    val navigation =
      compileGezginModule(
        SourceFile.kotlin(
          "Nav.kt",
          """
          package navigation

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.GoTo
          import dev.gezgin.core.annotation.NavGraph

          annotation class Tracked(val name: String)

          @NavGraph
          sealed interface AppGraph : Route {
            @GoTo(DetailRoute::class) data object ListRoute : AppGraph

            @Tracked("detail") data class DetailRoute(val id: String) : AppGraph
          }
          """
            .trimIndent(),
        )
      )
    val feature =
      compileGezginModule(
        SourceFile.kotlin(
          "Feature.kt",
          """
          package feature

          import androidx.compose.runtime.Composable
          import dev.gezgin.core.annotation.FilledBy
          import dev.gezgin.core.annotation.Screen
          import dev.gezgin.core.annotation.ScreenWrapper
          import dev.gezgin.core.compose.GezginWrapperScope
          import navigation.AppGraph

          @ScreenWrapper
          @Composable
          fun <S> GezginWrapperScope.featureRoot(
            @FilledBy(Screen::class) content: @Composable (S) -> Unit,
          ) = Unit

          @Screen(AppGraph.DetailRoute::class)
          @Composable
          fun detailScreen(state: String) = Unit
          """
            .trimIndent(),
        ),
        extraClasspath = listOf(navigation.outputDirectory),
      )
    val text = feature.generatedSourceFor("GezginWrapperEntries.kt")!!.readText().flat()

    assertContains(text, "routeName = \"DetailRoute\",")
    assertContains(text, "Tracked(name = \"detail\")")
    assertContains(text, "GezginGraph( name = \"AppGraph\", kind = GraphKind.Nav,")
  }

  // endregion
}
```

- [ ] **Step 3: Run the new tests and verify they fail**

Run: `$G :gezgin-processor:test --tests "dev.gezgin.processor.WrapperScopeCodegenTest" > /tmp/g.log 2>&1; echo exit=$?; grep -nE 'FAILED|AssertionError' /tmp/g.log | head -20`
Expected: non-zero exit; SW13/SW14 tests fail ("expected [SW13]"), codegen tests fail ("rememberGezginWrapperScope" absent).

- [ ] **Step 4: Implement `SW13` and `SW14` in `WrapperModelReader`**

Add the constant next to the others at the top of `WrapperModelReader.kt`:

```kotlin
internal const val WRAPPER_SCOPE_FQ = "dev.gezgin.core.compose.GezginWrapperScope"
```

At the start of `readWrapper` (before `val slots = …`):

```kotlin
    val receiverFq = declaration.extensionReceiver?.resolve()?.declaration?.qualifiedName?.asString()
    if (receiverFq != WRAPPER_SCOPE_FQ) {
      error(
        "SW13",
        "@ScreenWrapper $packageName.$simpleName does not declare a GezginWrapperScope receiver. " +
          "Screen wrappers reach the route, graph and back-stack state through that scope. " +
          "Declare it as `fun <…> GezginWrapperScope.$simpleName(…)`",
      )
    }
    declaration.parameters
      .filter { !it.hasFilledBy() && !it.hasDefault }
      .forEach { parameter ->
        error(
          "SW14",
          "Parameter '${parameter.name?.asString()}' of @ScreenWrapper $packageName.$simpleName " +
            "cannot be filled: it has no @FilledBy and no default value. Add " +
            "@FilledBy(<Marker>::class) to make it a slot. Route, graph and back-stack data are " +
            "read from the receiver instead (`route`, `graph`, `isTop`, …)",
        )
      }
```

And next to `filledByMarkerFq()` at the bottom of the file:

```kotlin
private fun KSValueParameter.hasFilledBy(): Boolean = annotations.any { it.isNamed(FILLED_BY_FQ) }
```

- [ ] **Step 5: Implement the route-metadata models and reader**

`routemeta/RouteMetaModel.kt`:

```kotlin
package dev.gezgin.processor.routemeta

import com.squareup.kotlinpoet.CodeBlock

/** What the generated scope needs to know about one route, already rendered as code. */
internal data class RouteMetaModel(
  val routeName: String,
  /** Each element is one annotation instance expression, e.g. `NoBack()`. */
  val annotations: List<CodeBlock>,
  /** `null` when the route is not inside any annotated graph. */
  val graph: GraphMetaModel?,
)

internal data class GraphMetaModel(
  val fq: String,
  val name: String,
  val isFlow: Boolean,
  val annotations: List<CodeBlock>,
  val parent: GraphMetaModel?,
)
```

`routemeta/AnnotationRenderer.kt`:

```kotlin
package dev.gezgin.processor.routemeta

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/** Annotations that belong to the compiler or its plugins rather than to the program. */
internal val EXCLUDED_ANNOTATION_PREFIXES =
  listOf("kotlin.", "kotlinx.serialization.", "androidx.compose.runtime.")

private val ARRAY_FACTORIES =
  mapOf(
    "kotlin.BooleanArray" to "booleanArrayOf",
    "kotlin.Boolean" to "booleanArrayOf",
    "kotlin.ByteArray" to "byteArrayOf",
    "kotlin.Byte" to "byteArrayOf",
    "kotlin.CharArray" to "charArrayOf",
    "kotlin.Char" to "charArrayOf",
    "kotlin.DoubleArray" to "doubleArrayOf",
    "kotlin.Double" to "doubleArrayOf",
    "kotlin.FloatArray" to "floatArrayOf",
    "kotlin.Float" to "floatArrayOf",
    "kotlin.IntArray" to "intArrayOf",
    "kotlin.Int" to "intArrayOf",
    "kotlin.LongArray" to "longArrayOf",
    "kotlin.Long" to "longArrayOf",
    "kotlin.ShortArray" to "shortArrayOf",
    "kotlin.Short" to "shortArrayOf",
  )

/**
 * Turns KSP annotations into expressions that construct the same annotation at runtime, such as
 * `Tracked(name = "detail", level = Level.HIGH)`. An annotation that cannot be reproduced is
 * skipped with `[SW15]`; it never fails the build.
 */
internal class AnnotationRenderer(private val logger: KSPLogger) {

  fun renderAll(owner: String, annotations: Sequence<KSAnnotation>): List<CodeBlock> =
    annotations
      .filterNot(::isExcluded)
      .mapNotNull { annotation ->
        render(annotation)
          ?: run {
            logger.warn(
              "[SW15] Annotation @${annotation.shortName.asString()} on $owner cannot be " +
                "reproduced at runtime (it is not accessible from generated code, or an argument " +
                "has an unsupported shape); it is omitted from the annotation list"
            )
            null
          }
      }
      .toList()

  private fun isExcluded(annotation: KSAnnotation): Boolean {
    val fq = annotation.annotationType.resolve().declaration.qualifiedName?.asString() ?: return true
    return EXCLUDED_ANNOTATION_PREFIXES.any { fq.startsWith(it) }
  }

  private fun render(annotation: KSAnnotation): CodeBlock? {
    val type = annotation.annotationType.resolve()
    if (type.isError) return null
    val declaration = type.declaration as? KSClassDeclaration ?: return null
    if (!isAccessible(declaration)) return null
    val parameters = declaration.primaryConstructor?.parameters.orEmpty()
    val arguments =
      annotation.arguments.map { argument ->
        val name = argument.name?.asString() ?: return null
        val parameter = parameters.firstOrNull { it.name?.asString() == name } ?: return null
        val value =
          renderValue(argument.value, parameter.type.resolve(), parameter.isVararg) ?: return null
        CodeBlock.of("%L = %L", name, value)
      }
    return CodeBlock.of("%T(%L)", declaration.toClassName(), arguments.joinToCode(", "))
  }

  /** A private declaration is never reachable; an internal one only from its own module. */
  private fun isAccessible(declaration: KSClassDeclaration): Boolean =
    when {
      Modifier.PRIVATE in declaration.modifiers -> false
      Modifier.INTERNAL in declaration.modifiers -> declaration.containingFile != null
      else -> true
    }

  private fun renderValue(value: Any?, expected: KSType?, isVararg: Boolean = false): CodeBlock? =
    when (value) {
      is Boolean,
      is Int,
      is Short,
      is Byte -> CodeBlock.of("%L", value)
      is Long -> CodeBlock.of("%LL", value)
      is Float -> if (value.isFinite()) CodeBlock.of("%Lf", value) else null
      is Double -> if (value.isFinite()) CodeBlock.of("%L", value) else null
      is Char -> charLiteral(value)
      is String -> CodeBlock.of("%S", value)
      is KSAnnotation -> render(value)
      is KSClassDeclaration -> enumEntry(value)
      is KSType -> typeValue(value)
      is List<*> -> array(value, expected, isVararg)
      is Array<*> -> array(value.toList(), expected, isVararg)
      else -> null
    }

  private fun charLiteral(value: Char): CodeBlock? =
    if (value.isISOControl() || value == '\'' || value == '\\') null
    else CodeBlock.of("%L", "'$value'")

  private fun enumEntry(entry: KSClassDeclaration): CodeBlock? {
    val owner = entry.parentDeclaration as? KSClassDeclaration ?: return null
    return CodeBlock.of("%T.%L", owner.toClassName(), entry.simpleName.asString())
  }

  private fun typeValue(type: KSType): CodeBlock? {
    if (type.isError) return null
    val declaration = type.declaration as? KSClassDeclaration ?: return null
    return if (declaration.classKind == ClassKind.ENUM_ENTRY) enumEntry(declaration)
    else CodeBlock.of("%T::class", declaration.toClassName())
  }

  /**
   * `arrayOf<T>(…)` or the primitive factory, chosen from the parameter's declared type. A
   * `vararg` parameter is spread (`*arrayOf<T>(…)`); KSP may report a vararg's type as either the
   * array type or the element type, so both are accepted.
   */
  private fun array(items: List<*>, expected: KSType?, isVararg: Boolean): CodeBlock? {
    expected ?: return null
    val expectedFq = expected.declaration.qualifiedName?.asString()
    val factory: CodeBlock
    val elementType: KSType?
    when {
      expectedFq == "kotlin.Array" -> {
        elementType = expected.arguments.firstOrNull()?.type?.resolve() ?: return null
        if (elementType.isError) return null
        factory = CodeBlock.of("arrayOf<%T>", elementType.toTypeName())
      }
      expectedFq != null && expectedFq in ARRAY_FACTORIES -> {
        elementType = null
        factory = CodeBlock.of("%L", ARRAY_FACTORIES.getValue(expectedFq))
      }
      isVararg -> {
        if (expected.isError) return null
        elementType = expected
        factory = CodeBlock.of("arrayOf<%T>", expected.toTypeName())
      }
      else -> return null
    }
    val elements = items.map { renderValue(it, elementType) ?: return null }
    val call = CodeBlock.of("%L(%L)", factory, elements.joinToCode(", "))
    return if (isVararg) CodeBlock.of("*%L", call) else call
  }
}
```

`routemeta/RouteMetaReader.kt`:

```kotlin
package dev.gezgin.processor.routemeta

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.gezgin.processor.model.FLOW_GRAPH_FQ
import dev.gezgin.processor.model.GraphMembership
import dev.gezgin.processor.model.hasGezginAnnotation

/**
 * Reads a route's name, annotations and nearest-graph chain straight from its declaration, so a
 * route compiled into another module (no `GraphModel` entry) is handled like a local one.
 */
internal class RouteMetaReader(logger: KSPLogger) {
  private val membership = GraphMembership()
  private val renderer = AnnotationRenderer(logger)
  private val graphs = HashMap<String, GraphMetaModel>()

  fun read(route: KSClassDeclaration): RouteMetaModel {
    val fq = route.qualifiedName?.asString() ?: route.simpleName.asString()
    return RouteMetaModel(
      routeName = route.simpleName.asString(),
      annotations = renderer.renderAll(fq, route.annotations),
      graph = membership.membershipParent(route)?.let(::graphOf),
    )
  }

  private fun graphOf(graph: KSClassDeclaration): GraphMetaModel {
    val fq = graph.qualifiedName?.asString() ?: graph.simpleName.asString()
    graphs[fq]?.let {
      return it
    }
    val parent = membership.membershipParent(graph)?.let(::graphOf)
    return GraphMetaModel(
        fq = fq,
        name = graph.simpleName.asString(),
        isFlow = graph.hasGezginAnnotation(FLOW_GRAPH_FQ),
        annotations = renderer.renderAll(fq, graph.annotations),
        parent = parent,
      )
      .also { graphs[fq] = it }
  }
}
```

- [ ] **Step 6: Carry the metadata on the entry model**

`EntryModel.kt` — add the last constructor property of `EntryFunctionModel` (after `onDismissField`):

```kotlin
  /** Compile-time route metadata for the wrapper scope; read only for entries bound to a wrapper. */
  val routeMeta: dev.gezgin.processor.routemeta.RouteMetaModel? = null,
```

`EntryModelReader.kt`:
1. Add a field in the class body: `private val routeMetaReader = dev.gezgin.processor.routemeta.RouteMetaReader(logger)` (the class already has `logger`; confirm its constructor property name with `grep -n "logger" entry/EntryModelReader.kt | head -3` and use it).
2. In the `EntryFunctionModel(` construction (around line 325), after `onDismissField = …,` add:

```kotlin
      routeMeta = if (routeFq in wrappedRoutes) routeMetaReader.read(routeDecl) else null,
```

- [ ] **Step 7: Generate the constants and the scope call**

Edit `WrapperEntryCodegen.kt`:

1. Add imports and constants:

```kotlin
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.parameterizedBy
import com.squareup.kotlinpoet.joinToCode
import dev.gezgin.processor.routemeta.GraphMetaModel
```
```kotlin
private val GEZGIN_GRAPH = ClassName(COMPOSE_PKG, "GezginGraph")
private val GRAPH_KIND = ClassName(COMPOSE_PKG, "GraphKind")
private val REMEMBER_SCOPE = MemberName(COMPOSE_PKG, "rememberGezginWrapperScope")
private val ANNOTATION_LIST = LIST.parameterizedBy(ClassName("kotlin", "Annotation"))
```
and extend the reserved set: `private val RESERVED_LOCALS = setOf("route", "nav", "scope")`.

2. Add the constant collector at the bottom of the file:

```kotlin
/**
 * File-private constants for one generated file, in declaration order. A graph constant references
 * its parent, so the parent is registered first: top-level properties initialize in textual order.
 */
private class MetaConstants {
  private val properties = linkedMapOf<String, PropertySpec>()

  fun all(): Collection<PropertySpec> = properties.values

  fun annotationsRef(owner: String, annotations: List<CodeBlock>, prefix: String): CodeBlock {
    if (annotations.isEmpty()) return CodeBlock.of("emptyList()")
    val name = "$prefix${owner.sanitized()}"
    properties.getOrPut(name) {
      PropertySpec.builder(name, ANNOTATION_LIST, KModifier.PRIVATE)
        .initializer(CodeBlock.of("listOf(%L)", annotations.joinToCode(", ")))
        .build()
    }
    return CodeBlock.of("%N", name)
  }

  fun graphRef(graph: GraphMetaModel?): CodeBlock {
    if (graph == null) {
      return CodeBlock.of(
        "%T(name = %S, kind = %T.Nav, annotations = emptyList(), parent = null)",
        GEZGIN_GRAPH,
        "",
        GRAPH_KIND,
      )
    }
    val name = "gezginGraph_${graph.fq.sanitized()}"
    if (name !in properties) {
      val parent = graph.parent?.let { graphRef(it) } ?: CodeBlock.of("null")
      val annotations = annotationsRef(graph.fq, graph.annotations, "gezginGraphAnnotations_")
      properties[name] =
        PropertySpec.builder(name, GEZGIN_GRAPH, KModifier.PRIVATE)
          .initializer(
            CodeBlock.of(
              "%T(name = %S, kind = %T.%L, annotations = %L, parent = %L)",
              GEZGIN_GRAPH,
              graph.name,
              GRAPH_KIND,
              if (graph.isFlow) "Flow" else "Nav",
              annotations,
              parent,
            )
          )
          .build()
    }
    return CodeBlock.of("%N", name)
  }

  private fun String.sanitized(): String = replace('.', '_').replace(Regex("[^A-Za-z0-9_]"), "_")
}
```

3. Change `generate` so every file opts in and carries the constants (the scope uses an internal-API composable regardless of navigator wiring):

```kotlin
      .map { (packageName, group) ->
        val constants = MetaConstants()
        val functions = group.map { provideEntryFun(it, constants) }
        FileSpec.builder(packageName, "GezginWrapperEntries")
          .apply { optInGezginInternalApi() }
          .apply { functions.forEach { addFunction(it) } }
          .apply { constants.all().forEach { addProperty(it) } }
          .build()
      }
```
(`navWired` stays, it still decides whether `nav` is built.)

4. Change `provideEntryFun(entry)` to `provideEntryFun(entry, constants)` and, after the optional `val nav = …` block and before the wrapper call, add:

```kotlin
    val meta = requireNotNull(entry.routeMeta) { "wrapped entry ${entry.routeFq} has no route metadata" }
    body.add("val scope = %M(\n", REMEMBER_SCOPE).indent()
    body.add("route = route,\n")
    body.add("routeName = %S,\n", meta.routeName)
    body.add(
      "routeAnnotations = %L,\n",
      constants.annotationsRef(entry.routeFq, meta.annotations, "gezginRouteAnnotations_"),
    )
    body.add("graph = %L,\n", constants.graphRef(meta.graph))
    body.add("noBack = %L,\n", entry.noBack)
    body.unindent().add(")\n")
```
and replace the wrapper call line `body.add("%M<", MemberName(binding.wrapper.packageName, binding.wrapper.functionSimpleName))` with:

```kotlin
    body.add(
      "scope.%M<",
      MemberName(binding.wrapper.packageName, binding.wrapper.functionSimpleName, isExtension = true),
    )
```
Update the class KDoc: the wrapper call now runs on `scope`, and the register body owns `route`, `nav` and `scope`.

- [ ] **Step 8: Run the new tests**

Run: `$G :gezgin-processor:test --tests "dev.gezgin.processor.WrapperScopeCodegenTest" > /tmp/g.log 2>&1; echo exit=$?; grep -nE 'FAILED|AssertionError|^e: ' /tmp/g.log | head -20`
Expected: exit=0. If the vararg test fails because KSP reports the vararg type differently, read `/tmp/g.log` for the produced text and adjust ONLY the `array()` type-resolution branches in `AnnotationRenderer` (the `isVararg` branch exists for exactly this); keep the assertion. If an assertion fails only on whitespace/wrapping, check `.flat()` is applied.

- [ ] **Step 9: Run the whole processor suite**

Run: `$G :gezgin-processor:test > /tmp/g.log 2>&1; echo exit=$?; grep -nE 'FAILED|^e: ' /tmp/g.log | head -20`
Expected: exit=0. Existing assertions that include the wrapper name still match because they are substring checks (`appRoot<DetailUiState, DetailIntent, DetailEffect>(` is contained in `scope.appRoot<…>(`). Fix any test that asserted exact structure around the register body by reading its failure message; do not weaken it.

- [ ] **Step 10: Commit**

```bash
./gradlew spotlessApply --console=plain -q --offline
git add gezgin-processor
git commit -m "$(cat <<'EOF'
feat(processor): wrapper'a GezginWrapperScope receiver'i, SW13/SW14/SW15 ve route metadata uretimi (#87)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `core` ↔ generated-code contract check

Generated code calls `rememberGezginWrapperScope` with named arguments; Task 1 and Task 3 were written separately. Prove they agree before touching samples.

**Files:** none created (verification task).

- [ ] **Step 1: Compare signatures**

Run:
```bash
grep -n "public fun rememberGezginWrapperScope" -A7 gezgin-core/src/commonMain/kotlin/dev/gezgin/core/compose/GezginWrapperScopeImpl.kt
grep -nE 'route = route|routeName =|routeAnnotations =|graph = |noBack = ' gezgin-processor/src/main/kotlin/dev/gezgin/processor/codegen/WrapperEntryCodegen.kt
```
Expected: parameter names `route`, `routeName`, `routeAnnotations`, `graph`, `noBack` match exactly, in a form Kotlin accepts as named arguments.

- [ ] **Step 2: Run both modules' suites together**

Run: `$G :gezgin-core:jvmTest :gezgin-processor:test > /tmp/g.log 2>&1; echo exit=$?; grep -nE 'FAILED|^e: ' /tmp/g.log | head`
Expected: exit=0. No commit.

---

### Task 5: Migrate the repository's real wrappers and prove the generated code compiles

kctfork cannot compile the generated entries (no Compose plugin), so this task is the real compile check of the rendering rules.

**Files:**
- Modify: `sample/shopr/src/main/kotlin/dev/gezgin/sample/shopr/ui/ShoprScreenRoot.kt`
- Modify: `sample/hello-shared/src/commonMain/kotlin/dev/gezgin/sample/hello/ui/AppScreenRoot.kt`
- Modify: `sample/designsystem/src/main/kotlin/dev/gezgin/sample/designsystem/ShowcaseScreenRoot.kt` (two wrappers: `ShowcaseScreenRoot`, `ShowcaseSheetRoot`)
- Modify: `compatibility/zad-consumer/src/main/kotlin/dev/gezgin/compat/zad/ZadCompatibilityUi.kt`
- Modify: one navigation file in `sample/shopr` (add an exercised custom annotation, see Step 2)

- [ ] **Step 1: Add the receiver to each wrapper**

For each `@ScreenWrapper` function (find them with `grep -rn "@ScreenWrapper" -A3 sample compatibility --include=*.kt`): put `GezginWrapperScope.` between the type parameters and the function name, and add `import dev.gezgin.core.compose.GezginWrapperScope`. Example:

```kotlin
// before
@ScreenWrapper
@Composable
fun <S, I, E> ShoprScreenRoot(
// after
@ScreenWrapper
@Composable
fun <S, I, E> GezginWrapperScope.ShoprScreenRoot(
```
Do not change any other behavior. `ShowcaseSheetRoot` (content slot `@BottomSheet`) uses the same base receiver.

- [ ] **Step 2: Exercise every annotation shape in a real build**

In the `sample/shopr` file that declares the navigation graph (find it with `grep -rln "@NavGraph" sample/shopr`), declare one custom annotation and put it on one wrapped route:

```kotlin
enum class ScreenArea { Catalog, Checkout }

annotation class ScreenInfo(
  val name: String,
  val area: ScreenArea = ScreenArea.Catalog,
  val tags: Array<String> = [],
  val weight: Long = 0L,
)
```
```kotlin
@ScreenInfo("detail", area = ScreenArea.Checkout, tags = ["a", "b"], weight = 3L)
```
(Choose a route that is bound to `ShoprScreenRoot`. The annotation has no UI effect, so the Maestro flows are unaffected.) Its purpose is to make the real Kotlin + Compose compilation consume the generated `listOf(ScreenInfo(…))` and the `@GoTo` `*arrayOf<KClass<out Route>>(…)` spreads that already exist on the sample routes.

- [ ] **Step 3: Compile every affected module for real**

Find the compile tasks: `./gradlew :sample:shopr:tasks --all --console=plain -q --offline | grep -iE 'compile.*kotlin' | head`; then run, for each of `:sample:shopr`, `:sample:hello-shared`, `:sample:designsystem`, `:sample:hello` and the feature modules the wrappers' routes live in, the debug/JVM compile task, e.g.:

`$G :sample:shopr:compileDebugKotlin :sample:designsystem:compileDebugKotlin :sample:hello-shared:compileKotlinJvm > /tmp/g.log 2>&1; echo exit=$?; grep -nE '^e: ' /tmp/g.log | head`

Expected: exit=0. On `^e: ` lines in a generated `GezginWrapperEntries.kt`, fix the renderer in `AnnotationRenderer.kt` (Task 3, Step 5), add a regression assertion to `WrapperScopeCodegenTest`, rerun Tasks 3–5 checks. Common suspects: spread of a typed array into a vararg (`*arrayOf<KClass<out Route>>`), a `Long`/`Float` literal suffix.

- [ ] **Step 4: Compile the compatibility consumer**

It has its own Gradle build. Find its command: `grep -rn "zad-consumer" .github/workflows | head -5`; run exactly that compile step. Expected: success.

- [ ] **Step 5: Run the existing sample unit tests that touch navigation**

Run: `$G :sample:shopr:testDebugUnitTest > /tmp/g.log 2>&1; echo exit=$?; grep -nE 'FAILED|^e: ' /tmp/g.log | head` (adapt the module/task to what `tasks --all` shows). Expected: exit=0.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply --console=plain -q --offline
git add sample compatibility
git commit -m "$(cat <<'EOF'
refactor(sample): wrapper'lar GezginWrapperScope receiver'ina tasindi (#87)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Documentation, changelog and spec tables

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `README.md`, `README.tr.md`
- Modify: `docs/superpowers/specs/2026-09-11-gezgin-screen-wrapper-design.md`
- Modify: `docs/superpowers/specs/2026-10-06-gezgin-wrapper-scope-design.md`
- Modify (examples only, if they show a wrapper): files found by the grep below

- [ ] **Step 1: Find every doc that shows a wrapper**

Run: `grep -rln "@ScreenWrapper" --include=*.md . | grep -v '^./docs/superpowers/plans/' | grep -v '^./.claude/' | grep -v '^./.superpowers/'`
For each hit that contains a wrapper code example, add the `GezginWrapperScope.` receiver (and the import) to the example. Leave the historical design specs' prose alone except for the two files below.

- [ ] **Step 2: Changelog**

Under `## [Unreleased]` in `CHANGELOG.md` add (Turkish, matching the file's style), in the existing `### Changed` and `### Added` blocks (create them if absent):

```markdown
### Changed
- **BREAKING:** `@ScreenWrapper` fonksiyonları artık zorunlu bir `GezginWrapperScope` receiver'ı
  tanımlamalı (`fun <…> GezginWrapperScope.AppScreenRoot(…)`). Geçiş mekanik: her wrapper'a
  receiver eklenir. Receiver'ı olmayan wrapper `SW13` hatası verir. `@FilledBy` taşımayan ve
  varsayılanı olmayan wrapper parametresi artık sessizce yok sayılmaz, `SW14` hatası verir.
  Wrapper'ı olmayan core-mode `register<R> { … }` kullanımları etkilenmez.

### Added
- `GezginWrapperScope`: wrapper içinde `route`, `routeName`, `routeAnnotations`, `graph`
  (`GezginGraph`, `GraphKind`), `canGoBack`, `isAloneInBackStack` ve `isTop`. Ad ve annotation'lar
  derleme anında üretilen sabitlerdir (reflection ve `::class.simpleName` yok, R8'e dayanıklı).
  Gezgin'in kendi annotation'ları ile uygulamanın özel annotation'ları dahil, `@Serializable` gibi
  derleyici/plugin annotation'ları hariçtir. Üretilemeyen bir annotation `SW15` uyarısıyla atlanır.
```
Also correct the stale "`SW1`–`SW11` hata kataloğu" mention (around line 146) to `SW1`–`SW14`.

- [ ] **Step 3: READMEs**

In the wrapper section of `README.md` (around lines 337–420) and `README.tr.md` (from line 336): change the example wrapper to the receiver form and add this subsection after the wrapper example (English; Turkish equivalent in `README.tr.md`):

```markdown
#### The wrapper scope

Every wrapper is an extension on `GezginWrapperScope`, which Gezgin fills in per entry:

| Member | Meaning |
|---|---|
| `route` | The entry's route instance |
| `routeName` | The route's declared name, written at compile time (R8-safe) |
| `routeAnnotations` | The route's annotations as instances (Gezgin's and your own; compiler annotations such as `@Serializable` excluded) |
| `graph` | The nearest graph (`GezginGraph`: `name`, `kind` = `Nav`/`Flow`, `annotations`, `parent`) |
| `canGoBack` | `false` for a `@NoBack` route and for a lone entry |
| `isAloneInBackStack` | The only entry on the stack (a deep-linked screen is alone) |
| `isTop` | On top of the stack; `false` while a dialog or sheet is open over it |

`canGoBack`, `isAloneInBackStack` and `isTop` are observable. App-specific helpers are plain
extensions: `val GezginWrapperScope.isNoBack get() = routeAnnotations.any { it is NoBack }`.
A wrapper without the receiver fails with `[SW13]`.
```

Also update the option/diagnostic mentions in README if an SW list exists there.

- [ ] **Step 4: SW table in the wrapper design spec**

In `docs/superpowers/specs/2026-09-11-gezgin-screen-wrapper-design.md`, after the `SW12` row of the error table (around line 314) add rows in the same format:

```markdown
| SW13 | `@ScreenWrapper` declares no `GezginWrapperScope` receiver (error) |
| SW14 | a wrapper parameter has neither `@FilledBy` nor a default value (error) |
| SW15 | an annotation on a route or graph cannot be reproduced at runtime and is omitted from `routeAnnotations` / `GezginGraph.annotations` (warning) |
```
Match the table's actual column layout when you see it.

- [ ] **Step 5: Align the new spec with what was built**

In `docs/superpowers/specs/2026-10-06-gezgin-wrapper-scope-design.md` §4.1 replace the snippet and its sentence so they match the implementation (constants instead of `remember`):

```kotlin
val scope = rememberGezginWrapperScope(
    route = route,
    routeName = "OptionOrderChainScreenRoute",
    routeAnnotations = gezginRouteAnnotations_app_OptionOrderChainScreenRoute,
    graph = gezginGraph_app_AppGraph,
    noBack = true,
)
scope.Wrapper<…>(slot = { … })
```
and add one sentence: "The annotation list and graph are file-private top-level constants in the same generated file; a route with no annotations passes `emptyList()`." Change the header status line to `Status: implemented on feat/wrapper-scope`.

- [ ] **Step 6: Verify docs build and the language test**

Run: `$G :gezgin-processor:test --tests "dev.gezgin.processor.ProductionErrorMessageLanguageTest" > /tmp/g.log 2>&1; echo exit=$?`
Expected: exit=0 (no Turkish string leaked into production code).
Run the docs/API build used by CI if it exists locally: `grep -n "build-test-api-docs" -A8 .github/workflows/*.yml | head -20` and run that task. Expected: success.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply --console=plain -q --offline
git add CHANGELOG.md README.md README.tr.md docs
git commit -m "$(cat <<'EOF'
docs: GezginWrapperScope icin changelog, README ve SW13-SW15 tablosu (#87)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Final verification (after Task 6)

- [ ] `$G :gezgin-core:jvmTest :gezgin-core:apiCheck :gezgin-processor:test > /tmp/g.log 2>&1; echo exit=$?` → exit=0.
- [ ] Re-run Task 5 Step 3's compile commands → exit=0.
- [ ] `git --no-pager log --oneline origin/main..HEAD` shows the spec commit plus the 5 task commits; `git status --porcelain` is empty.
- [ ] Hand the branch back to the maintainer: pushing and the PR need the `sahsenvar` token, which the maintainer authorizes.
