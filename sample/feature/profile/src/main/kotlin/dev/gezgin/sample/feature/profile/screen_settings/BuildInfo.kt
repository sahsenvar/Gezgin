package dev.gezgin.sample.feature.profile.screen_settings

import androidx.compose.runtime.staticCompositionLocalOf

data class BuildInfo(val version: String)

/**
 * The generated entry no longer takes app-supplied resolver parameters — a wrapper fills every
 * content parameter, and this value is app-wide rather than route-bound, so it travels the way any
 * other app-wide value does in Compose.
 */
val LocalBuildInfo = staticCompositionLocalOf { BuildInfo(version = "unknown") }
