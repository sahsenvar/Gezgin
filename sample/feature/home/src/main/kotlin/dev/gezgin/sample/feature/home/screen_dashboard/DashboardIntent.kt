package dev.gezgin.sample.feature.home.screen_dashboard

import dev.gezgin.core.NavResult
import dev.gezgin.sample.domain.model.SortOrder

sealed interface DashboardIntent {
    data class OpenItem(val id: String) : DashboardIntent
    data object OpenProfile : DashboardIntent
    data object OpenHelp : DashboardIntent
    data object PickSort : DashboardIntent
    data class SortResult(val result: NavResult<SortOrder>) : DashboardIntent
}
