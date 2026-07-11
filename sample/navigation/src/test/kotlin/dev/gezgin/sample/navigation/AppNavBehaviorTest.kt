package dev.gezgin.sample.navigation

import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.NavResult
import dev.gezgin.sample.domain.model.AvatarChoice
import dev.gezgin.sample.domain.model.NotificationLevel
import dev.gezgin.sample.domain.model.SortOrder
import dev.gezgin.test.GezginTestNavigator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * F-MAJOR-2 — the headline UI-less test flow, exercised in the canonical multi-module layout: graphs
 * live in `main`, these tests in `test`. `gezgin.emitTestAccessors=true` (set on the module's MAIN KSP
 * round — see `build.gradle.kts`) makes the processor emit typed `GezginTestNavigator.fromX()` accessors
 * into `main`, so this `test` source set drives navigation through them directly
 * (`nav.fromLogin().goToSignUp()`), with no `raw.xNavigator(entryId)` factory plumbing. The class-level
 * `@GezginInternalApi` opt-in survives only for the handful of low-level `raw.navigate` / `raw.currentEntryId`
 * setup / inspection calls a couple of tests still make — the `fromX()` accessors themselves need no opt-in.
 */
@OptIn(ExperimentalCoroutinesApi::class, GezginInternalApi::class)
class AppNavBehaviorTest {

