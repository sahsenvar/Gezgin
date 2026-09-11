package dev.gezgin.sample.hello

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.gezgin.core.compose.GezginDisplay
import dev.gezgin.core.compose.rememberNavigator
import dev.gezgin.sample.hello.nav.HelloGraph
import dev.gezgin.sample.hello.nav.gezginJson
import dev.gezgin.sample.hello.nav.gezginTopology

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HelloApp(onRootBack = { finish() }) }
    }
}

@Composable
private fun HelloApp(onRootBack: () -> Unit) {
    val navigator =
        rememberNavigator(
            start = HelloGraph.ContactListScreenRoute,
            topology = gezginTopology,
            json = gezginJson,
            restoreKey = "hello",
            onRootBack = onRootBack,
        )
    MaterialTheme {
        GezginDisplay(
            navigator = navigator
        ) {
            helloGraphEntries()
        }
    }
}
