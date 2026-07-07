package dev.gezgin.core

sealed interface NavEvent {
    data class Pushed(val route: Route) : NavEvent
    data class Popped(val route: Route) : NavEvent
    data class Replaced(val removed: List<Route>, val pushed: Route) : NavEvent
    data class FlowQuit(val flowInstanceId: Long, val canceled: Boolean) : NavEvent
    data class ResultDropped(val edgeId: String) : NavEvent
    data class BackToTargetMissing(val target: String) : NavEvent
    data object RootBack : NavEvent
}
