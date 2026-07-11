plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The central nav module owns the whole sealed graph tree; features depend on it (spec §3.3).
    // `api` so cross-module features see routes/navigators/topology transitively.
    api(project(":gezgin-core"))
    // Domain modeller (AvatarChoice/NotificationLevel/SortOrder) — `api` yüzeyiyle route imzalarını
    // kullanan feature'lara transitively de akar (sahibin isteği: her iki tarafta erişilebilir).
    api(project(":sample:domain"))
    ksp(project(":gezgin-processor"))

    testImplementation(kotlin("test"))
    testImplementation(project(":gezgin-test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// S3 finding — `gezgin.emitTestAccessors=true` via `kspTest` was tried and abandoned (see
// `sample/README.md` "Tasarım notları" + `.superpowers/sdd/sample-s3-report.md`): it's reachable
// per-TASK (the `kspTestKotlin` task's own `commandLineArgumentProviders`, additive on top of the
// shared `ksp {}` extension's GLOBAL `apOptions` — see `KspAATask.kt` in the KSP Gradle plugin), so
// scoping the flag to test-only compilation isn't the blocker. The real blocker is structural:
// `TestApiCodegen` needs [GraphModel] (built by scanning `@NavGraph`-annotated symbols in THIS KSP
// round's sources), but `kspTestKotlin`'s round is handed only the `test` source set's `.kt` files —
// `AppNav.kt`'s graphs live in `main`, already compiled to .class by then, and KSP's
// `getSymbolsWithAnnotation` never re-discovers annotations off binary classpath entries. So
// `model.graphs`/`model.routes` are empty for every module shaped like this one (graphs and tests in
// separate Gradle source sets) — `GezginTestAccessors.kt` is never emitted, confirmed empirically
// (ran `kspTestKotlin` standalone: zero output, not even the topology). Behavior tests below use the
// (already generated, main-round) `raw.xNavigator(entryId)` factories directly instead.

