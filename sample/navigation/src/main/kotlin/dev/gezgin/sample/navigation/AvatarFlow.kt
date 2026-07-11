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

    @FlowGraph
    @Serializable
    sealed interface ZoomFlow : AvatarFlow {

        @StartDestination
        @Serializable
        data object ZoomScreenRoute : ZoomFlow
    }
}
