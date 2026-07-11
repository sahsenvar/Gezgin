package dev.gezgin.sample.navigation

import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.test.GezginTestNavigator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Graphs live in `main`, tests in `test`: a `kspTestKotlin` round sees no `@NavGraph` symbols, so no
 * typed `fromX()` accessors are emitted for a module shaped like this. Tests therefore drive the
 * main-round `raw.xNavigator(entryId)` factories directly (see `sample/README.md` "Tasarım notları").
 */
@OptIn(ExperimentalCoroutinesApi::class, GezginInternalApi::class)
class AppNavBehaviorTest {

    private fun <T> GezginTestNavigator.on(route: KClass<out Route>, factory: (RawNavigator, Long) -> T): T =
        factory(raw, entryIdOf(route))

    private fun GezginTestNavigator.login() = on(AuthGraph.LoginScreenRoute::class, RawNavigator::loginNavigator)
    private fun GezginTestNavigator.credentials() = on(SignUpFlow.CredentialsScreenRoute::class, RawNavigator::credentialsNavigator)
    private fun GezginTestNavigator.profileInfo() = on(SignUpFlow.ProfileInfoScreenRoute::class, RawNavigator::profileInfoNavigator)
    private fun GezginTestNavigator.terms() = on(SignUpFlow.TermsScreenRoute::class, RawNavigator::termsNavigator)
    private fun GezginTestNavigator.welcome() = on(HomeGraph.WelcomeScreenRoute::class, RawNavigator::welcomeNavigator)
    private fun GezginTestNavigator.settings() = on(ProfileGraph.SettingsScreenRoute::class, RawNavigator::settingsNavigator)
    private fun GezginTestNavigator.profile() = on(ProfileGraph.ProfileScreenRoute::class, RawNavigator::profileNavigator)
    private fun GezginTestNavigator.pickSource() = on(AvatarFlow.PickSourceScreenRoute::class, RawNavigator::pickSourceNavigator)
    private fun GezginTestNavigator.crop() = on(AvatarFlow.CropScreenRoute::class, RawNavigator::cropNavigator)
    private fun GezginTestNavigator.zoom() = on(AvatarFlow.ZoomFlow.ZoomScreenRoute::class, RawNavigator::zoomNavigator)
    private fun GezginTestNavigator.dashboard() = on(HomeGraph.DashboardScreenRoute::class, RawNavigator::dashboardNavigator)
    private fun GezginTestNavigator.itemDetail() = on(HomeGraph.ItemDetailScreenRoute::class, RawNavigator::itemDetailNavigator)
    private fun GezginTestNavigator.itemImageViewer() = on(HomeGraph.ItemImageViewerRoute::class, RawNavigator::itemImageViewerNavigator)
    private fun GezginTestNavigator.forgotPassword() = on(AuthGraph.ForgotPasswordDialogRoute::class, RawNavigator::forgotPasswordDialogNavigator)
    private fun GezginTestNavigator.help() = on(HomeGraph.HelpScreenRoute::class, RawNavigator::helpNavigator)
    private fun GezginTestNavigator.filterBottomSheet() = on(HomeGraph.FilterBottomSheetRoute::class, RawNavigator::filterBottomSheetNavigator)
    private fun GezginTestNavigator.notificationsSheet() = on(ProfileGraph.NotificationsSheetRoute::class, RawNavigator::notificationsSheetNavigator)

