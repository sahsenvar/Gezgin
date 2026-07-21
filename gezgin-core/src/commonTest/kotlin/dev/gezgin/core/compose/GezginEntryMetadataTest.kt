@file:OptIn(dev.gezgin.core.ExperimentalGezginMigrationApi::class)

package dev.gezgin.core.compose

import androidx.navigation3.ui.NavDisplay
import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.BottomSheetDragHandleMode
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.ScreenBackOnlyTransition
import dev.gezgin.core.fixtures.ScreenOwnTransition
import dev.gezgin.core.fixtures.SheetDismissConfig
import dev.gezgin.core.fixtures.SheetDragHandleConfig
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task 3.5 fix (review) — ayırt edici per-entry metadata testleri: transition cascade artık `NavDisplay`
 * parametrelerinden DEĞİL, entry'nin KENDİ `NavEntry.metadata`'sından iner (pop B→A'da B'nin `backward{}`
 * spec'inin kullanılabilmesi için — top-route yaklaşımında A'nınki okunuyordu, §9 ihlali).
 *
 * Anahtar assertion'ları decompile'daki string sabitlerine DEĞİL, Nav3'ün PUBLIC sarmalayıcılarının
 * ürettiği gerçek anahtarlara (`NavDisplay.popTransitionSpec { null }.keys` vb.) karşı yapılır — anahtarın
 * iç temsili platforma göre farklı (alpha05: String sabiti, android 1.1.4: `NavMetadataKey.toString()`),
 * sarmalayıcı-üretimi anahtar her platformda kendi NavDisplay'iyle tutarlı.
 */
class GezginEntryMetadataTest {
    private val navigator = RawNavigator(start = Feed, topology = testTopology)

    private val forwardKey = NavDisplay.transitionSpec { null }.keys.single()
    private val popKey = NavDisplay.popTransitionSpec { null }.keys.single()
    private val predictiveKey = NavDisplay.predictivePopTransitionSpec { _ -> null }.keys.single()

    private fun scope() = GezginEntryScope().apply {
        register<Feed> { }
        register<ScreenBackOnlyTransition> { }
        register<ScreenOwnTransition> { }
        register<SheetDismissConfig>(kind = EntryKind.BOTTOM_SHEET) { }
        register<SheetDragHandleConfig>(kind = EntryKind.BOTTOM_SHEET) { }
    }

    @Test
    fun `back-only route'un entry metadata'sinda popTransitionSpec anahtari VAR, forward YOK`() {
        val entry = scope().toNavEntry(
            GezginKey(route = ScreenBackOnlyTransition, id = 2L), navigator, navTransitions {},
        )
        assertTrue(popKey in entry.metadata, "pop anahtari bekleniyordu; actual: ${entry.metadata.keys}")
        assertTrue(forwardKey !in entry.metadata, "forward anahtari OLMAMALI; actual: ${entry.metadata.keys}")
    }

    @Test
    fun `predictive yazilmamis backward'li route'ta predictive anahtari backward fallback'iyle VAR (§9)`() {
        val entry = scope().toNavEntry(
            GezginKey(route = ScreenBackOnlyTransition, id = 2L), navigator, navTransitions {},
        )
        assertTrue(
            predictiveKey in entry.metadata,
            "predictive=null iken back'ten dolmali (§9); actual: ${entry.metadata.keys}",
        )
    }

    @Test
    fun `transition'siz route'un (app default'u da yokken) entry metadata'si BOS`() {
        val entry = scope().toNavEntry(GezginKey(route = Feed, id = 1L), navigator, navTransitions {})
        assertTrue(entry.metadata.isEmpty(), "bos metadata bekleniyordu; actual: ${entry.metadata.keys}")
    }

    @Test
    fun `forward-only route'ta forward anahtari VAR, pop ve predictive YOK (null alan anahtar eklemez)`() {
        val entry = scope().toNavEntry(
            GezginKey(route = ScreenOwnTransition, id = 3L), navigator, navTransitions {},
        )
        assertTrue(forwardKey in entry.metadata, "forward anahtari bekleniyordu; actual: ${entry.metadata.keys}")
        assertTrue(popKey !in entry.metadata, "pop anahtari OLMAMALI; actual: ${entry.metadata.keys}")
        assertTrue(predictiveKey !in entry.metadata, "predictive anahtari OLMAMALI (back da yok); actual: ${entry.metadata.keys}")
    }

    @Test
    fun `transition'siz route app default'u varsa ONUN metadata'sini tasir (cascade app basamagi)`() {
        val entry = scope().toNavEntry(
            GezginKey(route = Feed, id = 1L), navigator,
            navTransitions { forward { error("spec cagirilmadan sadece anahtar kontrolu") } },
        )
        assertTrue(forwardKey in entry.metadata, "app-default forward anahtari bekleniyordu; actual: ${entry.metadata.keys}")
    }

    @Test
    fun `bottom sheet metadata yalniz sheetGesturesEnabled degisince esit degildir`() {
        fun props(gesturesEnabled: Boolean): GezginBottomSheetProps {
            val route = SheetDismissConfig(
                backDismiss = false,
                outsideDismiss = true,
                gesturesEnabled = gesturesEnabled,
            )
            val entry = scope().toNavEntry(GezginKey(route = route, id = 10L), navigator, navTransitions {})
            return entry.metadata.getValue(GEZGIN_BOTTOM_SHEET_KEY) as GezginBottomSheetProps
        }

        assertTrue(props(true) != props(false), "gesture alanı metadata props equality'sine katılmalı")
    }

    @Test
    fun `bottom sheet metadata yalniz dragHandleMode degisince esit degildir`() {
        fun props(mode: BottomSheetDragHandleMode): GezginBottomSheetProps {
            val entry = scope().toNavEntry(
                GezginKey(route = SheetDragHandleConfig(mode), id = 11L),
                navigator,
                navTransitions {},
            )
            return entry.metadata.getValue(GEZGIN_BOTTOM_SHEET_KEY) as GezginBottomSheetProps
        }

        assertTrue(props(BottomSheetDragHandleMode.Default) != props(BottomSheetDragHandleMode.None))
    }
}
