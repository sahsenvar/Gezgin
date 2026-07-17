plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.gezgin.sample.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.gezgin.sample.app"
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

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Central nav + every feature — `:app` is where the graph and the per-feature entries assemble.
    implementation(project(":sample:navigation"))
    implementation(project(":sample:feature:auth"))
    implementation(project(":sample:feature:home"))
    implementation(project(":sample:feature:profile"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)

    // Faz 6.4 — `AndroidFragment` host'unun ZORUNLU `FragmentActivity`/`AppCompatActivity` olması için
    // (§11.1/Task 6.0 §1e.1). gezgin-core fragment-compose'u `implementation` tuttuğundan tüketiciye
    // SIZMAZ → host modülü (bu :app) kendi appcompat'ını AÇIKÇA getirir. appcompat, `AppCompatActivity`
    // + `Theme.AppCompat.*` + transitively `androidx.fragment`'ı sağlar.
    implementation(libs.androidx.appcompat)

    testImplementation(kotlin("test"))
}
