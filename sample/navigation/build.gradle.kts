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

    // `:gezgin-test` on the MAIN compile classpath (F-MAJOR-2): the KSP main round emits
    // `GezginTestAccessors.kt` (typed `fromX()` extensions on `GezginTestNavigator`) into `main`, and
    // that file must compile — so `GezginTestNavigator` has to be visible to `compileKotlin`. `compileOnly`
    // keeps this test-only artifact OUT of the app's runtime classpath (the accessors are only ever called
    // from the `test` source set); `testImplementation` below re-adds it for the test compile+runtime.
    compileOnly(project(":gezgin-test"))

    testImplementation(kotlin("test"))
    testImplementation(project(":gezgin-test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// F-MAJOR-2 — the headline UI-less test API (`GezginTestNavigator.fromX()`) now works in the canonical
// multi-module layout (graphs in `main`, tests in `test`). The earlier `kspTest`-scoped attempt was
// structurally wrong: `TestApiCodegen` needs the [GraphModel] built from scanning `@NavGraph` symbols in
// THIS KSP round's SOURCES, but a `kspTestKotlin` round is handed only the `test` source set's `.kt` files —
// `AppNav.kt`'s graphs live in `main` (already compiled to .class), and KSP's `getSymbolsWithAnnotation`
// never re-discovers annotations off binary classpath entries → empty model → nothing emitted. The fix is
// to run the accessor codegen in the round that OWNS the graphs: set the flag on the GLOBAL `ksp {}`
// extension (below) so it rides `kspKotlin` (the `main` round, alongside topology/navigator codegen), which
// emits `GezginTestAccessors.kt` into `main`; the `test` source set then sees the compiled accessors and
// calls `nav.fromX()` directly (see `AppNavBehaviorTest.kt`). The `kspTestKotlin` round still receives the
// flag but no-ops there (no graphs in `test`), so there is no double emission.
ksp {
    arg("gezgin.emitTestAccessors", "true")
}

