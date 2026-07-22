package dev.gezgin.core.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.gezgin.core.RawNavigator
import dev.gezgin.core.Route
import dev.gezgin.core.fixtures.Catalog
import dev.gezgin.core.fixtures.Feed
import dev.gezgin.core.fixtures.Product
import dev.gezgin.core.fixtures.testSerializersModule
import dev.gezgin.core.fixtures.testTopology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

private val androidTestJson = Json { serializersModule = testSerializersModule }

@RunWith(RobolectricTestRunner::class)
class RememberNavigatorAndroidIdentityTest {
  @Test
  fun `retained owner rejects colliding changed namespace token while same namespace keeps holder`() {
    assertEquals(
      "Aa".hashCode(),
      "BB".hashCode(),
      "test keys must exercise a real String hash collision",
    )
    val owner = RetainedOwner()
    val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = activityController.get()
    try {
      val initial =
        composeNavigator(activity, owner, restoredValues = null, restoreKey = "Aa", start = Feed)
      initial.navigator.navigate(Catalog)

      val recreationState = initial.registry.performSave()
      initial.view.disposeComposition()
      val sameKey =
        composeNavigator(activity, owner, recreationState, restoreKey = "Aa", start = Feed)

      assertSame(initial.navigator, sameKey.navigator)
      assertEquals(listOf<Route>(Feed, Catalog), sameKey.navigator.keys.map { it.route })

      sameKey.view.disposeComposition()
      val changedKey =
        composeNavigator(
          activity = activity,
          owner = owner,
          restoredValues = recreationState,
          restoreKey = "BB",
          start = Product("fresh"),
        )

      assertNotSame(initial.navigator, changedKey.navigator)
      assertEquals(listOf<Route>(Product("fresh")), changedKey.navigator.keys.map { it.route })
      changedKey.view.disposeComposition()
    } finally {
      owner.viewModelStore.clear()
      activityController.pause().stop().destroy()
    }
  }

  @Test
  fun `saved state registry export imports same namespace snapshot into fresh activity owner`() {
    val initialController = launchRegistryActivity(restoreKey = "account-42", start = START_FEED)
    val initialActivity = initialController.get()
    val initialNavigator = initialActivity.navigators.single()
    initialNavigator.navigate(Catalog)

    val exportedState = saveAndDestroy(initialController)
    val restoredController =
      launchRegistryActivity(
        restoreKey = "account-42",
        start = START_PRODUCT,
        restoredState = exportedState,
      )
    try {
      val restoredActivity = restoredController.get()
      val restoredNavigator = restoredActivity.navigators.single()

      assertNotSame(initialActivity, restoredActivity)
      assertNotSame(initialActivity.viewModelStore, restoredActivity.viewModelStore)
      assertNotSame(initialNavigator, restoredNavigator)
      assertEquals(listOf<Route>(Feed, Catalog), restoredNavigator.keys.map { it.route })
    } finally {
      restoredController.pause().stop().destroy()
    }
  }

  @Test
  fun `saved state registry rejects Aa snapshot for fresh BB activity despite composite hash collision`() {
    assertEquals(
      "Aa".hashCode(),
      "BB".hashCode(),
      "test keys must exercise a real String hash collision",
    )
    val initialController = launchRegistryActivity(restoreKey = "Aa", start = START_FEED)
    initialController.get().navigators.single().navigate(Catalog)
    val exportedState = saveAndDestroy(initialController)

    val changedController =
      launchRegistryActivity(
        restoreKey = "BB",
        start = START_PRODUCT,
        restoredState = exportedState,
      )
    try {
      assertEquals(
        listOf<Route>(Product("fresh")),
        changedController.get().navigators.single().keys.map { it.route },
      )
    } finally {
      changedController.pause().stop().destroy()
    }
  }

