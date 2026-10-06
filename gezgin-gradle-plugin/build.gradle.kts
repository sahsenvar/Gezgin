import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
  alias(libs.plugins.kotlin.jvm)
  `java-gradle-plugin`
  alias(libs.plugins.dokka)
  alias(libs.plugins.maven.publish)
}

dokka {
  dokkaPublications.html { failOnWarning.set(true) }
  dokkaSourceSets.configureEach {
    reportUndocumented.set(true)
    suppressGeneratedFiles.set(true)
  }
}

kotlin {
  explicitApi()
  jvmToolchain(17)
}

gradlePlugin {
  plugins {
    register("gezgin") {
      id = "io.github.sahsenvar.gezgin"
      implementationClass = "dev.gezgin.gradle.GezginPlugin"
      displayName = "Gezgin"
      description = "Typed Gradle DSL over the Gezgin KSP processor options."
    }
  }
}

dependencies {
  compileOnly(
    "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${libs.versions.ksp.get()}"
  )

  testImplementation(
    "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${libs.versions.ksp.get()}"
  )
  testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
  testImplementation(kotlin("test-junit5"))
}

tasks.test { useJUnitPlatform() }

mavenPublishing {
  configure(
    GradlePlugin(
      javadocJar = JavadocJar.Dokka(tasks.named("dokkaGeneratePublicationHtml")),
      sourcesJar = SourcesJar.Sources(),
    )
  )
}
