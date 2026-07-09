package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import dev.gezgin.processor.fixtures.FRAGMENT_ROUTES
import dev.gezgin.processor.fixtures.FRAGMENT_SOURCE
import dev.gezgin.processor.fixtures.FRAGMENT_STUB
import dev.gezgin.processor.fixtures.MVI_SOURCE
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 6.1 gate for [dev.gezgin.processor.fragment.FragmentModelReader] (spec §11/§11.1/§11.2 brownfield
 * Fragment interop). Positive cases assert the exact `GezginFragmentDump.txt` produced under
 * `gezgin.dumpFragment=true`; guardrail cases (`FS1`-`FS3`) assert a `[<code>]`-prefixed KSP error.
 *
 * Fragment interop is screen-only (§11.2 — no dialog/bottom-sheet Fragment variant). `FragmentModelReader`
 * produces NO codegen in 6.1 (that's 6.2's `AndroidFragment` wiring), so the model dump is the golden
 * surface here. The `androidx.fragment.app.Fragment` the fixtures extend is a local stub (see
 * [dev.gezgin.processor.fixtures.FRAGMENT_STUB]) — `gezgin-processor` reads fragment symbols as string
 * FQNs with no real compile dependency.
 */
@OptIn(ExperimentalCompilerApi::class)
class FragmentModelReaderTest {

