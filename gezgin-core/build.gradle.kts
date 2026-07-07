plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}
kotlin {
    jvmToolchain(17)
    // jvm() = desktop Compose hedefi (Faz 3 GezginDisplay); compose.desktop.currentOs çalıştırma zamanı
    // yalnız desktop uiTest'te gerekebilir (Faz 3.2+), burada eklenmedi.
    jvm()
    androidTarget()
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(compose.runtime)
            api(compose.foundation)
            api(libs.androidx.navigation3.runtime)
            api(libs.jb.navigation3.ui)
            api(libs.jb.lifecycle.viewmodel.navigation3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
android { namespace = "dev.gezgin.core"; compileSdk = 36; defaultConfig { minSdk = 24 } }
