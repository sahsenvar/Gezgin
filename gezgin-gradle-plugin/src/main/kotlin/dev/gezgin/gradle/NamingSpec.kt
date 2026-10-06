package dev.gezgin.gradle

import org.gradle.api.Action

/**
 * The `naming` block of [GezginExtension].
 *
 * @author @sahsenvar
 */
public class NamingSpec {
  internal val memberFun = MemberFunSpec()

  /** Configures how generated navigator member names (`goToX`, `openX`, ...) are derived. */
  public fun memberFun(action: Action<in MemberFunSpec>) {
    action.execute(memberFun)
  }
}
