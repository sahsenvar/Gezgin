package dev.gezgin.test.fixtures

import dev.gezgin.core.EdgeSpec
import dev.gezgin.core.FlowType
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.Route
import kotlinx.serialization.Serializable

/**
 * Minimal local copy of gezgin-core's commonTest fixture (`gezgin-core/.../fixtures/TestGraph.kt`) —
 * that fixture lives in gezgin-core's commonTest and is NOT visible from :gezgin-test (separate
 * module, separate test source set). Trimmed to only what this module's tests need. Known
 * trade-off: duplicated with gezgin-core's fixture; a shared test-fixtures module is a Faz 2
 * candidate.
 */
@Serializable sealed interface ShopGraph : Route
@Serializable data object Feed : ShopGraph
@Serializable data object Catalog : ShopGraph
@Serializable data class Product(val id: String) : ShopGraph

@Serializable sealed interface CheckoutFlow : ShopGraph
@Serializable data object Cart : CheckoutFlow
@Serializable data object Payment : CheckoutFlow
@Serializable data class OrderId(val v: String)

val checkoutFlow = FlowType("CheckoutFlow", isResultFlow = true)

val testTopology = GezginTopology(
    flowChains = mapOf(
        Cart::class to listOf(checkoutFlow),
        Payment::class to listOf(checkoutFlow),
    ),
    flowStarts = mapOf(
        "CheckoutFlow" to Cart::class,
    ),
    edges = mapOf(
        "Catalog→CheckoutFlow" to EdgeSpec("Catalog→CheckoutFlow", OrderId.serializer()),
    ),
)
