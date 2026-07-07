package dev.gezgin.core

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Raw facade over [GezginState] + [ResultBus] + [NavEvent] — the untyped integration layer
 * Faz 2 (codegen) and Faz 3 (display) wrap. Single-writer: all mutation happens through the
 * methods below, each of which keeps [backStack] and [events] in sync with the underlying state.
 */
class RawNavigator(
    start: Route,
    private val topology: GezginTopology,
    internal val onRootBack: () -> Unit = {},
) {
    private val state = GezginState(emptyList(), nextId = 0, topology = topology)
    private val bus = ResultBus()

    private val _backStack = MutableStateFlow<List<Route>>(emptyList())
    val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
    val events: Flow<NavEvent> = _events

    /** GezginDisplay adapter'ı için raw entry görünümü. */
    internal val keys: List<GezginKey> get() = state.stack

    val current: Route get() = state.stack.last().route

    init {
        val enterFlow = topology.flowChain(start::class).isNotEmpty()
        state.push(start, enterFlow = enterFlow, singleTop = false)
        refreshBackStack()
    }

    // ---- public ops ----

    /** @GoTo — enterFlow'u topology'den çözer (hedef flow-start container-entry'si mi). */
    fun navigate(route: Route, singleTop: Boolean = true) {
        val enterFlow = resolveEnterFlow(route)
        val pushed = state.push(route, enterFlow = enterFlow, singleTop = singleTop) ?: return
        refreshBackStack()
        _events.tryEmit(NavEvent.Pushed(pushed.route))
    }

    /** pop; top pending-target ise Canceled teslim; dipte → onRootBack; flow-entry'de → quit(). */
    fun back() {
        val top = state.stack.last()
        if (isPendingTarget(top.id)) {
            bus.deliver(top.id, NavResult.Canceled)
            popTopAndEmit()
            return
        }
        if (isFlowEntry(top)) {
            quit()
            return
        }
        popTopAndEmit()
    }

    fun replaceTo(route: Route, clearUpTo: KClass<out Route>? = null, inclusive: Boolean = true) {
        val before = state.stack.toList()
        val enterFlow = resolveEnterFlow(route)
        val pushed = state.replaceUpTo(route, clearUpTo, inclusive, enterFlow = enterFlow)
        val afterIds = state.stack.map { it.id }.toSet()
        val removed = before.filter { it.id !in afterIds }
        refreshBackStack()
        _events.tryEmit(NavEvent.Replaced(removed.map { it.route }, pushed.route))
        dropCallers(removed)
    }

    fun backTo(target: KClass<out Route>, inclusive: Boolean = false) {
        val removed = state.backTo(target, inclusive)
        if (removed == null) {
            _events.tryEmit(NavEvent.BackToTargetMissing(target.simpleName ?: "?"))
            return
        }
        refreshBackStack()
        dropCallers(removed)
    }

    /** Canceled ile flow kapat (root'ta onRootBack). */
    fun quit() {
        val flowId = state.currentFlowId() ?: return
        val removed = state.quitFlow(flowId)
        if (removed == null) {
            onRootBack()
            _events.tryEmit(NavEvent.RootBack)
            return
        }
        removed.forEach { entry -> if (isPendingTarget(entry.id)) bus.deliver(entry.id, NavResult.Canceled) }
        refreshBackStack()
        _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = true))
        dropCallers(removed)
    }

    /** Value ile atomik kapat + caller'a teslim. Flow içinde değilken sessiz no-op (quit() ile simetrik). */
    fun quitWith(result: Any?) {
        val flowId = state.currentFlowId() ?: return
        val removed = state.quitFlow(flowId)
        if (removed == null) {
            onRootBack()
            _events.tryEmit(NavEvent.RootBack)
            return
        }
        removed.forEach { entry -> if (isPendingTarget(entry.id)) bus.deliver(entry.id, NavResult.Value(result)) }
        refreshBackStack()
        _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = false))
        dropCallers(removed)
    }

    /** top = pending target ise deliver + pop; değilse no-op (raw katman — sessizce yok sayar). */
    fun backWithResult(result: Any?) {
        val top = state.stack.last()
        if (!isPendingTarget(top.id)) return
        bus.deliver(top.id, NavResult.Value(result))
        popTopAndEmit()
    }

    /** idempotent (§6): aynı (caller, edge) için slot varken (in-flight VEYA teslim edilmiş-tüketilmemiş) push YAPMA. */
    fun launchForResult(edgeId: String, route: Route) {
        val caller = state.stack.last().id
        // Pre-guard, bus.launch'un predicate'iyle birebir aynı (HERHANGİ bir slot — result durumu fark etmez):
        // teslim edilmiş ama tüketilmemiş slot varken re-launch, slotsuz öksüz bir entry push'lardı.
        if (bus.slots.any { it.callerEntryId == caller && it.edgeId == edgeId }) return
        // @GoForResult edge'i DAİMA container-entry'dir → hedef flow için taze instance mint (spec §8.1
        // re-entrancy sınırı: aynı flow tipine içten re-entry, dış instance'ın id'sini miras ALMAZ).
        val enterFlow = topology.flowChain(route::class).isNotEmpty()
        val pushed = state.push(route, enterFlow = enterFlow, singleTop = true) ?: return
        // Guard yukarıda bus.launch'un predicate'ini aynen uyguladı ve bu katman senkron/tek-yazar
        // olduğu için buradaki launch false dönemez.
        bus.launch(caller, edgeId, pushed.id)
        refreshBackStack()
        _events.tryEmit(NavEvent.Pushed(pushed.route))
    }

    /** caller = ÇAĞRI ANINDAKİ top entry id. */
    fun <T> results(edgeId: String): Flow<NavResult<T>> {
        val caller = state.stack.last().id
        return bus.results(caller, edgeId)
    }

    /** sugar = launch + results.first(); caller çağrı anındaki top'tan (push'tan ÖNCE) yakalanır. */
    suspend fun <T> navigateForResult(edgeId: String, route: Route): NavResult<T> {
        val caller = state.stack.last().id
        launchForResult(edgeId, route)
        return bus.results<T>(caller, edgeId).first()
    }

    // ---- internal helpers ----

    private fun refreshBackStack() {
        _backStack.value = state.stack.map { it.route }
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
        dropCallers(listOf(popped))
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

    /** top, kendi flow segmentinin İLK entry'si mi (altındaki entry o innermost id'yi taşımıyor). */
    private fun isFlowEntry(top: GezginKey): Boolean {
        if (top.flowPath.isEmpty()) return false
        val innermost = top.flowPath.last()
        val below = state.stack.getOrNull(state.stack.size - 2)
        return below == null || innermost !in below.flowPath
    }

    private fun dropCallers(removed: List<GezginKey>) {
        if (removed.isEmpty()) return
        val dropped = bus.dropFor(removed.map { it.id }.toSet())
        dropped.forEach { _events.tryEmit(NavEvent.ResultDropped(it.edgeId)) }
    }
}
