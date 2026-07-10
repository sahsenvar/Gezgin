package dev.gezgin.processor.fragment

/**
 * Deterministic, line-based textual dump of the Task-6.1 Fragment model — the [FragmentEntryModel] list
 * — written by the processor under the test-only `gezgin.dumpFragment=true` KSP option for behavioral
 * assertions (mirrors [dev.gezgin.processor.mvi.dumpMviText] / [dev.gezgin.processor.model.dumpText]).
 *
 * Since Task 6.1 emits NO codegen (that's Task 6.2), this dump is the golden surface a model-read test
 * asserts against. Lines are sorted by owning Fragment FQ so the output never depends on KSP traversal
 * order.
 */
internal fun dumpFragmentText(models: List<FragmentEntryModel>): String =
    models.sortedBy { it.fragmentFq }.joinToString("\n") { m ->
        "fragment ${m.fragmentFq} route=${m.routeFq} pkg=${m.packageName} " +
            "routePkg=${m.routePackageName} x=${m.x} noBack=${m.noBack}"
    }
