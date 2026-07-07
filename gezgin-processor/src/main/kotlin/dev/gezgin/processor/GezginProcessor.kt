package dev.gezgin.processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.kotlinpoet.ksp.writeTo
import dev.gezgin.processor.codegen.NavigatorCodegen
import dev.gezgin.processor.codegen.TopologyCodegen
import dev.gezgin.processor.model.ModelReader
import dev.gezgin.processor.model.dumpText

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
            if (validationOk && model.graphs.isNotEmpty()) {
                val packageName = TopologyCodegen.targetPackage(model)

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
            }
        }
        return emptyList()
    }
}
