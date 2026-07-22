package dev.gezgin.core.compose

// Window-closing policy belongs to the desktop host, which may provide ExitApplication explicitly.
internal actual fun platformDefaultRootBack(): () -> Unit = {}
