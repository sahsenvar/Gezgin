plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}
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
