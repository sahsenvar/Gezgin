package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import dev.gezgin.core.GezginInternalApi
import dev.gezgin.core.GezginKey
import dev.gezgin.core.Route

internal class GezginWrapperScopeImpl(
  override val route: Route,
  override val routeName: String,
  override val routeAnnotations: List<Annotation>,
  override val graph: GezginGraph,
  private val noBack: Boolean,
  private val entryId: Long,
  private val keys: State<List<GezginKey>>,
) : GezginWrapperScope {
  override val isAloneInBackStack: Boolean
    get() = keys.value.size == 1

  override val isTop: Boolean
    get() = keys.value.lastOrNull()?.id == entryId

  override val canGoBack: Boolean
    get() = !isAloneInBackStack && !noBack
}

/**
 * Builds the scope a generated `@ScreenWrapper` call receives. Reads the entry id and raw navigator
 * that `toNavEntry` installs around every entry; the stack is collected as state so the scope's
 * flags recompose their readers.
 */
@GezginInternalApi
@Composable
public fun rememberGezginWrapperScope(
  route: Route,
  routeName: String,
  routeAnnotations: List<Annotation>,
  graph: GezginGraph,
  noBack: Boolean,
): GezginWrapperScope {
  val raw = LocalGezginRawNavigator.current
  val entryId = LocalGezginEntryId.current
  val keys = raw.keysState.collectAsState()
  return remember(route, routeName, routeAnnotations, graph, noBack, entryId, keys) {
    GezginWrapperScopeImpl(route, routeName, routeAnnotations, graph, noBack, entryId, keys)
  }
}
