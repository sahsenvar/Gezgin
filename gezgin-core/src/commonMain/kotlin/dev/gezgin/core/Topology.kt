@file:OptIn(GezginInternalApi::class)

package dev.gezgin.core

import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

/**
 * One flow segment in a route's enclosing chain. Constructed only by generated topology code
 * ([GezginTopology]), so the constructor is gated behind [GezginInternalApi] (M2); the type itself and
 * its [id]/[isResultFlow] stay public because [GezginTopology.flowChain] returns `List<FlowType>` (a
 * devtools/introspection value). Not a `data class` — `copy`/`componentN` are not part of the ABI.
 *
 * @author @sahsenvar
 */
public class FlowType @GezginInternalApi constructor(
    /** The generated stable identifier of the flow type. */
    public val id: String,
    /** Whether this flow produces a result for its caller. */
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
    /** The generated stable identifier of the navigation edge. */
    public val id: String,
    /** The serializer for result payloads, or `null` when the edge carries no result. */
    public val resultSerializer: KSerializer<*>?,
)

/**
 * The generated, loadable navigation topology. Constructed only by generated code (`GezginGenerated.kt`),
 * so the constructor is gated behind [GezginInternalApi] (M2); [flowChain]/[startOf] stay public for
 * introspection, while [edges] is `internal` (read only by the same-module runtime).
 *
 * @author @sahsenvar
 */
public class GezginTopology @GezginInternalApi constructor(
    private val flowChains: Map<KClass<out Route>, List<FlowType>>,
    private val flowStarts: Map<String, KClass<out Route>>,
    internal val edges: Map<String, EdgeSpec>,
) {
    /** Returns the outer-to-inner flow chain enclosing [route]. */
    public fun flowChain(route: KClass<out Route>): List<FlowType> {
        return flowChains[route] ?: emptyList()
    }

    /** Returns the start route registered for [flowTypeId]. */
    public fun startOf(flowTypeId: String): KClass<out Route> {
        return flowStarts.getValue(flowTypeId)
    }
}