    private val fragmentStub = SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB)

    private fun dumpOf(vararg sources: SourceFile): List<String> {
        val result = compileGezgin(
            *sources,
            kspArgs = mapOf(
                "gezgin.dumpFragment" to "true",
                "gezgin.emitSerializers" to "false",
                "gezgin.emitEntries" to "false",
            ),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val dump = findGeneratedResource("GezginFragmentDump.txt")
        assertNotNull(dump, "GezginFragmentDump.txt not generated; messages:\n${result.messages}")
        return dump.readText().lines()
    }

    /** Guardrail assertion: the compile fails and the KSP output carries the expected `[FSn]` code. */
    private fun assertViolates(code: String, source: String, vararg extra: SourceFile) {
        val result = compileGezgin(
            SourceFile.kotlin("Bad.kt", source),
            fragmentStub,
            *extra,
            kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitEntries" to "false"),
        )
        assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertContains(result.messages, "[$code]", message = result.messages)
    }

    // region Positive — dump

    @Test
    fun `positive — valid @FragmentScreen classes produce correct models`() {
        val dump = dumpOf(
            fragmentStub,
            SourceFile.kotlin("FragmentRoutes.kt", FRAGMENT_ROUTES),
            SourceFile.kotlin("FragmentSource.kt", FRAGMENT_SOURCE),
        )
        assertContains(
            dump,
            "fragment dev.gezgin.fragui.OrderChainFragment route=dev.gezgin.fragroutes.OrderChainRoute " +
                "pkg=dev.gezgin.fragui routePkg=dev.gezgin.fragroutes x=OrderChain noBack=false",
        )
        // routePkg is read off the route DECLARATION (dev.gezgin.fragroutes), not the Fragment's own
        // package (dev.gezgin.fragui); noBack is read off the route decl's own @NoBack.
        assertContains(
            dump,
            "fragment dev.gezgin.fragui.ArchivedFragment route=dev.gezgin.fragroutes.ArchivedRoute " +
                "pkg=dev.gezgin.fragui routePkg=dev.gezgin.fragroutes x=Archived noBack=true",
        )
    }

    // endregion

    // region Guardrails FS1-FS3

    @Test
    fun `FS1 — @FragmentScreen Fragment with a parameterized constructor is rejected`() {
        assertViolates(
            "FS1",
            """
            package dev.gezgin.fs1

            import androidx.fragment.app.Fragment
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FragmentScreen

            data class R(val x: Int = 0) : Route

            // Parameterized ctor — Android recreates Fragments via a no-arg ctor (§11.1). FS1.
            @FragmentScreen(R::class)
            class BadFragment(val id: String) : Fragment()
            """.trimIndent(),
        )
    }

    @Test
    fun `FS2 — @FragmentScreen route type that does not implement Route is rejected`() {
        assertViolates(
            "FS2",
            """
            package dev.gezgin.fs2

            import androidx.fragment.app.Fragment
            import dev.gezgin.core.annotation.FragmentScreen

            class NotARoute

            @FragmentScreen(NotARoute::class)
            class BadFragment : Fragment()
            """.trimIndent(),
        )
    }

    @Test
    fun `FS3 — two @FragmentScreen classes on the same route are rejected (same-kind)`() {
        assertViolates(
            "FS3",
            """
            package dev.gezgin.fsdup

            import androidx.fragment.app.Fragment
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FragmentScreen

            data class R(val x: Int = 0) : Route

            @FragmentScreen(R::class)
            class AFragment : Fragment()

            @FragmentScreen(R::class)
            class BFragment : Fragment()
            """.trimIndent(),
        )
    }

    @Test
    fun `FS3 — a @FragmentScreen and a core-mode @Screen on the same route are rejected (cross-kind)`() {
        // The load-bearing cross-kind case: a route registered by BOTH a @FragmentScreen and a @Screen
        // would compile two register<R> calls and crash at runtime. FragmentModelReader cross-checks the
        // already-built EntryModelReader entries, so FS3 fires (mirrors SC4/MV4 for the other kinds).
        assertViolates(
            "FS3",
            """
            package dev.gezgin.fscross

            import androidx.compose.runtime.Composable
            import androidx.fragment.app.Fragment
            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FragmentScreen
            import dev.gezgin.core.annotation.Screen

            data class R(val x: Int = 0) : Route

            @Screen(R::class)
            @Composable
            fun Content(route: R) {
            }

            @FragmentScreen(R::class)
            class FooFragment : Fragment()
            """.trimIndent(),
        )
    }

    // endregion

    // region Regression — coexistence with core-mode / MVI-mode

    @Test
    fun `zero regression — @FragmentScreen coexists with MVI-mode content in one module`() {
        // A valid @FragmentScreen alongside a full Faz-5 MVI triple must read cleanly with no cross-talk:
        // no [FS*]/[SC*]/[MV*] errors, both dumps populated independently.
        val result = compileGezgin(
            fragmentStub,
            SourceFile.kotlin("FragmentRoutes.kt", FRAGMENT_ROUTES),
            SourceFile.kotlin("FragmentSource.kt", FRAGMENT_SOURCE),
            SourceFile.kotlin("MviSource.kt", MVI_SOURCE),
            kspArgs = mapOf(
                "gezgin.dumpFragment" to "true",
                "gezgin.dumpMvi" to "true",
                "gezgin.emitSerializers" to "false",
                "gezgin.emitEntries" to "false",
            ),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(
            !result.messages.contains("[FS") && !result.messages.contains("[SC") && !result.messages.contains("[MV"),
            "unexpected KSP error: ${result.messages}",
        )

        val fragmentDump = findGeneratedResource("GezginFragmentDump.txt")
        assertNotNull(fragmentDump, "GezginFragmentDump.txt not generated: ${result.messages}")
        assertTrue(
            fragmentDump.readText().lines().any { it.startsWith("fragment dev.gezgin.fragui.OrderChainFragment") },
            "Fragment model missing from dump:\n${fragmentDump.readText()}",
        )

        val mviDump = findGeneratedResource("GezginMviDump.txt")
        assertNotNull(mviDump, "GezginMviDump.txt not generated: ${result.messages}")
        assertTrue(
            mviDump.readText().lines().any { it.startsWith("vm dev.gezgin.mviui.CounterViewModel") },
            "MVI model regressed — CounterViewModel missing:\n${mviDump.readText()}",
        )
    }

    // endregion
}
