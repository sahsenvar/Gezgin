plugins {
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.android.library)
}

android {
  namespace = "dev.gezgin.sample.designsystem"
  compileSdk = 36
  defaultConfig { minSdk = 24 }
  buildFeatures { compose = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  // `api` so every feature module sees the wrapper, its slot markers and Gezgin's annotations.
  api(project(":gezgin-core"))

  api(platform(libs.androidx.compose.bom))
  api(libs.androidx.compose.ui)
  api(libs.androidx.compose.material3)
  api(libs.androidx.lifecycle.viewmodel.compose)
  api(libs.androidx.lifecycle.runtime.compose)
}
