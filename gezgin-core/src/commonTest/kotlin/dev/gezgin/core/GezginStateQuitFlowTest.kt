package dev.gezgin.core

import dev.gezgin.core.fixtures.*
import kotlin.test.*

class GezginStateQuitFlowTest {
    @Test fun quitPopsOnlyOwnInstance_evenWhenContiguous() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed)
        s.push(Cart, enterFlow = true); s.push(Payment)                          // instance 1
        s.push(Cart, enterFlow = true, singleTop = false); s.push(Payment, singleTop = false) // instance 2 (bitişik!)
        val inner = s.currentFlowId()!!
        val removed = s.quitFlow(inner)!!
        assertEquals(2, removed.size)                                            // yalnız iç instance
        assertEquals(Payment, s.stack.last().route)                              // dış instance duruyor
    }
    @Test fun quitOnRootFlowReturnsNull_stackIntact() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Cart, enterFlow = true); s.push(Payment)                          // flow = root
        assertNull(s.quitFlow(s.currentFlowId()!!))
        assertEquals(2, s.stack.size)
    }

    @Test fun nestedFlowQuitOnlyQuitsInnerFlow() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed)
        s.push(Cart, enterFlow = true); s.push(Payment)                          // checkoutFlow instance
        val outerFlowId = s.currentFlowId()!!
        s.push(Otp, enterFlow = true)                                            // payAuthFlow nested
        val innerFlowId = s.currentFlowId()!!

        // Quit inner flow only
        val removed = s.quitFlow(innerFlowId)!!
        assertEquals(1, removed.size)                                            // only Otp removed
        assertEquals(Otp, removed.single().route)

        // Outer flow and Payment still there
        assertEquals(3, s.stack.size)
        assertEquals(Payment, s.stack.last().route)
        assertEquals(outerFlowId, s.currentFlowId())                             // back to outer flow
    }

    @Test fun quitOuterFlowFromNestedPopsAll() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed)
        s.push(Cart, enterFlow = true); s.push(Payment)                          // checkoutFlow instance 1
        val outerFlowId = s.currentFlowId()!!
        s.push(Otp, enterFlow = true)                                            // payAuthFlow nested (flowPath has outerFlowId)

        // Quit outer flow (from within nested context)
        val removed = s.quitFlow(outerFlowId)!!
        assertEquals(3, removed.size)                                            // Cart, Payment, Otp all removed

        // Only Feed left
        assertEquals(1, s.stack.size)
        assertEquals(Feed, s.stack.last().route)
        assertNull(s.currentFlowId())                                            // back to non-flow context
    }

    @Test fun quitUnknownFlowIdReturnsNull_noMutation() {
        val s = GezginState(emptyList(), 0, testTopology)
        s.push(Feed)
        s.push(Cart, enterFlow = true); s.push(Payment)
        val beforeSize = s.stack.size

        // Try to quit non-existent flow id
        assertNull(s.quitFlow(999L))

        // Stack unchanged
        assertEquals(beforeSize, s.stack.size)
        assertEquals(Payment, s.stack.last().route)
    }
}