    @Test fun loginSignUpQuitAndGoToWelcome_leavesLoginThenWelcome() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)

        nav.fromLogin().goToSignUp()
        nav.fromCredentials().goToProfileInfo("ada@example.com")
        nav.fromProfileInfo().goToTerms()
        nav.fromTerms().quitAndGoToWelcome("Ada")

        assertEquals(
            listOf(AuthGraph.LoginScreenRoute, HomeGraph.WelcomeScreenRoute("Ada")),
            nav.backStack,
        )
    }

    @Test fun welcomeContinueToDashboard_replacesWelcomeKeepsLoginBelow() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.fromLogin().goToSignUp()
        nav.fromCredentials().goToProfileInfo("ada@example.com")
        nav.fromProfileInfo().goToTerms()
        nav.fromTerms().quitAndGoToWelcome("Ada")

        nav.fromWelcome().continueToDashboard()

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

        nav.fromSettings().logout()

        assertEquals(listOf(AuthGraph.LoginScreenRoute, AuthGraph.LoginScreenRoute), nav.backStack)
    }

    @Test fun avatarFlowQuitWith_deliversValueToProfilePickAvatarResults() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.fromProfile()

        profile.launchPickAvatar()
        nav.fromPickSource().goToCrop("gallery")
        nav.fromCrop().quitWith(AvatarChoice("file://avatar.png"))

        assertEquals(ProfileGraph.ProfileScreenRoute, nav.current)
        assertEquals(
            NavResult.Value(AvatarChoice("file://avatar.png")),
            profile.pickAvatarResults.first(),
        )
    }

    @Test fun nestedZoomFlowBack_popsOnlyZoomLeavesCropOnTop() {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        nav.fromProfile().launchPickAvatar()
        nav.fromPickSource().goToCrop("camera")
        nav.fromCrop().goToZoom()
        assertEquals(AvatarFlow.ZoomFlow.ZoomScreenRoute, nav.current)

        nav.fromZoom().back()

        assertEquals(AvatarFlow.CropScreenRoute("camera"), nav.current)
        assertEquals(
            listOf(ProfileGraph.ProfileScreenRoute, AvatarFlow.PickSourceScreenRoute, AvatarFlow.CropScreenRoute("camera")),
            nav.backStack,
        )
    }

    @Test fun nestedZoomFlowQuitWith_deliversValueToProfileTearsDownWholeAvatarFlow() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.fromProfile()

        profile.launchPickAvatar()
        nav.fromPickSource().goToCrop("gallery")
        nav.fromCrop().goToZoom()
        assertEquals(AvatarFlow.ZoomFlow.ZoomScreenRoute, nav.current)

        nav.fromZoom().quitWith(AvatarChoice("zoomed://frame"))

        assertEquals(listOf(ProfileGraph.ProfileScreenRoute), nav.backStack)
        assertEquals(
            NavResult.Value(AvatarChoice("zoomed://frame")),
            profile.pickAvatarResults.first(),
        )
    }

    @Test fun goToRelatedTwiceSameId_createsThreeDistinctStackEntries() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)

        nav.fromDashboard().goToItemDetail("item-1")
        val firstId = nav.raw.currentEntryId
        nav.fromItemDetail().goToRelated("item-1")
        val secondId = nav.raw.currentEntryId
        nav.fromItemDetail().goToRelated("item-1")
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
        val login = nav.fromLogin()

        val resultDeferred = async { login.goToForgotPasswordDialogForResult(null) }
        runCurrent()
        assertEquals(AuthGraph.ForgotPasswordDialogRoute(null), nav.current)

        nav.backWithResult(true)

        assertEquals(NavResult.Value(true), resultDeferred.await())
    }

    @Test fun forgotPasswordBack_deliversCanceled() = runTest {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        val login = nav.fromLogin()

        val resultDeferred = async { login.goToForgotPasswordDialogForResult(null) }
        runCurrent()
        assertEquals(AuthGraph.ForgotPasswordDialogRoute(null), nav.current)

        nav.fromForgotPasswordDialog().back()

        assertEquals(NavResult.Canceled, resultDeferred.await())
    }

    @Test fun termsBackToStart_landsOnCredentialsKeepsLoginBelow() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.fromLogin().goToSignUp()
        nav.fromCredentials().goToProfileInfo("ada@example.com")
        nav.fromProfileInfo().goToTerms()

        nav.fromTerms().backToStart()

        assertEquals(
            listOf(AuthGraph.LoginScreenRoute, SignUpFlow.CredentialsScreenRoute),
            nav.backStack,
        )
    }

    @Test fun termsQuit_tearsDownSignUpFlowLeavesLoginOnTop() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.fromLogin().goToSignUp()
        nav.fromCredentials().goToProfileInfo("ada@example.com")
        nav.fromProfileInfo().goToTerms()

        nav.fromTerms().quit()

        assertEquals(listOf(AuthGraph.LoginScreenRoute), nav.backStack)
    }

    @Test fun backToDashboardThenCrossGraphGoToProfile_bothViaGeneratedNavigators() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)

        nav.fromDashboard().goToItemDetail("item-1")
        assertEquals(HomeGraph.ItemDetailScreenRoute("item-1"), nav.current)

        nav.fromItemDetail().backToDashboard()
        assertEquals(listOf(HomeGraph.DashboardScreenRoute), nav.backStack)

        nav.fromDashboard().goToProfile()
        assertEquals(
            listOf(HomeGraph.DashboardScreenRoute, ProfileGraph.ProfileScreenRoute),
            nav.backStack,
        )
    }

    @Test fun itemImageViewerBackToItemDetail_popsOnlyModalLeavesDashboardAndItemDetail() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)

        nav.fromDashboard().goToItemDetail("item-1")
        nav.fromItemDetail().goToItemImageViewer("item-1")
        assertEquals(HomeGraph.ItemImageViewerRoute("item-1"), nav.current)

        nav.fromItemImageViewer().backToItemDetail()

        assertEquals(
            listOf(HomeGraph.DashboardScreenRoute, HomeGraph.ItemDetailScreenRoute("item-1")),
            nav.backStack,
        )
    }

    @Test fun goToHelpThenBackToDashboard_roundTripsViaGeneratedNavigators() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)

        nav.fromDashboard().goToHelp("navigasyon")
        assertEquals(HomeGraph.HelpScreenRoute("navigasyon"), nav.current)
        assertEquals(
            listOf(HomeGraph.DashboardScreenRoute, HomeGraph.HelpScreenRoute("navigasyon")),
            nav.backStack,
        )

        nav.fromHelp().backToDashboard()
        assertEquals(listOf(HomeGraph.DashboardScreenRoute), nav.backStack)
    }

    @Test fun pickSortSuspendResult_deliversSortOrder() = runTest {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)
        val dashboard = nav.fromDashboard()

        val resultDeferred = async { dashboard.goToPickSortForResult(SortOrder.RELEVANCE.name) }
        runCurrent()
        assertEquals(HomeGraph.FilterBottomSheetRoute(SortOrder.RELEVANCE.name), nav.current)

        nav.fromFilterBottomSheet().backWithResult(SortOrder.PRICE_ASC)

        assertEquals(NavResult.Value(SortOrder.PRICE_ASC), resultDeferred.await())
        assertEquals(listOf(HomeGraph.DashboardScreenRoute), nav.backStack)
    }

    @Test fun pickSortLaunchStream_deliversValueToPickSortResults() = runTest {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)
        val dashboard = nav.fromDashboard()

        dashboard.launchPickSort(SortOrder.RELEVANCE.name)
        assertEquals(HomeGraph.FilterBottomSheetRoute(SortOrder.RELEVANCE.name), nav.current)

        nav.fromFilterBottomSheet().backWithResult(SortOrder.PRICE_DESC)

        assertEquals(NavResult.Value(SortOrder.PRICE_DESC), dashboard.pickSortResults.first())
    }

    @Test fun notificationsSheetSuspendResult_deliversSelectedLevel() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.fromProfile()

        val resultDeferred = async { profile.goToPickNotificationsForResult(NotificationLevel.ALL) }
        runCurrent()
        assertEquals(ProfileGraph.NotificationsSheetRoute(NotificationLevel.ALL), nav.current)

        nav.fromNotificationsSheet().backWithResult(NotificationLevel.MENTIONS)

        assertEquals(NavResult.Value(NotificationLevel.MENTIONS), resultDeferred.await())
        assertEquals(listOf(ProfileGraph.ProfileScreenRoute), nav.backStack)
    }

    @Test fun notificationsSheetBack_deliversCanceled() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.fromProfile()

        val resultDeferred = async { profile.goToPickNotificationsForResult(NotificationLevel.NONE) }
        runCurrent()
        assertEquals(ProfileGraph.NotificationsSheetRoute(NotificationLevel.NONE), nav.current)

        nav.fromNotificationsSheet().back()

        assertEquals(NavResult.Canceled, resultDeferred.await())
    }
}
