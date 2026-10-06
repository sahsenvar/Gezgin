package dev.gezgin.gradle

import org.gradle.api.Action

/**
 * The `gezgin { }` extension: a typed facade over the Gezgin KSP processor options.
 *
 * @author @sahsenvar
 */
public abstract class GezginExtension {
  internal val naming = NamingSpec()

  /** Configures the names of generated code. */
  public fun naming(action: Action<in NamingSpec>) {
    action.execute(naming)
  }
}
