# Gezgin Phase A → ZAD Phase B Handoff

Status: **GREEN**

Updated: 2026-07-22

Readiness base: `codex/zad-integration-readiness`

Release version: `0.2.0`

Repository: Maven Central

ZAD must consume the fixed release coordinates below. It must not use `includeBuild`, composite
substitution, `projectDir`, Maven Local, or a moving Gezgin checkout.

## Coordinates

- `io.github.sahsenvar:gezgin-core:0.2.0`
- `io.github.sahsenvar:gezgin-mvi:0.2.0`
- `io.github.sahsenvar:gezgin-processor:0.2.0`
- `io.github.sahsenvar:gezgin-test:0.2.0` (test source sets only)

The root metadata selects the published Android/JVM variants. Each publication contains Gradle
metadata, a POM, sources, Dokka javadoc, and detached signatures. The release workflow does not
create the GitHub Release until all four coordinates are visible on Maven Central and a clean
consumer compiles them.

## Verified build boundaries

| Layer | Version / contract |
|---|---|
| Gezgin root | Gradle 9.0.0, JDK 17, Kotlin 2.3.21, KSP 2.3.9, AGP 8.13.2 |
| Independent consumer | Gradle 9.4.1, JDK/JVM 21, AGP 9.2.1, compile/target SDK 37 |
| Navigation family | AndroidX Navigation 3 1.0.0 and lifecycle Navigation 3 2.10.0 |
| DI fixture | Koin 4.2.2 and compiler plugin 1.0.1 |

Local verification injects a temporary signed repository exclusively for the Gezgin group. Release
verification uses Maven Central exclusively for the same group, an empty Gradle user home, refreshed
dependencies, no build cache, and both `compileDebugKotlin` and `compileDebugUnitTestKotlin`.

```bash
./gradle/verify-release-publications.sh
./gradle/release/validate-release.sh v0.2.0
# Executed by the tag workflow only after Central publication:
./gradle/release/smoke-maven-central.sh 0.2.0
```

The signed local verifier checks the exact publication topology, POM and Gradle dependency metadata,
sources, non-empty javadocs, every detached signature, a corrupted-signature negative case, and the
independent consumer. The Central smoke waits at most 30 minutes for all four POMs, then repeats the
consumer compile without a local/source fallback.

## Runtime and code-generation contracts

### Restore namespace

`rememberNavigator(..., restoreKey = key)` requires a non-blank stable key. The key namespaces both
the saved snapshot and Android holder identity. Recreating with the same key restores the stack and
pending result slots; changing it creates a fresh navigator at `start`. ZAD derives it from persistent
session/account generation plus app mode so logout, account change, session reset, and mode change
invalidate previous navigation state.

### Route-bound strict MVI

The maintained direction is:

`Intent -> onIntent -> effect -> @EffectHandler(route) -> typed navigator`

`@Screen` is repeatable. Each bound route owns its own `@MviViewModel(route)` and route-explicit
`@EffectHandler(route)`. A shared content function uses State and Intent types compatible with every
route; Effect and generated Navigator types may differ. ViewModels never own `Navigator` or
`RawNavigator`.

An explicit handler may declare `onIntent: (I) -> Unit`; generated entry code binds it to the owner
ViewModel's `vm::onIntent`. The processor rejects ambiguous route ownership, wrong State/Intent/Effect
types, missing ViewModels, and duplicate handlers.

For process-death-safe result delivery, the handler calls generated `launchX(...)`, collects
`xResults` in composition, and maps each `NavResult` to a typed Intent. The result bus restores
in-flight and delivered-but-unconsumed results when the caller route and handler are recreated.
Suspend `goToXForResult(...)` remains a process-lifetime convenience, not the strict-MVI ownership
model.

### Experimental migration APIs

`TopBar`, `BottomBar`, `BottomSheetDragHandleMode`, and related temporary members require explicit
`@OptIn(ExperimentalGezginMigrationApi::class)`. The marker has `RequiresOptIn.Level.ERROR`; these
bridges may change or disappear when ZAD adopts its permanent app-owned container.

### Bottom sheets and modal dismissal

`BottomSheetContract` keeps three independent switches:

- `dismissOnBackPress`
- `dismissOnClickOutside`
- `sheetGesturesEnabled`

`BottomSheetDragHandleMode.None` removes Material's default handle while custom handles remain in
consumer-owned content. A fully non-dismissible sheet must explicitly disable all three switches.
Programmatic typed navigation remains available.

### Fragment boundary

`Gezgin.initFragmentInterop(...)` is required before Fragment-backed entries render. Generated entry
code resolves `FragmentScreenHost` and fails loudly if interop is missing. DialogFragment and
BottomSheetDialogFragment are not bridge targets; migrate those destinations to native Gezgin modal
routes.

## Phase B hard gates

- Keep the root architecture fail-closed until the ZAD modal and root-swap gates are green.
- Preserve process-death, Fragment interop, owner-intent dispatch, result-bus, and Android dependency
  family regression tests.
- Use only route-explicit `@EffectHandler(route)` and keep navigators out of ViewModels.
- Opt in to migration APIs only at the narrow consumer boundary; do not treat them as permanent UI
  architecture.
- Pin `0.2.0`. If a released artifact is faulty, publish `0.2.1`; never rewrite the tag or coordinate.
