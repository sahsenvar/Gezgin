@file:OptIn(dev.gezgin.core.GezginInternalApi::class)

package dev.gezgin.core.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.gezgin.core.GezginTopology
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.testSerializersModule
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RememberNavigatorTransientReleaseTest {

  private data class ConfirmDialog(val onConfirm: () -> Unit) : Route

  class ConfigAwareActivity : ComponentActivity() {
    var changingConfigurations = false

    override fun isChangingConfigurations(): Boolean = changingConfigurations
  }

  private val topology =
    GezginTopology(
      flowChains = emptyMap(),
      flowStarts = emptyMap(),
      edges = emptyMap(),
      transientRoutes = setOf(ConfirmDialog::class),
    )

  private val owner =
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }

  private fun compose(activity: ComponentActivity): Pair<RawNavigator, ComposeView> {
    var navigator: RawNavigator? = null
    val view = ComposeView(activity)
    activity.setContentView(view)
    view.setContent {
      CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        LocalSaveableStateRegistry provides SaveableStateRegistry(null) { true },
      ) {
        navigator =
          rememberRawNavigatorInstance(
            start = Feed,
            topology = topology,
            json = Json { serializersModule = testSerializersModule },
            restoreKey = "session",
            onRootBack = {},
          )
      }
    }
    return checkNotNull(navigator) to view
  }

  @Test
  fun leavingCompositionOutsideAConfigChangeDropsCallbackModals() {
    val controller = Robolectric.buildActivity(ConfigAwareActivity::class.java).setup()
    try {
      val (navigator, view) = compose(controller.get())
      navigator.navigate(Catalog)
      navigator.open(ConfirmDialog {})

      view.disposeComposition()

      assertEquals(listOf<Route>(Feed, Catalog), navigator.backStack.value)
    } finally {
      owner.viewModelStore.clear()
      controller.pause().stop().destroy()
    }
  }

  @Test
  fun leavingCompositionDuringAConfigChangeKeepsCallbackModals() {
    val controller = Robolectric.buildActivity(ConfigAwareActivity::class.java).setup()
    try {
      val activity = controller.get()
      val (navigator, view) = compose(activity)
      navigator.open(ConfirmDialog {})

      activity.changingConfigurations = true
      view.disposeComposition()

      assertEquals(2, navigator.backStack.value.size)
    } finally {
      owner.viewModelStore.clear()
      controller.pause().stop().destroy()
    }
  }
}
