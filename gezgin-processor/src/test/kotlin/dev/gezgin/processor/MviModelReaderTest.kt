package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.HILT_STUBS
import dev.gezgin.processor.fixtures.MV7_NO_NAV_SOURCE
import dev.gezgin.processor.fixtures.MVI_SOURCE
import dev.gezgin.processor.fixtures.ROUTE_CHROME_MVI_SOURCE
import dev.gezgin.processor.fixtures.ROUTE_EXPLICIT_MVI_SOURCE
import dev.gezgin.processor.fixtures.SHOP_SOURCE
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Faz 5.1 gate for [dev.gezgin.processor.mvi.ViewModelModelReader] + [dev.gezgin.processor.entry.EntryModelReader]'s
 * MVI-mode extension (spec §10/§10.1). Positive cases assert the exact `GezginMviDump.txt` produced
 * under `gezgin.dumpMvi=true`; guardrail cases (`MV1`-`MV7`) assert a `[<code>]`-prefixed KSP error.
 *
 * Route-linking mechanism (design decision — see task-5.1 report): MVI-mode content links to its VM by
 * the EXPLICIT `@Screen(Route::class)` it carries (matched to the VM's `@MviViewModel(Route::class)`), NOT
 * by inferring the route from the `state`/`onIntent` types. This is spec-§10.1-aligned (its example and
 * the Faz-5.0 `CounterMvi` fixture both put the route on the content) and strictly more robust than
 * S/I type-matching (never ambiguous — two VMs can't share a route, `MV4`).
 */
@OptIn(ExperimentalCompilerApi::class)
class MviModelReaderTest {

    private fun dumpOf(vararg sources: SourceFile): List<String> {
        // `gezgin.emitEntries=false`: the model-level dump must not trigger Faz-5.2 MVI codegen, whose
        // generated `collectAsStateWithLifecycle()` call site backend-ICEs kctfork's plugin-less compile
        // (see EntryCodegenTest's class KDoc). The dump itself runs regardless of this flag.
        val result = compileGezgin(
            *sources,
            kspArgs = mapOf("gezgin.dumpMvi" to "true", "gezgin.emitSerializers" to "false", "gezgin.emitEntries" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val dump = findGeneratedResource("GezginMviDump.txt")
        assertNotNull(dump, "GezginMviDump.txt not generated; messages:\n${result.messages}")
        return dump.readText().lines()
    }

    private fun assertViolates(code: String, source: String, vararg extra: SourceFile) {
        val result = compileGezgin(SourceFile.kotlin("Bad.kt", source), *extra)
        assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertContains(result.messages, "[$code]", message = result.messages)
    }

    private fun assertViolationMentions(code: String, source: String, vararg expected: String) {
        val result = compileGezgin(SourceFile.kotlin("Bad.kt", source))
        assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertContains(result.messages, "[$code]", message = result.messages)
        expected.forEach { assertContains(result.messages, it, message = result.messages) }
    }

    // region Positive — dump

    @Test
    fun `positive MVI triple — vm and mvientry dumped, S I E captured, effect wired`() {
        val dump = dumpOf(SourceFile.kotlin("MviSource.kt", MVI_SOURCE))

        assertContains(
            dump,
            "vm dev.gezgin.mviui.CounterViewModel route=dev.gezgin.mviui.CounterRoute " +
                "state=dev.gezgin.mviui.CounterState|dev.gezgin.mviui.CounterState " +
                "intent=dev.gezgin.mviui.CounterIntent|dev.gezgin.mviui.CounterIntent " +
                "effect=dev.gezgin.mviui.CounterEffect|dev.gezgin.mviui.CounterEffect " +
                "pkg=dev.gezgin.mviui " +
                "di=ANDROIDX factory=- ctor=route:dev.gezgin.mviui.CounterRoute",
        )
        assertContains(
            dump,
            "mvientry CounterContent pkg=dev.gezgin.mviui route=dev.gezgin.mviui.CounterRoute " +
                "kind=SCREEN x=Counter noBack=false vm=dev.gezgin.mviui.CounterViewModel " +
                "effect=CounterEffects effectNav=false top=- bottom=- role=- resolver=-",
        )
    }

    @Test
    fun `repeatable Screen emits one route-local model per route with explicit handlers`() {
        val dump = dumpOf(SourceFile.kotlin("RouteExplicit.kt", ROUTE_EXPLICIT_MVI_SOURCE))

        assertContains(
            dump,
            "mvientry SharedContent pkg=dev.gezgin.routeexplicit route=dev.gezgin.routeexplicit.G.A " +
                "kind=SCREEN x=A noBack=false vm=dev.gezgin.routeexplicit.VmA " +
                "effect=EffectsA effectNav=true top=- bottom=- role=- resolver=-",
        )
        assertContains(
            dump,
            "mvientry SharedContent pkg=dev.gezgin.routeexplicit route=dev.gezgin.routeexplicit.G.B " +
                "kind=SCREEN x=B noBack=false vm=dev.gezgin.routeexplicit.VmB " +
                "effect=EffectsB effectNav=true top=- bottom=- role=- resolver=-",
        )
    }

    @Test
    fun `route chrome model supports both providers and a content-only sibling route`() {
        val dump = dumpOf(SourceFile.kotlin("RouteChrome.kt", ROUTE_CHROME_MVI_SOURCE))
        val routeA = dump.first { it.startsWith("mvientry SharedContent") && "route=dev.gezgin.routechrome.G.A" in it }
        val routeB = dump.first { it.startsWith("mvientry SharedContent") && "route=dev.gezgin.routechrome.G.B" in it }

        assertContains(routeA, "top=RouteATopBar bottom=RouteABottomBar", message = routeA)
        assertContains(routeB, "top=- bottom=-", message = routeB)
    }

    @Test
    fun `route chrome model supports top-only and bottom-only bindings`() {
        val topOnly = dumpOf(
            SourceFile.kotlin(
                "TopOnly.kt",
                ROUTE_CHROME_MVI_SOURCE.replace(
                    """
                    @BottomBar(G.A::class)
                    @Composable
                    fun RouteABottomBar(state: SharedState, onIntent: (SharedIntent) -> Unit) {}
                    """.trimIndent(),
                    "",
                ),
            ),
        ).first { it.startsWith("mvientry SharedContent") && "route=dev.gezgin.routechrome.G.A" in it }
        val bottomOnly = dumpOf(
            SourceFile.kotlin(
                "BottomOnly.kt",
                ROUTE_CHROME_MVI_SOURCE.replace(
                    """
                    @TopBar(G.A::class)
                    @Composable
                    fun RouteATopBar(state: SharedState, onIntent: (SharedIntent) -> Unit) {}
                    """.trimIndent(),
                    "",
                ),
            ),
        ).first { it.startsWith("mvientry SharedContent") && "route=dev.gezgin.routechrome.G.A" in it }

        assertContains(topOnly, "top=RouteATopBar bottom=-", message = topOnly)
        assertContains(bottomOnly, "top=- bottom=RouteABottomBar", message = bottomOnly)
    }

    @Test
    fun `repeatable TopBar binds one provider independently to two routes`() {
        val source = ROUTE_CHROME_MVI_SOURCE.replace(
            "@TopBar(G.A::class)",
            "@TopBar(G.A::class)\n    @TopBar(G.B::class)",
        )
        val dump = dumpOf(SourceFile.kotlin("RepeatableTop.kt", source))
        val routeA = dump.first { it.startsWith("mvientry SharedContent") && "route=dev.gezgin.routechrome.G.A" in it }
        val routeB = dump.first { it.startsWith("mvientry SharedContent") && "route=dev.gezgin.routechrome.G.B" in it }

        assertContains(routeA, "top=RouteATopBar", message = routeA)
        assertContains(routeB, "top=RouteATopBar", message = routeB)
    }

    @Test
    fun `duplicate TopBar for one route fails with route and declarations`() {
        val source = ROUTE_CHROME_MVI_SOURCE + "\n" + """

            @TopBar(G.A::class)
            @Composable
            fun DuplicateTop(state: SharedState, onIntent: (SharedIntent) -> Unit) {}
        """.trimIndent()

        assertViolationMentions("MV19", source, "dev.gezgin.routechrome.G.A", "RouteATopBar", "DuplicateTop")
    }

    @Test
    fun `duplicate BottomBar for one route fails with route and declarations`() {
        val source = ROUTE_CHROME_MVI_SOURCE + "\n" + """

            @BottomBar(G.A::class)
            @Composable
            fun DuplicateBottom(state: SharedState, onIntent: (SharedIntent) -> Unit) {}
        """.trimIndent()

        assertViolationMentions("MV20", source, "dev.gezgin.routechrome.G.A", "RouteABottomBar", "DuplicateBottom")
    }

    @Test
    fun `chrome provider route without MVI Screen fails with route and declaration`() {
        val source = ROUTE_CHROME_MVI_SOURCE
            .replace("data object B : G", "data object B : G\n\n        data object C : G") + "\n" + """

            @TopBar(G.C::class)
            @Composable
            fun UnknownTop(state: SharedState, onIntent: (SharedIntent) -> Unit) {}
        """.trimIndent()

        assertViolationMentions("MV21", source, "dev.gezgin.routechrome.G.C", "UnknownTop")
    }

    @Test
    fun `chrome provider State and Intent must match its route ViewModel`() {
        val badState = ROUTE_CHROME_MVI_SOURCE
            .replace("data class SharedState", "data class OtherState(val value: String)\n    data class SharedState")
            .replace("fun RouteATopBar(state: SharedState", "fun RouteATopBar(state: OtherState")
        val badIntent = ROUTE_CHROME_MVI_SOURCE
            .replace("sealed interface SharedIntent", "sealed interface OtherIntent\n    sealed interface SharedIntent")
            .replace(
                "fun RouteABottomBar(state: SharedState, onIntent: (SharedIntent) -> Unit)",
                "fun RouteABottomBar(state: SharedState, onIntent: (OtherIntent) -> Unit)",
            )

        assertViolationMentions("MV22", badState, "dev.gezgin.routechrome.G.A", "RouteATopBar", "OtherState")
        assertViolationMentions("MV22", badIntent, "dev.gezgin.routechrome.G.A", "RouteABottomBar", "OtherIntent")
    }

    @Test
    fun `overloaded EffectHandler declarations for different routes are both retained`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE
            .replace("fun EffectsA(", "fun SharedEffects(")
            .replace("fun EffectsB(", "fun SharedEffects(")

        val dump = dumpOf(SourceFile.kotlin("OverloadedHandlers.kt", source))
        val routeA = dump.first { it.startsWith("mvientry SharedContent") && "route=dev.gezgin.routeexplicit.G.A" in it }
        val routeB = dump.first { it.startsWith("mvientry SharedContent") && "route=dev.gezgin.routeexplicit.G.B" in it }
        assertContains(routeA, "effect=SharedEffects", message = routeA)
        assertContains(routeB, "effect=SharedEffects", message = routeB)
    }

    @Test
    fun `overloaded EffectHandler declarations for one route are duplicate bindings`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE
            .replace("@EffectHandler(G.B::class)", "@EffectHandler(G.A::class)")
            .replace("fun EffectsB(", "fun EffectsA(")

        assertViolationMentions("MV14", source, "G.A", "EffectsA")
    }

    @Test
    fun `overloaded Screen declarations are rejected with routes and function name`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE.replace(
            """
            @Screen(G.A::class)
            @Screen(G.B::class)
            @Composable
            fun SharedContent(state: SharedState, onIntent: (SharedIntent) -> Unit) {}
            """.trimIndent(),
            """
            @Screen(G.A::class)
            @Composable
            fun SharedContent(state: SharedState, onIntent: (SharedIntent) -> Unit) {}

            @Screen(G.B::class)
            @Composable
            fun SharedContent(state: SharedState, onIntent: (SharedIntent) -> Unit, tag: String = "") {}
            """.trimIndent(),
        )

        assertViolationMentions("SC11", source, "SharedContent", "dev.gezgin.routeexplicit.G.A", "dev.gezgin.routeexplicit.G.B")
    }

    @Test
    fun `duplicate explicit handlers for one route name route and both functions`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE + """

            @EffectHandler(G.A::class)
            @Composable
            fun DuplicateEffectsA(effects: Flow<EffectA>) {}
        """.trimIndent()

        assertViolationMentions("MV14", source, "G.A", "EffectsA", "DuplicateEffectsA")
    }

    @Test
    fun `explicit handler route without a Screen names route and function`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE.replace(
            "data object B : G",
            "data object B : G\n\n        data object Orphan : G",
        ) + """

            @EffectHandler(G.Orphan::class)
            @Composable
            fun OrphanEffects(effects: Flow<EffectA>) {}
        """.trimIndent()

        assertViolationMentions("MV15", source, "G.Orphan", "OrphanEffects")
    }

    @Test
    fun `explicit handler effect mismatch names route and function`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE.replace(
            "fun EffectsA(effects: Flow<EffectA>, nav: ANavigator)",
            "fun EffectsA(effects: Flow<EffectB>, nav: ANavigator)",
        )

        assertViolationMentions("MV16", source, "G.A", "EffectsA", "EffectA", "EffectB")
    }

    @Test
    fun `explicit handler navigator mismatch names route and function`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE.replace(
            "fun EffectsA(effects: Flow<EffectA>, nav: ANavigator)",
            "fun EffectsA(effects: Flow<EffectA>, nav: String)",
        )

        assertViolationMentions("MV11", source, "G.A", "EffectsA", "ANavigator")
    }

    @Test
    fun `same-round wrong generated navigator name is rejected`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE.replace(
            "fun EffectsA(effects: Flow<EffectA>, nav: ANavigator)",
            "fun EffectsA(effects: Flow<EffectA>, nav: BNavigator)",
        )

        assertViolationMentions("MV11", source, "G.A", "EffectsA", "ANavigator", "BNavigator")
    }

    @Test
    fun `per-route state mismatch names route and shared content function`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE
            .replace("data class SharedState(val value: String)", "data class SharedState(val value: String)\n    data class OtherState(val value: String)")
            .replace(
                "class VmB : GezginMvi<SharedState, SharedIntent, EffectB>",
                "class VmB : GezginMvi<OtherState, SharedIntent, EffectB>",
            )
            .replace(
                "override val uiState: StateFlow<SharedState> = MutableStateFlow(SharedState(\"b\"))",
                "override val uiState: StateFlow<OtherState> = MutableStateFlow(OtherState(\"b\"))",
            )

        assertViolationMentions("MV5", source, "G.B", "SharedContent", "OtherState")
    }

    @Test
    fun `per-route intent mismatch names route and shared content function`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE
            .replace("data class EffectA", "sealed interface OtherIntent { data object Submit : OtherIntent }\n    data class EffectA")
            .replace(
                "class VmB : GezginMvi<SharedState, SharedIntent, EffectB>",
                "class VmB : GezginMvi<SharedState, OtherIntent, EffectB>",
            )
            .replace("override fun onIntent(intent: SharedIntent) {}\n    }\n\n    @Screen(G.A::class)", "override fun onIntent(intent: OtherIntent) {}\n    }\n\n    @Screen(G.A::class)")

        assertViolationMentions("MV5", source, "G.B", "SharedContent", "OtherIntent")
    }

    @Test
    fun `MVI @Screen emits GezginMviEntries, never core-mode GezginEntries, no SC error`() {
        // The MVI content must NOT be mis-read as a core-mode entry (that would land in EntryCodegen's
        // GezginEntries.kt with the wrong `(route, nav)` shape); Faz 5.2 emits it via MviEntryCodegen
        // into GezginMviEntries.kt instead. The read+validate stays clean (no [SC*]/[MV*]). Exit is NOT
        // asserted OK here: the emitted `collectAsStateWithLifecycle()` call site backend-ICEs kctfork's
        // plugin-less compile (EntryCodegenTest class KDoc) — the KSP-generated sources still populate.
        val result = compileGezgin(
            SourceFile.kotlin("MviSource.kt", MVI_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(
            !result.messages.contains("[SC") && !result.messages.contains("[MV"),
            "unexpected KSP error: ${result.messages}",
        )
        assertTrue(
            result.generatedSourceFor("GezginEntries.kt") == null,
            "MVI-mode content must not emit core-mode GezginEntries.kt",
        )
        assertNotNull(
            result.generatedSourceFor("GezginMviEntries.kt"),
            "MVI-mode content must emit GezginMviEntries.kt in 5.2: ${result.messages}",
        )
    }

    @Test
    fun `positive — S resolves through a base class and its generic TypeName is NOT flattened`() {
        // Non-flattening proof (the Sample-Showcase regression class): S = Wrapper<String> resolved
        // TRANSITIVELY through BaseMviVm. The flattened FQ is `…Wrapper`, but the captured TypeName
        // must keep the full `<kotlin.String>` parameterization.
        val source = """
            package dev.gezgin.genmvi

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class GenRoute(val id: String) : Route
            data class Wrapper<T>(val value: T)
            sealed interface GenIntent { data class Pick(val ids: List<String>) : GenIntent }
            data class GenEffect(val msg: String)

            abstract class BaseMviVm<S, I, E> : GezginMvi<S, I, E>

            @MviViewModel(GenRoute::class)
            class GenViewModel : BaseMviVm<Wrapper<String>, GenIntent, GenEffect>() {
                override val uiState: StateFlow<Wrapper<String>> = MutableStateFlow(Wrapper("x"))
                override fun onIntent(intent: GenIntent) {}
            }

            @Screen(GenRoute::class)
            @Composable
            fun GenContent(state: Wrapper<String>, onIntent: (GenIntent) -> Unit) {
            }
        """.trimIndent()

        val dump = dumpOf(SourceFile.kotlin("Gen.kt", source))
        val vmLine = dump.first { it.startsWith("vm dev.gezgin.genmvi.GenViewModel") }
        assertContains(
            vmLine,
            "state=dev.gezgin.genmvi.Wrapper|dev.gezgin.genmvi.Wrapper<kotlin.String>",
            message = "generic state TypeName must NOT be flattened: $vmLine",
        )
    }

    @Test
    fun `positive — Problem 2 extras split sheetState (role) from an arbitrary param (resolver)`() {
        val source = """
            package dev.gezgin.sheetmvi

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.BottomSheet
            import dev.gezgin.core.compose.GezginSheetController
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class SheetRoute(val x: Int = 0) : Route
            data class SheetStateData(val n: Int)
            sealed interface SheetIntent { data object Go : SheetIntent }
            data class SheetEffect(val m: String)
            class ImageLoader

            @MviViewModel(SheetRoute::class)
            class SheetVm : GezginMvi<SheetStateData, SheetIntent, SheetEffect> {
                override val uiState: StateFlow<SheetStateData> = MutableStateFlow(SheetStateData(0))
                override fun onIntent(intent: SheetIntent) {}
            }

            @BottomSheet(SheetRoute::class)
            @Composable
            fun SheetContent(
                state: SheetStateData,
                onIntent: (SheetIntent) -> Unit,
                controller: GezginSheetController,
                imageLoader: ImageLoader,
            ) {
            }
        """.trimIndent()

        val dump = dumpOf(SourceFile.kotlin("Sheet.kt", source))
        val entryLine = dump.first { it.startsWith("mvientry SheetContent") }
        assertContains(entryLine, "kind=BOTTOM_SHEET", message = entryLine)
        assertContains(entryLine, "role=controller:dev.gezgin.core.compose.GezginSheetController", message = entryLine)
        assertContains(entryLine, "resolver=imageLoader:dev.gezgin.sheetmvi.ImageLoader", message = entryLine)
    }

    // endregion

    // region Guardrails MV1-MV7

    @Test
    fun `MV1 — @MviViewModel that does not implement GezginMvi is rejected`() {
        assertViolates(
            "MV1",
            """
            package dev.gezgin.mv1

            import dev.gezgin.core.Route
            import dev.gezgin.mvi.annotation.MviViewModel

            data class R(val x: Int = 0) : Route

            @MviViewModel(R::class)
            class BadVm
            """.trimIndent(),
        )
    }

    @Test
    fun `MV2 — MVI-mode content with no matching @MviViewModel is rejected`() {
        assertViolates(
            "MV2",
            """
            package dev.gezgin.mv2

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }

            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV23 — EffectHandler onIntent type must match owner ViewModel intent`() {
        val source = ROUTE_EXPLICIT_MVI_SOURCE.replace(
            "fun EffectsA(effects: Flow<EffectA>, nav: ANavigator) {}",
            "fun EffectsA(effects: Flow<EffectA>, nav: ANavigator, onIntent: (String) -> Unit) {}",
        )

        assertViolates("MV23", source)
    }

    @Test
    fun `MV3 — @MviViewModel with no matching content is rejected`() {
        assertViolates(
            "MV3",
            """
            package dev.gezgin.mv3

            import dev.gezgin.core.Route
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV4 — two @MviViewModel classes on the same route are rejected`() {
        assertViolates(
            "MV4",
            """
            package dev.gezgin.mv4

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class VmA : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @MviViewModel(R::class)
            class VmB : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV5 — onIntent that is not a function type is rejected`() {
        assertViolates(
            "MV5",
            """
            package dev.gezgin.mv5

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            // onIntent is `I`, not `(I) -> Unit` — MVI-shaped by name but malformed shape.
            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: I) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV5 — Function1 with non-Unit return names route and composable`() {
        val source = """
            package dev.gezgin.mv5unit

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> String) {}
        """.trimIndent()

        assertViolationMentions("MV5", source, "dev.gezgin.mv5unit.R", "Content")
    }

    @Test
    fun `MV16 — route-explicit EffectHandler Flow E must match its ViewModel effect type`() {
        assertViolates(
            "MV16",
            """
            package dev.gezgin.mv6

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.EffectHandler
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)
            data class OtherE(val z: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit) {
            }

            // Flow<OtherE> does not match this route's ViewModel effect type.
            @EffectHandler(R::class)
            @Composable
            fun BadEffects(effects: Flow<OtherE>) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV7 — nav wired (VM ctor wants nav) but target route has no navigator`() {
        // MVI-mode SC2 parity: a bare @NoBack route is the explicit no-navigator case.
        assertViolates("MV7", MV7_NO_NAV_SOURCE)
    }

    // endregion

    // region Guardrails MV8-MV10 (Faz 5 final review) + TypeName-compare (MV5/MV6) regression

    @Test
    fun `MV8 — sheetState param on a non-BottomSheet (Screen) MVI content is rejected`() {
        // A @BottomSheet-kind content with a GezginSheetController PASSES (see the positive Problem-2 split
        // test). On a @Screen kind, codegen would emit `controller = LocalGezginSheetController.current`,
        // which compiles clean and crashes at first render (the Local `error()`s outside a @BottomSheet).
        assertViolates(
            "MV8",
            """
            package dev.gezgin.mv8

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.core.compose.GezginSheetController
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            // controller on a @Screen (not @BottomSheet) — MV8.
            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit, controller: GezginSheetController) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV10 — content extra named viewModel collides with the resolver param, rejected`() {
        assertViolates(
            "MV10",
            """
            package dev.gezgin.mv10a

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)
            class Foo

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            // `viewModel` collides with the emitted resolver param — MV10.
            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit, viewModel: Foo) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV10 — content extra named nav collides with the register-body local, rejected`() {
        assertViolates(
            "MV10",
            """
            package dev.gezgin.mv10b

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)
            class Foo

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            // `nav` on MVI content is a first-timer mistake (nav belongs in the VM ctor) — the emitted
            // `nav = nav()` would call the navigator instance as a lambda. MV10.
            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit, nav: Foo) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV5 — generic-argument state mismatch (Wrapper Int vs Wrapper String) is caught by TypeName`() {
        // The flattened FQ of both is `…Wrapper`, so an FQ-based MV5 would PASS and only surface as a
        // Kotlin type error inside the generated GezginMviEntries.kt. TypeName comparison catches it here.
        assertViolates(
            "MV5",
            """
            package dev.gezgin.mv5gen

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class Wrapper<T>(val value: T)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<Wrapper<String>, I, E> {
                override val uiState: StateFlow<Wrapper<String>> = MutableStateFlow(Wrapper("x"))
                override fun onIntent(intent: I) {}
            }

            // state is Wrapper<Int> — flattens to the same `…Wrapper` FQ as the VM's Wrapper<String>.
            @Screen(R::class)
            @Composable
            fun Content(state: Wrapper<Int>, onIntent: (I) -> Unit) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV16 — route-explicit generic effect mismatch is caught by TypeName`() {
        // Same flattening trap as MV5, on the @EffectHandler Flow<E> arg. TypeName catches Int vs String.
        assertViolates(
            "MV16",
            """
            package dev.gezgin.mv6gen

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.EffectHandler
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class Wrapper<T>(val value: T)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, Wrapper<String>> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit) {
            }

            // Flow<Wrapper<Int>> — flattens to the same `…Wrapper` FQ as VM effect Wrapper<String>.
            @EffectHandler(R::class)
            @Composable
            fun BadEffects(effects: Flow<Wrapper<Int>>) {
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region SC7 (@NoBack × modal) + SC8 (kind↔contract mismatch) — MVI-mode parity (Faz-4 recheck)

    @Test
    fun `SC7 — MVI @BottomSheet content on a @NoBack route is rejected`() {
        assertViolates(
            "SC7",
            """
            package dev.gezgin.sc7mvi

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.BottomSheet
            import dev.gezgin.core.annotation.NoBack
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            @NoBack
            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @BottomSheet(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV12 — plain HiltViewModel on a route that carries data is rejected (MJ4)`() {
        // Nav3 has no path that writes the route into SavedStateHandle, so a plain-Hilt VM whose route
        // carries data (`OrderRoute(orderId)`) would silently read null. Reject with an actionable message.
        assertViolates(
            "MV12",
            """
            package dev.gezgin.mv12

            import androidx.compose.runtime.Composable
            import dagger.hilt.android.lifecycle.HiltViewModel
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class OrderRoute(val orderId: String) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(OrderRoute::class)
            @HiltViewModel
            class OrderVm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @Screen(OrderRoute::class)
            @Composable
            fun OrderContent(state: S, onIntent: (I) -> Unit) {
            }
            """.trimIndent(),
            SourceFile.kotlin("HiltStubs.kt", HILT_STUBS),
        )
    }

    @Test
    fun `MV11 — route-explicit EffectHandler with an extra param beyond Flow and nav is rejected`() {
        // An extra `extra: SomeState` has no wiring path (no effect-binder resolver mechanism); codegen
        // would emit `Effects(effects = vm.effects)` and die with "No value passed for parameter". MV11.
        assertViolates(
            "MV11",
            """
            package dev.gezgin.mv11extra

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.EffectHandler
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)
            class SomeState

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit) {
            }

            // Extra `extra: SomeState` beyond {Flow<E>, nav} — MV11.
            @EffectHandler(R::class)
            @Composable
            fun Effects(effects: Flow<E>, extra: SomeState) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV11 — route-explicit EffectHandler nav param typed as a non-navigator is rejected`() {
        // `Home` earns a HomeNavigator (via @GoTo), so MV7 passes; but the effect's `nav: SomethingElse` is
        // a RESOLVED non-navigator type → the generated `HEffects(effects = …, nav = nav)` would type-clash.
        assertViolates(
            "MV11",
            """
            package dev.gezgin.mv11nav

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.EffectHandler
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            @NavGraph
            sealed interface G : Route {
                @GoTo(Other::class)
                data class Home(val id: String) : G
                data object Other : G
            }

            data class HState(val n: Int)
            sealed interface HIntent { data object Go : HIntent }
            data class HEffect(val m: String)
            class SomethingElse

            @MviViewModel(G.Home::class)
            class HVm(route: G.Home) : GezginMvi<HState, HIntent, HEffect> {
                override val uiState: StateFlow<HState> = MutableStateFlow(HState(0))
                override fun onIntent(intent: HIntent) {}
            }

            @Screen(G.Home::class)
            @Composable
            fun HContent(state: HState, onIntent: (HIntent) -> Unit) {
            }

            // nav is a RESOLVED non-navigator type → MV11.
            @EffectHandler(G.Home::class)
            @Composable
            fun HEffects(effects: Flow<HEffect>, nav: SomethingElse) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MN3 — nested generic forwarding (Base S colon GezginMvi Wrapped S) is rejected with MV1`() {
        // walkForGezginMvi substitutes only DIRECT type-param forwarding; a NESTED `Wrapped<S>` leaves an
        // unbound `S` that would otherwise leak a dangling type variable into the state TypeName. MV1 reject.
        assertViolates(
            "MV1",
            """
            package dev.gezgin.mn3

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class Wrapped<T>(val v: T)
            data class SData(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            // GezginMvi's S is Wrapped<S'> where S' is Base's own type param — NESTED forwarding.
            abstract class Base<S, I0, E0> : GezginMvi<Wrapped<S>, I0, E0>

            @MviViewModel(R::class)
            class Vm : Base<SData, I, E>() {
                override val uiState: StateFlow<Wrapped<SData>> = MutableStateFlow(Wrapped(SData(0)))
                override fun onIntent(intent: I) {}
            }

            @Screen(R::class)
            @Composable
            fun Content(state: Wrapped<SData>, onIntent: (I) -> Unit) {
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `SC8 — MVI @FullscreenModal content whose route implements DialogContract is rejected`() {
        assertViolates(
            "SC8",
            """
            package dev.gezgin.sc8mvi

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.DialogContract
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FullscreenModal
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route, DialogContract {
                override val dismissOnClickOutside get() = false
            }
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @FullscreenModal(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit) {
            }
            """.trimIndent(),
        )
    }

    // endregion

    // region MV13 — androidx-mode instantiability (Fragment FS1 parity)

    @Test
    fun `MV13 — androidx VM with no primary ctor and only a parameterized secondary is rejected`() {
        // di=ANDROIDX, primaryConstructor=null (only `constructor(route: R)`), so codegen's default
        // resolver would emit `Vm()` — uncompilable. Rejected at KSP with an actionable message instead.
        assertViolates(
            "MV13",
            """
            package dev.gezgin.mv13

            import dev.gezgin.core.Route
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.MviViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @MviViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}

                constructor(route: R)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV13 positive — androidx VM with a public no-arg secondary ctor is NOT rejected`() {
        // primaryConstructor is null but a public `constructor()` exists → `Vm()` compiles → no MV13.
        // (A full compile ICEs kctfork's plugin-less backend, so assert only the absence of the guard.)
        val result = compileGezgin(
            SourceFile.kotlin(
                "Ok.kt",
                """
                package dev.gezgin.mv13ok

                import dev.gezgin.core.Route
                import dev.gezgin.mvi.GezginMvi
                import dev.gezgin.mvi.annotation.MviViewModel
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow

                data class R(val x: Int = 0) : Route
                data class S(val n: Int)
                sealed interface I { data object Go : I }
                data class E(val m: String)

                @MviViewModel(R::class)
                class Vm : GezginMvi<S, I, E> {
                    override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                    override fun onIntent(intent: I) {}

                    constructor()
                    constructor(route: R) : this()
                }
                """.trimIndent(),
            ),
            kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitEntries" to "false"),
        )
        assertTrue(!result.messages.contains("[MV13]"), "unexpected MV13: ${result.messages}")
    }

    // endregion
}
