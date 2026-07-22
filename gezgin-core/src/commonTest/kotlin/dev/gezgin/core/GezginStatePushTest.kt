package dev.gezgin.core

import dev.gezgin.core.fixtures.*
import kotlin.test.*

class GezginStatePushTest {
  private fun state(vararg routes: Route): GezginState {
    val s = GezginState(emptyList(), nextId = 0, topology = testTopology)
    routes.forEach {
      s.push(
        it,
        enterFlow =
          testTopology.flowChain(it::class).isNotEmpty() &&
            s.stack.none { k -> testTopology.flowChain(k.route::class).isNotEmpty() },
      )
    }
    return s
  }

  @Test
  fun idsAreMonotonic_andDuplicateValuesGetDistinctIds() {
    val s = GezginState(emptyList(), 0, testTopology)
    s.push(Product("42"), singleTop = false)
    s.push(Feed)
    s.push(Product("42"), singleTop = false)
    assertEquals(listOf(0L, 1L, 2L), s.stack.map { it.id })
  }

  @Test
  fun singleTopDedupsOnlyTop() {
    val s = state(Feed)
    assertNull(s.push(Feed)) // top eşit → dedup
    s.push(Product("1"))
    assertNotNull(s.push(Feed)) // ortadaki eşe dokunma (§4.1)
  }

  @Test
  fun enteringFlowMintsInstanceId_membersInherit() {
    val s = state(Feed)
    val cart = s.push(Cart, enterFlow = true)!! // container-entry → start push
    val pay = s.push(Payment)!! // flow-içi @GoTo
    assertEquals(1, cart.flowPath.size)
    assertEquals(cart.flowPath, pay.flowPath) // miras
  }

  @Test
  fun reentryMintsNewInstance() {
    val s = state(Feed)
    val first = s.push(Cart, enterFlow = true)!!
    s.push(Payment)
    val second = s.push(Cart, enterFlow = true, singleTop = false)!! // yeniden giriş
    assertNotEquals(first.flowPath, second.flowPath) // re-entrancy sınırı (§8.1)
  }

  @Test
  fun externalRoundTripTargetHasEmptyFlowPath() {
    val s = state(Feed)
    s.push(Cart, enterFlow = true)
    assertTrue(s.push(Product("x"))!!.flowPath.isEmpty()) // dış hedef miras almaz
  }

  @Test
  fun nestedFlowEntryPreservesAncestorAndMintsOwnSegment() {
    val s = GezginState(emptyList(), 0, testTopology)
    s.push(Feed)
    val cart = s.push(Cart, enterFlow = true)!!
    val otp = s.push(Otp, enterFlow = true)!! // nested giriş
    assertEquals(2, otp.flowPath.size)
    assertEquals(cart.flowPath.single(), otp.flowPath.first()) // ata instance korunur
    assertNotEquals(otp.flowPath[0], otp.flowPath[1])
  }

  @Test
  fun siblingNestedPushDoesNotInheritForeignInstanceId() {
    val s = GezginState(emptyList(), 0, testTopology)
    s.push(Feed)
    s.push(Cart, enterFlow = true)
    val otp = s.push(Otp, enterFlow = true)!! // [ckt, payAuth]
    val gift = s.push(GiftPick)!! // raw-seviye: kardeş nested üyesine direkt push
    assertEquals(2, gift.flowPath.size)
    assertEquals(otp.flowPath.first(), gift.flowPath.first()) // ortak ata korunur
    assertNotEquals(otp.flowPath[1], gift.flowPath[1]) // yabancı instance MİRAS ALINMAZ
  }
}
