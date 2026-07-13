package dev.gezgin.sample.shopr

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.core.compose.rememberNavigator
import dev.gezgin.sample.shopr.debug.CorruptingSaveableStateRegistry
import dev.gezgin.sample.shopr.nav.HomeGraph.Feed
import dev.gezgin.sample.shopr.nav.gezginJson
import dev.gezgin.sample.shopr.nav.gezginTopology

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DEBUG-only PD-corruption harness (maestro madde-7): only on a debuggable build launched with the
        // `corrupt_state` extra. When false, `base` stays null → content runs directly = identical to prod.
        // Passed on the ORIGINAL launch intent so it survives process death: a launcher resume replays the
        // task's original intent (a fresh `am start` extra would be dropped), keeping the hook armed on restore.
        val corrupt = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            intent.getBooleanExtra("corrupt_state", false)
        setContent {
            val content = @Composable { ShoprApp(onRootBack = { finish() }) }
            val base = if (corrupt) LocalSaveableStateRegistry.current else null
            if (base != null) {
                CompositionLocalProvider(
                    LocalSaveableStateRegistry provides CorruptingSaveableStateRegistry(base),
                ) { content() }
            } else {
                content()
            }
        }
    }
}

@Composable
private fun ShoprApp(onRootBack: () -> Unit) {
    val navigator = rememberNavigator(
        start = Feed,
        topology = gezginTopology,
        json = gezginJson,
        onRootBack = onRootBack,
    )
    GezginDisplay(navigator = navigator) {
        shopGraphEntries()
    }
}
