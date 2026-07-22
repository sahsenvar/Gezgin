pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val releaseVerificationRepository = providers.gradleProperty("releaseVerificationRepository").orNull
val useAlpha04MavenLocal = providers.gradleProperty("useAlpha04MavenLocal")
    .map(String::toBoolean)
    .getOrElse(false)

require(releaseVerificationRepository == null || !useAlpha04MavenLocal) {
    "useAlpha04MavenLocal cannot be combined with releaseVerificationRepository"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (releaseVerificationRepository != null) {
            val repositoryPath = releaseVerificationRepository
            exclusiveContent {
                forRepository {
                    maven {
                        name = "GezginReleaseVerification"
                        url = uri(repositoryPath)
                    }
                }
                filter {
                    includeGroup("io.github.sahsenvar")
                }
            }
        } else if (useAlpha04MavenLocal) {
            mavenLocal()
        }
    }
}

rootProject.name = "gezgin-zad-consumer"
