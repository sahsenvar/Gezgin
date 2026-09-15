import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
}

kotlin {
  jvmToolchain(17)
  androidTarget { compilerOptions { jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY) } }
  // gezgin-core ile AYNI Apple hedef kümesi; iosX64 upstream'de yayınlanmıyor.
  listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "HelloShared"
      // Xcode projesi framework'ü doğrudan gömer; dinamik bağlama ek bir kurulum ister.
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":gezgin-core"))
      implementation(libs.kotlinx.serialization.json)
      // Ekran kodu androidx.compose.* paket adlarını kullanır; JetBrains artefaktları AYNI
      // paketleri multiplatform olarak yayınlar, bu yüzden kaynak kodu değişmedi.
      api("org.jetbrains.compose.runtime:runtime:${libs.versions.compose.multiplatform.get()}")
      api(
        "org.jetbrains.compose.foundation:foundation:${libs.versions.compose.multiplatform.get()}"
      )
      api("org.jetbrains.compose.ui:ui:${libs.versions.compose.multiplatform.get()}")
      api("org.jetbrains.compose.material3:material3:${libs.versions.compose.material3.get()}")
      // Sarmalayıcı kendi ViewModel'ini çözer ve state'i toplar. Sürüm 2.10.0: JetBrains bu
      // artefaktı 2.9.6 olarak YAYINLAMIYOR (en düşük 2.10.0) ve 2.11.0 hattının Android varyantı
      // AGP 9.1 ile compileSdk 37 istiyor. `compileOnly` de çözüm değil — runtime sınıf yolunda
      // görünmediğinden AGP'nin tutarlı çözümlemesi derleme tarafını compose-ui'nin 2.9.6'sına
      // sabitler ve uyuşmazlık çıkar.
      api(libs.jb.lifecycle.viewmodel.compose)
      api(libs.jb.lifecycle.runtime.compose)
    }
  }
}

// KMP'de `ksp(...)` tek satırı hedeflere ULAŞMAZ. Gezgin'in ürettiği navigator/entry kodu
// platformdan bağımsız olduğundan işlemci bir kez ortak metadata üzerinde koşar ve çıktısı
// commonMain'e kaynak dizini olarak eklenir; her derleme görevi o koşuma bağlanır.
dependencies { add("kspCommonMainMetadata", project(":gezgin-processor")) }

kotlin.sourceSets.commonMain { kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin") }

tasks.withType<KotlinCompilationTask<*>>().configureEach {
  if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}

android {
  namespace = "dev.gezgin.sample.hello.shared"
  compileSdk = 36
  defaultConfig { minSdk = 24 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
