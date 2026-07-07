package dev.gezgin.core

import kotlinx.serialization.Serializable

/** PD-safe snapshot of a [RawNavigator]: stack + id counter + in-flight/undelivered result slots. */
@Serializable
class SavedState(val keys: List<GezginKey>, val nextId: Long, val pendingSlots: List<SavedSlot>)

/**
 * Wire form of [ResultBus.Slot]. Payload durumları:
 * - in-flight (henüz teslim edilmemiş): payloadJson=null, canceled=false
 * - Value(v): payloadJson=json.encodeToString(edge.resultSerializer, v), canceled=false
 * - Canceled: payloadJson=null, canceled=true
 */
@Serializable
class SavedSlot(
    val callerEntryId: Long,
    val edgeId: String,
    val targetEntryId: Long,
    val payloadJson: String?,
    val canceled: Boolean,
)
