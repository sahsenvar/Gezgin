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
