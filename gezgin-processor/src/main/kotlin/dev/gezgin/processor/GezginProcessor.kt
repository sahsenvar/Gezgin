package dev.gezgin.processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.ksp.writeTo
import dev.gezgin.processor.codegen.EntryCodegen
import dev.gezgin.processor.codegen.FragmentEntryCodegen
import dev.gezgin.processor.codegen.MviEntryCodegen
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.codegen.NavigatorProbe
import dev.gezgin.processor.codegen.TestApiCodegen
import dev.gezgin.processor.codegen.TopologyCodegen
import dev.gezgin.processor.entry.EntryModelReader
import dev.gezgin.processor.fragment.FragmentModelReader
import dev.gezgin.processor.fragment.dumpFragmentText
import dev.gezgin.processor.model.GraphModelNode
import dev.gezgin.processor.model.ModelReader
import dev.gezgin.processor.model.RouteModel
import dev.gezgin.processor.model.dumpText
import dev.gezgin.processor.mvi.ViewModelModelReader
import dev.gezgin.processor.mvi.dumpMviText

/**
 * Reads the semantic [dev.gezgin.processor.model.GraphModel], validates it via [GezginValidator]
 * (violations fail the compilation as KSP errors), and on a clean model emits `GezginGenerated.kt`
 * (+ `GezginSerializers.kt` unless `gezgin.emitSerializers=false`). Test-only
 * `gezgin.dumpModel=true` writes the model as a deterministic text file instead.
 */
