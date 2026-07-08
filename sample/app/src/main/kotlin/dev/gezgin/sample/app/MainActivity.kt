package dev.gezgin.sample.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

/**
 * S1 skeleton — placeholder host. The real wiring (rememberNavigator + GezginDisplay over the
 * generated topology/serializers/entries, a NavLogger events observer, onRootBack = finish, and the
 * app-level transition cascade) lands in S2. For now this only proves the app module assembles with
 * every feature + `:sample:navigation` on its classpath.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Gezgin Showcase — S1 skeleton") }
    }
}
