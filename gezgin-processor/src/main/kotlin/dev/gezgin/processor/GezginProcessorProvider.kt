package dev.gezgin.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Service-loaded KSP entry point that creates Gezgin symbol processors for each compilation.
 *
 * @author @sahsenvar
 */
public class GezginProcessorProvider : SymbolProcessorProvider {
  /** Creates a Gezgin processor configured with [environment]. */
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
    GezginProcessor(environment)
}
