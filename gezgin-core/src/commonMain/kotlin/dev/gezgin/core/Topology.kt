package dev.gezgin.core

import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

public data class FlowType(val id: String, val isResultFlow: Boolean)

public data class EdgeSpec(val id: String, val resultSerializer: KSerializer<*>?)

public class GezginTopology(
    private val flowChains: Map<KClass<out Route>, List<FlowType>>,
    private val flowStarts: Map<String, KClass<out Route>>,
    public val edges: Map<String, EdgeSpec>,
) {
    public fun flowChain(route: KClass<out Route>): List<FlowType> {
        return flowChains[route] ?: emptyList()
    }

    public fun startOf(flowTypeId: String): KClass<out Route> {
        return flowStarts.getValue(flowTypeId)
    }
}
