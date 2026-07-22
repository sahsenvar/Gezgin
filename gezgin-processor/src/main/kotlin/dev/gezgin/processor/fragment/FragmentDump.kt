package dev.gezgin.processor.fragment

/**
 * Deterministic, line-based textual dump of the Fragment model — the [FragmentEntryModel] list —
 * written by the processor under the test-only `gezgin.dumpFragment=true` KSP option for behavioral
 * assertions (mirrors [dev.gezgin.processor.mvi.dumpMviText] /
 * [dev.gezgin.processor.model.dumpText]).
 *
 * This dump is the golden surface asserted by model-reader tests. Lines are sorted by owning
 * Fragment FQ so the output never depends on KSP traversal order.
 */
internal fun dumpFragmentText(models: List<FragmentEntryModel>): String =
  models
    .sortedBy { it.fragmentFq }
    .joinToString("\n") { m ->
      "fragment ${m.fragmentFq} route=${m.routeFq} pkg=${m.packageName} " +
        "routePkg=${m.routePackageName} x=${m.x} noBack=${m.noBack}"
    }
