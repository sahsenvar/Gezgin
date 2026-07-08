package dev.gezgin.core.compose

import dev.gezgin.core.NavResult
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.DialogDefault
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.testTopology
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 4.1 (§7) — dismiss→Canceled saf-JVM pini (Compose runtime GEREKMEZ). Dialog scene'in
 * `onDismissRequest`'i (tap-outside/Esc/geri-izin-varken) NavDisplay.onBack'e = Gezgin [gezginOnBack]'e
 * bağlıdır (4.0 raporu §2/§6); [gezginOnBack] top `@NoBack` DEĞİLSE `navigator.back()` çağırır. Bir
 * ResultRoute dialog (pending-target) için `back()` = pop + caller'a `Canceled` (mevcut `settleRemoved`
 * yolu). Bu test dismiss'in bu zinciri uçtan uca `Canceled` ürettiğini kanıtlar — dialog scene'in
 * pop'unun result muhasebesini atlamadığının garantisi.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GezginDialogDismissTest {

    @Test
    fun `ResultRoute dialog dismiss (gezginOnBack) - caller Canceled alir`() = runTest {
        val nav = RawNavigator(start = Feed, topology = testTopology)
        val scope = GezginEntryScope().apply {
            register<Feed> { }
            register<DialogDefault>(kind = EntryKind.DIALOG) { }   // ResultRoute-benzeri: pending target
        }
        val callerId = nav.currentEntryId

        // Dialog'u result isteğiyle push et (caller pending → dialog = pending target).
        nav.launchForResult(callerId, edgeId = "Feed→Dialog", route = DialogDefault("d"))
        assertEquals(DialogDefault("d"), nav.current)

        // DISMISS = dialog scene onDismissRequest → NavDisplay.onBack = gezginOnBack.
        gezginOnBack(nav, scope).invoke()

        // Pop olmuş + caller Canceled almış olmalı.
        assertEquals(Feed, nav.current, "dismiss dialog'u pop etmeli")
        val result = nav.results<String>(callerId, "Feed→Dialog").first()
        assertEquals(NavResult.Canceled, result, "dismiss → Canceled teslim edilmeli")
    }

    @Test
    fun `dogrudan back() de ayni Canceled'i uretir (dismiss = back esdegerligi)`() = runTest {
        val nav = RawNavigator(start = Feed, topology = testTopology)
        val callerId = nav.currentEntryId
        nav.launchForResult(callerId, edgeId = "Feed→Dialog", route = DialogDefault("d"))

        nav.back()   // dismiss'in düştüğü raw yol

        assertEquals(Feed, nav.current)
        assertEquals(NavResult.Canceled, nav.results<String>(callerId, "Feed→Dialog").first())
    }
}
