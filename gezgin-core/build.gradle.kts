import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
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
  // Açık API yüzeyi (her public bildirim explicit visibility + dönüş tipi ister).
  // Codegen'in
  // ürettiği kodun dokunduğu tipler (RawNavigator/GezginEntryScope/Local*/topology tipleri/fragment
  // interop) public KALIR; salt-iç durum makinesi (GezginState/ResultBus) ve PD save()/platform
  // kökü
  // `internal`'a çekildi. BCV .api dump'ı bu yüzeyi kilitler.
  explicitApi()
  jvmToolchain(17)
  // jvm() = desktop Compose hedefi; compose.desktop.currentOs çalıştırma
  // zamanı
  // yalnız desktop uiTest'te gerekebilir, burada eklenmedi.
  jvm { compilerOptions { jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY) } }
  androidTarget { compilerOptions { jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY) } }
  // iOS hedef kümesi upstream tarafından kilitli: JB `navigation3-ui` yalnız `iosArm64` ve
  // `iosSimulatorArm64` yayınlıyor, `iosX64` YOK → Intel Mac simülatörü desteklenmez
  // (spec S-1). Apple hedefleri yalnız macOS host'ta derlenir; Linux job'larında
  // `kotlin.native.ignoreDisabledTargets` (gradle.properties) onları sessizce devre dışı bırakır.
  iosArm64()
  iosSimulatorArm64()
  // `nonAndroidMain` = jvm + ios. Beş platform `expect`'inin dördü bu iki hedefte AYNI JetBrains
  // Navigation 3 ailesi üzerinde çalışır; tek kopya tutmak davranış sürüklenmesini yapısal olarak
  // imkânsız kılar ve kodu jvmTest üzerinden Kover kapsamında bırakır. Yalnız `GezginNoBackHandler`
  // ayrışır (desktop no-op, iOS geri jesti) ve hedef-özel kaynak kümelerinde kalır.
  applyDefaultHierarchyTemplate {
    common {
      group("nonAndroid") {
        withJvm()
        withIos()
      }
    }
  }
  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.serialization.json)
      // `compose.runtime`/`compose.foundation` (String-tipli DSL yardımcıları) bu compose
      // plugin sürümünde hard-deprecated (derleme hatası) — doğrudan koordinat kullanılıyor.
      api("org.jetbrains.compose.runtime:runtime:${libs.versions.compose.multiplatform.get()}")
      api(
        "org.jetbrains.compose.foundation:foundation:${libs.versions.compose.multiplatform.get()}"
      )
      // material3 (CMP) — BottomSheet scene: `ModalBottomSheet`/`SheetState`/
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
    // Fragment interop — androidMain-only runtime (`dev.gezgin.core.fragment`):
    // gezginArgs/gezginNav delege'leri + bind-registry + route.toBundle.
    // `androidx.fragment.app.Fragment`
    // ve `android.os.Bundle` tiplerini kullanır (fragment-compose transitively `fragment`'ı
    // getirir).
    // `implementation`: kullanıcıya SIZMAZ — `AndroidFragment` çağrısı yalnız
    // KULLANICI
    // modülünde üretilen `provideXEntry` içinde geçer, kullanıcı kendi fragment-compose sürümünü
    // ekler.
    androidMain.dependencies {
      implementation(libs.androidx.fragment.compose)
      api(libs.androidx.navigation3.ui)
      api(libs.androidx.lifecycle.viewmodel.navigation3)
      // C1 (spec §225) — host ViewModel-scope'lu kimlik-stabil navigator holder'ı için
      // `viewModel {}`/`viewModelFactory`/`initializer`. Yalnız androidMain: config-change'i
      // atlayan
      // retention SADECE Android'de gerekli (desktop actual `rememberSaveable` kullanır).
      // gezgin-mvi
      // ile AYNI KMP artefaktı (org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose).
      api(libs.androidx.lifecycle.viewmodel.compose)
    }
    // Desktop ve iOS AYNI JetBrains ailesini kullanır; AndroidX UI/lifecycle artefaktları yalnız
    // androidMain'de kalır (§2.2 adapter sınırı). commonMain'in deseni burada da geçerli:
    // paylaşılan actual'lar bu aileye KARŞI derlenir, ama yayınlanan bağımlılık olarak hedeflerin
    // KENDİ kaynak kümelerinden dışa verilir. Bir ARA kaynak kümesinin `api`'si ortak metadata
    // POM'una çıkar; oradan da JB artefaktları Maven ile çözen bir Android tüketicisinin grafiğine
    // sızardı.
    getByName("nonAndroidMain").dependencies {
      compileOnly(libs.jb.navigation3.ui)
      compileOnly(libs.jb.lifecycle.viewmodel.navigation3)
    }
    jvmMain.dependencies {
      api(libs.jb.navigation3.ui)
      api(libs.jb.lifecycle.viewmodel.navigation3)
      api(libs.jb.lifecycle.viewmodel.compose)
    }
    // `iosMain` de bir ARA kaynak kümesidir (iki hedefin ebeveyni), `jvmMain` ise bir YAPRAK.
    // Bu yüzden dışa verme, yalnız yaprak kaynak kümelerinden yapılır.
    iosArm64Main.dependencies {
      api(libs.jb.navigation3.ui)
      api(libs.jb.lifecycle.viewmodel.navigation3)
      api(libs.jb.lifecycle.viewmodel.compose)
    }
    iosSimulatorArm64Main.dependencies {
      api(libs.jb.navigation3.ui)
      api(libs.jb.lifecycle.viewmodel.navigation3)
      api(libs.jb.lifecycle.viewmodel.compose)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }
    // uiTest altyapısı (test-only) — NavDisplay'in gerçek render/back döngüsünü cihazsız
    // doğrular. Testler `runComposeUiTest` (kotlin.test tabanlı) kullandığından AYNI test
    // gövdeleri hem desktop hem iOS simülatöründe koşar. `compose.uiTest` (String-tipli DSL
    // yardımcısı) bu plugin sürümünde hard-deprecated — doğrudan koordinat.
    getByName("nonAndroidTest").dependencies {
      implementation(
        "org.jetbrains.compose.ui:ui-test:${libs.versions.compose.multiplatform.get()}"
      )
    }
    // Desktop'ta uiTest'in çalışması için gereken host runtime'ı; iOS karşılığı simülatörden gelir.
    jvmTest.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(
        "org.jetbrains.compose.ui:ui-test-junit4:${libs.versions.compose.multiplatform.get()}"
      )
    }
    androidUnitTest.dependencies {
      implementation(kotlin("test-junit"))
      implementation("org.robolectric:robolectric:4.14")
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation("androidx.compose.ui:ui-test-junit4:1.11.4")
      implementation("androidx.compose.ui:ui-test-manifest:1.11.4")
    }
  }
}

android {
  namespace = "dev.gezgin.core"
  compileSdk = 36
  defaultConfig { minSdk = 24 }
  testOptions { unitTests.isIncludeAndroidResources = true }
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
