package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.MVI_SOURCE
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
 * under `gezgin.dumpMvi=true`; guardrail cases (`MV1`-`MV6`) assert a `[<code>]`-prefixed KSP error.
 *
 * Route-linking mechanism (design decision — see task-5.1 report): MVI-mode content links to its VM by
 * the EXPLICIT `@Screen(Route::class)` it carries (matched to the VM's `@ViewModel(Route::class)`), NOT
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
                "effect=CounterEffects effectNav=false role=- resolver=-",
        )
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
            import dev.gezgin.mvi.annotation.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class GenRoute(val id: String) : Route
            data class Wrapper<T>(val value: T)
            sealed interface GenIntent { data class Pick(val ids: List<String>) : GenIntent }
            data class GenEffect(val msg: String)

            abstract class BaseMviVm<S, I, E> : GezginMvi<S, I, E>

            @ViewModel(GenRoute::class)
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
            @file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

            package dev.gezgin.sheetmvi

            import androidx.compose.material3.SheetState
            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.BottomSheet
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class SheetRoute(val x: Int = 0) : Route
            data class SheetStateData(val n: Int)
            sealed interface SheetIntent { data object Go : SheetIntent }
            data class SheetEffect(val m: String)
            class ImageLoader

            @ViewModel(SheetRoute::class)
            class SheetVm : GezginMvi<SheetStateData, SheetIntent, SheetEffect> {
                override val uiState: StateFlow<SheetStateData> = MutableStateFlow(SheetStateData(0))
                override fun onIntent(intent: SheetIntent) {}
            }

            @BottomSheet(SheetRoute::class)
            @Composable
            fun SheetContent(
                state: SheetStateData,
                onIntent: (SheetIntent) -> Unit,
                sheetState: SheetState,
                imageLoader: ImageLoader,
            ) {
            }
        """.trimIndent()

        val dump = dumpOf(SourceFile.kotlin("Sheet.kt", source))
        val entryLine = dump.first { it.startsWith("mvientry SheetContent") }
        assertContains(entryLine, "kind=BOTTOM_SHEET", message = entryLine)
        assertContains(entryLine, "role=sheetState:androidx.compose.material3.SheetState", message = entryLine)
        assertContains(entryLine, "resolver=imageLoader:dev.gezgin.sheetmvi.ImageLoader", message = entryLine)
    }

    // endregion

    // region Guardrails MV1-MV6

    @Test
    fun `MV1 — @ViewModel that does not implement GezginMvi is rejected`() {
        assertViolates(
            "MV1",
            """
            package dev.gezgin.mv1

            import dev.gezgin.core.Route
            import dev.gezgin.mvi.annotation.ViewModel

            data class R(val x: Int = 0) : Route

            @ViewModel(R::class)
            class BadVm
            """.trimIndent(),
        )
    }

    @Test
    fun `MV2 — MVI-mode content with no matching @ViewModel is rejected`() {
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
    fun `MV3 — @ViewModel with no matching content is rejected`() {
        assertViolates(
            "MV3",
            """
            package dev.gezgin.mv3

            import dev.gezgin.core.Route
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @ViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `MV4 — two @ViewModel classes on the same route are rejected`() {
        assertViolates(
            "MV4",
            """
            package dev.gezgin.mv4

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @ViewModel(R::class)
            class VmA : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @ViewModel(R::class)
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
            import dev.gezgin.mvi.annotation.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)

            @ViewModel(R::class)
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
    fun `MV6 — @ScreenEffect whose Flow E matches no @ViewModel effect type is rejected`() {
        assertViolates(
            "MV6",
            """
            package dev.gezgin.mv6

            import androidx.compose.runtime.Composable
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.Screen
            import dev.gezgin.mvi.GezginMvi
            import dev.gezgin.mvi.annotation.ScreenEffect
            import dev.gezgin.mvi.annotation.ViewModel
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.StateFlow

            data class R(val x: Int = 0) : Route
            data class S(val n: Int)
            sealed interface I { data object Go : I }
            data class E(val m: String)
            data class OtherE(val z: String)

            @ViewModel(R::class)
            class Vm : GezginMvi<S, I, E> {
                override val uiState: StateFlow<S> = MutableStateFlow(S(0))
                override fun onIntent(intent: I) {}
            }

            @Screen(R::class)
            @Composable
            fun Content(state: S, onIntent: (I) -> Unit) {
            }

            // Flow<OtherE> — E of no @ViewModel.
            @ScreenEffect
            @Composable
            fun BadEffects(effects: Flow<OtherE>) {
            }
            """.trimIndent(),
        )
    }

    // endregion
}
