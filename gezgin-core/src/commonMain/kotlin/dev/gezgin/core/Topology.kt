@file:OptIn(GezginInternalApi::class)

package dev.gezgin.core

import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

/**
 * One flow segment in a route's enclosing chain. Constructed only by generated topology code
 * ([GezginTopology]), so the constructor is gated behind [GezginInternalApi] (M2); the type itself and
 * its [id]/[isResultFlow] stay public because [GezginTopology.flowChain] returns `List<FlowType>` (a
 * devtools/introspection value). Not a `data class` — `copy`/`componentN` are not part of the ABI.
 */
public class FlowType @GezginInternalApi constructor(
    public val id: String,
    public val isResultFlow: Boolean,
)

/**
 * The result-edge descriptor (id + optional result serializer). Constructed and read only by generated
 * topology code and the same-module runtime, so the whole type is gated behind [GezginInternalApi] (M2) —
 * it is no longer referenced by any public signature (`edges` is `internal`), which keeps
 * `kotlinx.serialization` off the public surface.
 */
@GezginInternalApi
public class EdgeSpec(
    public val id: String,
    public val resultSerializer: KSerializer<*>?,
)

/**
 * The generated, loadable navigation topology. Constructed only by generated code (`GezginGenerated.kt`),
 * so the constructor is gated behind [GezginInternalApi] (M2); [flowChain]/[startOf] stay public for
 * introspection, while [edges] is `internal` (read only by the same-module runtime).
 */
public class GezginTopology @GezginInternalApi constructor(
    private val flowChains: Map<KClass<out Route>, List<FlowType>>,
    private val flowStarts: Map<String, KClass<out Route>>,
    internal val edges: Map<String, EdgeSpec>,
) {
    public fun flowChain(route: KClass<out Route>): List<FlowType> {
        return flowChains[route] ?: emptyList()
    }

    public fun startOf(flowTypeId: String): KClass<out Route> {
        return flowStarts.getValue(flowTypeId)
    }
}
