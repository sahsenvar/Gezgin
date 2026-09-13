package dev.gezgin.sample.navigation

import dev.gezgin.core.ResultFlow
import dev.gezgin.core.annotation.FlowGraph
import dev.gezgin.core.annotation.GoTo
import dev.gezgin.core.annotation.StartDestination
import dev.gezgin.sample.domain.model.AvatarChoice

@FlowGraph
sealed interface AvatarFlow : ProfileGraph, ResultFlow<AvatarChoice> {

  @StartDestination @GoTo(CropScreenRoute::class) data object PickSourceScreenRoute : AvatarFlow

  @GoTo(ZoomFlow::class) data class CropScreenRoute(val source: String) : AvatarFlow

  @FlowGraph
  sealed interface ZoomFlow : AvatarFlow {

    @StartDestination data object ZoomScreenRoute : ZoomFlow
  }
}
