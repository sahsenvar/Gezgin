plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Model tipleri @Serializable — koordinat serileştirme runtime'ını (core transitively) getirir.
    implementation(libs.kotlinx.serialization.json)
}
