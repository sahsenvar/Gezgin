package dev.gezgin.sample.navigation

import dev.gezgin.core.ResultFlow
import dev.gezgin.core.annotation.FlowGraph
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.StartDestination
import kotlinx.serialization.Serializable

@FlowGraph
@Serializable
sealed interface AvatarFlow : ProfileGraph, ResultFlow<AvatarChoice> {

    @StartDestination
    @GoTo(CropScreenRoute::class)
    @Serializable
    data object PickSourceScreenRoute : AvatarFlow

    @GoTo(ZoomFlow::class)
    @Serializable
    data class CropScreenRoute(val source: String) : AvatarFlow

    // quitWith, sözleşmeyi DOĞRUDAN deklare eden en yakın atayı (AvatarFlow) bitirir — ZoomFlow onu
    // yalnız transitif taşır (spec §6); nested içinden çağrı hem ZoomFlow hem AvatarFlow segmentini yıkar.
    @FlowGraph
    @Serializable
    sealed interface ZoomFlow : AvatarFlow {

        @StartDestination
        @Serializable
        data object ZoomScreenRoute : ZoomFlow
    }
}