  @Test
  fun `same business key keeps simultaneous call sites distinct across registry recreation`() {
    val initialController =
      launchRegistryActivity(
        restoreKey = "shared-business-key",
        start = START_FEED,
        navigatorCount = 2,
      )
    val initialNavigators = initialController.get().navigators
    val initialLeft = initialNavigators[0]
    val initialRight = initialNavigators[1]
    assertNotSame(
      initialLeft,
      initialRight,
      "distinct call-site tokens must resolve distinct holders",
    )

    initialLeft.navigate(Catalog)
    initialRight.navigate(Feed)
    assertEquals(listOf<Route>(Feed, Catalog), initialLeft.keys.map { it.route })
    assertEquals(listOf<Route>(Product("right"), Feed), initialRight.keys.map { it.route })

    val exportedState = saveAndDestroy(initialController)
    val restoredController =
      launchRegistryActivity(
        restoreKey = "shared-business-key",
        start = START_FEED,
        navigatorCount = 2,
        restoredState = exportedState,
      )
    try {
      val restoredLeft = restoredController.get().navigators[0]
      val restoredRight = restoredController.get().navigators[1]

      assertNotSame(
        restoredLeft,
        restoredRight,
        "restored call-site tokens must resolve distinct holders",
      )
      assertEquals(listOf<Route>(Feed, Catalog), restoredLeft.keys.map { it.route })
      assertEquals(listOf<Route>(Product("right"), Feed), restoredRight.keys.map { it.route })
    } finally {
      restoredController.pause().stop().destroy()
    }
  }

  private fun composeNavigator(
    activity: ComponentActivity,
    owner: RetainedOwner,
    restoredValues: Map<String, List<Any?>>?,
    restoreKey: String,
    start: Route,
  ): NavigatorComposition {
    val registry = SaveableStateRegistry(restoredValues = restoredValues, canBeSaved = { true })
    var navigator: RawNavigator? = null
    val view = ComposeView(activity)
    activity.setContentView(view)
    view.setContent {
      CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        LocalSaveableStateRegistry provides registry,
      ) {
        navigator =
          rememberRawNavigatorInstance(
            start = start,
            topology = testTopology,
            json = androidTestJson,
            restoreKey = restoreKey,
            onRootBack = {},
          )
      }
    }

    return NavigatorComposition(checkNotNull(navigator), registry, view)
  }

  private fun launchRegistryActivity(
    restoreKey: String,
    start: String,
    navigatorCount: Int = 1,
    restoredState: Bundle? = null,
  ): ActivityController<NavigatorSavedStateActivity> {
    val intent =
      Intent(RuntimeEnvironment.getApplication(), NavigatorSavedStateActivity::class.java)
        .putExtra(EXTRA_RESTORE_KEY, restoreKey)
        .putExtra(EXTRA_START, start)
        .putExtra(EXTRA_NAVIGATOR_COUNT, navigatorCount)
    val controller = Robolectric.buildActivity(NavigatorSavedStateActivity::class.java, intent)
    return if (restoredState == null) {
      controller.setup()
    } else {
      controller.create(restoredState).start().resume().visible()
    }
  }

  private fun saveAndDestroy(controller: ActivityController<NavigatorSavedStateActivity>): Bundle {
    val state = Bundle()
    controller.saveInstanceState(state)
    controller.pause().stop().destroy()
    return state
  }

  private class RetainedOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
  }

  private class NavigatorComposition(
    val navigator: RawNavigator,
    val registry: SaveableStateRegistry,
    val view: ComposeView,
  )
}

internal class NavigatorSavedStateActivity : ComponentActivity() {
  internal var navigators: List<RawNavigator> = emptyList()
    private set

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val restoreKey = requireNotNull(intent.getStringExtra(EXTRA_RESTORE_KEY))
    val navigatorCount = intent.getIntExtra(EXTRA_NAVIGATOR_COUNT, 1)
    val start =
      when (intent.getStringExtra(EXTRA_START)) {
        START_FEED -> Feed
        START_PRODUCT -> Product("fresh")
        else -> error("Unknown navigator test start")
      }
    setContent {
      val left =
        rememberNavigator(
          start = start,
          topology = testTopology,
          json = androidTestJson,
          restoreKey = restoreKey,
        )
      navigators =
        if (navigatorCount == 1) {
          listOf(left)
        } else {
          val right =
            rememberNavigator(
              start = Product("right"),
              topology = testTopology,
              json = androidTestJson,
              restoreKey = restoreKey,
            )
          listOf(left, right)
        }
    }
  }
}

private const val EXTRA_RESTORE_KEY = "restore-key"
private const val EXTRA_START = "start"
private const val EXTRA_NAVIGATOR_COUNT = "navigator-count"
private const val START_FEED = "feed"
private const val START_PRODUCT = "product"
