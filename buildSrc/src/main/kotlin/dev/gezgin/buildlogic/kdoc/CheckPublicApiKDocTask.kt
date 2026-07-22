package dev.gezgin.buildlogic.kdoc

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

@CacheableTask
abstract class CheckPublicApiKDocTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceFiles: ConfigurableFileCollection

  @get:Internal abstract val projectRoot: DirectoryProperty

  @get:Input abstract val expectedInventory: MapProperty<String, String>

  @get:Classpath abstract val scannerClasspath: ConfigurableFileCollection

  @get:Inject abstract val workerExecutor: WorkerExecutor

  init {
    group = "verification"
    description =
      "Checks handwritten consumer-visible public Kotlin declarations for KDoc and authorship."
    expectedInventory.convention(emptyMap())
  }

  @TaskAction
  fun checkPublicApiKDoc() {
    val inputSources = sourceFiles
    val inputRoot = projectRoot
    val inputInventory = expectedInventory
    workerExecutor
      .classLoaderIsolation { classpath.from(scannerClasspath) }
      .submit(CheckPublicApiKDocWorkAction::class.java) {
        sourceFiles.from(inputSources)
        projectRoot.set(inputRoot)
        expectedInventory.set(inputInventory)
      }
  }
}

interface CheckPublicApiKDocWorkParameters : WorkParameters {
  val sourceFiles: ConfigurableFileCollection
  val projectRoot: DirectoryProperty
  val expectedInventory: MapProperty<String, String>
}

abstract class CheckPublicApiKDocWorkAction : WorkAction<CheckPublicApiKDocWorkParameters> {
  override fun execute() {
    val root = parameters.projectRoot.get().asFile
    val results =
      KotlinPublicApiScanner().use { scanner ->
        parameters.sourceFiles.files
          .asSequence()
          .filter { it.isFile && it.extension == "kt" }
          .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
          .map { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            scanner.scan(KotlinSourceInput(path = path, content = file.readText()))
          }
          .toList()
      }
    val declarations = results.flatMap(KotlinPublicApiScanResult::declarations)
    val inventory =
      declarations
        .groupBy { it.path.substringBefore('/') }
        .mapValues { (_, moduleDeclarations) ->
          val included = moduleDeclarations.count { !it.excluded }
          val excluded = moduleDeclarations.count(PublicApiDeclaration::excluded)
          "$included/$excluded"
        }
        .toSortedMap()

    Logging.getLogger(CheckPublicApiKDocWorkAction::class.java)
      .lifecycle(
        "Public API KDoc inventory: " +
          inventory.entries.joinToString(", ") { (module, count) ->
            val (included, excluded) = count.split('/')
            "$module=$included included/$excluded excluded"
          }
      )

    val expected = parameters.expectedInventory.get()
    if (expected.isNotEmpty() && inventory != expected.toSortedMap()) {
      throw GradleException(
        "Public API KDoc inventory changed. Review the declarations and update expectedInventory " +
          "intentionally. Expected ${expected.toSortedMap()}, actual $inventory"
      )
    }

    val findings =
      results
        .flatMap(KotlinPublicApiScanResult::findings)
        .sortedWith(compareBy({ it.declaration.path }, { it.declaration.line }, { it.kind.name }))
    if (findings.isNotEmpty()) {
      val details =
        findings.joinToString("\n") { finding ->
          val declaration = finding.declaration
          val reason =
            when (finding.kind) {
              KDocFindingKind.MISSING_KDOC -> "missing KDoc"
              KDocFindingKind.MISSING_AUTHOR -> "missing exact @author @sahsenvar"
              KDocFindingKind.NON_ENGLISH_KDOC -> "public KDoc must be English"
              KDocFindingKind.PROCESS_ARTIFACT_KDOC ->
                "public KDoc contains an implementation-process artifact"
              KDocFindingKind.INTERNAL_SPEC_KDOC ->
                "public KDoc contains internal spec or review notation"
              KDocFindingKind.MALFORMED_KDOC -> "public KDoc contains malformed prose"
            }
          "${declaration.path}:${declaration.line}:${declaration.column}: $reason: " +
            "${declaration.kind} ${declaration.name}"
        }
      throw GradleException("Public API KDoc check failed (${findings.size} finding(s)):\n$details")
    }
  }
}
