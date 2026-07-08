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
 * `kspTestKotlin` round only sees the `test` source set's own `.kt` files — `AppNav.kt`'s
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

    private fun GezginTestNavigator.login() = on(AuthGraph.LoginRoute::class, RawNavigator::loginNavigator)
    private fun GezginTestNavigator.credentials() = on(AuthGraph.SignUpFlow.CredentialsRoute::class, RawNavigator::credentialsNavigator)
    private fun GezginTestNavigator.profileInfo() = on(AuthGraph.SignUpFlow.ProfileInfoRoute::class, RawNavigator::profileInfoNavigator)
    private fun GezginTestNavigator.terms() = on(AuthGraph.SignUpFlow.TermsRoute::class, RawNavigator::termsNavigator)
    private fun GezginTestNavigator.welcome() = on(HomeGraph.WelcomeRoute::class, RawNavigator::welcomeNavigator)
    private fun GezginTestNavigator.settings() = on(ProfileGraph.SettingsRoute::class, RawNavigator::settingsNavigator)
    private fun GezginTestNavigator.profile() = on(ProfileGraph.ProfileRoute::class, RawNavigator::profileNavigator)
    private fun GezginTestNavigator.pickSource() = on(ProfileGraph.AvatarFlow.PickSourceRoute::class, RawNavigator::pickSourceNavigator)
    private fun GezginTestNavigator.crop() = on(ProfileGraph.AvatarFlow.CropRoute::class, RawNavigator::cropNavigator)
    private fun GezginTestNavigator.zoom() = on(ProfileGraph.AvatarFlow.ZoomFlow.ZoomRoute::class, RawNavigator::zoomNavigator)
    private fun GezginTestNavigator.dashboard() = on(HomeGraph.DashboardRoute::class, RawNavigator::dashboardNavigator)
    private fun GezginTestNavigator.itemDetail() = on(HomeGraph.ItemDetailRoute::class, RawNavigator::itemDetailNavigator)
    private fun GezginTestNavigator.forgotPassword() = on(AuthGraph.ForgotPasswordDialog::class, RawNavigator::forgotPasswordDialogNavigator)

    // (a) login → signUp flow → terms → quitAndGoTo(Welcome): the whole SignUpFlow segment is torn
    // down (quitAndGoTo tears down the CURRENT flow unconditionally) and Welcome is pushed as a plain
    // (non-flow) HomeGraph route on top of the surviving Login entry.
    @Test fun loginSignUpQuitAndGoToWelcome_leavesLoginThenWelcome() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginRoute, topology = gezginTopology)

        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()
        nav.terms().quitAndGoToWelcome("Ada")

        assertEquals(
            listOf(AuthGraph.LoginRoute, HomeGraph.WelcomeRoute("Ada")),
            nav.backStack,
        )
    }

    // (b) Welcome → continueToDashboard (@ReplaceTo, Self-default clearUpTo=null, inclusive=true):
    // replaceTo with clearUpTo=null only cuts the CURRENT top (Welcome), so Login below survives —
    // back from Dashboard would still land on Login, not on Welcome (@NoBack's whole point).
    @Test fun welcomeContinueToDashboard_replacesWelcomeKeepsLoginBelow() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()
        nav.terms().quitAndGoToWelcome("Ada")

        nav.welcome().continueToDashboard()

        assertEquals(listOf(AuthGraph.LoginRoute, HomeGraph.DashboardRoute), nav.backStack)
    }

    // (c) DESIGN NOTE (see sample/README.md "Tasarım notları"): SettingsRoute's
    // `@ReplaceTo(LoginRoute, clearUpTo = DashboardRoute::class, inclusive = true, name = "logout")`
    // purges the stack down to (and including) Dashboard's nearest occurrence, which — starting from
    // [Login, Dashboard, Profile, Settings] — leaves only the ORIGINAL Login entry, and THEN
    // `replaceTo` always pushes a fresh (non-singleTop) route. The real runtime outcome is therefore
    // a STACKED SECOND Login entry, not a dedup back-to-the-existing-Login. This is a pinned,
    // deliberately-not-"fixed" finding: the sample graph is left as authored (S1), and this test
    // documents the actual behavior rather than an assumed one.
    @Test fun logoutClearUpToDashboardInclusive_stacksASecondLoginEntry() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginRoute, topology = gezginTopology)
        nav.raw.navigate(HomeGraph.DashboardRoute)
        nav.raw.navigate(ProfileGraph.ProfileRoute)
        nav.raw.navigate(ProfileGraph.SettingsRoute)
        assertEquals(
            listOf(AuthGraph.LoginRoute, HomeGraph.DashboardRoute, ProfileGraph.ProfileRoute, ProfileGraph.SettingsRoute),
            nav.backStack,
        )

        nav.settings().logout()

        assertEquals(listOf(AuthGraph.LoginRoute, AuthGraph.LoginRoute), nav.backStack)
    }

    // (d) Profile → launchPickAvatar (flow-mode @GoForResult) → PickSource → Crop → quitWith:
    // quitWith targets the NEAREST ENCLOSING ResultFlow (AvatarFlow, spec §6) — its own contiguous
    // segment [PickSource, Crop] is torn down, Profile survives on top, and the Value is delivered
    // to the flow-entry's own pending slot (i.e. Profile's `pickAvatarResults`).
    @Test fun avatarFlowQuitWith_deliversValueToProfilePickAvatarResults() = runTest {
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileRoute, topology = gezginTopology)
        val profile = nav.profile()

        profile.launchPickAvatar()
        nav.pickSource().goToCrop("gallery")
        nav.crop().quitWith(AvatarChoice("file://avatar.png"))

        assertEquals(ProfileGraph.ProfileRoute, nav.current)
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
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileRoute, topology = gezginTopology)
        nav.profile().launchPickAvatar()
        nav.pickSource().goToCrop("camera")
        nav.crop().goToZoom()
        assertEquals(ProfileGraph.AvatarFlow.ZoomFlow.ZoomRoute, nav.current)

        nav.zoom().back()

        assertEquals(ProfileGraph.AvatarFlow.CropRoute("camera"), nav.current)
        assertEquals(
            listOf(ProfileGraph.ProfileRoute, ProfileGraph.AvatarFlow.PickSourceRoute, ProfileGraph.AvatarFlow.CropRoute("camera")),
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
        val nav = GezginTestNavigator(start = ProfileGraph.ProfileRoute, topology = gezginTopology)
        val profile = nav.profile()

        profile.launchPickAvatar()
        nav.pickSource().goToCrop("gallery")
        nav.crop().goToZoom()
        assertEquals(ProfileGraph.AvatarFlow.ZoomFlow.ZoomRoute, nav.current)

        nav.zoom().quitWith(AvatarChoice("zoomed://frame"))

        assertEquals(listOf(ProfileGraph.ProfileRoute), nav.backStack)
        assertEquals(
            NavResult.Value(AvatarChoice("zoomed://frame")),
            profile.pickAvatarResults.first(),
        )
    }

    // (f) R2: `goToRelated` (singleTop=false, named 2nd edge to the SAME target) never dedups —
    // each call mints a genuinely new stack entry (distinct id), even though the route VALUE
    // (ItemDetailRoute("item-1")) repeats three times in `backStack`.
    @Test fun goToRelatedTwiceSameId_createsThreeDistinctStackEntries() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardRoute, topology = gezginTopology)

        nav.dashboard().goToItemDetail("item-1")
        val firstId = nav.raw.currentEntryId
        nav.itemDetail().goToRelated("item-1")
        val secondId = nav.raw.currentEntryId
        nav.itemDetail().goToRelated("item-1")
        val thirdId = nav.raw.currentEntryId

        assertEquals(3, setOf(firstId, secondId, thirdId).size)
        assertEquals(
            listOf(
                HomeGraph.DashboardRoute,
                HomeGraph.ItemDetailRoute("item-1"),
                HomeGraph.ItemDetailRoute("item-1"),
                HomeGraph.ItemDetailRoute("item-1"),
            ),
            nav.backStack,
        )
    }

    // (g) Screen-mode @GoForResult consumed via the suspend `goToXForResult` pattern (Dashboard's
    // real-screen counterpart): the call pushes synchronously then suspends on the result; delivering
    // via `deliverResult` (raw `backWithResult`) on the still-top dialog resumes it with the Value.
    @Test fun forgotPasswordSuspendResult_deliversValue() = runTest {
        val nav = GezginTestNavigator(start = AuthGraph.LoginRoute, topology = gezginTopology)
        val login = nav.login()

        val resultDeferred = async { login.goToForgotPasswordDialogForResult(null) }
        runCurrent()
        assertEquals(AuthGraph.ForgotPasswordDialog(null), nav.current)

        nav.deliverResult(true)

        assertEquals(NavResult.Value(true), resultDeferred.await())
    }

    // (h) Screen-mode @GoForResult, the CANCELED path: plain `back()` on a still-pending
    // ResultRoute (no `backWithResult`) delivers `NavResult.Canceled` to the awaiting suspend call
    // — mirrors (g) but exercises the "user dismissed without an answer" branch.
    @Test fun forgotPasswordBack_deliversCanceled() = runTest {
        val nav = GezginTestNavigator(start = AuthGraph.LoginRoute, topology = gezginTopology)
        val login = nav.login()

        val resultDeferred = async { login.goToForgotPasswordDialogForResult(null) }
        runCurrent()
        assertEquals(AuthGraph.ForgotPasswordDialog(null), nav.current)

        nav.forgotPassword().back()

        assertEquals(NavResult.Canceled, resultDeferred.await())
    }

    // (i) `@BackToStart` on `TermsRoute`: pops back to (not past) the flow's own
    // `@StartDestination` (`CredentialsRoute`) — the flow itself SURVIVES, only its interior
    // (ProfileInfo, Terms) is discarded; Login (outside the flow) is untouched below it.
    @Test fun termsBackToStart_landsOnCredentialsKeepsLoginBelow() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()

        nav.terms().backToStart()

        assertEquals(
            listOf(AuthGraph.LoginRoute, AuthGraph.SignUpFlow.CredentialsRoute),
            nav.backStack,
        )
    }

    // (j) `@Quit` on `TermsRoute`: a flow-entry `quit()` tears down the WHOLE SignUpFlow segment
    // (Canceled to any pending caller) and leaves Login exposed on top — contrast with (i), where
    // the same route's OTHER exit annotation keeps the flow alive.
    @Test fun termsQuit_tearsDownSignUpFlowLeavesLoginOnTop() {
        val nav = GezginTestNavigator(start = AuthGraph.LoginRoute, topology = gezginTopology)
        nav.login().goToSignUp()
        nav.credentials().goToProfileInfo("ada@example.com")
        nav.profileInfo().goToTerms()

        nav.terms().quit()

        assertEquals(listOf(AuthGraph.LoginRoute), nav.backStack)
    }

    // (k) `@BackTo` (`ItemDetailRoute` → `DashboardRoute`) built ENTIRELY through generated
    // navigators (no `raw.navigate`), AND pinning the cross-graph B1 edge
    // (`DashboardRoute.goToProfile()` → `ProfileGraph.ProfileRoute`) that every other test exercises
    // only by starting `GezginTestNavigator` directly on `ProfileRoute` — here it is actually invoked.
    @Test fun backToDashboardThenCrossGraphGoToProfile_bothViaGeneratedNavigators() {
        val nav = GezginTestNavigator(start = HomeGraph.DashboardRoute, topology = gezginTopology)

        nav.dashboard().goToItemDetail("item-1")
        assertEquals(HomeGraph.ItemDetailRoute("item-1"), nav.current)

        nav.itemDetail().backToDashboard()
        assertEquals(listOf(HomeGraph.DashboardRoute), nav.backStack)

        nav.dashboard().goToProfile()
        assertEquals(
            listOf(HomeGraph.DashboardRoute, ProfileGraph.ProfileRoute),
            nav.backStack,
        )
    }
}
