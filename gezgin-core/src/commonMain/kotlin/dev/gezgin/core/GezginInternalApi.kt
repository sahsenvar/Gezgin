package dev.gezgin.core

/**
 * Opt-in gate for the whole set of symbols that must be `public` only so generated code and
 * `:gezgin-test` can reach them — never application code. It covers the entry-id `RawNavigator`
 * overloads (`backWithResult(Long, …)`, `launchForResult`, `results`, `navigateForResult`,
 * `currentEntryId`, `entryIdOf`), the `GezginTopology`/`FlowType`/`EdgeSpec` constructors, the
 * `LocalGezgin*` composition locals, the Fragment interop glue (`Route.toBundle`, `bindGezgin`),
 * and `@GezginNavigatorFor`.
 *
 * These are registered as BCV non-public markers, so they are excluded from the locked `.api`
 * surface and can evolve after the first release without a deprecation cycle. Generated files emit
 * the matching `@OptIn(GezginInternalApi::class)`; application code should use the typed navigators
 * instead.
 *
 * @author @sahsenvar
 */
@RequiresOptIn(
  message =
    "Gezgin internal API — intended for :gezgin-test and generated codegen only, not application code.",
  level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
public annotation class GezginInternalApi
