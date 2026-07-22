plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
