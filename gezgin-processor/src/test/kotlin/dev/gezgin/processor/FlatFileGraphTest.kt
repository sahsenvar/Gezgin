package dev.gezgin.processor

import com.squareup.kotlinpoet.ClassName
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.core.FlowType
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.Route
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.findGeneratedResource
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Task 8.1 gate for flat-file graphs: a graph, its cross-file routes, and a cross-file `@FlowGraph`
 * (all same package, connected only by `: ParentGraph`) must produce the SAME semantic model and
 * generated artifacts as the nested single-file form — proving membership now derives from the
 * supertype relationship, not lexical nesting. Also pins the new parent-structure validations
 * (N11 belirsiz ebeveyn, N12 öksüz `@FlowGraph`) and confirms the existing N10 simple-name collision
 * rule already covers the new multi-file layout.
 */
@OptIn(ExperimentalCompilerApi::class)
class FlatFileGraphTest {

    // A graph split across THREE files, same package: the root `@NavGraph`, a cross-file route that
    // is a direct `: AppGraph` subtype AND enters the flow for a result, and a cross-file
    // `@FlowGraph : AppGraph` that is itself a `ResultFlow<SignUpResult>` with its own nested routes.
    private val appGraphFile = SourceFile.kotlin(
        "AppGraph.kt",
        """
        package dev.gezgin.flat

        import dev.gezgin.core.Route
        import dev.gezgin.core.annotation.NavGraph

        @NavGraph
        sealed interface AppGraph : Route
        """.trimIndent(),
    )

    private val homeRouteFile = SourceFile.kotlin(
        "HomeRoute.kt",
        """
        package dev.gezgin.flat

        import dev.gezgin.core.annotation.GoForResult

        @GoForResult(SignUpFlow::class)
        data object HomeRoute : AppGraph
        """.trimIndent(),
    )

    private val signUpFlowFile = SourceFile.kotlin(
        "SignUpFlow.kt",
        """
        package dev.gezgin.flat

        import dev.gezgin.core.ResultFlow
        import dev.gezgin.core.annotation.FlowGraph
        import dev.gezgin.core.annotation.GoTo
        import dev.gezgin.core.annotation.StartDestination
        import kotlinx.serialization.KSerializer
        import kotlinx.serialization.descriptors.SerialDescriptor
        import kotlinx.serialization.descriptors.buildClassSerialDescriptor
        import kotlinx.serialization.encoding.Decoder
        import kotlinx.serialization.encoding.Encoder

        data class SignUpResult(val ok: Boolean) {
            // Test-only stub (same pattern as SHOP_SOURCE's OrderId) — kctfork has no
            // kotlinx-serialization plugin, but the generated topology eagerly calls the factory.
            companion object {
                fun serializer(): KSerializer<SignUpResult> = object : KSerializer<SignUpResult> {
                    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SignUpResult")
                    override fun serialize(encoder: Encoder, value: SignUpResult): Unit =
                        throw UnsupportedOperationException("test stub")
                    override fun deserialize(decoder: Decoder): SignUpResult =
                        throw UnsupportedOperationException("test stub")
                }
            }
        }

        @FlowGraph
        sealed interface SignUpFlow : AppGraph, ResultFlow<SignUpResult> {

            @StartDestination
            @GoTo(SummaryRoute::class)
            data object CredentialsRoute : SignUpFlow

            data object SummaryRoute : SignUpFlow
        }
        """.trimIndent(),
    )

