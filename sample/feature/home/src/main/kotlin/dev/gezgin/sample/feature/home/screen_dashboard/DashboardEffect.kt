package dev.gezgin.sample.feature.home.screen_dashboard

sealed interface DashboardEffect {
    data class ShowMessage(val text: String) : DashboardEffect
    data class OpenItem(val id: String) : DashboardEffect
    data object OpenProfile : DashboardEffect
    data class OpenHelp(val topic: String) : DashboardEffect
    data class OpenSortPicker(val current: String) : DashboardEffect
}
