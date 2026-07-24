plugins {
  id("com.android.application") version "9.2.1"
  kotlin("android") version "2.3.21"
  kotlin("plugin.serialization") version "2.3.21"
  id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
  id("com.google.devtools.ksp") version "2.3.9"
  id("io.insert-koin.compiler.plugin") version "1.0.1"
}

val gezginGroup = "io.github.sahsenvar"
val gezginVersion = providers.gradleProperty("gezginVersion").getOrElse("0.2.0")

android {
  namespace = "dev.gezgin.compat.zad"
  compileSdk = 37

  defaultConfig {
    applicationId = "dev.gezgin.compat.zad"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }
}

kotlin { jvmToolchain(21) }

configurations.configureEach {
  resolutionStrategy {
    dependencySubstitution {
      substitute(module("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose"))
        .using(module("androidx.lifecycle:lifecycle-runtime-compose:2.10.0"))
        .because("The Android consumer resolves the AndroidX lifecycle family.")
      substitute(module("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose"))
        .using(module("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0"))
        .because("The Android consumer resolves the AndroidX lifecycle family.")
      substitute(module("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel"))
        .using(module("androidx.lifecycle:lifecycle-viewmodel:2.10.0"))
        .because("The Android consumer resolves the AndroidX lifecycle family.")
      substitute(module("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate"))
        .using(module("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0"))
        .because("The Android consumer resolves the AndroidX lifecycle family.")
    }
    componentSelection {
      all {
        if (
          candidate.group == "org.jetbrains.androidx.navigation3" ||
            (candidate.group == "org.jetbrains.androidx.lifecycle" &&
              candidate.module.contains("navigation3"))
        ) {
          reject(
            "The Android consumer must resolve the AndroidX Navigation 3 and lifecycle Navigation 3 families."
          )
        }
      }
    }
  }
}

dependencies {
  implementation("$gezginGroup:gezgin-core:$gezginVersion")
  implementation("$gezginGroup:gezgin-mvi:$gezginVersion")
  ksp("$gezginGroup:gezgin-processor:$gezginVersion")
  testImplementation("$gezginGroup:gezgin-test:$gezginVersion")

  implementation("androidx.navigation3:navigation3-runtime:1.0.0")
  implementation("androidx.navigation3:navigation3-ui:1.0.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")

  implementation("io.insert-koin:koin-android:4.2.2")
  implementation("io.insert-koin:koin-compose-viewmodel:4.2.2")
  implementation("io.insert-koin:koin-core-viewmodel:4.2.2")
  implementation("io.insert-koin:koin-annotations:4.2.2")
}
