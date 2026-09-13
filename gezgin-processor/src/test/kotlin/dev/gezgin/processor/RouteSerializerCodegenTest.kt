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
    assertContains(text, "String.serializer()")
    assertContains(text, "String.serializer().nullable")
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
