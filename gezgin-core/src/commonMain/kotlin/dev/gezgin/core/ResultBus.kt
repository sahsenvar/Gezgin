package dev.gezgin.core

import kotlinx.coroutines.flow.*

class ResultBus {
    data class Slot(val callerEntryId: Long, val edgeId: String, val targetEntryId: Long, val result: NavResult<Any?>? = null)
    private val state = MutableStateFlow<List<Slot>>(emptyList())
    val slots: List<Slot> get() = state.value

    fun launch(callerEntryId: Long, edgeId: String, targetEntryId: Long): Boolean {
        if (state.value.any { it.callerEntryId == callerEntryId && it.edgeId == edgeId }) return false
        state.update { it + Slot(callerEntryId, edgeId, targetEntryId) }
        return true
    }

    fun deliver(targetEntryId: Long, result: NavResult<Any?>): Boolean {
        var hit = false
        state.update { list -> list.map { if (it.targetEntryId == targetEntryId && it.result == null) { hit = true; it.copy(result = result) } else it } }
        return hit
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> results(callerEntryId: Long, edgeId: String): Flow<NavResult<T>> = state
        .mapNotNull { list -> list.firstOrNull { it.callerEntryId == callerEntryId && it.edgeId == edgeId && it.result != null } }
        .filter { slot -> state.value.contains(slot) && consume(slot) }   // ilk tüketici kazanır
        .map { it.result as NavResult<T> }

    private fun consume(slot: Slot): Boolean {
        val cur = state.value
        return cur.contains(slot) && state.compareAndSet(cur, cur - slot)
    }

    fun dropFor(callerEntryIds: Set<Long>): List<Slot> {
        val dropped = state.value.filter { it.callerEntryId in callerEntryIds }
        state.update { list -> list.filterNot { it.callerEntryId in callerEntryIds } }
        return dropped
    }

    fun restore(slots: List<Slot>) { state.value = slots }
}
