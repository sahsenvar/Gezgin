package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import dev.gezgin.processor.CompileHarness.generatedSourceFor
import dev.gezgin.processor.naming.MemberFunNaming
import dev.gezgin.processor.naming.MemberKind
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class MemberFunNamingTest {

  private fun naming(vararg options: Pair<String, String>, onError: (String) -> Unit = {}) =
    MemberFunNaming.fromOptions(options.toMap(), onError)

  @Test
  fun `nothing is stripped by default`() {
    assertEquals(
      "OldPinScreenRoute",
      MemberFunNaming.Default.x(MemberKind.GoTo, "OldPinScreenRoute"),
    )
  }

  @Test
  fun `suffixes are removed in list order and each at most once`() {
    val naming = naming("gezgin.naming.memberFun.stripSuffixes" to "Route, Screen")
    assertEquals("OldPin", naming.x(MemberKind.GoTo, "OldPinScreenRoute"))
    assertEquals("OldPinScreen", naming.x(MemberKind.GoTo, "OldPinScreenScreenRoute"))
    assertEquals("OldPinRoute", naming.x(MemberKind.GoTo, "OldPinRouteScreen"))
  }

  @Test
  fun `a name is never emptied`() {
    val naming = naming("gezgin.naming.memberFun.stripSuffixes" to "Route")
    assertEquals("Route", naming.x(MemberKind.GoTo, "Route"))
  }

  @Test
  fun `a per-kind list replaces the general list for that kind only`() {
    val naming =
      naming(
        "gezgin.naming.memberFun.stripSuffixes" to "Route",
        "gezgin.naming.memberFun.Open.stripSuffixes" to "Route,Dialog",
      )
    assertEquals("ErrorDialog", naming.x(MemberKind.GoTo, "ErrorDialogRoute"))
    assertEquals("Error", naming.x(MemberKind.Open, "ErrorDialogRoute"))
  }

  @Test
  fun `an empty value clears the suffixes`() {
    val naming =
      naming(
        "gezgin.naming.memberFun.stripSuffixes" to "Route",
        "gezgin.naming.memberFun.Open.stripSuffixes" to "",
      )
    assertEquals("ErrorDialogRoute", naming.x(MemberKind.Open, "ErrorDialogRoute"))
  }

  @Test
  fun `unknown naming options are reported`() {
    val errors = mutableListOf<String>()
    naming(
      "gezgin.naming.memberFun.Nope.stripSuffixes" to "Route",
      "gezgin.naming.navigator.stripSuffixes" to "Route",
      "gezgin.emitEntries" to "false",
      onError = errors::add,
    )
    assertEquals(2, errors.size, errors.toString())
    assertContains(errors[0], "Nope")
    assertContains(errors[1], "gezgin.naming.navigator.stripSuffixes")
  }

  private val graph =
    """
    package dev.gezgin.naming

    import dev.gezgin.core.DialogContract
    import dev.gezgin.core.Route
    import dev.gezgin.core.annotation.GoTo
    import dev.gezgin.core.annotation.NavGraph
    import dev.gezgin.core.annotation.Open

    @NavGraph
    sealed interface G : Route {
        @GoTo(OldPinScreenRoute::class)
        @Open(ErrorDialogRoute::class)
        data object Home : G
        data object OldPinScreenRoute : G
        data class ErrorDialogRoute(val onOk: () -> Unit) : G, DialogContract
    }
    """
      .trimIndent()

  private fun navigatorSource(vararg options: Pair<String, String>): String {
    val result =
      compileGezgin(
        SourceFile.kotlin("G.kt", graph),
        kspArgs = mapOf("gezgin.emitSerializers" to "false") + options,
      )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    return assertNotNull(result.generatedSourceFor("HomeNavigator.kt"), result.messages).readText()
  }

  @Test
  fun `generated members keep every suffix by default`() {
    val text = navigatorSource()
    assertContains(text, "fun goToOldPinScreenRoute(")
    assertContains(text, "fun openErrorDialogRoute(")
  }

  @Test
  fun `the configured suffixes shape the generated members and per-kind rules win`() {
    val text =
      navigatorSource(
        "gezgin.naming.memberFun.stripSuffixes" to "Route,Screen",
        "gezgin.naming.memberFun.Open.stripSuffixes" to "Route,Dialog",
      )
    assertContains(text, "fun goToOldPin(")
    assertContains(text, "fun openError(")
  }

  @Test
  fun `the navigator class name still strips Route and Screen`() {
    val result =
      compileGezgin(
        SourceFile.kotlin("G.kt", graph),
        kspArgs = mapOf("gezgin.emitSerializers" to "false"),
      )
    assertNotNull(result.generatedSourceFor("OldPinNavigator.kt"), result.messages)
    assertFalse(result.messages.contains("[NM1]"), result.messages)
  }

  @Test
  fun `NM1 rejects an unknown naming option`() {
    val result =
      compileGezgin(
        SourceFile.kotlin("G.kt", graph),
        kspArgs =
          mapOf(
            "gezgin.emitSerializers" to "false",
            "gezgin.naming.memberFun.GoToo.stripSuffixes" to "Route",
          ),
      )
    assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "[NM1]")
  }

  @Test
  fun `member-name clashes are checked with the configured naming`() {
    val result =
      compileGezgin(
        SourceFile.kotlin(
          "Clash.kt",
          """
          package dev.gezgin.namingclash

          import dev.gezgin.core.Route
          import dev.gezgin.core.annotation.GoTo
          import dev.gezgin.core.annotation.NavGraph

          @NavGraph
          sealed interface G : Route {
              @GoTo(Detail::class)
              @GoTo(DetailRoute::class)
              data object Home : G
              data object Detail : G
              data object DetailRoute : G
          }
          """
            .trimIndent(),
        ),
        kspArgs =
          mapOf(
            "gezgin.emitSerializers" to "false",
            "gezgin.naming.memberFun.stripSuffixes" to "Route",
          ),
      )
    assertNotEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertContains(result.messages, "[N10]")
  }
}
