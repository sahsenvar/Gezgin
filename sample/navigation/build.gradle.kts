plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The central nav module owns the whole sealed graph tree; features depend on it (spec §3.3).
    // `api` so cross-module features see routes/navigators/topology transitively.
    api(project(":gezgin-core"))
    ksp(project(":gezgin-processor"))

    testImplementation(kotlin("test"))
}
