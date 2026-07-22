import dev.gezgin.buildlogic.kdoc.CheckPublicApiKDocTask

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Faz 9.1 — BCV yalnız KÖK'e uygulanır (apply false DEĞİL); alt-projelerin apiCheck/apiDump görevlerini
    // kendisi kurar ve `apiCheck`'i `check` yaşam-döngüsüne bağlar (varsayılan davranış).
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka) apply false
}

val publicApiSourceRoots = mapOf(
    "gezgin-core" to listOf("commonMain", "androidMain", "jvmMain"),
    "gezgin-mvi" to listOf("commonMain", "androidMain", "jvmMain"),
    "gezgin-processor" to listOf("main"),
    "gezgin-test" to listOf("commonMain", "androidMain", "jvmMain"),
)

val publicApiKDocScannerClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isVisible = false
}

dependencies {
    publicApiKDocScannerClasspath("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
}

tasks.register<CheckPublicApiKDocTask>("checkPublicApiKDoc") {
    projectRoot.set(layout.projectDirectory)
    expectedInventory.set(
        mapOf(
            "gezgin-core" to "136/17",
            "gezgin-mvi" to "16/0",
            "gezgin-processor" to "1/1",
            "gezgin-test" to "12/1",
        ),
    )
    scannerClasspath.from(publicApiKDocScannerClasspath)
    val moduleSources = publicApiSourceRoots.mapValues { (module, sourceSets) ->
        sourceSets.map { sourceSet -> fileTree("$module/src/$sourceSet/kotlin") { include("**/*.kt") } }
    }
    sourceFiles.from(moduleSources.values.flatten())
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("checkPublicApiKDoc"))
    }
}

// F-MAJOR-2 — ABI doğrulaması artık yayınlanan/yayına-komşu 4 modül için (core/mvi/processor/test).
// gezgin-test bir POM iskeleti kazandı ve dış-benimseyene sunulabilir hale geldi → BCV kapsamına alındı
// (.api dump tutulur). Yalnız sample/* modülleri (gerçekten yayınlanmayan) hariç tutulur.
apiValidation {
    // Faz 9.3 (K4) — @GezginInternalApi ile işaretli forced-public semboller (codegen/gezgin-test kancaları)
    // kilitli ABI yüzeyinden düşürülür → alpha01 sonrası deprecation döngüsü olmadan evrilebilirler.
    nonPublicMarkers += "dev.gezgin.core.GezginInternalApi"
    ignoredProjects += listOf(
        "shopr", "navigation", "app", "domain",
        "auth", "home", "profile",
    )
}
