package dev.gezgin.processor

import com.google.devtools.ksp.getClassDeclarationByName
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
 * Reads the semantic [dev.gezgin.processor.model.GraphModel] (Task 2.2), validates it against the
 * Global Constraints rule list via [GezginValidator] (Task 2.3, KSP errors fail the compilation),
 * and, when the test-only `gezgin.dumpModel=true` KSP option is set, writes the model out as a
 * deterministic text file (`GezginModelDump.txt`) for behavioral assertions. On a clean (error-free)
 * model, also runs [TopologyCodegen] (Task 2.4) to emit `GezginGenerated.kt` and — unless the
 * `gezgin.emitSerializers=false` KSP option opts out — `GezginSerializers.kt`.
 */
class GezginProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (!invoked) {
            invoked = true
            // `info` can be swallowed depending on the KSP logging level wired by the host
            // compilation (kctfork routes it through messageOutputStream); `warn` as a fallback
            // guarantees visibility for the spike's assertion.
            environment.logger.info("Gezgin processor alive")
            environment.logger.warn("Gezgin processor alive")

            val model = ModelReader(resolver, environment.logger).read()

            val validationOk = GezginValidator(model, environment.logger).validate()

            if (environment.options["gezgin.dumpModel"].toBoolean()) {
                environment.codeGenerator.createNewFile(
                    dependencies = Dependencies.ALL_FILES,
                    packageName = "",
                    fileName = "GezginModelDump",
                    extensionName = "txt",
                ).use { it.write(model.dumpText().toByteArray()) }
            }

