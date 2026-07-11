// bilinçli: bu blok :feature:auth/:feature:home/:feature:profile arasında üçlenir — sample
// okunabilirliği için (her modül tek bakışta anlaşılsın); üretimde convention plugin önerilir.
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.gezgin.sample.feature.home"
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
    implementation(project(":sample:navigation"))
    implementation(project(":sample:domain"))
    // MVI add-on (Faz 10) — Dashboard/ItemDetail/Welcome MVI-mode'a çevrildi; üretilen entry'lerin
    // viewModel()/collectAsStateWithLifecycle() çağrıları ve VM'lerin androidx `ViewModel` tabanı buradan.
    implementation(project(":gezgin-mvi"))
    ksp(project(":gezgin-processor"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    // Faz 6.4 — `@FragmentScreen HelpFragment` + üretilen `provideHelpEntry()`'nin `AndroidFragment<..>`
    // çağrısı için. gezgin-core fragment-compose'u `implementation` tuttuğundan TÜKETİCİYE SIZMAZ →
    // @FragmentScreen barındıran bu feature modülü kendi fragment-compose'unu AÇIKÇA getirir (hem
    // `AndroidFragment` composable'ı hem transitively `androidx.fragment.app.Fragment`).
    implementation(libs.androidx.fragment.compose)
}
