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
      // Some KSP hosts suppress info output, so warn provides an observable fallback.
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
        // Graph-derived codegen (topology, serializers, navigators, and test accessors) requires
        // this module to own graphs. A cross-module feature owns none because they live in the
        // central `:navigation` module, so this block is skipped while its `@Screen` entry codegen
        // below still runs.
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

          // Navigators are emitted into the target package while cross-module probes search the
          // route declaration package. Require equality so a probe cannot silently miss output.
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
            // The stable process-wide `gezginJson` references `gezginSerializersModule`, so both
            // share the `emitSerializers` gate. No `@Composable` is emitted here because the graph
            // module is plain JVM; only the process-safe value is generated.
            TopologyCodegen.generateRememberNavigator(packageName)
              .writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
          }

          // Typed per-source navigators omit methods for undeclared edges, producing an unresolved
          // reference at the call site.
          NavigatorCodegen.generate(model, packageName).forEach {
            it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
          }

          // Generated typed test API (`GezginTestNavigator.fromX()`) is opt-in and disabled by
          // default. Only a test source set's KSP configuration sets
          // `gezgin.emitTestAccessors=true`; production has no `:gezgin-test` dependency.
          val emitTestAccessors = environment.options["gezgin.emitTestAccessors"].toBoolean()
          if (emitTestAccessors) {
            TestApiCodegen.generate(model, packageName)
              ?.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
          }
        }

        // Read `@MviViewModel` classes first so MVI-mode `@Screen(state,onIntent)` content can pair
        // with its same-module ViewModel by route. Reading, validation, and dumps run
        // unconditionally; only entry generation is gated by `gezgin.emitEntries` below.
        val (vmModels, vmOk) = ViewModelModelReader(resolver, environment.logger).read()
        val (entries, entriesOk) =
          EntryModelReader(resolver, environment.logger, model, vmModels).read()

        // The `@FragmentScreen` reader cross-checks each route against the existing entries so the
        // same route cannot be registered by both a Fragment and `@Screen`/MVI content (`FS3`).
        // This is a post-read cross-check rather than a shared-map mutation.
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

        // `provideXEntry` codegen is enabled by default. The opt-out supports kctfork when the
        // Compose compiler plugin is absent and the emitted body would fail the backend. Entry
        // generation is independent of graph ownership: a feature module qualifies each navigator
        // factory against the route package, not its own. Core mode writes `GezginEntries.kt`.
        // MVI mode writes `GezginMviEntries.kt` in the same package but a distinct file; `SC6`
        // keeps names unique. `fragOk` joins the gate so an FS
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
          // Each `@FragmentScreen` emits an `AndroidFragment`-hosting `provideXEntry` into the
          // separate `GezginFragmentEntries.kt` file under the same `emitEntries` gate.
          if (fragmentModels.isNotEmpty()) {
            // Whether a `@FragmentScreen` route earns an `xNavigator` factory is graph-derived, so
            // it is computed here where the model is available. `NavigatorProbe` handles both the
            // in-memory same-module case and the cross-module classpath case.
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
