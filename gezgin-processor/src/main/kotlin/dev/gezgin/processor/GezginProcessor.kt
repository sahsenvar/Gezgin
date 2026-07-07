package dev.gezgin.processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import dev.gezgin.processor.model.ModelReader
import dev.gezgin.processor.model.dumpText

/**
 * Currently only reads the semantic [dev.gezgin.processor.model.GraphModel] (Task 2.2) and, when
 * the test-only `gezgin.dumpModel=true` KSP option is set, writes it out as a deterministic text
 * file (`GezginModelDump.txt`) for behavioral assertions. Later tasks add validation and codegen.
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

            if (environment.options["gezgin.dumpModel"].toBoolean()) {
                environment.codeGenerator.createNewFile(
                    dependencies = Dependencies.ALL_FILES,
                    packageName = "",
                    fileName = "GezginModelDump",
                    extensionName = "txt",
                ).use { it.write(model.dumpText().toByteArray()) }
            }
        }
        return emptyList()
    }
}
