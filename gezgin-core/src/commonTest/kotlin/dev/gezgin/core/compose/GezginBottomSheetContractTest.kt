package dev.gezgin.core.compose

import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.SheetBackDismissable
import dev.gezgin.core.fixtures.SheetCustom
import dev.gezgin.core.fixtures.SheetDefault
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
            ),
            props,
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
    fun `guard - @NoBack + dismissOnBackPress=true BOTTOM_SHEET kurulusu require firlatir`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            scope().toNavEntry(GezginKey(route = SheetBackDismissable, id = 4L), navigator, navTransitions {})
        }
        assertTrue(ex.message?.contains("dismissOnBackPress") == true, "guard mesaji: ${ex.message}")
    }

    @Test
    fun `guard - @NoBack + dismissOnBackPress=false BOTTOM_SHEET kurulusu GECER`() {
        val props = sheetPropsOf(SheetNoBackCompatible, 5L)
        assertTrue(!props.dismissOnBackPress)
    }
}
