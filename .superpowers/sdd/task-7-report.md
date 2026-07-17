# Task 7 report: maintained strict-MVI API, samples, and guidance

## Status

`GREEN`

## Scope

- Regenerated the Android/JVM API dumps for `restoreKey`, repeatable `@Screen`,
  route-bound MVI annotations, migration-only chrome, and bottom-sheet gesture metadata.
- Updated maintained English/Turkish README, sample guidance, design, binder-location,
  and on-device guidance to the strict ZAD path:
  `Intent -> ViewModel effect -> @EffectHandler(route) -> typed navigator`.
- Kept `@ScreenEffect` only as a deprecated exact-single-unoccupied-route bridge.
- Marked deep-link dispatch as unavailable V2 debt and Fragment interop as screen-only.
- Preserved `docs/gezgin-design-notes.md` as a historical record; only a current-spec banner was added.
- Converted all maintained sample ViewModels away from injected/direct navigators. Navigation and
  result collection now live in route-explicit handlers; stable sample restore keys are explicit.

## Red and green evidence

- Initial `apiCheck` failed on the expected accumulated public additions.
- `apiDump` updated only the four maintained API files; final `apiCheck` passed.
- Auth/home/profile strict-MVI migration tests: 7 tests, 0 failures.
- Shopr topology/strict-MVI tests: 14 tests, 0 failures.
- Shopr result regressions: 44 tests, 0 failures.
- `:sample:app:assembleDebug`, owned feature tests, Shopr compile/tests, core/MVI/processor checks,
  and the independent Gradle 9.4.1 ZAD consumer compile passed.
- Generated source inspection found typed navigators only in handlers and no navigator arguments
  in generated ViewModel factories.

## Maintained-source searches

- Real sample `@ScreenEffect` annotation usage: zero.
- Maintained sample ViewModel navigator fields/direct navigation: zero.
- Current documentation `nav.raw.navigate`, `Channel.UNLIMITED`, and `StartUpHost` claims: zero.
- Deep-link mentions are limited to explicit unavailable/V2 design debt.
- Fragment modal API names do not appear in the public MVI API dumps.

## Residual risks

- Device-level configuration-change/process-death validation is owned by Task 8.
- General Gezgin ViewModel-navigation capability remains available for compatibility, but maintained
  strict-MVI samples deliberately do not exercise it.
- Historical design notes retain their historical proposals and must not be treated as current guidance.

## Independent review fixes

- Real route-bound Compose handler tests now prove persisted result delivery; the profile proof also
  detaches and reattaches the handler and asserts one active collector. Removing the production collector
  makes these tests fail.
- Shopr checkout results re-enter `CatalogViewModel` as an explicit Intent and produce a VM Effect before
  the handler performs replacement or emits the exact cancellation message.
- Showcase and Shopr restore-key tests inspect the actual balanced `rememberNavigator(...)` call and fail
  when host wiring drops the stable key.
- Maintained result guidance now assigns collection to the route-bound handler, which forwards an Intent
  into the ViewModel; the concise capability matrix again points to nested/results, typed edges,
  quit/back, fullscreen modals, transition cascading, observability, and test-navigator examples.
