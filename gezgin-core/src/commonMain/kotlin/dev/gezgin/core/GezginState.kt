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
}
