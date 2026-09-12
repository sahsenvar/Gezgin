plugins {
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
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
  implementation(project(":gezgin-core"))
  ksp(project(":gezgin-processor"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.activity.compose)
  implementation(libs.kotlinx.serialization.json)
  // The wrapper resolves its own ViewModel and collects state; gezgin-core no longer
  // brings these in, so the app declares them itself.
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
}
