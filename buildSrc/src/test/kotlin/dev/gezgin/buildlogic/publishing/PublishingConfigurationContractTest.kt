package dev.gezgin.buildlogic.publishing

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublishingConfigurationContractTest {
  private val projectRoot: Path =
    Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().let { workingDirectory ->
      if (workingDirectory.fileName.toString() == "buildSrc") workingDirectory.parent
      else workingDirectory
    }

  @Test
  fun `uses the supported release toolchain and publishing plugin`() {
    val wrapperProperties = properties("gradle/wrapper/gradle-wrapper.properties")
    assertEquals(
      "https://services.gradle.org/distributions/gradle-9.0.0-bin.zip",
      wrapperProperties.getProperty("distributionUrl"),
    )

    val catalog = text("gradle/libs.versions.toml")
    assertContains(catalog, "agp = \"8.13.2\"")
    assertContains(catalog, "vanniktech-maven-publish = \"0.37.0\"")
    assertContains(
      catalog,
      "maven-publish = { id = \"com.vanniktech.maven.publish\", version.ref = \"vanniktech-maven-publish\" }",
    )
    assertContains(text("build.gradle.kts"), "alias(libs.plugins.maven.publish) apply false")
  }

  @Test
  fun `centralizes release coordinates and removes module-local copies`() {
    val rootProperties = properties("gradle.properties")
    assertEquals("io.github.sahsenvar", rootProperties.getProperty("GROUP"))
    assertEquals("0.1.0", rootProperties.getProperty("VERSION_NAME"))

    val rootBuild = text("build.gradle.kts")
    assertContains(rootBuild, "providers.gradleProperty(\"GROUP\")")
    assertContains(rootBuild, "providers.gradleProperty(\"VERSION_NAME\")")
    assertContains(rootBuild, "\":gezgin-core\"")
    assertContains(rootBuild, "\":gezgin-mvi\"")
    assertContains(rootBuild, "\":gezgin-test\"")
    assertContains(rootBuild, "\":gezgin-processor\"")
    assertContains(rootBuild, "configure(publishedProjects)")
    assertFalse(
      rootBuild.contains("allprojects {"),
      "release coordinates must not leak into samples",
    )

    publishedModules.forEach { module ->
      val build = text("$module/build.gradle.kts")
      assertFalse(Regex("(?m)^group\\s*=").containsMatchIn(build), "$module must inherit GROUP")
      assertFalse(
        Regex("(?m)^version\\s*=").containsMatchIn(build),
        "$module must inherit VERSION_NAME",
      )
    }
  }

  @Test
  fun `configures four Central-ready signed publications without manual skeletons`() {
    val rootBuild = text("build.gradle.kts")
    assertContains(rootBuild, "publishToMavenCentral()")
    assertContains(rootBuild, "signAllPublications()")
    assertContains(rootBuild, "The Apache License, Version 2.0")
    assertContains(rootBuild, "https://github.com/sahsenvar/Gezgin")
    assertContains(rootBuild, "Şahan Şenvar")

    publishedModules.forEach { module ->
      val build = text("$module/build.gradle.kts")
      assertContains(build, "alias(libs.plugins.maven.publish)")
      assertContains(build, "mavenPublishing")
      assertFalse(
        build.contains("`maven-publish`"),
        "$module must not keep the manual publishing plugin",
      )
      assertFalse(
        build.contains("publishing {\n    publications"),
        "$module must not keep a manual publication skeleton",
      )
    }
  }

  @Test
  fun `publishes KMP release variants and the processor JVM component with Dokka docs`() {
    kmpModules.forEach { module ->
      val build = text("$module/build.gradle.kts")
      assertContains(build, "KotlinMultiplatform(")
      assertContains(build, "JavadocJar.Dokka(")
      assertContains(build, "SourcesJar.Sources()")
      assertContains(build, "androidVariantsToPublish = listOf(\"release\")")
    }

    val processorBuild = text("gezgin-processor/build.gradle.kts")
    assertContains(processorBuild, "KotlinJvm(")
    assertContains(processorBuild, "JavadocJar.Dokka(")
    assertContains(processorBuild, "SourcesJar.Sources()")
  }

  @Test
  fun `compatibility consumer resolves the release coordinates from an isolated repository`() {
    val consumerBuild = text("compatibility/zad-consumer/build.gradle.kts")
    assertContains(consumerBuild, "providers.gradleProperty(\"useAlpha04MavenLocal\")")
    assertContains(consumerBuild, "\"io.github.sahsenvar\"")
    assertContains(consumerBuild, "\"0.1.0\"")
    assertContains(consumerBuild, "\"dev.gezgin\"")
    assertContains(consumerBuild, "\"0.1.0-alpha04\"")

    val consumerSettings = text("compatibility/zad-consumer/settings.gradle.kts")
    assertContains(consumerSettings, "providers.gradleProperty(\"releaseVerificationRepository\")")
    assertContains(consumerSettings, "exclusiveContent")
    assertContains(consumerSettings, "includeGroup(\"io.github.sahsenvar\")")
    assertContains(consumerSettings, "providers.gradleProperty(\"useAlpha04MavenLocal\")")
    assertContains(consumerSettings, "releaseVerificationRepository == null")
    assertContains(consumerSettings, "mavenLocal()")
    assertContains(consumerSettings, "mavenCentral()")
  }

  @Test
  fun `ships a behavioral signed publication and consumer verification entrypoint`() {
    val rootBuild = text("build.gradle.kts")
    assertContains(rootBuild, "VerifyReleaseRepositoryTask")
    assertContains(rootBuild, "verifyReleasePublications")
    assertContains(rootBuild, "--refresh-dependencies")
    assertContains(rootBuild, "--project-cache-dir")

    val verificationScript = projectRoot.resolve("gradle/verify-release-publications.sh")
    assertTrue(
      Files.isRegularFile(verificationScript),
      "Missing behavioral release verification script",
    )
    assertTrue(
      Files.isExecutable(verificationScript),
      "Release verification script must be executable",
    )

    val script = verificationScript.readText()
    assertContains(script, "verify_home")
    assertContains(script, "--export")
    assertContains(script, "--import")
    assertContains(script, "gpg --homedir \"\$verify_home\" --batch --verify")
    assertContains(script, "CRYPTOGRAPHIC_SIGNATURES_VERIFIED=53")
    assertContains(script, "CORRUPTION_NEGATIVE=PASS")
  }

  @Test
  fun `runs release hardening commands in CI without repository secrets`() {
    val workflow = text(".github/workflows/ci.yml")
    assertContains(workflow, "./gradlew -p buildSrc test")
    assertContains(workflow, "./gradle/verify-release-publications.sh")
    assertContains(workflow, "gpg --version")
    assertContains(workflow, "java-version: '21'")
    assertFalse(workflow.contains("MAVEN_CENTRAL_USERNAME"))
    assertFalse(workflow.contains("MAVEN_CENTRAL_PASSWORD"))
  }

  @Test
  fun `enforces Kotlin formatting and no-regression coverage for published production modules`() {
    val catalog = text("gradle/libs.versions.toml")
    assertContains(catalog, "spotless = \"8.8.0\"")
    assertContains(catalog, "kover = \"0.9.8\"")
    assertContains(
      catalog,
      "spotless = { id = \"com.diffplug.spotless\", version.ref = \"spotless\" }",
    )
    assertContains(
      catalog,
      "kover = { id = \"org.jetbrains.kotlinx.kover\", version.ref = \"kover\" }",
    )

    val rootProperties = properties("gradle.properties")
    assertTrue(rootProperties.containsKey("KOVER_MIN_LINE_COVERAGE"))

    val rootBuild = text("build.gradle.kts")
    assertContains(rootBuild, "alias(libs.plugins.spotless)")
    assertContains(rootBuild, "alias(libs.plugins.kover)")
    assertContains(rootBuild, "ktfmt")
    assertContains(rootBuild, "KOVER_MIN_LINE_COVERAGE")
    assertContains(rootBuild, "total {")
    assertContains(rootBuild, "html {")
    assertContains(rootBuild, "verify {")
    assertContains(rootBuild, "publishedProjectPaths")
    assertContains(
      rootBuild,
      "publishedProject.pluginManager.apply(\"org.jetbrains.kotlinx.kover\")",
    )
    assertContains(rootBuild, "dependsOn(\"koverVerify\")")
    assertFalse(
      rootBuild.contains("sample:"),
      "samples must not lower the publication coverage baseline",
    )
  }

  private fun properties(relativePath: String): Properties =
    Properties().apply { projectRoot.resolve(relativePath).inputStream().use(::load) }

  private fun text(relativePath: String): String {
    val path = projectRoot.resolve(relativePath)
    assertTrue(Files.isRegularFile(path), "Missing project file: $relativePath")
    return path.readText()
  }

  private companion object {
    val kmpModules = listOf("gezgin-core", "gezgin-mvi", "gezgin-test")
    val publishedModules = kmpModules + "gezgin-processor"
  }
}
