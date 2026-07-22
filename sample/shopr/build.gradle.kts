plugins {
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
}

android {
  namespace = "dev.gezgin.sample.shopr"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.gezgin.sample.shopr"
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
  testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

dependencies {
  implementation(project(":gezgin-core"))
  implementation(project(":sample:domain"))
  // MVI add-on — shopr ekranları MVI-mode'u kullanır; `api` yüzeyiyle JB
  // lifecycle-viewmodel-compose/runtime-compose'u transitively getirir (androidx `ViewModel` tabanı
  // +
  // viewModelScope + collectAsStateWithLifecycle üretilen entry'lerden çözülür).
  implementation(project(":gezgin-mvi"))
  ksp(project(":gezgin-processor"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.activity.compose)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(kotlin("test-junit"))
  testImplementation("org.robolectric:robolectric:4.14")
}
