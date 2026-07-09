plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

// Faz 7.4 — versiyonlama: proje ilk sürümü. Convention-plugin bilinçle kullanılmıyor (sample netliği
// gerekçesi, bkz. sample/feature/*/build.gradle.kts) → yayınlanabilir 3 modülün (core/processor/mvi) her
// birine AÇIKÇA yazılır. Root gradle.properties'e koymak gezgin-test + sample modüllerine de sızardı
// (yayınlanmamalı) — bu yüzden modül-başına açık `version` tercih edildi.
group = "dev.gezgin"
version = "0.1.0-alpha01"

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
            // Nav3 üç ayrı koordinat/sürüm ailesi: `androidx.navigation3:navigation3-runtime` (Google,
            // gerçekten çok-platformlu, 1.1.4 — android sürüm çizgisi) vs. `org.jetbrains.androidx.
            // navigation3:navigation3-ui`/`lifecycle-viewmodel-navigation3` (JetBrains fork/uyarlaması,
            // 1.0.0-alpha05 — desktop/diğer target'lar için Google'ın Android-only `-ui`'ının yerine
            // geçer). Kritik: bu iki ailenin `NavDisplay`/decorator imzaları PLATFORMLAR ARASI BİREBİR
            // AYNI DEĞİL — `GezginDisplay`/`PlatformDisplay` yalnız HER İKİSİNDE de ORTAK olan (public,
            // aynı-imzalı) alt-kümeyi kullanacak şekilde yazılmalı (bkz. `GezginDisplay.kt` KDoc'undaki
            // "decompile bulgusu" notu — üç transition-metadata sarmalayıcısı bu ortak alt-kümeye örnek).
            api(libs.androidx.navigation3.runtime)
            api(libs.jb.navigation3.ui)
            api(libs.jb.lifecycle.viewmodel.navigation3)
        }
        // Faz 6 (Fragment interop, §11) — androidMain-only runtime (`dev.gezgin.core.fragment`):
        // gezginArgs/gezginNav delege'leri + bind-registry + route.toBundle. `androidx.fragment.app.Fragment`
        // ve `android.os.Bundle` tiplerini kullanır (fragment-compose transitively `fragment`'ı getirir).
        // `implementation` (Task 6.0 kararı): kullanıcıya SIZMAZ — `AndroidFragment` çağrısı yalnız KULLANICI
        // modülünde üretilen `provideXEntry` içinde geçer, kullanıcı kendi fragment-compose sürümünü ekler.
        androidMain.dependencies {
            implementation(libs.androidx.fragment.compose)
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
    }
}
android { namespace = "dev.gezgin.core"; compileSdk = 36; defaultConfig { minSdk = 24 } }

// İskelet — gerçek bir Maven repository/credentials YOK; `./gradlew publish` çalıştırılmaz, yalnız
// `assemble`/`build`'in bu bloktan etkilenmediği doğrulanır. Kotlin-multiplatform plugin'i `maven-publish`
// uygulanınca hedef-başına publication'ları (kotlinMultiplatform/jvm/android/metadata) otomatik kurar;
// burada yalnız POM metadata'sı (group=dev.gezgin project'ten gelir) `configureEach` ile tembel eklenir.
// Repository/signing bilinçle EKLENMEDİ → yalnız `publishToMavenLocal`/`generatePomFile*` görevleri mümkün,
// gerçek uzak yayın yolu yok.
// NOT (gerçek yayın günü için): `android { publishLibraryVariants("release") }` HENÜZ eklenmedi — güncel
// Kotlin Gradle Plugin bunu vermeden Android varyantını publish etmez; bu iskelet bugün hiçbir şeyi
// publish etmediği için zararsız, ama V1 sonrası gerçek yayın adımının kontrol listesine eklenmeli.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("gezgin-core")
            description.set(
                "DI-agnostik runtime + core codegen + Compose display katmanı (GezginDisplay) + " +
                    "modal scene strategy'leri — Gezgin'in zorunlu temeli.",
            )
        }
    }
}
