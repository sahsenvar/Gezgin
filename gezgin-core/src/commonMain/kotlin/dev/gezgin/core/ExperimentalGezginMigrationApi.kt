package dev.gezgin.core

/**
 * Opt-in gate for temporary public APIs that exist only to support an application migration.
 * These symbols may be changed or removed when their permanent replacement is available.
 *
 * @author @sahsenvar
 */
@RequiresOptIn(
    message = "ExperimentalGezginMigrationApi: temporary migration API; do not adopt as permanent application architecture.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalGezginMigrationApi
