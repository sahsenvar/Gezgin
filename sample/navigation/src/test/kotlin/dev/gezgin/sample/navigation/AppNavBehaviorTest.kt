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
 * Sample S3 — behavior tests for the whole showcase graph.
 *
 * FALLBACK PATH (not the typed `fromX()` test API — see `build.gradle.kts` + `sample/README.md`
 * "Tasarım notları" for the full trace): `gezgin.emitTestAccessors=true` is reachable per-`kspTest`
 * TASK, but `TestApiCodegen` still needs a non-empty [dev.gezgin.processor.model.GraphModel], and a
 * `kspTestKotlin` round only sees the `test` source set's own `.kt` files — the graph files'
 * `@NavGraph`s live in `main` and are already compiled by then, so `getSymbolsWithAnnotation` finds
 * nothing and no `GezginTestAccessors.kt` is ever emitted for a module shaped like this one (graphs
 * and tests in separate Gradle source sets). So every test below drives the ALREADY generated
 * (main-round) `raw.xNavigator(entryId)` factories directly via [GezginTestNavigator.raw] — same
 * generated code the display layer and screens use, just resolved by hand instead of by `fromX()`.
 */
@OptIn(ExperimentalCoroutinesApi::class, GezginInternalApi::class)
class AppNavBehaviorTest {

    /** [GezginTestNavigator.entryIdOf] + the generated `raw.xNavigator(id)` factory, in one call. */
    private fun <T> GezginTestNavigator.on(route: KClass<out Route>, factory: (RawNavigator, Long) -> T): T =
        factory(raw, entryIdOf(route))

    private fun GezginTestNavigator.login() = on(AuthGraph.LoginScreenRoute::class, RawNavigator::loginNavigator)
    private fun GezginTestNavigator.credentials() = on(AuthGraph.SignUpFlow.CredentialsScreenRoute::class, RawNavigator::credentialsNavigator)
    private fun GezginTestNavigator.profileInfo() = on(AuthGraph.SignUpFlow.ProfileInfoScreenRoute::class, RawNavigator::profileInfoNavigator)
    private fun GezginTestNavigator.terms() = on(AuthGraph.SignUpFlow.TermsScreenRoute::class, RawNavigator::termsNavigator)
    private fun GezginTestNavigator.welcome() = on(HomeGraph.WelcomeScreenRoute::class, RawNavigator::welcomeNavigator)
    private fun GezginTestNavigator.settings() = on(ProfileGraph.SettingsScreenRoute::class, RawNavigator::settingsNavigator)
    private fun GezginTestNavigator.profile() = on(ProfileGraph.ProfileScreenRoute::class, RawNavigator::profileNavigator)
    private fun GezginTestNavigator.pickSource() = on(ProfileGraph.AvatarFlow.PickSourceScreenRoute::class, RawNavigator::pickSourceNavigator)
    private fun GezginTestNavigator.crop() = on(ProfileGraph.AvatarFlow.CropScreenRoute::class, RawNavigator::cropNavigator)
    private fun GezginTestNavigator.zoom() = on(ProfileGraph.AvatarFlow.ZoomFlow.ZoomScreenRoute::class, RawNavigator::zoomNavigator)
    private fun GezginTestNavigator.dashboard() = on(HomeGraph.DashboardScreenRoute::class, RawNavigator::dashboardNavigator)
    private fun GezginTestNavigator.itemDetail() = on(HomeGraph.ItemDetailScreenRoute::class, RawNavigator::itemDetailNavigator)
    private fun GezginTestNavigator.itemImageViewer() = on(HomeGraph.ItemImageViewerRoute::class, RawNavigator::itemImageViewerNavigator)
    private fun GezginTestNavigator.forgotPassword() = on(AuthGraph.ForgotPasswordDialogRoute::class, RawNavigator::forgotPasswordDialogNavigator)
    private fun GezginTestNavigator.help() = on(HomeGraph.HelpScreenRoute::class, RawNavigator::helpNavigator)
    private fun GezginTestNavigator.filterBottomSheet() = on(HomeGraph.FilterBottomSheetRoute::class, RawNavigator::filterBottomSheetNavigator)
    private fun GezginTestNavigator.notificationsSheet() = on(ProfileGraph.NotificationsSheetRoute::class, RawNavigator::notificationsSheetNavigator)

    // (a) login → signUp flow → terms → quitAndGoTo(Welcome): the whole SignUpFlow segment is torn
    // down (quitAndGoTo tears down the CURRENT flow unconditionally) and Welcome is pushed as a plain
    // (non-flow) HomeGraph route on top of the surviving Login entry.
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

    // (b) Welcome → continueToDashboard (@ReplaceTo, Self-default clearUpTo=null, inclusive=true):
    // replaceTo with clearUpTo=null only cuts the CURRENT top (Welcome), so Login below survives —
    // back from Dashboard would still land on Login, not on Welcome (@NoBack's whole point).
    @Test fun welcomeContinueToDashboard_replacesWelcomeKeepsLoginBelow() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()
        nav.terms().quitAndGoToWelcome("Ada")

        nav.welcome().continueToDashboard()

