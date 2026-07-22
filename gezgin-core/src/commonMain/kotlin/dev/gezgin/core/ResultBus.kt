package dev.gezgin.core

import kotlinx.coroutines.flow.*

internal class ResultBus {
  data class Slot(
    val callerEntryId: Long,
    val edgeId: String,
    val targetEntryId: Long,
    val result: NavResult<Any?>? = null,
  )

  private val state = MutableStateFlow<List<Slot>>(emptyList())
  val slots: List<Slot>
    get() = state.value

  fun launch(callerEntryId: Long, edgeId: String, targetEntryId: Long): Boolean {
    if (state.value.any { it.callerEntryId == callerEntryId && it.edgeId == edgeId }) return false
    state.update { it + Slot(callerEntryId, edgeId, targetEntryId) }
    return true
  }

  fun deliver(targetEntryId: Long, result: NavResult<Any?>): Boolean {
    var hit = false
    state.update { list ->
      list.map {
        if (it.targetEntryId == targetEntryId && it.result == null) {
          hit = true
          it.copy(result = result)
        } else it
      }
    }
    return hit
  }

  /**
   * Replay-until-consumed result flow for one caller and edge. Compare-and-set consumption sends
   * each delivery to at most one collector; callers should keep one live collector per key.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T> results(callerEntryId: Long, edgeId: String): Flow<NavResult<T>> =
    state
      .mapNotNull { list ->
        list.firstOrNull {
          it.callerEntryId == callerEntryId && it.edgeId == edgeId && it.result != null
        }
      }
      .filter { slot -> state.value.contains(slot) && consume(slot) } // The first consumer wins.
      .map { it.result as NavResult<T> }

  private fun consume(slot: Slot): Boolean {
    val cur = state.value
    return cur.contains(slot) && state.compareAndSet(cur, cur - slot)
  }

  fun dropFor(callerEntryIds: Set<Long>): List<Slot> {
    val before =
      state.getAndUpdate { list -> list.filterNot { it.callerEntryId in callerEntryIds } }
    return before.filter { it.callerEntryId in callerEntryIds }
  }

  fun restore(slots: List<Slot>) {
    state.value = slots
  }
}
