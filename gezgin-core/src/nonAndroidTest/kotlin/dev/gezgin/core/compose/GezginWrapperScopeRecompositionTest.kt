@file:OptIn(ExperimentalTestApi::class)

package dev.gezgin.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.gezgin.core.GezginKey
import dev.gezgin.core.fixtures.Product
import kotlin.test.Test
import kotlin.test.assertEquals

class GezginWrapperScopeRecompositionTest {
  private val graph = GezginGraph("AppGraph", GraphKind.Nav, emptyList(), null)

  private fun key(id: Long) = GezginKey(Product("$id"), id = id)

  private fun scope(keys: MutableState<List<GezginKey>>, entryId: Long) =
    GezginWrapperScopeImpl(
      route = Product("$entryId"),
      routeName = "Product",
      routeAnnotations = emptyList(),
      graph = graph,
      noBack = false,
      entryId = entryId,
      keys = keys,
    )

  @Test
  fun `a stack change that leaves the back flags unchanged does not recompose their reader`() {
    val keys = mutableStateOf(listOf(key(1), key(2)))
    val scope = scope(keys, entryId = 1)
    var compositions = 0

    runComposeUiTest {
      setContent { BackFlagsReader(scope) { compositions++ } }
      waitForIdle()
      assertEquals(1, compositions)

      keys.value = keys.value + key(3)
      waitForIdle()

      assertEquals(1, compositions)
    }
  }

  @Test
  fun `an entry pushed on top flips isTop without recomposing a reader of the back flags`() {
    val keys = mutableStateOf(listOf(key(1), key(2)))
    val scope = scope(keys, entryId = 2)
    var compositions = 0

    runComposeUiTest {
      setContent { BackFlagsReader(scope) { compositions++ } }
      waitForIdle()

      keys.value = keys.value + key(3)
      waitForIdle()

      assertEquals(false, scope.isTop)
      assertEquals(1, compositions)
    }
  }

  @Test
  fun `a stack change that flips a back flag recomposes its reader once`() {
    val keys = mutableStateOf(listOf(key(1)))
    val scope = scope(keys, entryId = 1)
    var compositions = 0

    runComposeUiTest {
      setContent { BackFlagsReader(scope) { compositions++ } }
      waitForIdle()

      keys.value = keys.value + key(2)
      waitForIdle()

      assertEquals(2, compositions)
    }
  }

  @Test
  fun `popping an entry does not recompose a reader of its back flags`() {
    val keys = mutableStateOf(listOf(key(1), key(2)))
    val scope = scope(keys, entryId = 2)
    var compositions = 0

    runComposeUiTest {
      setContent { BackFlagsReader(scope) { compositions++ } }
      waitForIdle()

      keys.value = listOf(key(1))
      waitForIdle()

      assertEquals(1, compositions)
    }
  }

  @Test
  fun `popping an entry recomposes a reader of isTop once`() {
    val keys = mutableStateOf(listOf(key(1), key(2)))
    val scope = scope(keys, entryId = 2)
    var compositions = 0
    var lastTop: Boolean? = null

    runComposeUiTest {
      setContent {
        TopReader(scope) { top ->
          compositions++
          lastTop = top
        }
      }
      waitForIdle()

      keys.value = listOf(key(1))
      waitForIdle()

      assertEquals(2, compositions)
      assertEquals(false, lastTop)
    }
  }
}

@Composable
private fun BackFlagsReader(scope: GezginWrapperScope, onComposed: () -> Unit) {
  scope.canGoBack
  scope.isAloneInBackStack
  SideEffect(onComposed)
}

@Composable
private fun TopReader(scope: GezginWrapperScope, onComposed: (Boolean) -> Unit) {
  val top = scope.isTop
  SideEffect { onComposed(top) }
}