    @Test fun loginSignUpQuitAndGoToWelcome_leavesLoginThenWelcome() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)

        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()
        nav.terms().quitAndGoToWelcome("Ada")

        assertEquals(
            listOf(AuthGraph.LoginScreenRoute, HomeGraph.WelcomeScreenRoute("Ada")),
            nav.backStack,
        )
    }

    @Test fun welcomeContinueToDashboard_replacesWelcomeKeepsLoginBelow() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()
        nav.terms().quitAndGoToWelcome("Ada")

        nav.welcome().continueToDashboard()

        assertEquals(listOf(AuthGraph.LoginScreenRoute, HomeGraph.DashboardScreenRoute), nav.backStack)
    }

    // Pinned (README "Tasarım notları"): clearUpTo=Dashboard inclusive purges down to the original
    // Login, then replaceTo always pushes fresh → a STACKED second Login, not a dedup. Left as authored.
    @Test fun logoutClearUpToDashboardInclusive_stacksASecondLoginEntry() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.raw.navigate(HomeGraph.DashboardScreenRoute)
        nav.raw.navigate(ProfileGraph.ProfileScreenRoute)
        nav.raw.navigate(ProfileGraph.SettingsScreenRoute)
        assertEquals(
            listOf(AuthGraph.LoginScreenRoute, HomeGraph.DashboardScreenRoute, ProfileGraph.ProfileScreenRoute, ProfileGraph.SettingsScreenRoute),
            nav.backStack,
        )

        nav.settings().logout()

        assertEquals(listOf(AuthGraph.LoginScreenRoute, AuthGraph.LoginScreenRoute), nav.backStack)
    }

    @Test fun avatarFlowQuitWith_deliversValueToProfilePickAvatarResults() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.profile()

        profile.launchPickAvatar()
        nav.pickSource().goToCrop("gallery")
        nav.crop().quitWith(AvatarChoice("file://avatar.png"))

        assertEquals(ProfileGraph.ProfileScreenRoute, nav.current)
        assertEquals(
            NavResult.Value(AvatarChoice("file://avatar.png")),
            profile.pickAvatarResults.first(),
        )
    }

    @Test fun nestedZoomFlowBack_popsOnlyZoomLeavesCropOnTop() {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        nav.profile().launchPickAvatar()
        nav.pickSource().goToCrop("camera")
        nav.crop().goToZoom()
        assertEquals(AvatarFlow.ZoomFlow.ZoomScreenRoute, nav.current)

        nav.zoom().back()

        assertEquals(AvatarFlow.CropScreenRoute("camera"), nav.current)
        assertEquals(
            listOf(ProfileGraph.ProfileScreenRoute, AvatarFlow.PickSourceScreenRoute, AvatarFlow.CropScreenRoute("camera")),
            nav.backStack,
        )
    }

    @Test fun nestedZoomFlowQuitWith_deliversValueToProfileTearsDownWholeAvatarFlow() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.profile()

        profile.launchPickAvatar()
        nav.pickSource().goToCrop("gallery")
        nav.crop().goToZoom()
        assertEquals(AvatarFlow.ZoomFlow.ZoomScreenRoute, nav.current)

        nav.zoom().quitWith(AvatarChoice("zoomed://frame"))

        assertEquals(listOf(ProfileGraph.ProfileScreenRoute), nav.backStack)
        assertEquals(
            NavResult.Value(AvatarChoice("zoomed://frame")),
            profile.pickAvatarResults.first(),
        )
    }

    @Test fun goToRelatedTwiceSameId_createsThreeDistinctStackEntries() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)

        nav.dashboard().goToItemDetail("item-1")
        val firstId = nav.raw.currentEntryId
        nav.itemDetail().goToRelated("item-1")
        val secondId = nav.raw.currentEntryId
        nav.itemDetail().goToRelated("item-1")
        val thirdId = nav.raw.currentEntryId

        assertEquals(3, setOf(firstId, secondId, thirdId).size)
        assertEquals(
            listOf(
                HomeGraph.DashboardScreenRoute,
                HomeGraph.ItemDetailScreenRoute("item-1"),
                HomeGraph.ItemDetailScreenRoute("item-1"),
                HomeGraph.ItemDetailScreenRoute("item-1"),
            ),
            nav.backStack,
        )
    }

    @Test fun forgotPasswordSuspendResult_deliversValue() = runTest {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        val login = nav.login()

        val resultDeferred = async { login.goToForgotPasswordDialogForResult(null) }
        runCurrent()
        assertEquals(AuthGraph.ForgotPasswordDialogRoute(null), nav.current)

        nav.backWithResult(true)

        assertEquals(NavResult.Value(true), resultDeferred.await())
    }

    @Test fun forgotPasswordBack_deliversCanceled() = runTest {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        val login = nav.login()

        val resultDeferred = async { login.goToForgotPasswordDialogForResult(null) }
        runCurrent()
        assertEquals(AuthGraph.ForgotPasswordDialogRoute(null), nav.current)

        nav.forgotPassword().back()

        assertEquals(NavResult.Canceled, resultDeferred.await())
    }

    @Test fun termsBackToStart_landsOnCredentialsKeepsLoginBelow() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()

        nav.terms().backToStart()

        assertEquals(
            listOf(AuthGraph.LoginScreenRoute, SignUpFlow.CredentialsScreenRoute),
            nav.backStack,
        )
    }

    @Test fun termsQuit_tearsDownSignUpFlowLeavesLoginOnTop() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()

        nav.terms().quit()

        assertEquals(listOf(AuthGraph.LoginScreenRoute), nav.backStack)
    }

    @Test fun backToDashboardThenCrossGraphGoToProfile_bothViaGeneratedNavigators() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)

        nav.dashboard().goToItemDetail("item-1")
        assertEquals(HomeGraph.ItemDetailScreenRoute("item-1"), nav.current)

        nav.itemDetail().backToDashboard()
        assertEquals(listOf(HomeGraph.DashboardScreenRoute), nav.backStack)

        nav.dashboard().goToProfile()
        assertEquals(
            listOf(HomeGraph.DashboardScreenRoute, ProfileGraph.ProfileScreenRoute),
            nav.backStack,
        )
    }

    @Test fun itemImageViewerBackToItemDetail_popsOnlyModalLeavesDashboardAndItemDetail() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)

        nav.dashboard().goToItemDetail("item-1")
        nav.itemDetail().goToItemImageViewer("item-1")
        assertEquals(HomeGraph.ItemImageViewerRoute("item-1"), nav.current)

        nav.itemImageViewer().backToItemDetail()

        assertEquals(
            listOf(HomeGraph.DashboardScreenRoute, HomeGraph.ItemDetailScreenRoute("item-1")),
            nav.backStack,
        )
    }

    @Test fun goToHelpThenBackToDashboard_roundTripsViaGeneratedNavigators() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)

        nav.dashboard().goToHelp("navigasyon")
        assertEquals(HomeGraph.HelpScreenRoute("navigasyon"), nav.current)
        assertEquals(
            listOf(HomeGraph.DashboardScreenRoute, HomeGraph.HelpScreenRoute("navigasyon")),
            nav.backStack,
        )

        nav.help().backToDashboard()
        assertEquals(listOf(HomeGraph.DashboardScreenRoute), nav.backStack)
    }

    @Test fun pickSortSuspendResult_deliversSortOrder() = runTest {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)
        val dashboard = nav.dashboard()

        val resultDeferred = async { dashboard.goToPickSortForResult(SortOrder.RELEVANCE.name) }
        runCurrent()
        assertEquals(HomeGraph.FilterBottomSheetRoute(SortOrder.RELEVANCE.name), nav.current)

        nav.filterBottomSheet().backWithResult(SortOrder.PRICE_ASC)

        assertEquals(NavResult.Value(SortOrder.PRICE_ASC), resultDeferred.await())
        assertEquals(listOf(HomeGraph.DashboardScreenRoute), nav.backStack)
    }

    @Test fun pickSortLaunchStream_deliversValueToPickSortResults() = runTest {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)
        val dashboard = nav.dashboard()

        dashboard.launchPickSort(SortOrder.RELEVANCE.name)
        assertEquals(HomeGraph.FilterBottomSheetRoute(SortOrder.RELEVANCE.name), nav.current)

        nav.filterBottomSheet().backWithResult(SortOrder.PRICE_DESC)

        assertEquals(NavResult.Value(SortOrder.PRICE_DESC), dashboard.pickSortResults.first())
    }

    @Test fun notificationsSheetSuspendResult_deliversSelectedLevel() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.profile()

        val resultDeferred = async { profile.goToPickNotificationsForResult(NotificationLevel.ALL) }
        runCurrent()
        assertEquals(ProfileGraph.NotificationsSheetRoute(NotificationLevel.ALL), nav.current)

        nav.notificationsSheet().backWithResult(NotificationLevel.MENTIONS)

        assertEquals(NavResult.Value(NotificationLevel.MENTIONS), resultDeferred.await())
        assertEquals(listOf(ProfileGraph.ProfileScreenRoute), nav.backStack)
    }

    @Test fun notificationsSheetBack_deliversCanceled() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.profile()

        val resultDeferred = async { profile.goToPickNotificationsForResult(NotificationLevel.NONE) }
        runCurrent()
        assertEquals(ProfileGraph.NotificationsSheetRoute(NotificationLevel.NONE), nav.current)

        nav.notificationsSheet().back()

        assertEquals(NavResult.Canceled, resultDeferred.await())
    }
}
