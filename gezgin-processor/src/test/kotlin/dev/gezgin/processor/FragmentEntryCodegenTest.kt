package dev.gezgin.processor

import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.fixtures.FRAGMENT_ROUTES
import dev.gezgin.processor.fixtures.FRAGMENT_SOURCE
import dev.gezgin.processor.fixtures.FRAGMENT_STUB
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 6.2 gate for [dev.gezgin.processor.codegen.FragmentEntryCodegen] — the `AndroidFragment`-hosting
 * `provideXEntry` codegen for `@FragmentScreen` (spec §11.1, screen-only §11.2).
 *
 * **kctfork caveat (same as [EntryCodegenTest]/[MviEntryCodegenTest]):** the emitted body calls
 * `androidx.fragment.compose.AndroidFragment` (an inline `@Composable`, not on the classpath here) and the
 * gezgin-core `androidMain` `toBundle`/`bindGezgin` glue (androidMain, also not on the jvm test classpath),
 * plus the compose-runtime inline accessors the plugin-less kctfork BACKEND can't inline — so this test does
 * NOT assert `exitCode == OK`. It asserts no `[FS*]`/`[SC*]` KSP error (codegen ran clean) plus the generated
 * `GezginFragmentEntries.kt` golden TEXT, which the successful KSP round populates regardless of the later
 * compile failure (`sourcesGeneratedBySymbolProcessor` reads the KSP output dir). Reuses the Task-6.1
 * fixtures — routes in a DIFFERENT package than the Fragments, so cross-module factory qualification shows.
 */
@OptIn(ExperimentalCompilerApi::class)
class FragmentEntryCodegenTest {

    private fun generateFragmentEntries(): String {
        val result = compileGezgin(
            SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB),
            SourceFile.kotlin("FragmentRoutes.kt", FRAGMENT_ROUTES),
            SourceFile.kotlin("FragmentSource.kt", FRAGMENT_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false"),
        )
        assertTrue(
            !result.messages.contains("[FS") && !result.messages.contains("[SC"),
            "unexpected KSP [FS|SC] error: ${result.messages}",
        )
        val gen = result.generatedSourceFor("GezginFragmentEntries.kt")
        assertNotNull(gen, "GezginFragmentEntries.kt not generated: ${result.messages}")
        return gen.readText()
    }

    @Test
    fun `provideXEntry golden text — AndroidFragment host, toBundle args, onUpdate bind, unconditional nav`() {
        val text = generateFragmentEntries()

        // File lives in the FRAGMENT's own package (like core-mode's composable-package grouping).
        assertTrue(text.startsWith("package dev.gezgin.fragui"), text)

        // FQ-imported symbols: AndroidFragment (androidx.fragment, no processor dep), the gezgin-core
        // runtime glue, and the navigator FACTORY qualified against the ROUTE's package (cross-module-safe).
        assertContains(text, "import androidx.fragment.compose.AndroidFragment")
        assertContains(text, "import dev.gezgin.core.fragment.toBundle")
        assertContains(text, "import dev.gezgin.core.fragment.bindGezgin")
        assertContains(text, "import dev.gezgin.fragroutes.orderChainNavigator")

        // OrderChain: screen-kind, noBack=false, unconditional nav wiring, AndroidFragment<OrderChainFragment>.
        assertContains(text, "public fun GezginEntryScope.provideOrderChainEntry()")
        assertContains(text, "register<OrderChainRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->")
        assertContains(text, "val raw = LocalGezginRawNavigator.current")
        assertContains(text, "val nav = raw.orderChainNavigator(LocalGezginEntryId.current)")
        assertContains(text, "AndroidFragment<OrderChainFragment>(")
        assertContains(text, "arguments = route.toBundle(raw),")
        assertContains(text, "onUpdate = { fragment -> bindGezgin(fragment, route, nav) },")

        // Archived: @NoBack route → register carries noBack = true (read off the route decl, cross-module).
        assertContains(text, "public fun GezginEntryScope.provideArchivedEntry()")
        assertContains(text, "register<ArchivedRoute>(kind = EntryKind.SCREEN, noBack = true) { route ->")
        assertContains(text, "val nav = raw.archivedNavigator(LocalGezginEntryId.current)")
        assertContains(text, "AndroidFragment<ArchivedFragment>(")
    }

    @Test
    fun `gezgin_emitEntries=false suppresses GezginFragmentEntries generation`() {
        val result = compileGezgin(
            SourceFile.kotlin("FragmentStub.kt", FRAGMENT_STUB),
            SourceFile.kotlin("FragmentRoutes.kt", FRAGMENT_ROUTES),
            SourceFile.kotlin("FragmentSource.kt", FRAGMENT_SOURCE),
            kspArgs = mapOf("gezgin.emitSerializers" to "false", "gezgin.emitEntries" to "false"),
        )
        assertTrue(
            result.generatedSourceFor("GezginFragmentEntries.kt") == null,
            "GezginFragmentEntries.kt emitted despite opt-out",
        )
    }
}
