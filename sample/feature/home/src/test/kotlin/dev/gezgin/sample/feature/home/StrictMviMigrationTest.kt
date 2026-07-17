package dev.gezgin.sample.feature.home

import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.sample.domain.model.SortOrder
import dev.gezgin.sample.feature.home.screen_dashboard.DashboardEffect
import dev.gezgin.sample.feature.home.screen_dashboard.DashboardIntent
import dev.gezgin.sample.feature.home.screen_dashboard.DashboardViewModel
import dev.gezgin.sample.feature.home.screen_dashboard.handleDashboardEffect
import dev.gezgin.sample.navigation.HomeGraph.DashboardScreenRoute
import dev.gezgin.sample.navigation.HomeGraph.ItemDetailScreenRoute
import dev.gezgin.sample.navigation.dashboardNavigator
import dev.gezgin.sample.navigation.gezginTopology
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StrictMviMigrationTest {

    @Test
    fun `dashboard navigation intent becomes an effect before the typed handler navigates`() = runBlocking {
        val viewModel = DashboardViewModel()

        viewModel.onIntent(DashboardIntent.OpenItem("item-42"))

        val effect = viewModel.effects.first()
        assertEquals(DashboardEffect.OpenItem("item-42"), effect)

        val raw = RawNavigator(start = DashboardScreenRoute, topology = gezginTopology)
        handleDashboardEffect(effect, raw.dashboardNavigator(entryId = 1L))
        assertEquals(ItemDetailScreenRoute("item-42"), raw.current)
    }

    @Test
    fun `sort result re-enters the ViewModel as an intent`() = runBlocking {
        val viewModel = DashboardViewModel()

        viewModel.effects
            .resultIntentSink<DashboardIntent>()
            .sendResultIntent(DashboardIntent.SortResult(NavResult.Value(SortOrder.PRICE_DESC)))

        assertEquals(SortOrder.PRICE_DESC, viewModel.uiState.value.order)
        assertEquals(DashboardEffect.ShowMessage("Sıralama: PRICE_DESC"), viewModel.effects.first())
    }
}
