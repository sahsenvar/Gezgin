plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}
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