            // Codegen (Task 2.4) only runs on a clean model — a validation failure already fails
            // the compilation via KSP errors, and the model may be too malformed to emit sane code
            // for (e.g. a @GoForResult edge with no resolvable result type, normally rejected by E2).
            if (validationOk) {
                // GRAPH-derived codegen (topology / serializers / navigators / test accessors) is
                // gated on this module actually OWNING graphs — its sealed graph tree, and hence its
                // single shared nav-topology package. A cross-module FEATURE (spec §3.3) has NO
                // graphs of its own (they all live in the central `:navigation` module), so this
                // whole block is skipped there — but its `@Screen` ENTRY codegen below still runs.
                if (model.graphs.isNotEmpty()) {
                    val packageName = TopologyCodegen.targetPackage(model)
                    if (packageName.isEmpty()) {
                        // All graph-derived artifacts (topology, serializers, navigators, test
                        // accessors) share ONE target package — the common prefix of every
                        // graph/route. When that prefix is empty the nav sources sprawl across
                        // unrelated top-level packages and there is no coherent home for the
                        // generated code; fail rather than emit into "".
                        environment.logger.error(
                            "[PKG] nav modülü route'ları ortak bir pakette olmalı — ortak paket öneki boş " +
                                "(kaynaklar ayrışık top-level paketlere dağılmış)",
                        )
                        return emptyList()
                    }

                    TopologyCodegen.generateTopology(model, packageName)
                        .writeTo(environment.codeGenerator, Dependencies.ALL_FILES)

                    val emitSerializers = environment.options["gezgin.emitSerializers"]?.toBooleanStrictOrNull() ?: true
                    if (emitSerializers) {
                        TopologyCodegen.generateSerializers(model, packageName)
                            .writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
                    }

                    // Task 2.5: typed per-source navigators — undeclared edges simply have no
                    // corresponding method (unresolved reference), which is the core value proposition.
                    NavigatorCodegen.generate(model, packageName).forEach {
                        it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
                    }

                    // Task 2.6: §13 typed test API (`GezginTestNavigator.fromX()`) — opt-IN (default
                    // false): production modules don't depend on `:gezgin-test`, only a test source
                    // set's KSP configuration sets `gezgin.emitTestAccessors=true`.
                    val emitTestAccessors = environment.options["gezgin.emitTestAccessors"].toBoolean()
                    if (emitTestAccessors) {
                        TestApiCodegen.generate(model, packageName)
                            ?.writeTo(environment.codeGenerator, Dependencies.ALL_FILES)
                    }
                }

                // Faz 5.1/5.2 — MVI add-on read + validate. @ViewModel classes first (MV1/MV4 +
                // DI-detection), then feed them to the entry reader so MVI-mode `@Screen(state,onIntent)`
                // content can pair with its same-module @ViewModel by route (MV2/MV3/MV5/MV6). Core-mode
                // (route,nav) reading is untouched — vmModels is empty in any module with no @ViewModel.
                // Reading runs UNCONDITIONALLY (validation + the dump must not depend on codegen
                // emission — only WRITING generated entries is gated by `gezgin.emitEntries` below).
                val (vmModels, vmOk) = ViewModelModelReader(resolver, environment.logger).read()
                val (entries, entriesOk) = EntryModelReader(resolver, environment.logger, model, vmModels).read()

                // Task 6.1 — brownfield Fragment interop (§11). Reads @FragmentScreen classes into
                // FragmentEntryModels (FS1 no-arg-ctor / FS2 route-sanity guardrails), cross-checking each
                // route against the already-built `entries` (core + MVI) so a route can't be registered by
                // BOTH a @FragmentScreen and a @Screen/MVI content (FS3). EntryModelReader is untouched —
                // this is a post-hoc cross-check, not a shared-map change (see FragmentModelReader KDoc).
                // Codegen (the AndroidFragment `provideXEntry`) is Task 6.2; 6.1 only reads/validates/dumps.
                val (fragmentModels, fragOk) = FragmentModelReader(resolver, environment.logger, entries).read()

                if (environment.options["gezgin.dumpMvi"].toBoolean()) {
                    environment.codeGenerator.createNewFile(
                        dependencies = Dependencies.ALL_FILES,
                        packageName = "",
                        fileName = "GezginMviDump",
                        extensionName = "txt",
                    ).use { it.write(dumpMviText(vmModels, entries).toByteArray()) }
                }

                if (environment.options["gezgin.dumpFragment"].toBoolean()) {
                    environment.codeGenerator.createNewFile(
                        dependencies = Dependencies.ALL_FILES,
                        packageName = "",
                        fileName = "GezginFragmentDump",
                        extensionName = "txt",
                    ).use { it.write(dumpFragmentText(fragmentModels).toByteArray()) }
                }

                // `provideXEntry` codegen — opt-OUT (default true, mirrors `gezgin.emitSerializers`).
                // The opt-out exists purely for kctfork test infra where the compose-compiler plugin
                // isn't wired up (see EntryCodegenTest); real (Gradle/AGP) builds always emit. Runs
                // INDEPENDENTLY of graph ownership: a feature module registers cross-module routes'
                // `@Screen`s (§3.3) with no graphs of its own, qualifying each navigator factory against
                // the ROUTE's package ([EntryFunctionModel.routePackageName]), not this module's (absent)
                // target package. Faz 5.2 emits BOTH modes: core-mode `(route,nav)` via EntryCodegen into
                // `GezginEntries.kt`, and MVI-mode `(state,onIntent)` via MviEntryCodegen into a SEPARATE
                // `GezginMviEntries.kt` (VM resolver / DI-detection / @ScreenEffect wiring / Problem-2
                // resolver params) — same package, distinct file, no collision (SC6 keeps provideXEntry
                // names unique across both modes).
                // `fragOk` joins the gate so an FS-guardrail violation (e.g. an FS3 route collision
                // between a @FragmentScreen and a @Screen) fails the build cleanly instead of emitting the
                // surviving registration. Fragment-less modules always have `fragOk = true` → zero change.
                val emitEntries = environment.options["gezgin.emitEntries"]?.toBooleanStrictOrNull() ?: true
                if (emitEntries && vmOk && entriesOk && fragOk) {
                    val coreEntries = entries.filter { it.mvi == null }
                    if (coreEntries.isNotEmpty()) {
                        EntryCodegen.generate(coreEntries)
                            .forEach { it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES) }
                    }
                    val mviEntries = entries.filter { it.mvi != null }
                    if (mviEntries.isNotEmpty()) {
                        MviEntryCodegen.generate(mviEntries)
                            .forEach { it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES) }
                    }
                    // Task 6.2 — Fragment interop `provideXEntry` (§11.1): each @FragmentScreen becomes an
                    // `AndroidFragment<XFragment>`-hosting entry, grouped by Fragment package into a THIRD
                    // dedicated file `GezginFragmentEntries.kt` (screen-only, §11.2). Same emitEntries gate
                    // (kctfork has no compose-compiler plugin → the emitted body ICEs the backend, exactly
                    // like EntryCodegen; the opt-out lets those tests assert the golden text without OK exit).
                    if (fragmentModels.isNotEmpty()) {
                        // Fragment nav-wiring GUARD (SC2/MV7 parity, one phase later, FS5). Whether a
                        // @FragmentScreen route earns a NavigatorCodegen `xNavigator` factory is a
                        // GRAPH-derived fact — computed HERE (where `model` is in scope), NOT in the
                        // graph-unaware FragmentModelReader. Two branches by where the route lives:
                        //  - SAME-module route (in THIS model): decide from the in-memory GraphModel via
                        //    NavigatorCodegen.hasNavigator, exactly like SC2/MV7. Its navigator, if earned, is
                        //    generated in THIS SAME KSP round → NOT yet resolvable on the classpath, so the
                        //    model check is the ONLY reliable source here (a classpath probe can't replace it).
                        //  - CROSS-module route (routeModel == null, compiled in another module): its navigator,
                        //    IF the route earns one, is an ALREADY-COMPILED `XNavigator` class visible on the
                        //    classpath NOW → PROBE it deterministically (getClassDeclarationByName) instead of the
                        //    old `?: true` blind optimism. A bare @FragmentScreen has NO per-entry nav signal (unlike
                        //    core/MVI, which only reach this check when the function signature requests `nav`), so
                        //    `?: true` nav-wired EVERY cross-module Fragment — including a genuinely display-only
                        //    leaf whose cross-module route has ZERO edges (no navigator). That emitted a
                        //    `raw.xNavigator()` call to a nonexistent factory: an unresolved reference, the EXACT
                        //    bug FS5 exists to kill, just relocated to the cross-module case. The probe fixes it:
                        //    class resolves → wire nav; doesn't → the FS5 no-nav path (2-arg bindGezgin; gezginNav
                        //    throws the actionable [FS5] runtime error). The leaf is NOT rejected at KSP time.
                        val graphsByFq = model.graphs.associateBy(GraphModelNode::fqName)
                        val routesByFq = model.routes.associateBy(RouteModel::fqName)
                        FragmentEntryCodegen.generate(fragmentModels) { entry ->
                            val routeModel = routesByFq[entry.routeFq]
                            if (routeModel != null) {
                                NavigatorCodegen.hasNavigator(routeModel, graphsByFq)
                            } else {
                                resolver.getClassDeclarationByName(
                                    "${entry.routePackageName}.${entry.x}Navigator",
                                ) != null
                            }
                        }.forEach { it.writeTo(environment.codeGenerator, Dependencies.ALL_FILES) }
                    }
                }
            }
        }
        return emptyList()
    }
}
