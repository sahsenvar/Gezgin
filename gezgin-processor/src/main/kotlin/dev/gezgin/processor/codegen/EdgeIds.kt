package dev.gezgin.processor.codegen

/**
 * The single edge-id format shared by [TopologyCodegen] (which keys `GezginTopology.edges` /
 * `EdgeSpec.id` with it — and thus PD-save's `SavedSlot.edgeId`) and [NavigatorCodegen] (which
 * bakes the same id into every generated `launchForResult`/`results`/`navigateForResult` call). The
 * two sides MUST stay byte-for-byte identical or result slots silently stop resolving, so the
 * format lives here exactly once: `"<sourceFq>→<targetFq>"`, plus `"#<name>"` when the edge carries
 * a disambiguating `name=`. Fully-qualified (not simple) names keep ids globally unique — two
 * distinct sources sharing a simple name (e.g. `a.Feed` and `b.Feed`) would otherwise collide on
 * the same slot key. These ids are internal state, never part of the visible generated API.
 */
internal fun edgeId(sourceFq: String, targetFq: String, name: String): String {
  val suffix = if (name.isNotEmpty()) "#$name" else ""
  return "$sourceFq→$targetFq$suffix"
}