    @Test
    fun `membership derives from supertypes across files`() {
        val result = compileGezgin(
            appGraphFile, homeRouteFile, signUpFlowFile,
            kspArgs = mapOf("gezgin.dumpModel" to "true", "gezgin.emitSerializers" to "false"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val dump = findGeneratedResource("GezginModelDump.txt")!!.readText().lines()

        // AppGraph's members are discovered cross-file: the separate-file route AND the separate-file flow.
        assertContains(
            dump,
            "graph dev.gezgin.flat.AppGraph flow=false resultFlow=false resultType=- start=- " +
                "parentFlow=- members=dev.gezgin.flat.HomeRoute,dev.gezgin.flat.SignUpFlow",
            message = "AppGraph must own its cross-file route + flow via `: AppGraph`:\n${dump.joinToString("\n")}",
        )
        // The cross-file flow: ResultFlow<SignUpResult>, start + members read from its own file.
        assertContains(
            dump,
            "graph dev.gezgin.flat.SignUpFlow flow=true resultFlow=true resultType=dev.gezgin.flat.SignUpResult " +
                "start=dev.gezgin.flat.SignUpFlow.CredentialsRoute parentFlow=- " +
                "members=dev.gezgin.flat.SignUpFlow.CredentialsRoute,dev.gezgin.flat.SignUpFlow.SummaryRoute",
            message = dump.joinToString("\n"),
        )
        // A route nested in the flat-file flow: graph + flow-chain resolve through the supertype.
        assertContains(
            dump,
            "route dev.gezgin.flat.SignUpFlow.CredentialsRoute graph=dev.gezgin.flat.SignUpFlow " +
                "chain=dev.gezgin.flat.SignUpFlow start=true noBack=false resultType=- params=-",
            message = dump.joinToString("\n"),
        )
        // The top-level cross-file route belongs to AppGraph with no flow chain.
        assertContains(
            dump,
            "route dev.gezgin.flat.HomeRoute graph=dev.gezgin.flat.AppGraph chain=- start=false " +
                "noBack=false resultType=- params=-",
            message = dump.joinToString("\n"),
        )
    }

    @Test
    fun `flat-file graph generates topology, serializers and navigators end-to-end`() {
        val result = compileGezgin(
            appGraphFile, homeRouteFile, signUpFlowFile,
            kspArgs = mapOf("gezgin.emitSerializers" to "true"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // Topology (loaded off the compiled classloader): flow chain + start + result edge all resolve
        // from the cross-file supertype relationship.
        val loader = result.classLoader
        val topology = loader.loadClass("dev.gezgin.flat.GezginGeneratedKt")
            .getMethod("getGezginTopology").invoke(null) as GezginTopology

        val credentials = loader.kClassOf("dev.gezgin.flat.SignUpFlow.CredentialsRoute")
        assertEquals(
            listOf(FlowType("dev.gezgin.flat.SignUpFlow", isResultFlow = true)),
            topology.flowChain(credentials),
        )
        assertEquals(credentials.java, topology.startOf("dev.gezgin.flat.SignUpFlow").java)

        val homeToFlow = topology.edges["dev.gezgin.flat.HomeRoute→dev.gezgin.flat.SignUpFlow"]
        assertNotNull(homeToFlow, "expected a cross-file HomeRoute→SignUpFlow @GoForResult edge")
        assertNotNull(homeToFlow.resultSerializer, "the flow-result edge must carry a serializer")

        // Serializers: the polymorphic subclass set spans both files (top-level route + nested flow route).
        val serializers = result.generatedSourceFor("GezginSerializers.kt")!!.readText()
        assertTrue("polymorphic(Route::class)" in serializers, serializers)
        assertTrue("subclass(HomeRoute::class)" in serializers, serializers)
        assertTrue("subclass(SignUpFlow.CredentialsRoute::class)" in serializers, serializers)

        // quitFlow surface: every member of the ResultFlow gets a `quitWith(result: SignUpResult)` —
        // proving the flow's T (read from the `ResultFlow<SignUpResult>` supertype in another file)
        // reaches navigator codegen for a member declared in that same file.
        val credentialsNav = result.generatedSourceFor("CredentialsNavigator.kt")!!.readText()
        assertTrue("public fun quitWith(result: SignUpResult)" in credentialsNav, credentialsNav)
    }

    @Test
    fun `N11 - a graph implementing two annotated parents is rejected`() {
        assertViolates(
            "N11",
            """
            package dev.gezgin.n11

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.StartDestination

            @NavGraph
            sealed interface GraphA : Route

            @NavGraph
            sealed interface GraphB : Route

            @FlowGraph
            sealed interface AmbiguousFlow : GraphA, GraphB {
                @StartDestination
                data object Start : AmbiguousFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `N12 - a FlowGraph nested in a non-graph with no annotated supertype is rejected`() {
        assertViolates(
            "N12",
            """
            package dev.gezgin.n12

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.StartDestination

            object Container {
                @FlowGraph
                sealed interface OrphanFlow : Route {
                    @StartDestination
                    data object Start : OrphanFlow
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `N12 positive - a top-level FlowGraph entered by an edge is allowed`() {
        assertCompilesClean(
            """
            package dev.gezgin.n12.pos

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.StartDestination

            @NavGraph
            sealed interface AppGraph : Route {
                @GoTo(TopFlow::class)
                data object Home : AppGraph
            }

            @FlowGraph
            sealed interface TopFlow : Route {
                @StartDestination
                data object Landing : TopFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `N10 - same route simpleName across separate files still collides`() {
        // AppGraph.Dup and SubFlow.Dup have distinct fqNames but the same simpleName; both earn a
        // `DupNavigator` into the single generated package — the existing N10 rule catches this
        // file-agnostically (it groups model routes by simpleName, not by declaring file).
        assertViolates(
            "N10",
            """
            package dev.gezgin.collide

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.FlowGraph
            import dev.gezgin.core.annotation.GoTo
            import dev.gezgin.core.annotation.NavGraph
            import dev.gezgin.core.annotation.StartDestination

            @NavGraph
            sealed interface AppGraph : Route {
                @GoTo(Dup::class)
                data object Home : AppGraph

                @GoTo(Home::class)
                data object Dup : AppGraph
            }

            @FlowGraph
            sealed interface SubFlow : AppGraph {
                @StartDestination
                @GoTo(Other::class)
                data object Dup : SubFlow

                data object Other : SubFlow
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `cross-file sealed or abstract intermediate types under a graph are not routes`() {
        // getSealedSubclasses(HomeGraph) also surfaces the separate-file intermediate subtypes; the
        // isRouteDeclaration filter must exclude them (SEALED/ABSTRACT = not instantiable) exactly as
        // for a lexically-nested intermediate — otherwise a shared base would leak into the route list
        // and the polymorphic serializer set.
        val graph = SourceFile.kotlin(
            "HomeGraph.kt",
            """
            package dev.gezgin.intermediate

            import dev.gezgin.core.Route
            import dev.gezgin.core.annotation.NavGraph

            @NavGraph
            sealed interface HomeGraph : Route
            """.trimIndent(),
        )
        val members = SourceFile.kotlin(
            "Members.kt",
            """
            package dev.gezgin.intermediate

            // Two separate-file intermediate layers, both direct subtypes of the graph — surfaced by
            // getSealedSubclasses, neither a navigable destination.
            sealed interface SharedMid : HomeGraph
            abstract class SharedBase : HomeGraph

            data object Feed : HomeGraph
            """.trimIndent(),
        )
        val result = compileGezgin(
            graph, members,
            kspArgs = mapOf("gezgin.dumpModel" to "true", "gezgin.emitSerializers" to "true"),
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val routeLines = findGeneratedResource("GezginModelDump.txt")!!.readText().lines()
            .filter { it.startsWith("route ") }
        assertFalse(
            routeLines.any { "SharedMid" in it || "SharedBase" in it },
            "cross-file intermediate types must not be read as routes: $routeLines",
        )
        assertTrue(
            routeLines.any { "route dev.gezgin.intermediate.Feed " in it },
            "the concrete cross-file Feed route must still be present: $routeLines",
        )

        val serializers = result.generatedSourceFor("GezginSerializers.kt")!!.readText()
        assertFalse("SharedMid" in serializers || "SharedBase" in serializers, serializers)
        assertTrue("subclass(Feed::class)" in serializers, serializers)
    }

    @Test
    fun `N13 - a non-sealed graph with a separate-file member is rejected`() {
        // MAJOR-1 regression: a non-sealed @NavGraph makes getSealedSubclasses return EMPTY, so the
        // separate-file `data object Home : AppGraph` would silently drop out of the model (no route,
        // no navigator) surfacing only as a misleading downstream symptom. N13 rejects the non-sealed
        // graph up front — the member MUST live in a separate file, since a nested one would survive
        // via the lexical fallback and hide the gap.
        assertViolates(
            "N13",
            SourceFile.kotlin(
                "AppGraph.kt",
                """
                package dev.gezgin.n13

                import dev.gezgin.core.Route
                import dev.gezgin.core.annotation.NavGraph

                @NavGraph
                interface AppGraph : Route
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Home.kt",
                """
                package dev.gezgin.n13

                data object Home : AppGraph
                """.trimIndent(),
            ),
        )
    }

    // region helpers (mirrors ValidationTest / TopologyCodegenTest — kept local to this feature)

    private fun assertViolates(code: String, source: String) {
        val result = compileGezgin(SourceFile.kotlin("Source.kt", source))
        assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertContains(result.messages, "[$code]", message = result.messages)
    }

    private fun assertViolates(code: String, vararg sources: SourceFile) {
        val result = compileGezgin(*sources)
        assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertContains(result.messages, "[$code]", message = result.messages)
    }

    private fun assertCompilesClean(source: String) {
        val result = compileGezgin(SourceFile.kotlin("Source.kt", source))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    private fun binaryNameOf(fqName: String): String {
        val className = ClassName.bestGuess(fqName)
        val prefix = className.packageName.takeIf { it.isNotEmpty() }?.plus(".").orEmpty()
        return prefix + className.simpleNames.joinToString("$")
    }

    @Suppress("UNCHECKED_CAST")
    private fun ClassLoader.kClassOf(fqName: String): KClass<out Route> =
        loadClass(binaryNameOf(fqName)).kotlin as KClass<out Route>

    // endregion
}
