package dev.gezgin.core

/**
 * Observe-only navigation event (§10). `navigator.events: Flow<NavEvent>` emits these types — intended for
 * logging/analytics/devtools; it does NOT affect the flow (it only observes).
 */
public sealed interface NavEvent {
    /** A new target was pushed onto the stack. */
    public data class Pushed(val route: Route) : NavEvent

    /** The top target was popped. */
    public data class Popped(val route: Route) : NavEvent

    /** `replaceTo`: the `removed` targets were replaced by `pushed`. */
    public data class Replaced(val removed: List<Route>, val pushed: Route) : NavEvent

    /** `backTo`/`backToStart`: popped up to `target`; `removed` are the entries taken off. */
    public data class PoppedTo(val target: String, val removed: List<Route>) : NavEvent

    /** A flow unit closed (`flowInstanceId`); `canceled` = whether it ended without a result (quit). */
    public data class FlowQuit(val flowInstanceId: Long, val canceled: Boolean) : NavEvent

    /** A result edge's (`edgeId`) result was dropped because there was no pending caller. */
    public data class ResultDropped(val edgeId: String) : NavEvent

    /** A `@BackTo`/`backTo` target (`target`) was not found in the stack — no pop happened. */
    public data class BackToTargetMissing(val target: String) : NavEvent

    /**
     * A `@ReplaceTo`/`replaceTo` `clearUpTo` target (`target`) was not on the stack — NO replacement
     * happened (graceful no-op, mirroring [BackToTargetMissing]). Emitted e.g. when a `@ReplaceTo` edge is
     * fired twice in quick succession (a user double-tap): the first call already removed `clearUpTo`, so
     * the second finds it absent. The alternative — throwing from the `clearUpTo` lookup — would crash the
     * app on an ordinary double-tap, so this is an observable event instead.
     */
    public data class ReplaceToTargetMissing(val target: String) : NavEvent

    /** Back was attempted at the root stack → `onRootBack` fired (the app-exit point). */
    public data object RootBack : NavEvent
}
