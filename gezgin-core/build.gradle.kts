import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

// Faz 9.3 (M7) — JVM/Android derlemelerinde `-Xjvm-default=all`: default'lu interface üyeleri gerçek JVM
// default method'una çevrilir → `$DefaultImpls` sınıfları ABI'ye girmez (yayın sonrası `all`'a geçiş
// binary-breaking olurdu; ilk yayında temiz başlanır). `KotlinCompile` yalnız JVM+Android compile
// task'lerini yakalar (metadata `KotlinCompileCommon` hariç → JVM-only flag orada uyarı vermez).
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
}

// Faz 7.4 — versiyonlama: proje ilk sürümü. Convention-plugin bilinçle kullanılmıyor (sample netliği
// gerekçesi, bkz. sample/feature/*/build.gradle.kts) → yayınlanabilir 3 modülün (core/processor/mvi) her
// birine AÇIKÇA yazılır. Root gradle.properties'e koymak gezgin-test + sample modüllerine de sızardı
// (yayınlanmamalı) — bu yüzden modül-başına açık `version` tercih edildi.
group = "dev.gezgin"
version = "0.1.0-alpha05"

kotlin {
    // Faz 9.1 — açık API yüzeyi (her public bildirim explicit visibility + dönüş tipi ister). Codegen'in
    // ürettiği kodun dokunduğu tipler (RawNavigator/GezginEntryScope/Local*/topology tipleri/fragment
    // interop) public KALIR; salt-iç durum makinesi (GezginState/ResultBus) ve PD save()/platform kökü
    // `internal`'a çekildi. BCV .api dump'ı bu yüzeyi kilitler.
    explicitApi()
    jvmToolchain(17)
    // jvm() = desktop Compose hedefi (Faz 3 GezginDisplay); compose.desktop.currentOs çalıştırma zamanı
    // yalnız desktop uiTest'te gerekebilir (Faz 3.2+), burada eklenmedi.
    jvm()
    androidTarget {
        publishLibraryVariants("release")
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            // `compose.runtime`/`compose.foundation` (String-tipli DSL yardımcıları) bu compose
            // plugin sürümünde hard-deprecated (derleme hatası) — doğrudan koordinat kullanılıyor.
            api("org.jetbrains.compose.runtime:runtime:${libs.versions.compose.multiplatform.get()}")
            api("org.jetbrains.compose.foundation:foundation:${libs.versions.compose.multiplatform.get()}")
            // material3 (CMP) — Faz 4.2 BottomSheet scene: `ModalBottomSheet`/`SheetState`/
            // `rememberModalBottomSheetState` commonMain'de (iki platformda), el-yazımı `OverlayScene`
            // için gerekli (Nav3'te hazır BottomSheetSceneStrategy YOK — 4.0 raporu §3). `api` çünkü
            // `sheetState: SheetState` Local'i public yüzeyde (@BottomSheet content'i okur). Sürüm
            // composeVersion'dan AYRI (1.9.0) — bkz. libs.versions.toml `compose-material3` notu.
            api("org.jetbrains.compose.material3:material3:${libs.versions.compose.material3.get()}")
            // Runtime is the only shared Navigation 3 surface. UI/lifecycle are compiled against the
            // desktop family here but exported from their actual Android/JVM source sets below.
            api(libs.androidx.navigation3.runtime)
            compileOnly(libs.jb.navigation3.ui)
            compileOnly(libs.jb.lifecycle.viewmodel.navigation3)
        }
        // Faz 6 (Fragment interop, §11) — androidMain-only runtime (`dev.gezgin.core.fragment`):
        // gezginArgs/gezginNav delege'leri + bind-registry + route.toBundle. `androidx.fragment.app.Fragment`
        // ve `android.os.Bundle` tiplerini kullanır (fragment-compose transitively `fragment`'ı getirir).
        // `implementation` (Task 6.0 kararı): kullanıcıya SIZMAZ — `AndroidFragment` çağrısı yalnız KULLANICI
        // modülünde üretilen `provideXEntry` içinde geçer, kullanıcı kendi fragment-compose sürümünü ekler.
        androidMain.dependencies {
            implementation(libs.androidx.fragment.compose)
            api(libs.androidx.navigation3.ui)
            api(libs.androidx.lifecycle.viewmodel.navigation3)
            // C1 (spec §225) — host ViewModel-scope'lu kimlik-stabil navigator holder'ı için
            // `viewModel {}`/`viewModelFactory`/`initializer`. Yalnız androidMain: config-change'i atlayan
            // retention SADECE Android'de gerekli (desktop actual `rememberSaveable` kullanır). gezgin-mvi
            // ile AYNI KMP artefaktı (org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose).
            api(libs.androidx.lifecycle.viewmodel.compose)
        }
        jvmMain.dependencies {
            api(libs.jb.navigation3.ui)
            api(libs.jb.lifecycle.viewmodel.navigation3)
            api(libs.jb.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Task 3.2 (desktop uiTest altyapısı, test-only) — NavDisplay'in gerçek render/back döngüsünü
        // cihazsız (JVM/desktop) doğrulamak için. `compose.uiTest`/`compose.desktop.uiTestJUnit4`
        // (String-tipli DSL yardımcıları) da aynı şekilde hard-deprecated — doğrudan koordinat.
        jvmTest.dependencies {
            implementation("org.jetbrains.compose.ui:ui-test:${libs.versions.compose.multiplatform.get()}")
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.ui:ui-test-junit4:${libs.versions.compose.multiplatform.get()}")
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation("org.robolectric:robolectric:4.14")
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation("androidx.compose.ui:ui-test-junit4:1.7.8")
            implementation("androidx.compose.ui:ui-test-manifest:1.7.8")
        }
    }
}
android {
    namespace = "dev.gezgin.core"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// İskelet — gerçek bir Maven repository/credentials YOK; `./gradlew publish` çalıştırılmaz, yalnız
// `assemble`/`build`'in bu bloktan etkilenmediği doğrulanır. Kotlin-multiplatform plugin'i `maven-publish`
// uygulanınca hedef-başına publication'ları (kotlinMultiplatform/jvm/android/metadata) otomatik kurar;
// burada yalnız POM metadata'sı (group=dev.gezgin project'ten gelir) `configureEach` ile tembel eklenir.
// Repository/signing bilinçle EKLENMEDİ → yalnız `publishToMavenLocal`/`generatePomFile*` görevleri mümkün,
// gerçek uzak yayın yolu yok.
// Android release publication is enabled above with `publishLibraryVariants("release")`; remote repository
// and signing configuration intentionally remain absent, so only local publication is supported here.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("gezgin-core")
            description.set(
                "DI-agnostik runtime + core codegen + Compose display katmanı (GezginDisplay) + " +
                    "modal scene strategy'leri — Gezgin'in zorunlu temeli.",
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
