package dev.gezgin.core.compose

/**
 * `rememberNavigator`'ın platform-specific default `onRootBack` callback'i. Both platform
 * implementations are intentionally no-op because only the host owns the action that should run at
 * the root (for example, finishing an Android activity). Applications that need root-back behavior
 * must pass an explicit callback to `rememberNavigator`; without one, root `back()` is consumed
 * without mutating the empty-stack invariant.
 */
internal expect fun platformDefaultRootBack(): () -> Unit
