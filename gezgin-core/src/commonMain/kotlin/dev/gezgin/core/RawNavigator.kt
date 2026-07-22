@file:OptIn(GezginInternalApi::class)

package dev.gezgin.core

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Untyped navigation runtime used by generated typed navigators and
 * [GezginDisplay][dev.gezgin.core.compose.GezginDisplay]. Every mutation keeps [backStack] and
 * [events] synchronized with the underlying state and pending-result slots.
 *
 * This type is not thread-safe. Navigation mutations and display reads must run on the
 * application's UI thread. If navigation follows background work, switch back to the main
 * dispatcher before calling this API; an off-main mutation can race with Compose reads.
 *
 * Process-death restoration is handled by `rememberNavigator`. A restored snapshot replaces the
 * initial [start] route and recovers stack identifiers and pending results. The same application
 * `Json` configuration is used for back-stack and Fragment-argument serialization, including any
 * contextual or polymorphic serializers.
 *
 * The public constructor creates a fresh navigator for tests and custom hosts. It intentionally
 * omits restoration inputs so saved-state schema details remain internal.
 *
 * @author @sahsenvar
 */
public class RawNavigator
internal constructor(
  start: Route,
  private val topology: GezginTopology,
  internal val onRootBack: () -> Unit,
  internal val json: Json,
  restored: SavedState? = null,
) {
  /**
   * Public construction for tests and integration (`GezginTestNavigator`, custom hosts).
   * Deliberately omits the [json]/[restored] process-death parameters — those belong to the
   * same-module `rememberNavigator` restore path (the app's stable `Json` is supplied there), so
   * [SavedState] and its slot schema stay off the public surface.
   */
  public constructor(
    start: Route,
    topology: GezginTopology,
    onRootBack: () -> Unit = {},
  ) : this(start, topology, onRootBack, Json, restored = null)

  // Mutable state lets restoration update this identity-stable facade without replacing the
  // navigator reference captured by ViewModels or the flows observed by the display.
  private var state =
    if (restored != null) GezginState(restored.keys, restored.nextId, topology)
    else GezginState(emptyList(), nextId = 0, topology = topology)
  private val bus = ResultBus()

  /**
   * Display-provided predicate that identifies modal routes. [replaceTo] checks it before mutation
   * so a modal can never become the stack root; the default keeps standalone unit use independent
   * of display registration.
   */
  internal var modalRootGuard: (Route) -> Boolean = { false }

  private val _backStack = MutableStateFlow<List<Route>>(emptyList())
  /**
   * The public, observable back stack — a devtools / "where are we now" indicator. It carries only
   * `Route` (id-less); the id-aware stream the display needs is [keysState] (internal).
   */
  public val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()

  private val _keysState = MutableStateFlow<List<GezginKey>>(emptyList())
  /**
   * Id-aware entry stream for the display. Unlike [backStack], it emits when an equal route value
   * is replaced by a new entry identity, ensuring Compose rebuilds the corresponding content key.
   */
  internal val keysState: StateFlow<List<GezginKey>> = _keysState.asStateFlow()

  private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
  /**
   * The observe-only navigation event stream. `extraBufferCapacity=64` + `tryEmit` — if there is no
   * subscriber, or a slow collector fills the buffer, an event is SILENTLY DROPPED. The source of
   * truth is [backStack]/[keys]; this stream is a signal channel alongside them, not a replacement.
   */
  public val events: Flow<NavEvent> = _events

  /** Raw entry view consumed by the GezginDisplay adapter. */
  internal val keys: List<GezginKey>
    get() = state.stack

  /** The route at the top of the stack (currently active). */
  public val current: Route
    get() = state.stack.last().route

  init {
    if (restored != null) {
      bus.restore(restored.pendingSlots.map(::decodeSlot))
    } else {
      val enterFlow = topology.flowChain(start::class).isNotEmpty()
      state.push(start, enterFlow = enterFlow, singleTop = false)
    }
    refreshBackStack()
  }

  /**
   * Encodes pending slot payloads with topology serializers and the constructor's shared [json].
   */
  @Suppress("UNCHECKED_CAST")
  internal fun save(): SavedState {
    val pendingSlots =
      bus.slots.map { slot ->
        when (val result = slot.result) {
          null ->
            SavedSlot(
              slot.callerEntryId,
              slot.edgeId,
              slot.targetEntryId,
              payloadJson = null,
              canceled = false,
            )
          NavResult.Canceled ->
            SavedSlot(
              slot.callerEntryId,
              slot.edgeId,
              slot.targetEntryId,
              payloadJson = null,
              canceled = true,
            )
          is NavResult.Value<*> -> {
            val serializer =
              requireNotNull(topology.edges[slot.edgeId]?.resultSerializer) {
                "Edge '${slot.edgeId}' has no resultSerializer; delivered Value slot cannot be serialized."
              }
                as KSerializer<Any?>
            val payload = json.encodeToString(serializer, result.value)
            SavedSlot(
              slot.callerEntryId,
              slot.edgeId,
              slot.targetEntryId,
              payloadJson = payload,
              canceled = false,
            )
          }
        }
      }
    return SavedState(keys = state.stack, nextId = state.nextId, pendingSlots = pendingSlots)
  }

  @Suppress("UNCHECKED_CAST")
  private fun decodeSlot(saved: SavedSlot): ResultBus.Slot {
    val result: NavResult<Any?>? =
      when {
        saved.canceled -> NavResult.Canceled
        saved.payloadJson != null -> {
          val serializer =
            requireNotNull(topology.edges[saved.edgeId]?.resultSerializer) {
              "Edge '${saved.edgeId}' has no resultSerializer; Value payload cannot be decoded."
            }
              as KSerializer<Any?>
          NavResult.Value(json.decodeFromString(serializer, saved.payloadJson))
        }
        else -> null
      }
    return ResultBus.Slot(saved.callerEntryId, saved.edgeId, saved.targetEntryId, result)
  }

  /**
   * Atomically adopts process-restored state into this existing facade. The result bus and flow
   * objects retain their identity so current collectors remain attached. Re-adopting the same
   * snapshot is idempotent and emits no navigation event.
   */
  internal fun adoptRestored(restored: SavedState) {
    // Decode every slot before mutation. Missing edges or incompatible schemas can then fail
    // without leaving the navigator partially restored.
    val decodedSlots = restored.pendingSlots.map(::decodeSlot)
    state = GezginState(restored.keys, restored.nextId, topology)
    bus.restore(decodedSlots)
    refreshBackStack()
  }

  // Public operations.

  /** @GoTo — resolves enterFlow from the topology (is the target a flow-start container entry). */
  public fun navigate(route: Route, singleTop: Boolean = true) {
    val enterFlow = resolveEnterFlow(route)
    val pushed = state.push(route, enterFlow = enterFlow, singleTop = singleTop) ?: return
    refreshBackStack()
    _events.tryEmit(NavEvent.Pushed(pushed.route))
  }

  /**
   * Back order: (1) if the top is a flow ENTRY, delegate ENTIRELY to quit() (even if there is a
   * pending target: settleRemoved delivers Canceled), the event becomes `FlowQuit(canceled=true)`,
   * NO `Popped`; (2) a plain pop + `Popped` — if the top is a pending-target, settleRemoved
   * delivers its Canceled (a single gate). At the bottom → onRootBack.
   */
  public fun back() {
    val top = state.stack.last()
    if (isFlowEntry(top)) { // Flow-entry back delegates to quit and settles pending results.
      quit()
      return
    }
    popTopAndEmit() // A plain pop also cancels any pending target on the removed entry.
  }

  /**
   * The `@ReplaceTo` runtime: clears up to `clearUpTo` (null = the top only) and pushes `route`;
   * delivers Canceled to the removed pending-targets.
   */
  public fun replaceTo(
    route: Route,
    clearUpTo: KClass<out Route>? = null,
    inclusive: Boolean = true,
  ) {
    // Treat an absent clear target as a reported no-op before cutIndex can fail. This also makes
    // repeated replacement requests safe after the first request removes the target.
    if (!state.hasOnStack(clearUpTo)) {
      _events.tryEmit(NavEvent.ReplaceToTargetMissing(clearUpTo?.simpleName ?: "?"))
      return
    }
    // Reject a modal result root before mutation so an error boundary cannot retain an invalid
    // stack after composition reports the problem.
    val resultingRoot = state.resultingRootAfterReplace(route, clearUpTo, inclusive)
    require(!modalRootGuard(resultingRoot)) {
      "replaceTo: resulting stack root cannot be a modal kind (${resultingRoot::class.simpleName}); " +
        "a modal must have at least one SCREEN entry underneath it (Nav3 OverlayScene invariant, §7). " +
        "Do not place a modal at root with clearUpTo=root."
    }
    val before = state.stack.toList()
    val enterFlow = resolveEnterFlow(route)
    val pushed = state.replaceUpTo(route, clearUpTo, inclusive, enterFlow = enterFlow)
    val afterIds = state.stack.map { it.id }.toSet()
    val removed = before.filter { it.id !in afterIds }
    refreshBackStack()
    _events.tryEmit(NavEvent.Replaced(removed.map { it.route }, pushed.route))
    settleRemoved(removed)
  }

  /**
   * The `@BackTo` runtime: pops up to `target` in the stack (including target if `inclusive`). If
   * the target is absent, a `BackToTargetMissing` event, NO pop.
   */
  public fun backTo(target: KClass<out Route>, inclusive: Boolean = false) {
    val removed = state.backTo(target, inclusive)
    if (removed == null) {
      _events.tryEmit(NavEvent.BackToTargetMissing(target.simpleName ?: "?"))
      return
    }
    refreshBackStack()
    _events.tryEmit(NavEvent.PoppedTo(target.simpleName ?: "?", removed.map { it.route }))
    settleRemoved(removed)
  }

  /** Close the flow with Canceled (onRootBack at the root). */
  public fun quit() {
    val flowId = state.currentFlowId() ?: return
    val removed = state.quitFlow(flowId)
    if (removed == null) {
      onRootBack()
      _events.tryEmit(NavEvent.RootBack)
      return
    }
    refreshBackStack()
    _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = true))
    settleRemoved(removed) // Surviving callers receive Canceled for removed pending targets.
  }

  /**
   * Close atomically with a Value + deliver to the caller. A silent no-op when not inside a flow
   * (symmetric with quit()). In a nested ResultFlow the target = the NEAREST-ENCLOSING ResultFlow
   * while quit() stays on the innermost.
   */
  public fun quitWith(result: Any?) {
    // Prefer the innermost enclosing ResultFlow; the typed layer only exposes quitWith there.
    val top = state.stack.last()
    val chain = topology.flowChain(top.route::class) // Parallel to flowPath with equal length.
    val idx = chain.indexOfLast { it.isResultFlow }
    val flowId =
      (if (idx >= 0) top.flowPath.getOrNull(idx) else top.flowPath.lastOrNull()) ?: return
    val removed = state.quitFlow(flowId)
    if (removed == null) {
      onRootBack()
      _events.tryEmit(NavEvent.RootBack)
      return
    }
    refreshBackStack()
    _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = false))
    // Deliver the value only to the removed flow entry's slot. Other surviving callers are
    // canceled, while slots whose caller was also removed are dropped.
    settleRemoved(removed, deliverValue = result, valueTargetId = removed.first().id)
  }

  /**
   * Entry-scoped `backWithResult`: pins THE RESULT to its OWNER entry. If [entryId] is no longer
   * the top (e.g. an async job's result arrived late after the sheet was dismissed by a gesture) it
   * is a SILENT NO-OP → the value is not delivered to the slot of a foreign entry that is not
   * waiting for that slot (that expects a result of a different type) and that entry is not
   * accidentally popped (a dirty-delivery/double-back race is prevented). The typed
   * `backWithResult(result)` that codegen generates binds the ctor's `entryId` to this overload.
   *
   * If the owner is still top but has no pending slot, the result is discarded and the owner still
   * closes. This covers result-capable routes opened without a result request and avoids trapping
   * the user on screen. Delivered slots are not settled twice during the subsequent pop.
   */
  @GezginInternalApi
  public fun backWithResult(entryId: Long, result: Any?) {
    val top = state.stack.last()
    if (top.id != entryId) return // Never deliver or pop after ownership moves away from the top.
    if (isPendingTarget(top.id)) bus.deliver(top.id, NavResult.Value(result))
    popTopAndEmit() // Close the owning top entry even when no receiver exists.
  }

  /**
   * Convenience: owner = the call-time top entry. The typed layer pins the entry via the id
   * overload.
   */
  public fun backWithResult(result: Any?): Unit = backWithResult(currentEntryId, result)

  /**
   * Entry-pinned result-free back. It acts only while [entryId] remains top, preventing late or
   * duplicate modal dismissal callbacks from popping the screen underneath the former owner.
   */
  @GezginInternalApi
  public fun back(entryId: Long) {
    if (state.stack.last().id != entryId) return // Ignore callbacks from entries that lost the top.
    back()
  }

  /**
   * The call-time top entry id — the hook generated navigators bind to the explicit-caller
   * overloads.
   */
  @GezginInternalApi
  public val currentEntryId: Long
    get() = state.stack.last().id

  /**
   * A minimal public entry point for `:gezgin-test`'s typed `fromX()` access: the id of the NEAREST
   * (topmost in the stack) entry that implements [route], or `null`. `keys` stays `internal`; this
   * is the single [GezginInternalApi] opt-in member built on top of it.
   */
  @GezginInternalApi
  public fun entryIdOf(route: KClass<out Route>): Long? =
    keys.lastOrNull { route.isInstance(it.route) }?.id

  /**
   * Explicit-caller result launch. It is idempotent while a slot exists for the same (caller, edge)
   * (in-flight OR delivered-but-unconsumed). Otherwise a result request would ALWAYS create a new
   * entry.
   */
  @GezginInternalApi
  public fun launchForResult(callerEntryId: Long, edgeId: String, route: Route) {
    // Match ResultBus launch idempotence before pushing, including delivered but unconsumed slots.
    if (bus.slots.any { it.callerEntryId == callerEntryId && it.edgeId == edgeId }) return
    // A result launch always creates a fresh flow instance instead of inheriting an outer id.
    val enterFlow = topology.flowChain(route::class).isNotEmpty()
    val pushed =
      state.push(
        route,
        enterFlow = enterFlow,
        singleTop = false,
      )!! // A non-single-top result launch always creates an entry.
    bus.launch(callerEntryId, edgeId, pushed.id)
    refreshBackStack()
    _events.tryEmit(NavEvent.Pushed(pushed.route))
  }

  /**
   * Convenience overload that captures the top entry as caller at call time. Generated bindings
   * whose caller is not the top must use the explicit overload. Two quick top-based calls see
   * different callers (the first push changes the top) → if you want dedupe, use the
   * explicit-caller overload; the typed layer binds the caller to the entry.
   */
  public fun launchForResult(edgeId: String, route: Route): Unit =
    launchForResult(currentEntryId, edgeId, route)

  /** The result stream for an explicit `(caller, edge)` slot. */
  @GezginInternalApi
  public fun <T> results(callerEntryId: Long, edgeId: String): Flow<NavResult<T>> =
    bus.results(callerEntryId, edgeId)

  /**
   * Convenience: caller = the top entry id AT CALL TIME. A late re-attach after restore therefore
   * only works while the original caller entry is the current top (the call-time-top contract);
   * when the caller is not the top after process-death reattachment, the explicit
   * [results]`(callerEntryId, edgeId)` overload MUST be used.
   */
  public fun <T> results(edgeId: String): Flow<NavResult<T>> = results(currentEntryId, edgeId)

  /** Explicit-caller convenience that launches and awaits the first result. */
  @GezginInternalApi
  public suspend fun <T> navigateForResult(
    callerEntryId: Long,
    edgeId: String,
    route: Route,
  ): NavResult<T> {
    launchForResult(callerEntryId, edgeId, route)
    return bus.results<T>(callerEntryId, edgeId).first()
  }

  /** Convenience sugar; the caller is captured from the call-time top (BEFORE the push). */
  public suspend fun <T> navigateForResult(edgeId: String, route: Route): NavResult<T> =
    navigateForResult(currentEntryId, edgeId, route)

  /**
   * Implements `@QuitAndGoTo`: tear down the current flow without a result (exactly the same
   * teardown as quit(): Canceled to surviving caller-bearing pending slots, `FlowQuit(canceled =
   * true)`) and then navigate to the target. When the source is NOT INSIDE a flow (no flowId) there
   * is nothing to tear down — it is equivalent to a plain `navigate`. In the root flow (quitFlow →
   * null), the same rule as quit()/quitWith: fall to onRootBack() + emit `RootBack`, do NOT
   * navigate (the teardown itself failed).
   */
  public fun quitAndGoTo(route: Route) {
    val flowId = state.currentFlowId()
    if (flowId != null) {
      val removed = state.quitFlow(flowId)
      if (removed == null) {
        onRootBack()
        _events.tryEmit(NavEvent.RootBack)
        return
      }
      refreshBackStack()
      _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = true))
      settleRemoved(removed)
    }
    // Single-top makes repeated quit-and-navigate requests idempotent after the first tears down
    // the flow. A different target still pushes normally.
    navigate(route, singleTop = true)
  }

  // Internal helpers.

  private fun refreshBackStack() {
    _backStack.value = state.stack.map { it.route }
    _keysState.value = state.stack.toList() // Entry ids make same-route replacements observable.
  }

  private fun popTopAndEmit() {
    val popped = state.pop()
    if (popped == null) {
      onRootBack()
      _events.tryEmit(NavEvent.RootBack)
      return
    }
    refreshBackStack()
    _events.tryEmit(NavEvent.Popped(popped.route))
    settleRemoved(listOf(popped))
  }

  private fun resolveEnterFlow(route: Route): Boolean {
    val target = topology.flowChain(route::class)
    if (target.isEmpty()) return false
    val top = state.stack.lastOrNull() ?: return true
    val source = topology.flowChain(top.route::class)
    val common = target.zip(source).takeWhile { (a, b) -> a.id == b.id }.count()
    return common < target.size
  }

  private fun isPendingTarget(id: Long): Boolean =
    bus.slots.any { it.targetEntryId == id && it.result == null }

  /** Returns whether the top is the first entry in its innermost flow segment. */
  private fun isFlowEntry(top: GezginKey): Boolean {
    if (top.flowPath.isEmpty()) return false
    val innermost = top.flowPath.last()
    val below = state.stack.getOrNull(state.stack.size - 2)
    return below == null || innermost !in below.flowPath
  }

  /**
   * Settles result slots for every removal path. A value goes only to [valueTargetId]; other
   * pending targets with surviving callers receive Canceled. With no value target, all receive
   * Canceled, preventing values from leaking into unrelated result types.
   */
  private fun settleRemoved(
    removed: List<GezginKey>,
    deliverValue: Any? = null,
    valueTargetId: Long? = null,
  ) {
    if (removed.isEmpty()) return
    val removedIds = removed.map { it.id }.toSet()
    for (slot in bus.slots) {
      if (
        slot.result == null && slot.targetEntryId in removedIds && slot.callerEntryId !in removedIds
      ) {
        // The presence of valueTargetId, not the payload, distinguishes Value from Canceled. This
        // preserves a legitimate Value(null) for nullable result flows.
        bus.deliver(
          slot.targetEntryId,
          if (valueTargetId != null && slot.targetEntryId == valueTargetId)
            NavResult.Value(deliverValue)
          else NavResult.Canceled,
        )
      }
    }
    bus.dropFor(removedIds).forEach { _events.tryEmit(NavEvent.ResultDropped(it.edgeId)) }
  }
}