internal class GezginProcessor(private val environment: SymbolProcessorEnvironment) :
  SymbolProcessor {

  private var invoked = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (!invoked) {
      invoked = true
      // `info` KSP logging seviyesine göre yutulabilir (kctfork messageOutputStream) → `warn`
      // fallback.
      environment.logger.info("Gezgin processor alive")
      environment.logger.warn("Gezgin processor alive")

      val model = ModelReader(resolver, environment.logger).read()

      val validationOk = GezginValidator(model, environment.logger).validate()

      if (environment.options["gezgin.dumpModel"].toBoolean()) {
        environment.codeGenerator
          .createNewFile(
            dependencies = Dependencies.ALL_FILES,
            packageName = "",
            fileName = "GezginModelDump",
            extensionName = "txt",
          )
          .use { it.write(model.dumpText().toByteArray()) }
      }

      // Codegen only runs on a clean model — a malformed model can't emit sane code.
      if (validationOk) {
        // GRAPH-derived codegen (topology/serializers/navigators/test accessors) is gated on
        // this module OWNING graphs. A cross-module feature has none (they
        // live in
        // the central `:navigation` module) → this block is skipped, but its `@Screen` ENTRY
        // codegen below still runs.
        if (model.graphs.isNotEmpty()) {
          val packageName = TopologyCodegen.targetPackage(model)
          if (packageName.isEmpty()) {
            // All graph-derived artifacts (topology, serializers, navigators, test
            // accessors) share ONE target package — the common prefix of every
            // graph/route. When that prefix is empty the nav sources sprawl across
            // unrelated top-level packages and there is no coherent home for the
            // generated code; fail rather than emit into "".
            environment.logger.error(
              "[PKG] navigation-module routes must share a common package; the common " +
                "package prefix is empty (sources are spread across unrelated top-level packages)"
            )
            return emptyList()
          }

          // [PKG]  — navigator'lar targetPackage'a üretilir ama cross-module probe route'un
          // DECLARATION paketinde (routePackageName) arar; alt-paketler ayrışırsa probe sessizce
          // ıskalar. "Her graph/route paketi == targetPackage" şartı bu varsayımı sözleşmeye
          // çevirir.
          val strayPackages = TopologyCodegen.declaredPackages(model).filter { it != packageName }
          if (strayPackages.isNotEmpty()) {
            environment.logger.error(
              "[PKG] every graph/route in the navigation module must be in the SAME package " +
                "(target package: $packageName); navigators are generated there, and a different " +
                "package would make the cross-module probe miss them. Package(s) outside the common " +
                "package: ${strayPackages.sorted()}"
            )
            return emptyList()
          }

          TopologyCodegen.generateTopology(model, packageName)
            .writeTo(environment.codeGenerator, Dependencies.ALL_FILES)

          val emitSerializers =
            environment.options["gezgin.emitSerializers"]?.toBooleanStrictOrNull() ?: true
          if (emitSerializers) {
            TopologyCodegen.generateSerializers(model, packageName)
              .writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
            // The stable process-wide gezginJson references gezginSerializersModule, so it shares
            // gezginSerializersModule, so it shares the emitSerializers gate. No @Composable is
            // emitted here: the graph module is plain-JVM (crash-safe val only).
            TopologyCodegen.generateRememberNavigator(packageName)
              .writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
          }

          // Typed per-source navigators — undeclared edges simply have no method (unresolved
          // reference).
          NavigatorCodegen.generate(model, packageName).forEach {
            it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
          }

          // Generated typed test API (`GezginTestNavigator.fromX()`) — opt-IN (default false): only
          // a
          // test source set's KSP config sets `gezgin.emitTestAccessors=true` (no `:gezgin-test`
          // dep in prod).
          val emitTestAccessors = environment.options["gezgin.emitTestAccessors"].toBoolean()
          if (emitTestAccessors) {
            TestApiCodegen.generate(model, packageName)
              ?.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
          }
        }

        // @MviViewModel classes read first so MVI-mode `@Screen(state,onIntent)` content can pair
        // with
        // its same-module VM by route. Reading (validate + dump) runs UNCONDITIONALLY — only
        // WRITING
        // entries is gated by `gezgin.emitEntries` below.
        val (vmModels, vmOk) = ViewModelModelReader(resolver, environment.logger).read()
        val (entries, entriesOk) =
          EntryModelReader(resolver, environment.logger, model, vmModels).read()

        // @FragmentScreen read cross-checks each route against the already-built `entries` so a
        // route
        // can't be registered by BOTH a @FragmentScreen and a @Screen/MVI content (FS3). Post-hoc
        // cross-check, not a shared-map change (see FragmentModelReader KDoc).
        val (fragmentModels, fragOk) =
          FragmentModelReader(resolver, environment.logger, entries).read()

        if (environment.options["gezgin.dumpMvi"].toBoolean()) {
          environment.codeGenerator
            .createNewFile(
              dependencies = Dependencies.ALL_FILES,
              packageName = "",
              fileName = "GezginMviDump",
              extensionName = "txt",
            )
            .use { it.write(dumpMviText(vmModels, entries).toByteArray()) }
        }

        if (environment.options["gezgin.dumpFragment"].toBoolean()) {
          environment.codeGenerator
            .createNewFile(
              dependencies = Dependencies.ALL_FILES,
              packageName = "",
              fileName = "GezginFragmentDump",
              extensionName = "txt",
            )
            .use { it.write(dumpFragmentText(fragmentModels).toByteArray()) }
        }

        // `provideXEntry` codegen — opt-OUT (default true); the opt-out exists for kctfork infra
        // with
        // no compose-compiler plugin (the emitted body ICEs the backend). Runs INDEPENDENTLY of
        // graph
        // ownership — a feature module qualifies each navigator factory against the ROUTE's
        // package,
        // not this module's. Core-mode → `GezginEntries.kt`, MVI-mode → separate
        // `GezginMviEntries.kt`
        // (same package, distinct file; SC6 keeps names unique). `fragOk` joins the gate so an FS
        // guardrail violation fails the build instead of emitting the surviving registration.
        val emitEntries = environment.options["gezgin.emitEntries"]?.toBooleanStrictOrNull() ?: true
        if (emitEntries && vmOk && entriesOk && fragOk) {
          val coreEntries = entries.filter { it.mvi == null }
          if (coreEntries.isNotEmpty()) {
            EntryCodegen.generate(coreEntries).forEach {
              it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
            }
          }
          val mviEntries = entries.filter { it.mvi != null }
          if (mviEntries.isNotEmpty()) {
            MviEntryCodegen.generate(mviEntries).forEach {
              it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
            }
          }
          // Fragment interop `provideXEntry`: each @FragmentScreen →
          // `AndroidFragment`-hosting
          // entry in a THIRD file `GezginFragmentEntries.kt`. Same emitEntries gate.
          if (fragmentModels.isNotEmpty()) {
            // Whether a @FragmentScreen route earns an `xNavigator` factory is a GRAPH-derived fact
            // —
            // computed HERE (model in scope), not in the graph-unaware FragmentModelReader.
            // Delegated
            // to the SHARED [NavigatorProbe] (same-module: in-memory; cross-module: classpath
            // probe).
            val graphsByFq = model.graphs.associateBy(GraphModelNode::fqName)
            val routesByFq = model.routes.associateBy(RouteModel::fqName)
            FragmentEntryCodegen.generate(fragmentModels) { entry ->
                NavigatorProbe.routeEarnsNavigator(
                  resolver = resolver,
                  routeModel = routesByFq[entry.routeFq],
                  graphsByFq = graphsByFq,
                  routePackageName = entry.routePackageName,
                  x = entry.x,
                  routeFq = entry.routeFq,
                )
              }
              .forEach { it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES) }
          }
        }
      }
    }
    return emptyList()
  }
}
