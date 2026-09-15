package dev.gezgin.core.compose

// Neither target has an Android-style `finish()`. Window-closing policy belongs to the desktop
// host, which may provide ExitApplication explicitly, and iOS forbids an application from
// terminating itself, so a host that wants root behaviour supplies it through `rememberNavigator`.
internal actual fun platformDefaultRootBack(): () -> Unit = {}
