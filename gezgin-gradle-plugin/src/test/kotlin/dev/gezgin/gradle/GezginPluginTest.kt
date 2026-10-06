package dev.gezgin.gradle

import com.google.devtools.ksp.gradle.KspExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder

class GezginPluginTest {
  private fun project(kspFirst: Boolean = true): Project =
    ProjectBuilder.builder().build().also { project ->
      if (kspFirst) project.pluginManager.apply("com.google.devtools.ksp")
      project.pluginManager.apply(GezginPlugin::class.java)
      if (!kspFirst) project.pluginManager.apply("com.google.devtools.ksp")
    }

  private fun Project.gezgin(block: MemberFunSpec.() -> Unit) =
    extensions.getByType(GezginExtension::class.java).naming { naming ->
      naming.memberFun { spec -> spec.block() }
    }

  private fun Project.kspArguments(): Map<String, String> {
    (this as ProjectInternal).evaluate()
    return extensions.getByType(KspExtension::class.java).arguments
  }

  @Test
  fun `an unconfigured extension adds no ksp arguments`() {
    assertEquals(emptyMap(), project().kspArguments().filterKeys { it.startsWith("gezgin.") })
  }

  @Test
  fun `configured values land in the ksp extension`() {
    val project = project()
    project.gezgin {
      stripSuffixes = listOf("Route", "Screen")
      rule { kind -> if (kind == GezginAnnotation.Open) stripSuffixes += "Dialog" }
    }

    assertEquals(
      mapOf(
        "gezgin.naming.memberFun.stripSuffixes" to "Route,Screen",
        "gezgin.naming.memberFun.Open.stripSuffixes" to "Route,Screen,Dialog",
      ),
      project.kspArguments(),
    )
  }

  @Test
  fun `statement order does not matter because options resolve lazily`() {
    val project = project()
    project.gezgin { rule { stripSuffixes += "Dialog" } }
    project.gezgin { stripSuffixes = listOf("Route") }

    assertEquals(
      "Route,Dialog",
      project.kspArguments().getValue("gezgin.naming.memberFun.GoTo.stripSuffixes"),
    )
  }

  @Test
  fun `works when applied before the ksp plugin`() {
    val project = project(kspFirst = false)
    project.gezgin { stripPrefixes = listOf("Old") }

    assertEquals("Old", project.kspArguments()["gezgin.naming.memberFun.stripPrefixes"])
  }

  @Test
  fun `registers the extension without the ksp plugin`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply(GezginPlugin::class.java)

    assertNotNull(project.extensions.findByName("gezgin"))
    assertNull(project.extensions.findByType(KspExtension::class.java))
  }

  @Test
  fun `property accessors default to empty lists`() {
    val spec = MemberFunSpec()

    assertEquals(emptyList(), spec.stripSuffixes)
    assertEquals(emptyList(), spec.stripPrefixes)
  }
}
