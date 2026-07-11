// bilinçli: bu blok :feature:auth/:feature:home/:feature:profile arasında üçlenir — sample
// okunabilirliği için (her modül tek bakışta anlaşılsın); üretimde convention plugin önerilir.
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.gezgin.sample.feature.auth"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The central nav module (spec §3.3) — brings gezgin-core (routes/navigators) transitively.
    implementation(project(":sample:navigation"))
    implementation(project(":sample:domain"))
    // MVI add-on (Faz 10) — auth ekranları MVI-mode'a çevrildi; GezginMvi/GezginEffects/ObserveEffects
    // ve üretilen GezginMviEntries.kt'nin androidx ViewModel tabanı buradan (`api` yüzeyi) çözülür.
    implementation(project(":gezgin-mvi"))
    ksp(project(":gezgin-processor"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
