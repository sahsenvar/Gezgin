package dev.gezgin.sample.feature.home.screen_dashboard

sealed interface DashboardIntent {
    data class OpenItem(val id: String) : DashboardIntent
    data object OpenProfile : DashboardIntent
    data object OpenHelp : DashboardIntent
    data object PickSort : DashboardIntent
}
