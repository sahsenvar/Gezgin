package dev.gezgin.core

import kotlin.reflect.KClass

class GezginState(initial: List<GezginKey>, internal var nextId: Long, private val topology: GezginTopology) {
    private val _stack = initial.toMutableList()
    val stack: List<GezginKey> get() = _stack

    fun push(route: Route, enterFlow: Boolean = false, singleTop: Boolean = true): GezginKey? {
        val top = _stack.lastOrNull()
        if (singleTop && top?.route == route) return null
        val target = topology.flowChain(route::class)
        val source = top?.let { topology.flowChain(it.route::class) } ?: emptyList()
        var common = target.zip(source).takeWhile { (a, b) -> a.id == b.id }.count()
        if (enterFlow && target.isNotEmpty()) common = minOf(common, target.size - 1)
        val flowPath = (top?.flowPath ?: emptyList()).take(common) +
            List(target.size - common) { nextId++ }        // her yeni flow segmenti taze id
        return GezginKey(route, nextId++, flowPath).also { _stack += it }
    }

    fun pop(): GezginKey? {
        if (_stack.size <= 1) return null
        return _stack.removeAt(_stack.lastIndex)
    }

    fun replaceUpTo(route: Route, clearUpTo: KClass<out Route>?, inclusive: Boolean, enterFlow: Boolean = false): GezginKey {
        val cutFrom = if (clearUpTo == null) _stack.lastIndex else {
            val i = _stack.indexOfLast { clearUpTo.isInstance(it.route) }   // nearest-ancestor (§4.2/M3)
            require(i >= 0) { "clearUpTo hedefi stack'te yok: ${clearUpTo.simpleName}" }
            if (inclusive) i else i + 1
        }
        while (_stack.size > cutFrom) _stack.removeAt(_stack.lastIndex)
        return push(route, enterFlow = enterFlow, singleTop = false)!!   // !! güvenli: singleTop=false → push null dönemez
    }

    fun backTo(target: KClass<out Route>, inclusive: Boolean): List<GezginKey>? {
        val i = _stack.dropLast(1).indexOfLast { target.isInstance(it.route) }  // top hariç ara
        if (i < 0) return null
        val keepUntil = if (inclusive) i else i + 1
        val removed = _stack.subList(keepUntil, _stack.size).toList()
        while (_stack.size > keepUntil) _stack.removeAt(_stack.lastIndex)
        return removed
    }

    fun currentFlowId(): Long? = _stack.lastOrNull()?.flowPath?.lastOrNull()

    fun quitFlow(flowInstanceId: Long): List<GezginKey>? {
        val top = _stack.lastOrNull() ?: return null
        if (flowInstanceId !in top.flowPath) return null   // quit yalnız içinde bulunulan flow için — orta-stack pop yok
        val first = _stack.indexOfFirst { flowInstanceId in it.flowPath }
        if (first <= 0) return null                        // dipte (root flow) → üst katman onRootBack'e çevirir
        // Invariant (push'un miras kuralından): id'yi taşıyan entry'ler daima bitişik bir blok oluşturur
        // ve top-guard sayesinde bu blok stack'in tepesinde biter → filter+removeAll = tepeden atomik pop.
        val removed = _stack.filter { flowInstanceId in it.flowPath }
        _stack.removeAll { flowInstanceId in it.flowPath }
        return removed
    }
}
