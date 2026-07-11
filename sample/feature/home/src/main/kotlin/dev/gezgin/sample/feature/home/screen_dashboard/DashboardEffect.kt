package dev.gezgin.sample.feature.home.screen_dashboard

sealed interface DashboardEffect {
    data class ShowMessage(val text: String) : DashboardEffect
}
