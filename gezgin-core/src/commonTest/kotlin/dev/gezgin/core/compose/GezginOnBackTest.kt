package dev.gezgin.core.compose

import dev.gezgin.core.RawNavigator
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 3.3 deliverable 3 — `@NoBack` runtime guard'ının ([gezginOnBack]) saf-JVM davranış pini
 * (Compose runtime GEREKMEZ; [GezginNoBackHandler] entry-scoped handler'ı android'de gerçek,
 * desktop'ta no-op olduğu için davranışsal taşıyıcı BUDUR — bkz. PlatformDisplay.kt KDoc).
 *
 * Kurulum: `register<R>(noBack = ...)` content'i burada hiç invoke edilmez (boş @Composable
 * lambda), yalnız registry flag'i okunur.
 */
class GezginOnBackTest {

  private fun nav(onRootBack: () -> Unit = {}) =
    RawNavigator(start = Feed, topology = testTopology, onRootBack = onRootBack)

  @Test
  fun `noBack top entry - onBack pop YAPMAZ (geri yutulur)`() {
    val nav = nav()
    val scope =
      GezginEntryScope().apply {
        register<Feed> {}
        register<Catalog>(noBack = true) {}
      }
    nav.navigate(Catalog) // stack: [Feed, Catalog(noBack)]
    assertEquals(Catalog, nav.current)

    gezginOnBack(nav, scope).invoke() // @NoBack top → yutulur

    assertEquals(Catalog, nav.current, "noBack top iken onBack pop YAPMAMALI")
    assertEquals(2, nav.backStack.value.size)
  }

  @Test
  fun `noBack=false top entry - onBack normal pop yapar`() {
    val nav = nav()
    val scope =
      GezginEntryScope().apply {
        register<Feed> {}
        register<Catalog>(noBack = false) {}
      }
    nav.navigate(Catalog) // stack: [Feed, Catalog]

    gezginOnBack(nav, scope).invoke()

    assertEquals(Feed, nav.current, "noBack=false top iken onBack normal pop yapmalı")
    assertEquals(1, nav.backStack.value.size)
  }

  @Test
  fun `kok muafiyeti - tek entry noBack olsa bile onBack onRootBack tetikler (app'e hapsolmaz)`() {
    var rootBackCount = 0
    // start = Product (noBack kaydedilecek), stack tek entry → kök.
    val nav =
      RawNavigator(start = Product("x"), topology = testTopology, onRootBack = { rootBackCount++ })
    val scope = GezginEntryScope().apply { register<Product>(noBack = true) {} }
    assertEquals(1, nav.backStack.value.size)

    gezginOnBack(nav, scope).invoke() // kök entry: noBack YOK SAYILIR → back() → onRootBack

    assertEquals(1, rootBackCount, "kök noBack entry'de back onRootBack'e düşmeli")
  }
}
