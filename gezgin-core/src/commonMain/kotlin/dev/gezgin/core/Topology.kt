package dev.gezgin.core

import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

data class FlowType(val id: String, val isResultFlow: Boolean)

data class EdgeSpec(val id: String, val resultSerializer: KSerializer<*>?)

class GezginTopology(
    private val flowChains: Map<KClass<out Route>, List<FlowType>>,
    private val flowStarts: Map<String, KClass<out Route>>,
    val edges: Map<String, EdgeSpec>,
) {
    fun flowChain(route: KClass<out Route>): List<FlowType> {
        return flowChains[route] ?: emptyList()
    }

    fun startOf(flowTypeId: String): KClass<out Route> {
        return flowStarts.getValue(flowTypeId)
    }
}