        assertEquals(listOf(AuthGraph.LoginScreenRoute, HomeGraph.DashboardScreenRoute), nav.backStack)
    }

    // (c) DESIGN NOTE (see sample/README.md "Tasarım notları"): SettingsScreenRoute's
    // `@ReplaceTo(LoginScreenRoute, clearUpTo = DashboardScreenRoute::class, inclusive = true, name = "logout")`
    // purges the stack down to (and including) Dashboard's nearest occurrence, which — starting from
    // [Login, Dashboard, Profile, Settings] — leaves only the ORIGINAL Login entry, and THEN
    // `replaceTo` always pushes a fresh (non-singleTop) route. The real runtime outcome is therefore
    // a STACKED SECOND Login entry, not a dedup back-to-the-existing-Login. This is a pinned,
    // deliberately-not-"fixed" finding: the sample graph is left as authored (S1), and this test
    // documents the actual behavior rather than an assumed one.
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

    // (d) Profile → launchPickAvatar (flow-mode @GoForResult) → PickSource → Crop → quitWith:
    // quitWith targets the NEAREST ENCLOSING ResultFlow (AvatarFlow, spec §6) — its own contiguous
    // segment [PickSource, Crop] is torn down, Profile survives on top, and the Value is delivered
    // to the flow-entry's own pending slot (i.e. Profile's `pickAvatarResults`).
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

    // (e) Nested @FlowGraph: Crop → goToZoom (enters ZoomFlow, chain [AvatarFlow, ZoomFlow]) →
    // back() on Zoom's @StartDestination is a flow-ENTRY back (`RawNavigator.isFlowEntry`), so it
    // delegates to quit() and tears down ONLY the ZoomFlow segment — Crop (still inside AvatarFlow,
    // untouched) is left on top. AvatarFlow's own pending pickAvatar slot is unaffected.
    @Test fun nestedZoomFlowBack_popsOnlyZoomLeavesCropOnTop() {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        nav.profile().launchPickAvatar()
        nav.pickSource().goToCrop("camera")
        nav.crop().goToZoom()
        assertEquals(ProfileGraph.AvatarFlow.ZoomFlow.ZoomScreenRoute, nav.current)

        nav.zoom().back()

        assertEquals(ProfileGraph.AvatarFlow.CropScreenRoute("camera"), nav.current)
        assertEquals(
            listOf(ProfileGraph.ProfileScreenRoute, ProfileGraph.AvatarFlow.PickSourceScreenRoute, ProfileGraph.AvatarFlow.CropScreenRoute("camera")),
            nav.backStack,
        )
    }

    // (e2) `quitWith` from the NESTED ZoomFlow (chain [AvatarFlow, ZoomFlow]) targets the nearest
    // CONTRACT-OWNING flow (spec §6 ownership): ZoomFlow carries ResultFlow only TRANSITIVELY
    // (inherited from AvatarFlow) and owns no contract, so the generated topology marks it
    // isResultFlow=false (direct-declaration semantics — see TopologyCodegen). Result: BOTH
    // ZoomFlow's and AvatarFlow's segments are torn down in one call, Profile survives on top, and
    // the Value reaches Profile's `pickAvatarResults` exactly like a direct `crop().quitWith(...)`
    // would (see (d)) — no PickSource/Crop/Zoom entry remains on the stack, nothing is dropped.
    @Test fun nestedZoomFlowQuitWith_deliversValueToProfileTearsDownWholeAvatarFlow() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileScreenRoute, topology = gezginTopology)
        val profile = nav.profile()

        profile.launchPickAvatar()
        nav.pickSource().goToCrop("gallery")
        nav.crop().goToZoom()
        assertEquals(ProfileGraph.AvatarFlow.ZoomFlow.ZoomScreenRoute, nav.current)

        nav.zoom().quitWith(AvatarChoice("zoomed://frame"))

        assertEquals(listOf(ProfileGraph.ProfileScreenRoute), nav.backStack)
        assertEquals(
            NavResult.Value(AvatarChoice("zoomed://frame")),
            profile.pickAvatarResults.first(),
        )
    }

    // (f) R2: `goToRelated` (singleTop=false, named 2nd edge to the SAME target) never dedups —
    // each call mints a genuinely new stack entry (distinct id), even though the route VALUE
    // (ItemDetailScreenRoute("item-1")) repeats three times in `backStack`.
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

    // (g) Screen-mode @GoForResult consumed via the suspend `goToXForResult` pattern (Dashboard's
    // real-screen counterpart): the call pushes synchronously then suspends on the result; delivering
    // via `deliverResult` (raw `backWithResult`) on the still-top dialog resumes it with the Value.
    @Test fun forgotPasswordSuspendResult_deliversValue() = runTest {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        val login = nav.login()

        val resultDeferred = async { login.goToForgotPasswordDialogForResult(null) }
        runCurrent()
        assertEquals(AuthGraph.ForgotPasswordDialogRoute(null), nav.current)

        nav.deliverResult(true)

        assertEquals(NavResult.Value(true), resultDeferred.await())
    }

    // (h) Screen-mode @GoForResult, the CANCELED path: plain `back()` on a still-pending
    // ResultRoute (no `backWithResult`) delivers `NavResult.Canceled` to the awaiting suspend call
    // — mirrors (g) but exercises the "user dismissed without an answer" branch.
    @Test fun forgotPasswordBack_deliversCanceled() = runTest {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        val login = nav.login()

        val resultDeferred = async { login.goToForgotPasswordDialogForResult(null) }
        runCurrent()
        assertEquals(AuthGraph.ForgotPasswordDialogRoute(null), nav.current)

        nav.forgotPassword().back()

        assertEquals(NavResult.Canceled, resultDeferred.await())
    }

    // (i) `@BackToStart` on `TermsScreenRoute`: pops back to (not past) the flow's own
    // `@StartDestination` (`CredentialsScreenRoute`) — the flow itself SURVIVES, only its interior
    // (ProfileInfo, Terms) is discarded; Login (outside the flow) is untouched below it.
    @Test fun termsBackToStart_landsOnCredentialsKeepsLoginBelow() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()

        nav.terms().backToStart()

        assertEquals(
            listOf(AuthGraph.LoginScreenRoute, AuthGraph.SignUpFlow.CredentialsScreenRoute),
            nav.backStack,
        )
    }

    // (j) `@Quit` on `TermsScreenRoute`: a flow-entry `quit()` tears down the WHOLE SignUpFlow segment
    // (Canceled to any pending caller) and leaves Login exposed on top — contrast with (i), where
    // the same route's OTHER exit annotation keeps the flow alive.
    @Test fun termsQuit_tearsDownSignUpFlowLeavesLoginOnTop() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginScreenRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()

        nav.terms().quit()

        assertEquals(listOf(AuthGraph.LoginScreenRoute), nav.backStack)
    }

    // (k) `@BackTo` (`ItemDetailScreenRoute` → `DashboardScreenRoute`) built ENTIRELY through generated
    // navigators (no `raw.navigate`), AND pinning the cross-graph B1 edge
    // (`DashboardScreenRoute.goToProfile()` → `ProfileGraph.ProfileScreenRoute`) that every other test exercises
    // only by starting `GezginTestNavigator` directly on `ProfileScreenRoute` — here it is actually invoked.
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

    // (l) @FullscreenModal round-trip (Faz 7.2 / GAP-1): the ItemImageViewerRoute modal is reached from
    // ItemDetail via the plain @GoTo edge (`goToItemImageViewer`, singleTop=true) and dismissed via its
    // @BackTo(ItemDetailScreenRoute) edge (`backToItemDetail()`, inclusive=false). The dismiss pops ONLY
    // the modal — the opening ItemDetail (directly below it) survives, leaving exactly
    // [Dashboard, ItemDetail]; the whole Dashboard→ItemDetail chain is NOT torn down. Pins the genuinely
    // new navigation edge added in Task 7.2 (no earlier test navigates to ItemImageViewer at all).
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

    // (m) HelpScreenRoute (@FragmentScreen leaf) BOTH generated edges through generated navigators:
    // Dashboard's `goToHelp(topic)` (@GoTo) pushes the legacy Fragment leaf, then Help's
    // `@BackTo(DashboardScreenRoute)` (`backToDashboard()`) pops back to it. Pure nav logic — no
    // Fragment binding — but until now the `Help` edges had zero coverage (grep: 0).
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

    // (n) `pickSort` (Dashboard's named screen-mode @GoForResult → FilterBottomSheetRoute) via the
    // suspend `goToPickSortForResult`: pushes synchronously, suspends on the result, and the sheet's
    // typed `backWithResult` (entry-pinned) resumes it with the chosen SortOrder.
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

    // (o) The SAME `pickSort` triple's launch+stream half: `launchPickSort` pushes without suspending,
    // `pickSortResults` re-collects the pending slot, and `backWithResult` delivers the Value there —
    // exercises `launchPickSort`/`pickSortResults` (the members `goToPickSortForResult` doesn't touch).
    @Test fun pickSortLaunchStream_deliversValueToPickSortResults() = runTest {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardScreenRoute, topology = gezginTopology)
        val dashboard = nav.dashboard()

        dashboard.launchPickSort(SortOrder.RELEVANCE.name)
        assertEquals(HomeGraph.FilterBottomSheetRoute(SortOrder.RELEVANCE.name), nav.current)

        nav.filterBottomSheet().backWithResult(SortOrder.PRICE_DESC)

        assertEquals(NavResult.Value(SortOrder.PRICE_DESC), dashboard.pickSortResults.first())
    }

    // (p) The MVI @BottomSheet (Integ M3) nav path: Profile's `goToPickNotificationsForResult`
    // (named screen-mode @GoForResult) pushes the MVI-mode sheet route, and its entry-pinned typed
    // `backWithResult` (what the VM's Confirm intent calls) delivers the chosen NotificationLevel.
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

    // (q) MVI @BottomSheet CANCELED path: a plain `back()` on the still-pending sheet (a swipe/scrim
    // dismiss maps to the same `back()`) delivers Canceled to the awaiting caller — the sheet's
    // gesture-dismiss branch of the same M3 route.
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
