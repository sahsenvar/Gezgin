import com.vanniktech.maven.publish.MavenPublishBaseExtension
import dev.gezgin.buildlogic.kdoc.CheckPublicApiKDocTask
import dev.gezgin.buildlogic.publishing.VerifyReleaseRepositoryTask
import org.gradle.api.publish.PublishingExtension

plugins {
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.kotlin.compose) apply false
  // Faz 9.1 — BCV yalnız KÖK'e uygulanır (apply false DEĞİL); alt-projelerin apiCheck/apiDump
  // görevlerini
  // kendisi kurar ve `apiCheck`'i `check` yaşam-döngüsüne bağlar (varsayılan davranış).
  alias(libs.plugins.binary.compatibility.validator)
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.maven.publish) apply false
  alias(libs.plugins.spotless)
  alias(libs.plugins.kover)
}

val releaseGroup = providers.gradleProperty("GROUP").get()
val releaseVersion = providers.gradleProperty("VERSION_NAME").get()
val publishedProjectPaths =
  setOf(":gezgin-core", ":gezgin-mvi", ":gezgin-test", ":gezgin-processor")
val publishedProjects = publishedProjectPaths.map(::project)
val koverMinLineCoverage = providers.gradleProperty("KOVER_MIN_LINE_COVERAGE").map(String::toInt)

