package dev.gezgin.core.compose

import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.SheetBackDismissable
import dev.gezgin.core.fixtures.SheetCustom
import dev.gezgin.core.fixtures.SheetDefault
import dev.gezgin.core.fixtures.SheetDismissConfig
import dev.gezgin.core.fixtures.SheetNoBackCompatible
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 4.2 (§7) — adapter'ın BottomSheet contract okuması + guard'ının saf pini (Compose runtime GEREKMEZ):
 * [toNavEntry] `NavEntry.metadata`'sına [GEZGIN_BOTTOM_SHEET_KEY] altında yazdığı [GezginBottomSheetProps]'un
 * route'un opsiyonel [dev.gezgin.core.BottomSheetContract]'ından (runtime değer, §2.4) DOĞRU geldiğini
 * doğrular. `@NoBack + dismissOnBackPress` guard'ı DIALOG'la ORTAK yardımcıdan geçer (aynı davranış).
 */
class GezginBottomSheetContractTest {
    private val navigator = RawNavigator(start = Feed, topology = testTopology)

    private fun scope() = GezginEntryScope().apply {
        register<Feed> { }
        register<SheetDefault>(kind = EntryKind.BOTTOM_SHEET) { }
        register<SheetCustom>(kind = EntryKind.BOTTOM_SHEET) { }
        register<SheetDismissConfig>(kind = EntryKind.BOTTOM_SHEET, noBack = true) { }
        register<SheetBackDismissable>(kind = EntryKind.BOTTOM_SHEET, noBack = true) { }
        register<SheetNoBackCompatible>(kind = EntryKind.BOTTOM_SHEET, noBack = true) { }
    }

    private fun sheetPropsOf(route: dev.gezgin.core.Route, id: Long): GezginBottomSheetProps {
        val entry = scope().toNavEntry(GezginKey(route = route, id = id), navigator, navTransitions {})
        return entry.metadata.values.filterIsInstance<GezginBottomSheetProps>().single()
    }

    @Test
    fun `SCREEN kind entry metadata'sinda GezginBottomSheetProps YOK`() {
        val entry = scope().toNavEntry(GezginKey(route = Feed, id = 1L), navigator, navTransitions {})
        assertNull(entry.metadata.values.filterIsInstance<GezginBottomSheetProps>().firstOrNull())
    }

    @Test
    fun `BottomSheetContract'siz BOTTOM_SHEET - tip-varsayilan props`() {
        val props = sheetPropsOf(SheetDefault("x"), 2L)
        assertEquals(
            GezginBottomSheetProps(
                skipPartiallyExpanded = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                sheetGesturesEnabled = true,
            ),
            props,
        )
    }

    // Minor (contract-default lockstep pin): adapter'ın `?: ...` literalleri [BottomSheetContract]'ın
    // interface default'larıyla AYNI kalsın (drift önle). Contract'sız sheet'in çözülen props'u ==
    // tüm-default BottomSheetContract'ın getter'larından kurulan props.
    @Test
    fun `contract-default lockstep - contract'siz BOTTOM_SHEET == tum-default BottomSheetContract`() {
        val allDefault = object : dev.gezgin.core.BottomSheetContract {}
        assertEquals(
            GezginBottomSheetProps(
                skipPartiallyExpanded = allDefault.skipPartiallyExpanded,
                dismissOnBackPress = allDefault.dismissOnBackPress,
                dismissOnClickOutside = allDefault.dismissOnClickOutside,
                sheetGesturesEnabled = allDefault.sheetGesturesEnabled,
            ),
            sheetPropsOf(SheetDefault("x"), 20L),
        )
    }

    @Test
    fun `BottomSheetContract'li BOTTOM_SHEET - property'ler contract'tan iner`() {
        val props = sheetPropsOf(SheetCustom("x"), 3L)
        assertEquals(
            GezginBottomSheetProps(
                skipPartiallyExpanded = true,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                sheetGesturesEnabled = false,
            ),
            props,
        )
    }

    @Test
    fun `dismissOnClickOutside - contract'tan iner (Important 1 - scrim-tap knob acikta)`() {
        // Contract false → props false (scrim-tap kapatmaz); contract'sız → default true.
        assertTrue(!sheetPropsOf(SheetCustom("x"), 31L).dismissOnClickOutside, "contract false → false")
        assertTrue(sheetPropsOf(SheetDefault("x"), 32L).dismissOnClickOutside, "contract'sız → default true")
    }

    @Test
    fun `sheetGesturesEnabled default true ve route getter false metadata'ya iner`() {
        val allDefault = object : dev.gezgin.core.BottomSheetContract {}
        assertTrue(allDefault.sheetGesturesEnabled, "contract default true olmalı")
        assertTrue(sheetPropsOf(SheetDefault("x"), 33L).sheetGesturesEnabled, "contract'sız default true")
        assertTrue(!sheetPropsOf(SheetCustom("x"), 34L).sheetGesturesEnabled, "getter false metadata'ya inmeli")
    }

    @Test
    fun `guard - @NoBack contract'siz sheet tip default'lariyla require firlatir`() {
        val noContractScope = GezginEntryScope().apply {
            register<Feed> { }
            register<SheetDefault>(kind = EntryKind.BOTTOM_SHEET, noBack = true) { }
        }
        val ex = assertFailsWith<IllegalArgumentException> {
            noContractScope.toNavEntry(
                GezginKey(route = SheetDefault("x"), id = 4L),
                navigator,
                navTransitions {},
            )
        }
        assertTrue(ex.message?.contains("dismissOnBackPress") == true, "guard mesaji: ${ex.message}")
    }

    @Test
    fun `guard - @NoBack + default BottomSheetContract require firlatir`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            scope().toNavEntry(GezginKey(route = SheetBackDismissable, id = 5L), navigator, navTransitions {})
        }
        assertTrue(ex.message?.contains("dismissOnBackPress") == true, "guard mesaji: ${ex.message}")
    }

    @Test
    fun `guard - back false gestures true require firlatir`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            sheetPropsOf(
                SheetDismissConfig(backDismiss = false, outsideDismiss = false, gesturesEnabled = true),
                6L,
            )
        }
        assertTrue(ex.message?.contains("sheetGesturesEnabled") == true, "guard mesaji: ${ex.message}")
    }

    @Test
    fun `guard - back true gestures false require firlatir`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            sheetPropsOf(
                SheetDismissConfig(backDismiss = true, outsideDismiss = false, gesturesEnabled = false),
                7L,
            )
        }
        assertTrue(ex.message?.contains("dismissOnBackPress") == true, "guard mesaji: ${ex.message}")
    }

    @Test
    fun `guard - back false gestures false GECER ve outside predicate'e katilmaz`() {
        val outsideEnabled = sheetPropsOf(
            SheetDismissConfig(backDismiss = false, outsideDismiss = true, gesturesEnabled = false),
            8L,
        )
        val outsideDisabled = sheetPropsOf(
            SheetDismissConfig(backDismiss = false, outsideDismiss = false, gesturesEnabled = false),
            9L,
        )

        assertTrue(outsideEnabled.dismissOnClickOutside)
        assertTrue(!outsideDisabled.dismissOnClickOutside)
        assertTrue(!sheetPropsOf(SheetNoBackCompatible, 10L).sheetGesturesEnabled)
    }
}
