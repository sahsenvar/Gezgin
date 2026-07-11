pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "gezgin"
include(":gezgin-core")
include(":gezgin-mvi")
include(":gezgin-test")
include(":gezgin-processor")
include(":sample:shopr")
include(":sample:domain")
include(":sample:navigation")
include(":sample:feature:auth")
include(":sample:feature:home")
include(":sample:feature:profile")
include(":sample:app")
