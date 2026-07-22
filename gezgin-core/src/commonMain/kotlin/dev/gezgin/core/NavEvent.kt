package dev.gezgin.core

/**
 * Observe-only navigation event (§10). `navigator.events: Flow<NavEvent>` emits these types — intended for
 * logging/analytics/devtools; it does NOT affect the flow (it only observes).
 *
 * @author @sahsenvar
 */
public sealed interface NavEvent {
    /**
     * A new target was pushed onto the stack.
     *
     * @property route the route that was pushed
     */
    public data class Pushed(val route: Route) : NavEvent

    /**
     * The top target was popped.
     *
     * @property route the route that was popped
     */
    public data class Popped(val route: Route) : NavEvent

    /**
     * `replaceTo`: the [removed] targets were replaced by [pushed].
     *
     * @property removed the routes removed from the stack
     * @property pushed the replacement route
     */
    public data class Replaced(val removed: List<Route>, val pushed: Route) : NavEvent

    /**
     * `backTo`/`backToStart`: popped up to [target]; [removed] are the entries taken off.
     *
     * @property target the requested target route name
     * @property removed the routes removed while reaching the target
     */
    public data class PoppedTo(val target: String, val removed: List<Route>) : NavEvent

    /**
     * A flow unit closed.
     *
     * @property flowInstanceId the instance identifier of the flow
     * @property canceled whether the flow ended without a result
     */
    public data class FlowQuit(val flowInstanceId: Long, val canceled: Boolean) : NavEvent

    /**
     * A result was dropped because there was no pending caller.
     *
     * @property edgeId the result edge whose value was dropped
     */
    public data class ResultDropped(val edgeId: String) : NavEvent

    /**
     * A `@BackTo`/`backTo` target was not found in the stack, so no pop happened.
     *
     * @property target the missing target route name
     */
    public data class BackToTargetMissing(val target: String) : NavEvent

    /**
     * A `@ReplaceTo`/`replaceTo` `clearUpTo` target (`target`) was not on the stack — NO replacement
     * happened (graceful no-op, mirroring [BackToTargetMissing]). Emitted e.g. when a `@ReplaceTo` edge is
     * fired twice in quick succession (a user double-tap): the first call already removed `clearUpTo`, so
     * the second finds it absent. The alternative — throwing from the `clearUpTo` lookup — would crash the
     * app on an ordinary double-tap, so this is an observable event instead.
     *
     * @property target the missing target route name
     */
    public data class ReplaceToTargetMissing(val target: String) : NavEvent

    /** Back was attempted at the root stack → `onRootBack` fired (the app-exit point). */
    public data object RootBack : NavEvent
}
