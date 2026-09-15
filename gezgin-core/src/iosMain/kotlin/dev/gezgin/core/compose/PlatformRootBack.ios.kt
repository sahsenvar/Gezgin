package dev.gezgin.core.compose

// iOS forbids an application from terminating itself, so there is no `finish()` equivalent for the
// root back edge. A host that wants root behaviour supplies it through `rememberNavigator`.
internal actual fun platformDefaultRootBack(): () -> Unit = {}
