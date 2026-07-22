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
        mavenCentral()
        if (releaseVerificationRepository != null) {
            val repositoryPath = releaseVerificationRepository
            maven { url = uri(repositoryPath) }
        } else {
            mavenLocal()
        }
    }
}

rootProject.name = "gezgin-zad-consumer"
