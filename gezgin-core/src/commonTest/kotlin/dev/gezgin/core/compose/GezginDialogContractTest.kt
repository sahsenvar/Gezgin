package dev.gezgin.core.compose

import androidx.compose.ui.window.DialogProperties
import dev.gezgin.core.GezginKey
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.ConditionalBackDialog
import dev.gezgin.core.fixtures.DialogBackDismissable
import dev.gezgin.core.fixtures.DialogCustom
import dev.gezgin.core.fixtures.DialogDefault
import dev.gezgin.core.fixtures.DialogNoBackCompatible
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.FullModal
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 4.1 (§7) — adapter'ın modal contract okuması + guard'ının saf pini (Compose runtime
 * GEREKMEZ): [toNavEntry] `NavEntry.metadata`'sına `DialogSceneStrategy.dialog(props)` yazarken
 * `props`'un route'un opsiyonel
 * [dev.gezgin.core.DialogContract]/[dev.gezgin.core.FullscreenModalContract]'ından (runtime değer,
 * §2.4) DOĞRU geldiğini doğrular. Assertion metadata'daki [DialogProperties] instance'ına karşı
 * (anahtarın iç temsili platforma göre farklı → değere göre süz, ada bağlanma).
 */
class GezginDialogContractTest {
  private val navigator = RawNavigator(start = Feed, topology = testTopology)

  private fun scope() =
    GezginEntryScope().apply {
      register<Feed> {}
      register<DialogDefault>(kind = EntryKind.DIALOG) {}
      register<DialogCustom>(kind = EntryKind.DIALOG) {}
      register<FullModal>(kind = EntryKind.FULLSCREEN_MODAL) {}
      register<DialogBackDismissable>(kind = EntryKind.DIALOG, noBack = true) {}
      register<DialogNoBackCompatible>(kind = EntryKind.DIALOG, noBack = true) {}
      register<ConditionalBackDialog>(kind = EntryKind.DIALOG, noBack = true) {}
    }

  private fun dialogPropsOf(route: dev.gezgin.core.Route, id: Long): DialogProperties {
    val entry = scope().toNavEntry(GezginKey(route = route, id = id), navigator, navTransitions {})
    return entry.metadata.values.filterIsInstance<DialogProperties>().single()
  }

  @Test
  fun `SCREEN kind entry metadata'sinda DialogProperties YOK`() {
    val entry = scope().toNavEntry(GezginKey(route = Feed, id = 1L), navigator, navTransitions {})
    assertNull(entry.metadata.values.filterIsInstance<DialogProperties>().firstOrNull())
  }

  @Test
  fun `DialogContract'siz DIALOG - tip-varsayilan DialogProperties (hepsi true)`() {
    val props = dialogPropsOf(DialogDefault("x"), 2L)
    assertEquals(DialogProperties(true, true, true), props)
  }

  // Minor (contract-default lockstep pin): adapter'ın `?: true` literalleri [DialogContract]'ın
  // interface
  // default'larıyla AYNI kalsın (drift önle). Contract'sız DIALOG'un çözülen props'u == tüm-default
  // DialogContract'ın getter'larından kurulan props. Adapter default'u değişir ama interface
  // değişmezse
  // (ya da tersi) bu test kırılır.
  @Test
  fun `contract-default lockstep - contract'siz DIALOG == tum-default DialogContract`() {
    val allDefault = object : dev.gezgin.core.DialogContract {}
    assertEquals(
      DialogProperties(
        dismissOnBackPress = allDefault.dismissOnBackPress,
        dismissOnClickOutside = allDefault.dismissOnClickOutside,
        usePlatformDefaultWidth = allDefault.usePlatformDefaultWidth,
      ),
      dialogPropsOf(DialogDefault("x"), 20L),
    )
  }

  @Test
  fun `DialogContract'li DIALOG - property'ler contract'tan iner`() {
    val props = dialogPropsOf(DialogCustom("x"), 3L)
    assertEquals(
      DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
      ),
      props,
    )
  }

  @Test
  fun `FULLSCREEN_MODAL - usePlatformDefaultWidth SABIT false, dismiss'ler contract'tan`() {
    val props = dialogPropsOf(FullModal, 4L)
    assertTrue(!props.usePlatformDefaultWidth, "tam-ekran = usePlatformDefaultWidth false")
    assertTrue(!props.dismissOnClickOutside, "contract dismissOnClickOutside=false inmeli")
    assertTrue(props.dismissOnBackPress, "contract vermezse dismissOnBackPress default true")
  }

  @Test
  fun `guard - @NoBack + dismissOnBackPress=true DIALOG kurulusu require firlatir`() {
    val ex =
      assertFailsWith<IllegalArgumentException> {
        scope()
          .toNavEntry(
            GezginKey(route = DialogBackDismissable, id = 5L),
            navigator,
            navTransitions {},
          )
      }
    assertTrue(ex.message?.contains("dismissOnBackPress") == true, "guard mesaji: ${ex.message}")
  }

  @Test
  fun `guard - @NoBack + dismissOnBackPress=false DIALOG kurulusu GECER`() {
    val props = dialogPropsOf(DialogNoBackCompatible, 6L)
    assertTrue(!props.dismissOnBackPress)
  }

  // --- Minor: guard KOŞULLU (ctor-param'dan beslenen) dismissOnBackPress yolunu da pinle. Guard'ın
  // sabit-override değil property GETTER'ından (= instance-değeri) okuduğunun kanıtı: AYNI route
  // tipi,
  // farklı ctor-param → biri fırlatır, diğeri geçer.
  @Test
  fun `guard - @NoBack + KOSULLU dismissOnBackPress=true (ctor-param) require firlatir`() {
    val ex =
      assertFailsWith<IllegalArgumentException> {
        scope()
          .toNavEntry(
            GezginKey(route = ConditionalBackDialog(backDismiss = true), id = 7L),
            navigator,
            navTransitions {},
          )
      }
    assertTrue(ex.message?.contains("dismissOnBackPress") == true, "guard mesaji: ${ex.message}")
  }

  @Test
  fun `guard - @NoBack + KOSULLU dismissOnBackPress=false (ctor-param) GECER`() {
    val props = dialogPropsOf(ConditionalBackDialog(backDismiss = false), 8L)
    assertTrue(
      !props.dismissOnBackPress,
      "ctor-param false → guard geçmeli, props dismissOnBackPress false",
    )
  }
}
