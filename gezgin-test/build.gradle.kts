import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.android.library)
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
  // F-MAJOR-2 — gezgin-test artık yayına-komşu bir artefakt (POM iskeleti aşağıda): manşet "UI'sız
  // test"
  // özelliğinin evi (GezginTestNavigator + codegen'li typed `fromX()` accessor'lar) dış-benimseyene
  // `io.github.sahsenvar:gezgin-test` olarak sunulabilsin diye. Yayınlanan diğer 3 modülü aynen
  // yansıtır:
  // explicitApi() + BCV .api dump (root apiValidation'da artık ignore EDİLMEZ) → tüketilen yüzey
  // ABI-kilitli.
  // @GezginInternalApi işaretli `raw` seam'i BCV'nin nonPublicMarkers'ıyla dump'tan düşer.
  explicitApi()
  jvmToolchain(17)
  jvm()
  androidTarget()
  sourceSets {
    commonMain.dependencies { api(project(":gezgin-core")) }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

android {
  namespace = "dev.gezgin.test"
  compileSdk = 36
  defaultConfig { minSdk = 24 }
}

mavenPublishing {
  configure(
    KotlinMultiplatform(
      javadocJar = JavadocJar.Dokka(tasks.named("dokkaGeneratePublicationHtml")),
      sourcesJar = SourcesJar.Sources(),
      androidVariantsToPublish = listOf("release"),
    )
  )
}
