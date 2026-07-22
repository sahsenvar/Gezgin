package dev.gezgin.mvi.annotation

import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * MVI-mode binding — placed on the VM class itself (CLASS target), binding it to a route:
 * `@MviViewModel(OrderChainRoute::class) class OrderChainViewModel(...) : GezginMvi<S,I,E>`.
 *
 * Named `MviViewModel` (not `ViewModel`) because every MVI VM extends
 * `androidx.lifecycle.ViewModel` in the same file — a bare `@ViewModel` would collide with that
 * supertype's simple name and force an import alias on every usage.
 *
 * Like every kind annotation (`@Screen` et al.), [route] is mandatory-explicit — the VM's route is
 * given directly; codegen matches `@MviViewModel` and `@Screen` by route, then attaches an optional
 * route-explicit `@EffectHandler`. Every participating declaration must be in the SAME module
 * through per-module KSP matching.
 *
 * Guardrail: an `@MviViewModel` class MUST implement [dev.gezgin.mvi.GezginMvi]; otherwise a
 * compile error. Codegen also validates the matched `@Screen(Route)` content's `(state, onIntent)`
 * and, if present, the `@EffectHandler`'s `Flow<E>` type against the VM's `GezginMvi<S,I,E>`
 * supertype arguments.
 *
 * Note: gezgin-mvi is DI-agnostic at RUNTIME; DI-detection reads the VM's
 * `@HiltViewModel`/`@KoinViewModel` annotations + ctor `@Assisted`/`@InjectedParam` by string-FQN
 * in codegen (this annotation adds no DI dependency).
 *
 * Note (naming rule): a same-module VM's navigator ctor param MUST be named `nav` so the default
 * `viewModel` resolver's DI-detection recognizes it — the same-module `nav: XNavigator` type isn't
 * generated yet in this KSP round (unresolvable), so the navigator param is matched BY NAME
 * (`nav`). A different name (e.g. `SettingsViewModel(navigator: SettingsNavigator)`) is not
 * recognized and makes the `viewModel` param required (a safe but silent degradation). The route
 * ctor param is matched BY TYPE (always resolvable), so it has no such naming constraint.
 *
 * @property route the route whose entry resolves the annotated ViewModel
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS) public annotation class MviViewModel(val route: KClass<out Route>)
