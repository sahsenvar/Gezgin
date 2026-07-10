package dev.gezgin.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.symbolProcessorProviders
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 8.0 spike gate — proves KSP2 can discover a sealed graph's members that are declared in
 * OTHER files (routes AND flow sub-interfaces) within one compilation, so Task 8.1 can switch
 * membership derivation from lexical nesting to the supertype relationship (spec design-notes §3:
 * "Alt-graph'lar ayrı dosyada, `: ParentGraph` ile bağlanır (subtyping = nesting)").
 *
 * The [SealedProbeProcessor] runs `getSealedSubclasses()` once per annotated graph and reports each
 * discovered subtype + its containing file via `logger.warn`; the tests assert on that log. A second
 * assertion validates the FALLBACK enumeration (scan a declaration's direct supertypes) so the
 * report can weigh both mechanisms.
 */
@OptIn(ExperimentalCompilerApi::class)
class Faz8SpikeTest {

    private companion object {
        const val PKG = "dev.gezgin.spike8"

        val graphFile = SourceFile.kotlin(
            "RootGraph.kt",
            """
            package $PKG

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.NavGraph

            @NavGraph
            sealed interface RootGraph : Route
            """.trimIndent(),
        )

        // Route in a SEPARATE file, connected only by `: RootGraph` — the flat-file case.
        val crossFileRouteFile = SourceFile.kotlin(
            "CrossFileRoute.kt",
            """
            package $PKG

            data object CrossFileRoute : RootGraph
            """.trimIndent(),
        )

        // Sub-flow in a SEPARATE file, plus its start route — proves sealed sub-interfaces AND their
        // members are discoverable cross-file.
        val subFlowFile = SourceFile.kotlin(
            "SignUpFlow.kt",
            """
            package $PKG

            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.StartDestination

            @FlowGraph
            sealed interface SignUpFlow : RootGraph {
                @StartDestination
                data object CredentialsScreenRoute : SignUpFlow
            }
            """.trimIndent(),
        )

        fun compileWithProbe(): Pair<KotlinCompilation.ExitCode, String> {
            val messages = ByteArrayOutputStream()
            val compilation = KotlinCompilation().apply {
                sources = listOf(graphFile, crossFileRouteFile, subFlowFile)
                configureKsp(useKsp2 = true) {
                    symbolProcessorProviders += SealedProbeProvider()
                }
                inheritClassPath = true
                messageOutputStream = messages
                jvmTarget = "17"
                kotlincArguments += listOf("-Xlambdas=class", "-Xsam-conversions=class")
            }
            val result = compilation.compile()
            return result.exitCode to messages.toString()
        }

        val probeResult: Pair<KotlinCompilation.ExitCode, String> by lazy { compileWithProbe() }
    }

    @Test
    fun `getSealedSubclasses finds a route declared in another file`() {
        val (exit, log) = probeResult
        assertEquals(KotlinCompilation.ExitCode.OK, exit, log)
        assertContains(
            log,
            "SPIKE sealed-sub $PKG.RootGraph -> $PKG.CrossFileRoute file=CrossFileRoute.kt",
            message = "getSealedSubclasses(RootGraph) must find the route declared in CrossFileRoute.kt",
        )
    }

    @Test
    fun `getSealedSubclasses finds a flow sub-interface declared in another file`() {
        val (exit, log) = probeResult
        assertEquals(KotlinCompilation.ExitCode.OK, exit, log)
        assertContains(
            log,
            "SPIKE sealed-sub $PKG.RootGraph -> $PKG.SignUpFlow file=SignUpFlow.kt",
            message = "getSealedSubclasses(RootGraph) must find the @FlowGraph sub-interface in SignUpFlow.kt",
        )
    }

    @Test
    fun `getSealedSubclasses recurses into a cross-file flow to find its member route`() {
        val (exit, log) = probeResult
        assertEquals(KotlinCompilation.ExitCode.OK, exit, log)
        assertContains(
            log,
            "SPIKE sealed-sub $PKG.SignUpFlow -> $PKG.SignUpFlow.CredentialsScreenRoute file=SignUpFlow.kt",
            message = "getSealedSubclasses(SignUpFlow) must find its @StartDestination member (same file as flow)",
        )
    }

    @Test
    fun `fallback - a route's direct supertype resolves to the annotated graph`() {
        val (exit, log) = probeResult
        assertEquals(KotlinCompilation.ExitCode.OK, exit, log)
        // Proves the fallback enumeration (scan declared supertypes) is viable if getSealedSubclasses
        // ever proves unreliable: CrossFileRoute's direct supertype is exactly RootGraph.
        assertContains(
            log,
            "SPIKE supertype $PKG.CrossFileRoute -> $PKG.RootGraph",
            message = "CrossFileRoute's direct supertype must resolve to RootGraph across files",
        )
    }

    private class SealedProbeProvider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            SealedProbeProcessor(environment)
    }

    private class SealedProbeProcessor(
        private val environment: SymbolProcessorEnvironment,
    ) : SymbolProcessor {

        private var done = false

        override fun process(resolver: Resolver): List<KSAnnotated> {
            if (done) return emptyList()
            done = true

            val graphs = (
                resolver.getSymbolsWithAnnotation("dev.gezgin.core.annotation.NavGraph") +
                    resolver.getSymbolsWithAnnotation("dev.gezgin.core.annotation.FlowGraph")
                )
                .filterIsInstance<KSClassDeclaration>()

            graphs.forEach { graph ->
                graph.getSealedSubclasses().forEach { sub ->
                    environment.logger.warn(
                        "SPIKE sealed-sub ${graph.qualifiedName?.asString()} -> " +
                            "${sub.qualifiedName?.asString()} file=${sub.containingFile?.fileName}",
                    )
                }
            }

            // Fallback probe: resolve a cross-file route's declared supertypes.
            resolver.getClassDeclarationByName("$PKG.CrossFileRoute")?.superTypes?.forEach { st ->
                val decl = st.resolve().declaration
                environment.logger.warn(
                    "SPIKE supertype $PKG.CrossFileRoute -> ${decl.qualifiedName?.asString()}",
                )
            }

            return emptyList()
        }
    }
}
