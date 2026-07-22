package dev.gezgin.core.compose

// A non-composable default cannot access an Activity; hosts must pass an explicit root action.
internal actual fun platformDefaultRootBack(): () -> Unit = {}
