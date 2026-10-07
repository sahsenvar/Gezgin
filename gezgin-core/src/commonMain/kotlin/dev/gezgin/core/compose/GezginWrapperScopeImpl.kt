package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
  // Written only by the derivation: an entry animating out keeps the value it last had on the
  // stack, so readers of the back flags are not recomposed by the pop that removed it.
  private var aloneWhileOnStack: Boolean? = null

  // One derived state per flag: a reader is invalidated only when the flag it reads changes.
  private val alone = derivedStateOf {
    val live = keys.value
    if (live.any { it.id == entryId }) (live.size == 1).also { aloneWhileOnStack = it }
    else aloneWhileOnStack ?: (live.size == 1)
  }

  private val top = derivedStateOf { keys.value.lastOrNull()?.id == entryId }

  override val isAloneInBackStack: Boolean
    get() = alone.value

  override val isTop: Boolean
    get() = top.value

  override val canGoBack: Boolean
    get() = !alone.value && !noBack
}

/**
 * Builds the scope a generated `@ScreenWrapper` call receives. Reads the entry id and raw navigator
 * that `toNavEntry` installs around every entry; the stack is collected as state and the scope's
 * flags are derived from it, so a reader recomposes only when the flag it reads changes.
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
