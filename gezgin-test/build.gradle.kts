plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}
kotlin {
    jvmToolchain(17)
    jvm()
    androidTarget()
    sourceSets {
        commonMain.dependencies {
            api(project(":gezgin-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
android { namespace = "dev.gezgin.test"; compileSdk = 36; defaultConfig { minSdk = 24 } }
