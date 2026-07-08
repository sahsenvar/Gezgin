plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(project(":gezgin-core"))
    testImplementation(project(":gezgin-test"))
    // Faz 5.1 — MVI-mode fixtures (`@ViewModel`/`@ScreenEffect`/`GezginMvi`) compiled by kctfork.
    // Mirrors the `:gezgin-core` test dep; the processor itself has NO compile dep on gezgin-mvi
    // (all its annotations are read as string FQNs), only this test sourceset does.
    testImplementation(project(":gezgin-mvi"))
    testImplementation(libs.kctfork.ksp)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
