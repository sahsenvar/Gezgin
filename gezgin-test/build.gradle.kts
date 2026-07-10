plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}
kotlin {
    // Faz 9.1 — gezgin-test yayınlanmıyor (maven-publish YOK) ama diğer modüllerin testlerince project-dep
    // olarak tüketiliyor → tutarlılık için açık API yüzeyi. BCV/POM YOK (yalnız yayınlanan 3 modül).
    explicitApi()
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
