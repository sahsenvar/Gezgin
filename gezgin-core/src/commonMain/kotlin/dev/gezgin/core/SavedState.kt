package dev.gezgin.core

import kotlinx.serialization.Serializable

/**
 * PD-safe snapshot of a [RawNavigator]: stack + id counter + in-flight/undelivered result slots.
 * `internal` (K1): the process-death snapshot schema is not part of the public ABI — only the same-module
 * `rememberNavigator` restore path reads it, so adding a slot field later stays a non-breaking change.
 */
@Serializable
internal class SavedState(val keys: List<GezginKey>, val nextId: Long, val pendingSlots: List<SavedSlot>)

/**
 * Wire form of [ResultBus.Slot]. Payload states:
 * - in-flight (not yet delivered): payloadJson=null, canceled=false
 * - Value(v): payloadJson=json.encodeToString(edge.resultSerializer, v), canceled=false
 * - Canceled: payloadJson=null, canceled=true
 */
@Serializable
internal class SavedSlot(
    val callerEntryId: Long,
    val edgeId: String,
    val targetEntryId: Long,
    val payloadJson: String?,
    val canceled: Boolean,
)
