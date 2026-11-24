package dev.gezgin.core

import kotlin.reflect.KClass

/**
 * Result of [GezginState.switchTo]: the roots of the tab segment that was left and of the one that
 * became active. [created] is `true` when the target segment did not exist yet.
 */
internal class TabSwitch(val from: GezginKey, val to: GezginKey, val created: Boolean)

/**
 * Entry store split into tab segments. Every segment is an independent back stack; exactly one is
 * active. An app without `@Tab` routes keeps a single segment, so all stack operations behave as on
 * a flat list.
 *
 * [stack] is the ACTIVE segment: push, pop, back-to and flow operations act on it alone. [allKeys]
 * lists every segment (passive ones first, the active one last) and is what the display decorates,
 * so a passive tab keeps its entry identities — and therefore its ViewModel store and saved state.
 * A segment is identified by an id unique among live segments: the id of its root entry when the
 * segment is created, kept when [replaceUpTo] swaps that root so the display never mistakes a graph
 * replacement for a tab switch.
 */
internal class GezginState(
  initial: List<GezginKey>,
  internal var nextId: Long,
  private val topology: GezginTopology,
  activeSegment: Long? = null,
) {
  private val segments = LinkedHashMap<Long, MutableList<GezginKey>>()
  private var activeId: Long

  init {
    initial.forEach { segments.getOrPut(it.segment) { mutableListOf() } += it }
    activeId = activeSegment?.takeIf { it in segments } ?: initial.lastOrNull()?.segment ?: 0
  }

  // Detached empty list while no segment exists: reads see an empty stack, mutations are no-ops.
  private val _stack: MutableList<GezginKey>
    get() = segments[activeId] ?: mutableListOf()

  val stack: List<GezginKey>
    get() = _stack

  /** The active segment's id (the id of its root entry), or `null` before the first push. */
  val activeSegment: Long?
    get() = activeId.takeIf { it in segments }

  /** Passive segments in creation order, then the active segment last. */
  val allKeys: List<GezginKey>
    get() = segments.filterKeys { it != activeId }.values.flatten() + _stack

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
    val id = nextId++
    if (segments.isEmpty()) activeId = id // The first entry roots the first segment.
    return GezginKey(route, id, flowPath, segment = activeId).also {
      segments.getOrPut(activeId) { mutableListOf() } += it
    }
  }

  fun pop(): GezginKey? {
    if (_stack.size <= 1) return null
    return _stack.removeAt(_stack.lastIndex)
  }

  /**
   * Clears the active segment up to the cut and pushes [route]. A cut that reaches the segment root
   * replaces the whole navigation graph, so every passive segment is dropped with it.
   */
  fun replaceUpTo(
    route: Route,
    clearUpTo: KClass<out Route>?,
    inclusive: Boolean,
    enterFlow: Boolean = false,
  ): GezginKey {
    val cutFrom = cutIndex(clearUpTo, inclusive)
    if (cutFrom == 0) {
      val kept = activeId
      segments.clear()
      segments[kept] = mutableListOf() // The replacement root keeps the segment identity.
    } else while (_stack.size > cutFrom) _stack.removeAt(_stack.lastIndex)
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
   * Callers emit `ReplaceToTargetMissing` and return early when a non-null target is absent. Only
   * the active segment is searched.
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

  /** Removes matching entries from every segment, including passive ones. */
  fun removeAll(predicate: (GezginKey) -> Boolean): List<GezginKey> {
    val removed = segments.values.flatMap { it.filter(predicate) }
    segments.values.forEach { it.removeAll(predicate) }
    segments.values.removeAll { it.isEmpty() }
    if (activeId !in segments) activeId = segments.keys.lastOrNull() ?: 0
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

  /**
   * Makes the tab segment rooted at [tabRoot]'s type active. The left segment is kept untouched; a
   * segment that was never opened is created as `[tabRoot]`. Returns `null` when that tab is
   * already active (a no-op). No entry is recreated, so entry ids — and with them ViewModel stores
   * and saved state — survive the switch.
   */
  fun switchTo(tabRoot: Route): TabSwitch? {
    val type = tabRoot::class
    require(type in topology.tabRoots) { "switchTo target is not a @Tab route: ${type.simpleName}" }
    val from = _stack.firstOrNull() ?: error("switchTo: the navigator has no entries")
    check(from.route::class in topology.tabRoots) {
      "switchTo requires the active segment to be rooted at a @Tab route, but it is rooted at " +
        "${from.route::class.simpleName}. Make the tab root the stack root (for example " +
        "replaceTo the tab root with clearUpTo = the previous root) instead of pushing it."
    }
    if (from.route::class == type) return null
    val existing = segments.values.firstOrNull { it.first().route::class == type }
    val target = existing?.first() ?: newSegmentRoot(tabRoot)
    activeId = target.segment
    return TabSwitch(from = from, to = target, created = existing == null)
  }

  private fun newSegmentRoot(route: Route): GezginKey {
    val flowPath = List(topology.flowChain(route::class).size) { nextId++ }
    val id = nextId++
    return GezginKey(route, id, flowPath, segment = id).also { segments[id] = mutableListOf(it) }
  }
}
