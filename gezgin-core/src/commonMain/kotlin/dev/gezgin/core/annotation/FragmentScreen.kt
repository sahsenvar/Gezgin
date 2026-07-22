package dev.gezgin.core.annotation

import dev.gezgin.core.Route
import kotlin.reflect.KClass

/**
 * Brownfield Fragment interop — marks a legacy Android `androidx.fragment.app.Fragment` as a **leaf
 * destination** in Gezgin's Nav3-based navigation, registered alongside the function-target kinds
 * (`@Screen`/`@Dialog`/`@BottomSheet`/`@FullscreenModal`). Unlike those, this annotation stands on
 * the **Fragment class itself** (CLASS target), because the Fragment — not a composable — is the
 * content:
 * ```
 * @FragmentScreen(OrderChainRoute::class)
 * class OrderChainFragment : Fragment() {
 *     private val args by gezginArgs<OrderChainRoute>()    // tipli; route Bundle'dan → PD-safe
 *     private val nav  by gezginNav<OrderChainNavigator>() // the counterpart of @Screen's 'nav' param
 * }
 * ```
 *
 * **[route] is MANDATORY — no sentinel (mirrors every kind annotation:
 * `@Screen`/`@Dialog`/`@BottomSheet`/ `@FullscreenModal` and gezgin-mvi's `@MviViewModel`).** A
 * `@FragmentScreen` class has **no constructor params at all** (see below), and the route arg is
 * mandatory-explicit across the board, so the route MUST be named explicitly here. The processor
 * reads it into the fragment entry model and generates the corresponding `AndroidFragment` entry.
 *
 * **Parameterized Fragment ctor FORBIDDEN.** A `@FragmentScreen`-annotated Fragment MUST have a
 * no-arg primary constructor: Android's own instantiation contract recreates Fragments via
 * reflection on a no-arg ctor after process-death / config-change, so a parameterized ctor would
 * silently lose its args or crash on recreation. The route and navigator are delivered NOT through
 * the ctor but through the `gezginArgs`/`gezginNav` delegates (route rides the Fragment `arguments`
 * Bundle, PD-safe; nav rides an instance-keyed side-table). The processor enforces the no-arg-ctor
 * rule at model-read time (`FS1`).
 *
 * **Host precondition — the hosting `Activity` MUST be a `FragmentActivity`/`AppCompatActivity`.**
 * Hosting a Fragment via `androidx.fragment.compose.AndroidFragment` needs a `FragmentManager` in
 * the view tree, which only a `FragmentActivity` (or its `AppCompatActivity` subclass) provides — a
 * plain `ComponentActivity` crashes at RUNTIME (not compile time) when this entry is first shown.
 * Documented in the README, the sample `MainActivity`, and the on-device checklist too.
 *
 * **Android-only in practice.** Like every other Gezgin annotation this declaration is trivially
 * cross-platform (it names only `KClass<out Route>`), so it lives in `commonMain`. But its *use* is
 * Android-only: its target, `androidx.fragment.app.Fragment`, exists only on Android, and there is
 * deliberately **no dialog/bottom-sheet Fragment variant** ( — Fragment interop is screen-only; no
 * `@FragmentDialog`/`@FragmentBottomSheet`). No special multiplatform handling is needed at the
 * declaration level — the annotation simply never resolves a target on non-Android sources.
 *
 * @property route the route type rendered by the annotated Fragment
 * @author @sahsenvar
 */
@Target(AnnotationTarget.CLASS)
public annotation class FragmentScreen(val route: KClass<out Route>)
