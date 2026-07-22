pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

val releaseVerificationRepository = providers.gradleProperty("releaseVerificationRepository").orNull

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    if (releaseVerificationRepository != null) {
      val repositoryPath = releaseVerificationRepository
      exclusiveContent {
        forRepository {
          maven {
            name = "GezginReleaseVerification"
            url = uri(repositoryPath)
          }
        }
        filter { includeGroup("io.github.sahsenvar") }
      }
    } else {
      exclusiveContent {
        forRepository {
          maven {
            name = "GezginMavenCentral"
            url = uri("https://repo.maven.apache.org/maven2")
          }
        }
        filter { includeGroup("io.github.sahsenvar") }
      }
    }
    mavenCentral { content { excludeGroup("io.github.sahsenvar") } }
  }
}

rootProject.name = "gezgin-zad-consumer"
