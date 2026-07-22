package dev.gezgin.processor.mvi

import dev.gezgin.processor.entry.EntryFunctionModel

/**
 * Deterministic, line-based textual dump of the MVI model — the [ViewModelModel] list plus the
 * MVI-mode subset of the resolved [EntryFunctionModel]s — written by the processor under the
 * `gezgin.dumpMvi=true` KSP option for test assertions (mirrors
 * [dev.gezgin.processor.model.dumpText]).
 *
 * Each `vm` line dumps S/I/E BOTH as flattened FQ and as KotlinPoet `TypeName` (`fq|typeName`) so a
 * golden test can prove the [com.squareup.kotlinpoet.TypeName] capture does NOT flatten generics
 * (the Sample-Showcase regression class): a `List<String>` state arg dumps as
 * `kotlin.collections.List|kotlin.collections.List<kotlin.String>`. It also dumps the DI-detection
 * (`di=`, `factory=`) and primary-ctor params (`ctor=name:typeFq[(di)],…`) so the
 * DI-default-resolver decision has model-level golden coverage alongside the codegen golden.
 *
 * Lines are sorted by owning FQ so the output never depends on KSP traversal order.
 */
internal fun dumpMviText(
  vmModels: List<ViewModelModel>,
  entries: List<EntryFunctionModel>,
): String {
  val lines = mutableListOf<String>()

  vmModels
    .sortedBy { it.vmFq }
    .forEach { vm ->
      lines +=
        "vm ${vm.vmFq} route=${vm.routeFq} " +
          "state=${vm.stateTypeFq}|${vm.stateTypeName} " +
          "intent=${vm.intentTypeFq}|${vm.intentTypeName} " +
          "effect=${vm.effectTypeFq}|${vm.effectTypeName} " +
          "pkg=${vm.packageName} " +
          "di=${vm.di} factory=${vm.assistedFactoryFq ?: "-"} " +
          "ctor=${vm.ctorParams.joinToString(",") { "${it.name}:${it.typeFq}${if (it.diAnnotated) "(di)" else ""}" }.ifEmpty { "-" }}"
    }

  entries
    .filter { it.mvi != null }
    .sortedBy { it.routeFq }
    .forEach { entry ->
      val mvi = entry.mvi!!
      lines +=
        "mvientry ${entry.functionSimpleName} pkg=${entry.packageName} route=${entry.routeFq} " +
          "kind=${entry.kind} x=${entry.x} noBack=${entry.noBack} vm=${mvi.vm.vmFq} " +
          "effect=${mvi.effectFunSimpleName ?: "-"} effectNav=${mvi.effectHasNavParam} " +
          "top=${mvi.topBar?.functionSimpleName ?: "-"} bottom=${mvi.bottomBar?.functionSimpleName ?: "-"} " +
          "role=${mvi.roleExtraParams.joinToString(",") { "${it.name}:${it.typeName}" }.ifEmpty { "-" }} " +
          "resolver=${mvi.resolverExtraParams.joinToString(",") { "${it.name}:${it.typeName}" }.ifEmpty { "-" }}"
    }

  return lines.joinToString("\n")
}
