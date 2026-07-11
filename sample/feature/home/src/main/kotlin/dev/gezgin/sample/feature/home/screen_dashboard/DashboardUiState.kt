package dev.gezgin.sample.feature.home.screen_dashboard

import dev.gezgin.sample.domain.model.SortOrder

data class DashboardUiState(val order: SortOrder = SortOrder.RELEVANCE)
