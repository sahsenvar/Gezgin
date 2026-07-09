plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

// Faz 7.4 — versiyonlama: proje ilk sürümü (bkz. gezgin-core/build.gradle.kts gerekçesi — modül-başına
// açık `version`, root gradle.properties'e konmaz ki gezgin-test/sample'a sızmasın).
group = "dev.gezgin"
version = "0.1.0-alpha01"

kotlin {
    jvmToolchain(17)
    // gezgin-core ile aynı hedef seti: jvm() = desktop Compose, androidTarget() = Android.
    // Hilt Android-only olduğundan Hilt default resolver'ı yalnız androidTarget'ta anlamlı (spike task-5.0);
    // gezgin-mvi'nin KENDİSİ DI-agnostik (§15) — Hilt/Koin RUNTIME dep'i YOK, codegen string-FQN okur.
    jvm()
    androidTarget()
    sourceSets {
        commonMain.dependencies {
            // gezgin-core: Route/annotation'lar (@Screen) + GezginEntryScope.register + navigator seam'i.
            // `api` çünkü @ViewModel(Route::class) ve provideXEntry public yüzeyde gezgin-core tiplerine dokunur.
            api(project(":gezgin-core"))
            // compose.runtime (@Composable/LaunchedEffect/remember) gezgin-core'dan transitively `api` gelir;
            // yine de compose plugin'in compile classpath'i için gezgin-core api yeterli.
            // JB lifecycle-compose — codegen'li provideXEntry + ObserveAsEvents bunları KULLANIR → `api`.
            api(libs.jb.lifecycle.viewmodel.compose)   // viewModel()/viewModelFactory{} (androidx-fallback resolver)
            api(libs.jb.lifecycle.runtime.compose)     // collectAsStateWithLifecycle()/LocalLifecycleOwner (state/effect gözleme)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
android { namespace = "dev.gezgin.mvi"; compileSdk = 36; defaultConfig { minSdk = 24 } }

// İskelet — gerçek bir Maven repository/credentials YOK; `./gradlew publish` çalıştırılmaz, yalnız
// `assemble`/`build`'in bu bloktan etkilenmediği doğrulanır. KMP publication'ları `maven-publish` ile
// otomatik kurulur; yalnız POM metadata'sı tembel (`configureEach`) eklenir. Repository/signing YOK.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("gezgin-mvi")
            description.set(
                "Opsiyonel MVI binder add-on'u (gezgin-core'a bağımlı): @ViewModel/@ScreenEffect + " +
                    "GezginMvi<S,I,E> sözleşmesi + codegen binder + ObserveAsEvents + DI-detection " +
                    "(Hilt/Koin, androidx fallback).",
            )
        }
    }
}
