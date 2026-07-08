package dev.gezgin.sample.shopr.nav

import dev.gezgin.core.ResultFlow
import dev.gezgin.core.Route
import dev.gezgin.core.annotation.BackTo
import dev.gezgin.core.annotation.FlowGraph
import dev.gezgin.core.annotation.GoForResult
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.NavGraph
import dev.gezgin.core.annotation.NoBack
import dev.gezgin.core.annotation.ReplaceTo
import dev.gezgin.core.annotation.StartDestination
import kotlinx.serialization.Serializable

/**
 * Task 3.6 — Shopr mini-sample'ın nav grafiği (docs/gezgin-by-example.md §1-5'in gerçek
 * gezgin-core annotation'larıyla karşılığı). `sample:shopr` gerçek bir Android application modülü
 * (KSP + kotlinx-serialization compiler plugin ikisi de derlemede) — bu yüzden serializers-ON
 * (`gezgin.emitSerializers` default=true) burada CANLI derlenir, kctfork fixture'larında olduğu gibi
 * kapatılmaz (bkz. plan §3.6 deliverable 3.6/Faz-2 devri (e)).
 */
@Serializable
data class OrderId(val value: String)

@NavGraph
interface HomeGraph : Route {

    @GoTo(Catalog::class)
    @Serializable
    data object Feed : HomeGraph

    @GoTo(Product::class)
    @GoForResult(CheckoutFlow::class)
    @ReplaceTo(OrderPlaced::class)
    @Serializable
    data object Catalog : HomeGraph

    @Serializable
    data class Product(val id: String) : HomeGraph

    @NoBack
    @BackTo(Feed::class)
    @Serializable
    data class OrderPlaced(val orderId: String) : HomeGraph
}

@FlowGraph
interface CheckoutFlow : Route, ResultFlow<OrderId> {

    @StartDestination
    @GoTo(Payment::class)
    @Serializable
    data object Cart : CheckoutFlow

    @Serializable
    data object Payment : CheckoutFlow
}
