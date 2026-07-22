import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.dokka)
  alias(libs.plugins.maven.publish)
}

dokka {
  dokkaPublications.html { failOnWarning.set(true) }
  dokkaSourceSets.configureEach {
    reportUndocumented.set(true)
    suppressGeneratedFiles.set(true)
  }
}

kotlin {
  // Faz 9.1 — yayınlanan modüller için açık API yüzeyi (her public bildirim explicit visibility +
  // dönüş tipi ister; kasıtlı-olmayan yüzey `internal`'a çekilir). BCV .api dump'ıyla birlikte
  // çalışır.
  explicitApi()
  jvmToolchain(17)
  // gezgin-core ile aynı hedef seti: jvm() = desktop Compose, androidTarget() = Android.
  // Hilt Android-only olduğundan Hilt default resolver'ı yalnız androidTarget'ta anlamlı (spike
  // task-5.0);
  // gezgin-mvi'nin KENDİSİ DI-agnostik (§15) — Hilt/Koin RUNTIME dep'i YOK, codegen string-FQN
  // okur.
  jvm { compilerOptions { jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY) } }
  androidTarget { compilerOptions { jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY) } }
  sourceSets {
    commonMain.dependencies {
      // gezgin-core: Route/annotation'lar (@Screen) + GezginEntryScope.register + navigator seam'i.
      // `api` çünkü @MviViewModel(Route::class) ve provideXEntry public yüzeyde gezgin-core
      // tiplerine dokunur.
      api(project(":gezgin-core"))
      // compose.runtime (@Composable/LaunchedEffect/remember) gezgin-core'dan transitively `api`
      // gelir;
      // yine de compose plugin'in compile classpath'i için gezgin-core api yeterli.
      // Common sources compile against AndroidX's KMP lifecycle surface. Android/JVM export
      // their own runtime family below, keeping JetBrains lifecycle artifacts off Android.
      compileOnly(libs.androidx.lifecycle.viewmodel.compose)
      compileOnly(libs.androidx.lifecycle.runtime.compose)
    }
    androidMain.dependencies {
      api(libs.androidx.lifecycle.viewmodel.compose)
      api(libs.androidx.lifecycle.runtime.compose)
    }
    jvmMain.dependencies {
      api(libs.jb.lifecycle.viewmodel.compose)
      api(libs.jb.lifecycle.runtime.compose)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }
    // MN-A — desktop (jvm) MVI'nın uçtan-uca (collectAsStateWithLifecycle + ObserveEffects,
    // host-sürülen
    // LocalLifecycleOwner'a bağlı) render/efekt teslimini cihazsız doğrulamak için
    // `runComposeUiTest`
    // altyapısı. gezgin-core jvmTest ile AYNI koordinatlar (String-tipli compose.uiTest DSL
    // yardımcıları
    // hard-deprecated → doğrudan koordinat).
    jvmTest.dependencies {
      implementation(
        "org.jetbrains.compose.ui:ui-test:${libs.versions.compose.multiplatform.get()}"
      )
      implementation(compose.desktop.currentOs)
      implementation(
        "org.jetbrains.compose.ui:ui-test-junit4:${libs.versions.compose.multiplatform.get()}"
      )
    }
  }
}

android {
  namespace = "dev.gezgin.mvi"
  compileSdk = 36
  defaultConfig { minSdk = 24 }
}

mavenPublishing {
  configure(
    KotlinMultiplatform(
      javadocJar = JavadocJar.Dokka(tasks.named("dokkaGeneratePublicationHtml")),
      sourcesJar = SourcesJar.Sources(),
      androidVariantsToPublish = listOf("release"),
    )
  )
}
