package dev.gezgin.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.gezgin.processor.CompileHarness.compileGezgin
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class ExperimentalGezginMigrationApiTest {

    @Test
    fun `temporary migration APIs require explicit opt-in`() {
        migrationReferences().forEach { (name, declaration) ->
            val result = compileGezgin(
                SourceFile.kotlin(
                    "MigrationApi${name}.kt",
                    migrationSource(declaration),
                ),
            )

            assertEquals(
                KotlinCompilation.ExitCode.COMPILATION_ERROR,
                result.exitCode,
                "$name unexpectedly compiled without opt-in:\n${result.messages}",
            )
            assertContains(result.messages, "ExperimentalGezginMigrationApi", message = result.messages)
        }
    }

    @Test
    fun `temporary migration APIs compile with explicit opt-in`() {
        val result = compileGezgin(
            SourceFile.kotlin(
                "OptedInMigrationApis.kt",
                migrationSource(
                    migrationReferences().joinToString("\n") { (_, declaration) -> declaration },
                    optedIn = true,
                ),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    private fun migrationReferences(): List<Pair<String, String>> = listOf(
        "TopBar" to "val topBar = TopBar::class",
        "TopBarRoute" to "val topBarRoute = TopBar::route",
        "BottomBar" to "val bottomBar = BottomBar::class",
        "BottomBarRoute" to "val bottomBarRoute = BottomBar::route",
        "DragHandleMode" to "val dragHandleMode = BottomSheetDragHandleMode::class",
        "DragHandleDefault" to "val dragHandleDefault = BottomSheetDragHandleMode.Default",
        "DragHandleNone" to "val dragHandleNone = BottomSheetDragHandleMode.None",
        "DragHandleContract" to "fun dragHandle(contract: BottomSheetContract) = contract.dragHandleMode",
    )

    private fun migrationSource(declarations: String, optedIn: Boolean = false): String = """
        ${if (optedIn) "@file:OptIn(dev.gezgin.core.ExperimentalGezginMigrationApi::class)" else ""}

        package dev.gezgin.migrationoptin

        import dev.gezgin.core.BottomSheetContract
        import dev.gezgin.core.BottomSheetDragHandleMode
        import dev.gezgin.mvi.annotation.BottomBar
        import dev.gezgin.mvi.annotation.TopBar

        $declarations
    """.trimIndent()
}
