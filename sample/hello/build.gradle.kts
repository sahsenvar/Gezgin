plugins {
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.android.application)
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

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  // Ekranlar, graph ve codegen paylaşılan modülde; bu modül yalnız Android host'u.
  // Compose ve lifecycle ailesi oradan `api` ile gelir — iki modülün aynı aileyi
  // kullanması, Android sınıf yolunda sürüm çakışmasını önler.
  implementation(project(":sample:hello-shared"))
  implementation(libs.androidx.activity.compose)
}
