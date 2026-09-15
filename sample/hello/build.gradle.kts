import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
}

kotlin {
  jvmToolchain(17)
  androidTarget { compilerOptions { jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY) } }
  // gezgin-core ile AYNI Apple hedef kümesi; iosX64 upstream'de yayınlanmıyor.
  listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "HelloApp"
      // Xcode projesi framework'ü doğrudan gömer; dinamik bağlama ek bir kurulum ister.
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":gezgin-core"))
      implementation(libs.kotlinx.serialization.json)
      // Ekran kodu androidx.compose.* paket adlarını kullanır; JetBrains artefaktları AYNI
      // paketleri multiplatform olarak yayınlar, bu yüzden tek satır bile kaynak değişmedi.
      implementation(
        "org.jetbrains.compose.runtime:runtime:${libs.versions.compose.multiplatform.get()}"
      )
      implementation(
        "org.jetbrains.compose.foundation:foundation:${libs.versions.compose.multiplatform.get()}"
      )
      implementation("org.jetbrains.compose.ui:ui:${libs.versions.compose.multiplatform.get()}")
      implementation(
        "org.jetbrains.compose.material3:material3:${libs.versions.compose.material3.get()}"
      )
      // Sarmalayıcı kendi ViewModel'ini çözer ve state'i toplar. gezgin-core'un §2.2 ayrımı
      // burada da geçerli: lifecycle ailesi Android'de AndroidX, non-Android'de JetBrains
      // olmalı. JetBrains 2.11.0 hattının Android varyantı AGP 9.1 ve compileSdk 37 istiyor;
      // ortak koda konursa Android derlemesi çözümleme aşamasında kırılır. Ortak kaynak yalnız
      // tiplere KARŞI derlenir, gerçek artefaktı her platform kendi kümesinden getirir.
      compileOnly(libs.jb.lifecycle.viewmodel.compose)
      compileOnly(libs.jb.lifecycle.runtime.compose)
    }
    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation(libs.androidx.lifecycle.runtime.compose)
    }
    iosMain.dependencies {
      implementation(libs.jb.lifecycle.viewmodel.compose)
      implementation(libs.jb.lifecycle.runtime.compose)
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
  namespace = "dev.gezgin.sample.hello"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.gezgin.sample.hello"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
