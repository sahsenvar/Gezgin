import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
  alias(libs.plugins.kotlin.jvm)
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
  // Faz 9.1 — açık API yüzeyi. Processor'ın yayınlanan tek public tipi KSP giriş noktası
  // `GezginProcessorProvider`'dır (ServiceLoader); geri kalan tüm codegen/model/reader tipleri
  // `internal` (yalnız bu modül + kendi testleri kullanır → API yüzeyi minimuma iner).
  explicitApi()
  jvmToolchain(17)
}

dependencies {
  implementation(libs.ksp.api)
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)

  testImplementation(project(":gezgin-core"))
  testImplementation(project(":gezgin-test"))
  // Faz 5.1 — MVI-mode fixtures (`@MviViewModel`/`@EffectHandler`/`GezginMvi`) compiled by kctfork.
  // Mirrors the `:gezgin-core` test dep; the processor itself has NO compile dep on gezgin-mvi
  // (all its annotations are read as string FQNs), only this test sourceset does.
  testImplementation(project(":gezgin-mvi"))
  testImplementation(libs.kctfork.ksp)
  // Compile-testing resolves the same KSP 2.3.9 API as production; keep it explicit so a
  // transitive fork dependency cannot silently move the processor test toolchain.
  testImplementation(libs.ksp.api)
  testImplementation(kotlin("test-junit5"))
  testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test { useJUnitPlatform() }

mavenPublishing {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Dokka(tasks.named("dokkaGeneratePublicationHtml")),
      sourcesJar = SourcesJar.Sources(),
    )
  )
}
