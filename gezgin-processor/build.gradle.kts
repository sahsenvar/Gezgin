plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

// Faz 7.4 — versiyonlama: proje ilk sürümü (bkz. gezgin-core/build.gradle.kts gerekçesi — modül-başına
// açık `version`, root gradle.properties'e konmaz ki gezgin-test/sample'a sızmasın).
group = "dev.gezgin"
version = "0.1.0-alpha01"

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

// İskelet — gerçek bir Maven repository/credentials YOK; `./gradlew publish` çalıştırılmaz, yalnız
// `assemble`/`build`'in bu bloktan etkilenmediği doğrulanır. Düz JVM modülü → tek `java`-bileşenli
// publication elle tanımlanır (KMP'nin aksine otomatik değil). Repository/signing bilinçle EKLENMEDİ.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("gezgin-processor")
                description.set(
                    "Gezgin KSP2 symbol processor — tipli navigator'ları, entry provider'larını ve " +
                        "deep-link tablosunu üretir; ksp(project(\":gezgin-processor\")) ile uygulanır.",
                )
            }
        }
    }
}
