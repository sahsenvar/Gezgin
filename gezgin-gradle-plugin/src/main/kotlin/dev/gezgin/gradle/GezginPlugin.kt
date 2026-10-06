package dev.gezgin.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers the `gezgin` extension and feeds its settings to KSP as `gezgin.*` processor options.
 *
 * The plugin neither applies KSP nor adds the processor dependency; it configures KSP once the KSP
 * plugin is present, whichever of the two is applied first. The options are computed after the
 * project is evaluated, so the order of statements in the build script does not matter. They are
 * passed as plain values because KSP drops every option when a lazily provided one is absent.
 *
 * @author @sahsenvar
 */
public class GezginPlugin : Plugin<Project> {
  /** Registers the `gezgin` extension on [project]. */
  override fun apply(project: Project) {
    val gezgin = project.extensions.create("gezgin", GezginExtension::class.java)
    project.pluginManager.withPlugin("com.google.devtools.ksp") {
      project.afterEvaluate {
        val ksp = project.extensions.getByType(KspExtension::class.java)
        gezgin.naming.memberFun.options().forEach { (key, value) -> ksp.arg(key, value) }
      }
    }
  }
}
