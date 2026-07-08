package dev.gezgin.processor.mvi

import com.squareup.kotlinpoet.TypeName

/**
 * One `@ViewModel(Route::class)`-annotated class that additionally implements
 * `dev.gezgin.mvi.GezginMvi<S, I, E>` (Faz 5.1, spec §10/§10.1), resolved and validated by
 * [ViewModelModelReader] into everything Faz 5.2's MVI-mode `provideXEntry` codegen needs.
 *
 * **Route linking (§10.1):** the VM binds itself to a route via `@ViewModel(Route::class)`; the
 * matching stateless `@Screen(Route::class)` content binds to the SAME route explicitly. [routeFq]
 * is the join key between this model and an [dev.gezgin.processor.entry.EntryFunctionModel] whose
 * `mvi` descriptor points back here (see the route-linking note on [ViewModelModelReader]).
 *
 * **S/I/E — FQ + [TypeName] pair per generic arg (load-bearing).** Each of state/intent/effect is
 * captured BOTH as a flattened fully-qualified string (`…FooState`, for validation/comparison/dump)
 * AND as a KotlinPoet [TypeName] (for 5.2 codegen — `StateFlow<S>`, VM references, etc.). The FQ
 * string alone flattens generics (`List<String>` → `kotlin.collections.List`); the [TypeName]
 * preserves the full parameterization. Capturing both avoids re-introducing the "generic-flattening"
 * bug the Sample Showcase phase fixed for ctor params (mirrors [dev.gezgin.processor.model.ParamModel]).
 */
data class ViewModelModel(
    val vmFq: String,
    val vmSimpleName: String,
    /** The VM class's own package — used for the same-module `@ViewModel`/`@Screen` cross-check. */
    val packageName: String,
    /** `@ViewModel(route = …)` — the route this VM (and its matching `@Screen` content) binds to. */
    val routeFq: String,
    val stateTypeFq: String,
    val stateTypeName: TypeName,
    val intentTypeFq: String,
    val intentTypeName: TypeName,
    val effectTypeFq: String,
    val effectTypeName: TypeName,
)
