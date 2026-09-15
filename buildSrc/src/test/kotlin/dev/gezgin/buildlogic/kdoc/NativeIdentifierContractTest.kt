package dev.gezgin.buildlogic.kdoc

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin/Native rejects a backtick-quoted declaration name containing any of `(`, `)`, `@`, `,` or
 * `§` with "Name contains illegal characters". The JVM-style `` `back() pops the top entry` ``
 * spelling therefore cannot appear in a source set that compiles for an Apple target. Without this
 * gate the breakage only surfaces on CI, one source set at a time, because a name is legal right up
 * until the day its source set gains those targets.
 */
class NativeIdentifierContractTest {
  private val projectRoot: Path =
    Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().let { workingDirectory ->
      if (workingDirectory.fileName.toString() == "buildSrc") workingDirectory.parent
      else workingDirectory
    }

  @Test
  fun `declaration names in Apple compiled source sets avoid illegal characters`() {
    val sources =
      appleCompiledSourceSets.map(projectRoot::resolve).filter(Files::isDirectory).flatMap { root ->
        Files.walk(root).use { paths ->
          paths.filter(Path::isRegularFile).toList().filter { it.extension == "kt" }
        }
      }
    val findings =
      sources
        .flatMap { path ->
          val relative = projectRoot.relativize(path).toString().replace('\\', '/')
          backtickedName.findAll(path.readText()).mapNotNull { match ->
            val name = match.groupValues[1]
            val illegal = name.filter { it in illegalCharacters }.toSortedSet()
            if (illegal.isEmpty()) null
            else "$relative: `$name` contains ${illegal.joinToString(separator = "")}"
          }
        }
        .sorted()

    assertEquals(emptyList(), findings, findings.joinToString(separator = "\n"))
  }

  private companion object {
    /** Every source set that Gradle compiles for `iosArm64` or `iosSimulatorArm64`. */
    val appleCompiledSourceSets =
      listOf(
        "gezgin-core/src/commonMain",
        "gezgin-core/src/commonTest",
        "gezgin-core/src/nonAndroidMain",
        "gezgin-core/src/nonAndroidTest",
        "gezgin-core/src/iosMain",
        "gezgin-core/src/iosTest",
        "gezgin-test/src/commonMain",
        "gezgin-test/src/commonTest",
      )
    val backtickedName = Regex("(?:fun|val|var|class|object|interface)\\s+`([^`]*)`")
    val illegalCharacters = setOf('(', ')', '@', ',', '§')
  }
}
