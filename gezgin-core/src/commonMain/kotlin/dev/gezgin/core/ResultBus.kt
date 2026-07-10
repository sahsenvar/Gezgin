package dev.gezgin.core

import kotlinx.coroutines.flow.*

internal class ResultBus {
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

    /**
     * (callerEntryId, edgeId) slotunun sonuç akışı — replay-until-consumed.
     * Her teslim EN FAZLA BİR collector'a gider (CAS ile). Sözleşme: anahtar başına aynı anda
     * TEK canlı collector tutun (VM-init deseni bunu doğal sağlar). Birden çok collector varsa
     * teslimler sırayla dağıtılır — sızdırılmış eski bir collector sonraki isteğin sonucunu alabilir.
     */
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
        val before = state.getAndUpdate { list -> list.filterNot { it.callerEntryId in callerEntryIds } }
        return before.filter { it.callerEntryId in callerEntryIds }
    }

    fun restore(slots: List<Slot>) { state.value = slots }
}
