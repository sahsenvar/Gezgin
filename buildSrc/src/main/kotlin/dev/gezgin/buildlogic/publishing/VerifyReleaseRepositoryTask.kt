package dev.gezgin.buildlogic.publishing

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The task validates a temporary publication repository.")
abstract class VerifyReleaseRepositoryTask : DefaultTask() {
  @get:InputDirectory abstract val repositoryDirectory: DirectoryProperty

  @get:Input abstract val publicationVersion: Property<String>

  @get:Input abstract val requireSignatures: Property<Boolean>

  @TaskAction
  fun verifyRepository() {
    val summary =
      ReleasePublicationVerifier.verify(
        repository = repositoryDirectory.get().asFile.toPath(),
        version = publicationVersion.get(),
        requireSignatures = requireSignatures.get(),
      )
    logger.lifecycle(
      "Verified ${summary.coordinateCount} release coordinates and " +
        "${summary.signatureCount} detached signatures."
    )
  }
}
