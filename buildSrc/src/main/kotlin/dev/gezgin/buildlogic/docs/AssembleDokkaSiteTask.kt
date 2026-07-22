package dev.gezgin.buildlogic.docs

import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class AssembleDokkaSiteTask
@Inject
constructor(private val fileSystemOperations: FileSystemOperations) : DefaultTask() {
  @get:Input abstract val moduleNames: ListProperty<String>

  @get:Input abstract val moduleDocumentationPaths: MapProperty<String, String>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val documentationDirectories: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun assemble() {
    val documentationPaths = moduleDocumentationPaths.get()
    val modules = moduleNames.get()
    fileSystemOperations.sync {
      into(outputDirectory)
      modules.forEach { moduleName ->
        from(documentationPaths.getValue(moduleName)) { into(moduleName) }
      }
    }
    outputDirectory
      .file("index.html")
      .get()
      .asFile
      .writeText(indexHtml(modules), StandardCharsets.UTF_8)
  }

  private fun indexHtml(moduleNames: List<String>): String =
    """
    <!doctype html>
    <html lang="en">
      <head><meta charset="utf-8"><title>Gezgin API documentation</title></head>
      <body>
        <h1>Gezgin API documentation</h1>
        <ul>
          ${moduleNames.joinToString("\n") { "<li><a href=\"$it/\">$it</a></li>" }}
        </ul>
      </body>
    </html>
    """
      .trimIndent()
}
