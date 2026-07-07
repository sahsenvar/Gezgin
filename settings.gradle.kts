pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "gezgin"
include(":gezgin-core")
include(":gezgin-test")
include(":gezgin-processor")
