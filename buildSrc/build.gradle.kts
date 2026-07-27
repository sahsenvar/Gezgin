plugins { `kotlin-dsl` }

repositories {
  google()
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
  val repositoryRoot = layout.projectDirectory.dir("..")
  inputs.files(
    listOf("gezgin-core", "gezgin-mvi", "gezgin-processor", "gezgin-test").map { module ->
      fileTree(repositoryRoot.dir("$module/src")) {
        include("**/*Main/**/*.kt")
        include("**/main/**/*.kt")
      }
    }
  )
}
