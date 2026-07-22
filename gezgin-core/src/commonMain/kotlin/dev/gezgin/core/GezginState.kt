package dev.gezgin.core

import kotlin.reflect.KClass

internal class GezginState(
  initial: List<GezginKey>,
  internal var nextId: Long,
  private val topology: GezginTopology,
) {
  private val _stack = initial.toMutableList()
  val stack: List<GezginKey>
    get() = _stack

  fun push(route: Route, enterFlow: Boolean = false, singleTop: Boolean = true): GezginKey? {
    val top = _stack.lastOrNull()
    if (singleTop && top?.route == route) return null
    val target = topology.flowChain(route::class)
    val source = top?.let { topology.flowChain(it.route::class) } ?: emptyList()
    var common = target.zip(source).takeWhile { (a, b) -> a.id == b.id }.count()
    if (enterFlow && target.isNotEmpty()) common = minOf(common, target.size - 1)
    val flowPath =
      (top?.flowPath ?: emptyList()).take(common) +
        List(target.size - common) { nextId++ } // Each new flow segment receives a fresh id.
    return GezginKey(route, nextId++, flowPath).also { _stack += it }
  }

  fun pop(): GezginKey? {
    if (_stack.size <= 1) return null
    return _stack.removeAt(_stack.lastIndex)
  }

  fun replaceUpTo(
    route: Route,
    clearUpTo: KClass<out Route>?,
    inclusive: Boolean,
    enterFlow: Boolean = false,
  ): GezginKey {
    val cutFrom = cutIndex(clearUpTo, inclusive)
    while (_stack.size > cutFrom) _stack.removeAt(_stack.lastIndex)
    return push(
      route,
      enterFlow = enterFlow,
      singleTop = false,
    )!! // A non-single-top push always creates an entry.
  }

  /** Computes the resulting root before [RawNavigator.replaceTo] mutates the stack. */
  fun resultingRootAfterReplace(
    route: Route,
    clearUpTo: KClass<out Route>?,
    inclusive: Boolean,
  ): Route = if (cutIndex(clearUpTo, inclusive) == 0) route else _stack.first().route

  /**
   * Checks whether `clearUpTo` exists before [RawNavigator.replaceTo] mutates the stack or reaches
   * [cutIndex]'s precondition. A `null` target means "replace only the top" and is always valid.
   * Callers emit `ReplaceToTargetMissing` and return early when a non-null target is absent.
   */
  fun hasOnStack(clearUpTo: KClass<out Route>?): Boolean =
    clearUpTo == null || _stack.any { clearUpTo.isInstance(it.route) }

  /** Returns the number of entries retained by replacement. */
  private fun cutIndex(clearUpTo: KClass<out Route>?, inclusive: Boolean): Int =
    if (clearUpTo == null) _stack.lastIndex
    else {
      val i = _stack.indexOfLast { clearUpTo.isInstance(it.route) } // Nearest matching ancestor.
      require(i >= 0) { "clearUpTo target is not on the stack: ${clearUpTo.simpleName}" }
      if (inclusive) i else i + 1
    }

  fun backTo(target: KClass<out Route>, inclusive: Boolean): List<GezginKey>? {
    val i = _stack.dropLast(1).indexOfLast { target.isInstance(it.route) } // Exclude the top.
    if (i < 0) return null
    val keepUntil =
      maxOf(
        if (inclusive) i else i + 1,
        1,
      ) // Preserve the root entry even when an inclusive pop targets it.
    val removed = _stack.subList(keepUntil, _stack.size).toList()
    while (_stack.size > keepUntil) _stack.removeAt(_stack.lastIndex)
    return removed
  }

  fun currentFlowId(): Long? = _stack.lastOrNull()?.flowPath?.lastOrNull()

  fun quitFlow(flowInstanceId: Long): List<GezginKey>? {
    val top = _stack.lastOrNull() ?: return null
    if (flowInstanceId !in top.flowPath)
      return null // Quit applies only to the flow that contains the current top.
    val first = _stack.indexOfFirst { flowInstanceId in it.flowPath }
    if (first <= 0) return null // The caller maps a root-flow quit to onRootBack.
    // Flow ids are inherited as one contiguous block ending at the top, so removal is atomic.
    val removed = _stack.filter { flowInstanceId in it.flowPath }
    _stack.removeAll { flowInstanceId in it.flowPath }
    return removed
  }
}
