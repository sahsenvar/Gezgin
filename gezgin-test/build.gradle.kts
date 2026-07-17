plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    `maven-publish`
}

// F-MAJOR-2 — versiyonlama: yayınlanan diğer 3 modülle aynı koordinat. Modül-başına açık `version`
// (root gradle.properties'e konmaz ki sample'a sızmasın — bkz. gezgin-core/mvi build.gradle.kts gerekçesi).
group = "dev.gezgin"
version = "0.1.0-alpha02"

kotlin {
    // F-MAJOR-2 — gezgin-test artık yayına-komşu bir artefakt (POM iskeleti aşağıda): manşet "UI'sız test"
    // özelliğinin evi (GezginTestNavigator + codegen'li typed `fromX()` accessor'lar) dış-benimseyene
    // `dev.gezgin:gezgin-test` olarak sunulabilsin diye. Yayınlanan diğer 3 modülü aynen yansıtır:
    // explicitApi() + BCV .api dump (root apiValidation'da artık ignore EDİLMEZ) → tüketilen yüzey ABI-kilitli.
    // @GezginInternalApi işaretli `raw` seam'i BCV'nin nonPublicMarkers'ıyla dump'tan düşer.
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

// İSKELET — gerçek bir Maven repository/credentials YOK; `./gradlew publish*` ASLA çalıştırılmaz. Yalnız
// `assemble`/`build`'in bu bloktan etkilenmediği ve POM'un geçerli olduğu (`generatePomFile*`) doğrulanır.
// KMP publication'ları `maven-publish` ile otomatik kurulur; yalnız POM metadata'sı tembel (`configureEach`)
// eklenir. Repository/signing/credentials YOK — gezgin-core/mvi ile birebir aynı iskelet deseni.
// NOT (gerçek yayın günü için): `android { publishLibraryVariants("release") }` HENÜZ eklenmedi — bkz.
// gezgin-core/build.gradle.kts'teki aynı not; V1 sonrası gerçek yayın kontrol listesine eklenmeli.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("gezgin-test")
            description.set(
                "UI'sız test yardımcı artefaktı: GezginTestNavigator + codegen'li typed `fromX()` " +
                    "accessor'ların evi (gezgin-core'a bağımlı). Test source-set'lerince tüketilir.",
            )
            // Faz 9.1 — Maven Central'ın zorunlu kıldığı POM metadata'sı (url/licenses/developers/scm).
            // repository{}/signing HÂLÂ YOK: bu blok iskelet kalır, `publish` çalıştırılmaz (yukarıdaki nota bkz.).
            url.set("https://github.com/sahsenvar/Gezgin")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("sahsenvar")
                    name.set("Şahan Şenvar")
                }
            }
            scm {
                url.set("https://github.com/sahsenvar/Gezgin")
                connection.set("scm:git:https://github.com/sahsenvar/Gezgin.git")
                developerConnection.set("scm:git:ssh://git@github.com/sahsenvar/Gezgin.git")
            }
        }
    }
}
