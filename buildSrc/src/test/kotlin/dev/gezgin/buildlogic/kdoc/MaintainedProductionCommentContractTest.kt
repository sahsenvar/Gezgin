package dev.gezgin.buildlogic.kdoc

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class MaintainedProductionCommentContractTest {
  private val projectRoot: Path =
    Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().let { workingDirectory ->
      if (workingDirectory.fileName.toString() == "buildSrc") workingDirectory.parent
      else workingDirectory
    }

  @Test
  fun `maintained production comments contain no rewrite or process artifacts`() {
    val findings =
      KotlinProductionCommentScanner().use { scanner ->
        publishedModules.flatMap { module ->
          Files.walk(projectRoot.resolve(module).resolve("src")).use { paths ->
            paths
              .filter(Path::isRegularFile)
              .filter { path ->
                path.extension == "kt" &&
                  productionSourceSet.containsMatchIn(
                    path.toString().replace('\\', '/').lowercase()
                  )
              }
              .flatMap { path ->
                val relative = projectRoot.relativize(path).toString().replace('\\', '/')
                scanner.scan(KotlinSourceInput(path = relative, content = path.readText())).stream()
              }
              .toList()
          }
        }
      }
    val summaries =
      findings
        .map { finding ->
          val excerpt = finding.text.lineSequence().joinToString(" ") { it.trim() }.take(240)
          "${finding.path}:${finding.line}: ${finding.kind}: $excerpt"
        }
        .sorted()

    assertEquals(emptyList<String>(), summaries, summaries.joinToString(separator = "\n"))
  }

  private companion object {
    val publishedModules = listOf("gezgin-core", "gezgin-mvi", "gezgin-processor", "gezgin-test")
    val productionSourceSet = Regex("/src/[^/]*main/")
  }
}
