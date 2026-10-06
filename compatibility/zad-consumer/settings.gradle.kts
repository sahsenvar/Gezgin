pluginManagement {
  val releaseVerificationRepository =
    providers.gradleProperty("releaseVerificationRepository").orNull
  val gezginRepositoryUrl =
    providers
      .gradleProperty("gezginRepositoryUrl")
      .getOrElse("https://repo.maven.apache.org/maven2")

  repositories {
    exclusiveContent {
      forRepository {
        maven {
          name = "GezginPlugin"
          url = uri(releaseVerificationRepository ?: gezginRepositoryUrl)
        }
      }
      filter {
        includeGroup("io.github.sahsenvar")
        includeGroup("io.github.sahsenvar.gezgin")
      }
    }
    google()
    mavenCentral()
    gradlePluginPortal()
  }

  plugins {
    id("io.github.sahsenvar.gezgin") version
      providers.gradleProperty("gezginVersion").getOrElse("0.3.0")
  }
}

val releaseVerificationRepository = providers.gradleProperty("releaseVerificationRepository").orNull
val gezginRepositoryUrl =
  providers.gradleProperty("gezginRepositoryUrl").getOrElse("https://repo.maven.apache.org/maven2")

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
            url = uri(gezginRepositoryUrl)
          }
        }
        filter { includeGroup("io.github.sahsenvar") }
      }
    }
    mavenCentral { content { excludeGroup("io.github.sahsenvar") } }
  }
}

rootProject.name = "gezgin-zad-consumer"
