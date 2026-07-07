package dev.gezgin.processor.codegen

/**
 * The single edge-id format shared by [TopologyCodegen] (which keys `GezginTopology.edges` /
 * `EdgeSpec.id` with it — and thus PD-save's `SavedSlot.edgeId`) and [NavigatorCodegen] (which
 * bakes the same id into every generated `launchForResult`/`results`/`navigateForResult` call).
 * The two sides MUST stay byte-for-byte identical or result slots silently stop resolving, so the
 * format lives here exactly once: `"<SourceSimple>→<TargetSimple>"`, plus `"#<name>"` when the
 * edge carries a disambiguating `name=`.
 */
internal fun edgeId(sourceFq: String, targetFq: String, name: String): String {
    val sourceSimple = sourceFq.substringAfterLast('.')
    val targetSimple = targetFq.substringAfterLast('.')
    val suffix = if (name.isNotEmpty()) "#$name" else ""
    return "$sourceSimple→$targetSimple$suffix"
}
