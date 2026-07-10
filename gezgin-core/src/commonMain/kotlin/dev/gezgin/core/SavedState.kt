package dev.gezgin.core

import kotlinx.serialization.Serializable

/** PD-safe snapshot of a [RawNavigator]: stack + id counter + in-flight/undelivered result slots. */
@Serializable
public class SavedState(public val keys: List<GezginKey>, public val nextId: Long, public val pendingSlots: List<SavedSlot>)

/**
 * Wire form of [ResultBus.Slot]. Payload durumları:
 * - in-flight (henüz teslim edilmemiş): payloadJson=null, canceled=false
 * - Value(v): payloadJson=json.encodeToString(edge.resultSerializer, v), canceled=false
 * - Canceled: payloadJson=null, canceled=true
 */
@Serializable
public class SavedSlot(
    public val callerEntryId: Long,
    public val edgeId: String,
    public val targetEntryId: Long,
    public val payloadJson: String?,
    public val canceled: Boolean,
)