publishedProjects.forEach { publishedProject ->
  publishedProject.pluginManager.apply("org.jetbrains.kotlinx.kover")
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/.gradle/**", "**/build/**", "**/generated/**")
    ktfmt("0.58").googleStyle()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/.gradle/**", "**/build/**")
    ktfmt("0.58").googleStyle()
  }
}

dependencies { publishedProjects.forEach { publishedProject -> add("kover", publishedProject) } }

kover {
  reports {
    total {
      filters { includes { classes("dev.gezgin.*") } }
      html { onCheck = true }
      verify {
        rule("published production line coverage") {
          bound {
            minValue = koverMinLineCoverage.get()
            coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
            aggregationForGroup = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
          }
        }
      }
    }
  }
}

val publishedModuleDescriptions =
  mapOf(
    "gezgin-core" to
      "DI-agnostic Kotlin Multiplatform navigation runtime and Compose display layer.",
    "gezgin-mvi" to "Optional MVI bindings and generated route effect handlers for Gezgin.",
    "gezgin-test" to "UI-free typed navigation test utilities for Gezgin applications.",
    "gezgin-processor" to
      "KSP2 processor that generates typed Gezgin navigators and entry providers.",
  )

configure(publishedProjects) {
  group = releaseGroup
  version = releaseVersion

  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
      publishToMavenCentral()
      signAllPublications()
      pom {
        name.set(project.name)
        description.set(publishedModuleDescriptions.getValue(project.name))
        url.set("https://github.com/sahsenvar/Gezgin")
        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        developers {
          developer {
            id.set("sahsenvar")
            name.set("Şahan Şenvar")
            url.set("https://github.com/sahsenvar")
          }
        }
        scm {
          url.set("https://github.com/sahsenvar/Gezgin")
          connection.set("scm:git:https://github.com/sahsenvar/Gezgin.git")
          developerConnection.set("scm:git:ssh://git@github.com/sahsenvar/Gezgin.git")
        }
      }
    }

    providers.gradleProperty("releaseVerificationRepository").orNull?.let { repositoryPath ->
      extensions.configure<PublishingExtension> {
        repositories.maven {
          name = "ReleaseVerification"
          url = uri(repositoryPath)
        }
      }
    }
  }
}

val releaseVerificationRepository = providers.gradleProperty("releaseVerificationRepository")
val releaseVerificationDirectory = layout.dir(releaseVerificationRepository.map(::file))

val verifyPublishedReleaseRepository =
  tasks.register<VerifyReleaseRepositoryTask>("verifyPublishedReleaseRepository") {
    group = "verification"
    description =
      "Verifies the exact artifacts, metadata, dependency mappings, docs, and signatures."
    repositoryDirectory.set(releaseVerificationDirectory)
    requireSignatures.set(
      providers.gradleProperty("verifyReleaseSignatures").map(String::toBoolean).orElse(false)
    )
  }

gradle.projectsEvaluated {
  verifyPublishedReleaseRepository.configure {
    dependsOn(
      publishedProjects.map { publishedProject ->
        "${publishedProject.path}:publishAllPublicationsToReleaseVerificationRepository"
      }
    )
  }
}

val verifyReleaseConsumer =
  tasks.register<Exec>("verifyReleaseConsumer") {
    group = "verification"
    description = "Compiles the Gradle 9.4.1 consumer against only the injected Gezgin repository."
    dependsOn(verifyPublishedReleaseRepository)
    workingDir(layout.projectDirectory.dir("compatibility/zad-consumer"))
    doFirst {
      val repository = releaseVerificationDirectory.get().asFile
      val projectCache = repository.parentFile.resolve("consumer-project-cache")
      commandLine(
        "./gradlew",
        "compileDebugKotlin",
        "-PreleaseVerificationRepository=${repository.absolutePath}",
        "--refresh-dependencies",
        "--rerun-tasks",
        "--no-build-cache",
        "--project-cache-dir=${projectCache.absolutePath}",
        "--no-daemon",
      )
    }
  }

tasks.register("verifyReleasePublications") {
  group = "verification"
  description = "Publishes, verifies, and consumes the complete signed release repository."
  dependsOn(verifyReleaseConsumer)
}

val publicApiSourceRoots =
  mapOf(
    "gezgin-core" to listOf("commonMain", "androidMain", "jvmMain"),
    "gezgin-mvi" to listOf("commonMain", "androidMain", "jvmMain"),
    "gezgin-processor" to listOf("main"),
    "gezgin-test" to listOf("commonMain", "androidMain", "jvmMain"),
  )

val publicApiKDocScannerClasspath by
  configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isVisible = false
  }

dependencies {
  publicApiKDocScannerClasspath("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
}

tasks.register<CheckPublicApiKDocTask>("checkPublicApiKDoc") {
  projectRoot.set(layout.projectDirectory)
  expectedInventory.set(
    mapOf(
      "gezgin-core" to "136/17",
      "gezgin-mvi" to "16/0",
      "gezgin-processor" to "1/1",
      "gezgin-test" to "12/1",
    )
  )
  scannerClasspath.from(publicApiKDocScannerClasspath)
  val moduleSources =
    publicApiSourceRoots.mapValues { (module, sourceSets) ->
      sourceSets.map { sourceSet ->
        fileTree("$module/src/$sourceSet/kotlin") { include("**/*.kt") }
      }
    }
  sourceFiles.from(moduleSources.values.flatten())
}

tasks.named("check") {
  dependsOn("spotlessCheck")
  dependsOn("koverVerify")
  dependsOn("checkPublicApiKDoc")
}

subprojects {
  tasks
    .matching { it.name == "check" }
    .configureEach { dependsOn(rootProject.tasks.named("checkPublicApiKDoc")) }
}

// F-MAJOR-2 — ABI doğrulaması artık yayınlanan/yayına-komşu 4 modül için (core/mvi/processor/test).
// gezgin-test bir POM iskeleti kazandı ve dış-benimseyene sunulabilir hale geldi → BCV kapsamına
// alındı
// (.api dump tutulur). Yalnız sample/* modülleri (gerçekten yayınlanmayan) hariç tutulur.
apiValidation {
  // Faz 9.3 (K4) — @GezginInternalApi ile işaretli forced-public semboller (codegen/gezgin-test
  // kancaları)
  // kilitli ABI yüzeyinden düşürülür → alpha01 sonrası deprecation döngüsü olmadan evrilebilirler.
  nonPublicMarkers += "dev.gezgin.core.GezginInternalApi"
  ignoredProjects += listOf("shopr", "navigation", "app", "domain", "auth", "home", "profile")
}
