// bilinçli: bu blok :feature:auth/:feature:home/:feature:profile arasında üçlenir — sample
// okunabilirliği için (her modül tek bakışta anlaşılsın); üretimde convention plugin önerilir.
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.gezgin.sample.feature.profile"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sample:navigation"))
    implementation(project(":sample:domain"))
    // MVI add-on (Faz 5) — SettingsScreen MVI-mode'a çevrildi (bkz. SettingsMvi.kt). `api` yüzeyiyle
    // JB lifecycle-viewmodel-compose / lifecycle-runtime-compose'u transitively getirir; üretilen
    // GezginMviEntries.kt'nin viewModel()/viewModelFactory{}/collectAsStateWithLifecycle() çağrıları
    // ile SettingsViewModel'in androidx `ViewModel` tabanı buradan çözülür (ayrıca eklenmez).
    implementation(project(":gezgin-mvi"))
    ksp(project(":gezgin-processor"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.androidx.activity.compose)
    testImplementation("org.robolectric:robolectric:4.14")
}
