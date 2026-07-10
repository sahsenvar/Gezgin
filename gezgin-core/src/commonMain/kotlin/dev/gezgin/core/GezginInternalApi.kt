package dev.gezgin.core

/**
 * Opt-in gate for the narrow slice of [RawNavigator]'s internal stack state that test tooling
 * needs (Task 2.6) — `keys` itself stays `internal`; this annotation guards the one derived,
 * public-but-deliberately-inconvenient accessor built on top of it ([RawNavigator.entryIdOf]).
 * Not meant for application code: `:gezgin-test` and Faz 2 codegen are the only intended callers.
 */
@RequiresOptIn(
    message = "Gezgin internal API — intended for :gezgin-test and generated codegen only, not application code.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
public annotation class GezginInternalApi
