package dev.gezgin.processor.mvi

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueArgument
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName

internal const val VIEW_MODEL_FQ = "dev.gezgin.mvi.annotation.MviViewModel"
internal const val SCREEN_EFFECT_FQ = "dev.gezgin.mvi.annotation.ScreenEffect"
internal const val GEZGIN_MVI_FQ = "dev.gezgin.mvi.GezginMvi"

// DI-detection FQNs (§10.1, Faz-5.0 spike) — read as strings, no compile dep on Hilt/Koin.
private const val HILT_VIEW_MODEL_FQ = "dagger.hilt.android.lifecycle.HiltViewModel"
private const val KOIN_VIEW_MODEL_FQ = "org.koin.core.annotation.KoinViewModel"
private const val ASSISTED_FQ = "dagger.assisted.Assisted"
private const val INJECTED_PARAM_FQ = "org.koin.core.annotation.InjectedParam"
private const val UNIT_FQ = "kotlin.Unit"

/**
 * Faz 5.1 — reads every `@MviViewModel(Route::class)`-annotated CLASS (spec §10/§10.1 MVI add-on) into a
 * validated [ViewModelModel] list, mirroring [dev.gezgin.processor.entry.EntryModelReader]'s
 * constructor shape and its collect-all-then-fail, bracketed-code error idiom (`MV1`/`MV4`) via
 * [logger]. [read] never throws — it reports every violation in one pass and returns whether the read
 * was clean alongside whatever models DID resolve.
 *
 * All `dev.gezgin.mvi.*` symbols are read as **string FQNs** — `gezgin-processor` gains NO compile
 * dependency on `gezgin-mvi` (only its test sourceset does, for fixtures), exactly like the existing
 * `dev.gezgin.core.*` reads.
 *
 * **`MV1` (guardrail, §10.1):** a `@MviViewModel` class MUST implement `GezginMvi<S,I,E>` (transitively,
 * via [getAllSuperTypes] — same pattern as `ModelReader.resultTypeArgOf`). A `@MviViewModel` that doesn't
 * → error, no model emitted for it (S/I/E are unreadable, so it would be useless to 5.2 anyway).
 *
 * **`MV4` (duplicate):** two `@MviViewModel` classes resolving to the same route → error (mirrors
 * [dev.gezgin.processor.entry.EntryModelReader]'s `SC4` duplicate-route pattern).
 *
 * **Same-module (§10.1):** KSP only sees one module's symbols per run, so a cross-module VM/content
 * pairing simply won't match — the `@MviViewModel`/`@Screen`/`@ScreenEffect` triple being in the SAME
 * module is naturally enforced by `resolver.getSymbolsWithAnnotation` returning only THIS module's
 * `@MviViewModel`s. `EntryModelReader` closes the loop symmetrically (`MV2`/`MV3`).
 */
internal class ViewModelModelReader(
    private val resolver: Resolver,
    private val logger: KSPLogger,
) {

    private var ok = true
    private val seenRouteFqs = mutableMapOf<String, String>() // routeFq -> first VM's simple name

    fun read(): Pair<List<ViewModelModel>, Boolean> {
        val models = resolver.getSymbolsWithAnnotation(VIEW_MODEL_FQ)
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { decl -> buildModel(decl) }
            .toList()
        return models to ok
    }

    private fun buildModel(decl: KSClassDeclaration): ViewModelModel? {
        val vmFq = decl.qualifiedName?.asString() ?: return null
        val vmSimpleName = decl.simpleName.asString()
        val annotation = decl.annotations.first { it.fqName() == VIEW_MODEL_FQ }

        // `@MviViewModel(route)` is a mandatory KClass arg (no sentinel, §10.1) — but stay defensive if it
        // somehow fails to resolve to a class type.
        val routeType = annotation.classArg("route")
        val routeFq = routeType?.declaration?.qualifiedName?.asString()
        if (routeFq == null) {
            error("MV1", "$vmSimpleName: @MviViewModel(route=…) type could not be resolved")
            return null
        }

        // MV1 — the VM must implement GezginMvi<S,I,E> (transitively). Its S/I/E are the whole source
        // of truth for MVI-mode (content/effect types are validated AGAINST these, never derive them),
        // so a VM without the supertype has no readable contract → reject, emit no model.
        val mviArgs = gezginMviArgs(decl)
        if (mviArgs == null) {
            error(
                "MV1",
                "$vmSimpleName (@MviViewModel(${routeFq.substringAfterLast('.')})) " +
                    "does not implement $GEZGIN_MVI_FQ<S,I,E>; both @MviViewModel and GezginMvi are required (§10.1)",
            )
            return null
        }
        val (state, intent, effect) = mviArgs

        // MN3 (Faz-5 recheck) — `walkForGezginMvi` substitutes only DIRECT type-param forwarding
        // (`Base<S,I,E> : GezginMvi<S,I,E>`). A NESTED forward (`Base<S,I,E> : GezginMvi<Wrapped<S>,I,E>`)
        // leaves an unbound `S` inside `Wrapped<S>`. Resolving/`toTypeName()`-ing that dangling parameter
        // trips the SAME KSP transitive-substitution bug the walk avoids: KSP throws exactly
        // `NoSuchElementException("No TypeParameter found for index …")`, which would ESCAPE the processor
        // and fail the round with an opaque internal error instead of a diagnostic. Materialize the S/I/E
        // TypeNames here, in one guarded spot, catching ONLY that specific exception → clean `MV1` reject
        // (task's "reject" option for MN3). Any OTHER exception (a genuinely broken/transient KSP state,
        // unrelated to nested forwarding) is NOT swallowed — it propagates so it can't be mis-diagnosed as
        // MV1. The direct-forwarding and concrete cases materialize without incident.
        val sie: Triple<TypeName, TypeName, TypeName> = try {
            Triple(state.toTypeName(), intent.toTypeName(), effect.toTypeName())
        } catch (e: NoSuchElementException) {
            error(
                "MV1",
                "$vmSimpleName: GezginMvi<S,I,E> type arguments could not be resolved (${e.message}); this is most " +
                    "likely nested generic forwarding (for example Base<S> : GezginMvi<Wrapped<S>, …>). KSP does " +
                    "not support this type-parameter substitution. Implement GezginMvi directly with concrete " +
                    "types, or forward through an intermediate base as `Base<S,I,E> : GezginMvi<S,I,E>` (§10.1)",
            )
            return null
        }
        val (stateTypeName, intentTypeName, effectTypeName) = sie

        // MV4 — two @MviViewModel classes on the same route would both try to register the same MVI entry.
        val previousOwner = seenRouteFqs[routeFq]
        if (previousOwner != null) {
            error(
                "MV4",
                "route ${routeFq.substringAfterLast('.')} is annotated by multiple @MviViewModel classes: " +
                    "$previousOwner, $vmSimpleName",
            )
            return null
        }
        seenRouteFqs[routeFq] = vmSimpleName

        // DI-detection (§10.1, Faz-5.2): read the VM's own DI annotation + its ctor params so
        // MviEntryCodegen can emit the right default `viewModel` resolver (Hilt/Koin/androidx) and
        // decide whether a default is even possible (only when every DI-relevant param is route/nav).
        val (di, assistedFactoryFq) = detectDi(decl)

        // MV13 — androidx-mode instantiability (Fragment FS1 parity). Only the ANDROIDX resolver
        // constructs the VM DIRECTLY (`initializer { VM(...) }`, MviEntryCodegen.defaultResolver); Hilt/Koin
        // hand off to hiltViewModel()/koinViewModel(), which never call `VM(...)`. When the class has NO
        // primary constructor (only parameterized secondary ctors), `ctorParams` is empty → the default
        // resolver emits a no-arg `VM()`, which is uncompilable unless an accessible no-arg constructor
        // exists. A public/internal `constructor()` (or a normal primary ctor) is fine; a route-carrying
        // secondary-only VM is rejected here with an actionable message instead of a cryptic
        // "no value passed for parameter" surfacing inside the generated GezginMviEntries.kt.
        if (di == VmDiKind.ANDROIDX && decl.primaryConstructor == null) {
            val hasNoArgCtor = decl.getConstructors().any {
                it.parameters.isEmpty() && (it.isPublic() || it.isInternal())
            }
            if (!hasNoArgCtor) {
                error(
                    "MV13",
                    "$vmSimpleName (@MviViewModel(${routeFq.substringAfterLast('.')})): androidx-mode VM has no " +
                        "primary constructor and no accessible no-arg constructor, so the generated default " +
                        "resolver's `$vmSimpleName()` call would not compile. Declare a primary constructor " +
                        "(route/nav are supplied positionally) or a public `constructor()`, or override the " +
                        "`viewModel` resolver param at the provide-entry call site (§10.1)",
                )
                return null
            }
        }

        val ctorParams = decl.primaryConstructor?.parameters.orEmpty().map { p ->
            val resolved = p.type.resolve()
            VmCtorParam(
                name = p.name?.asString().orEmpty(),
                // Best-effort: a same-module `nav: XNavigator` type isn't generated yet in this KSP
                // round, so it may be unresolved — codegen matches nav by NAME in that case (only when
                // [isError], per MJ1 — a RESOLVED non-navigator `nav` is NOT hijacked by the name).
                typeFq = resolved.fqOrString(),
                diAnnotated = p.annotations.any { it.fqName() == ASSISTED_FQ || it.fqName() == INJECTED_PARAM_FQ },
                isError = resolved.isError,
                hasDefault = p.hasDefault,
            )
        }

        return ViewModelModel(
            vmFq = vmFq,
            vmSimpleName = vmSimpleName,
            packageName = decl.packageName.asString(),
            routeFq = routeFq,
            stateTypeFq = state.fqOrString(),
            stateTypeName = stateTypeName,
            intentTypeFq = intent.fqOrString(),
            intentTypeName = intentTypeName,
            effectTypeFq = effect.fqOrString(),
            effectTypeName = effectTypeName,
            di = di,
            assistedFactoryFq = assistedFactoryFq,
            ctorParams = ctorParams,
        )
    }

    /**
     * The VM's DI framework + (for Hilt-assisted) its factory FQ, by inspecting the class's own
     * annotations (§10.1). `@HiltViewModel` with a non-sentinel `assistedFactory` → [VmDiKind.HILT_ASSISTED];
     * a bare `@HiltViewModel` → [VmDiKind.HILT_PLAIN]; `@KoinViewModel` → [VmDiKind.KOIN]; none →
     * [VmDiKind.ANDROIDX]. The sentinel guard accepts real Hilt's `HiltViewModel::class` self-referential
     * default (which the fixture stub now mirrors exactly) — and, defensively, a bare `Unit::class` — so
     * neither is mistaken for a real assisted factory.
     */
    private fun detectDi(decl: KSClassDeclaration): Pair<VmDiKind, String?> {
        val hilt = decl.annotations.firstOrNull { it.fqName() == HILT_VIEW_MODEL_FQ }
        if (hilt != null) {
            val factoryFq = hilt.classArg("assistedFactory")?.declaration?.qualifiedName?.asString()
            val isSentinel = factoryFq == null || factoryFq == UNIT_FQ || factoryFq == HILT_VIEW_MODEL_FQ
            return if (isSentinel) VmDiKind.HILT_PLAIN to null else VmDiKind.HILT_ASSISTED to factoryFq
        }
        if (decl.annotations.any { it.fqName() == KOIN_VIEW_MODEL_FQ }) return VmDiKind.KOIN to null
        return VmDiKind.ANDROIDX to null
    }

    /**
     * The three substituted type args `S, I, E` of `GezginMvi<S,I,E>` if [decl] transitively implements
     * it, else null. Three-argument sibling of `ModelReader.resultTypeArgOf`, but deliberately NOT built
     * on KSP's `getAllSuperTypes()`: that helper throws `NoSuchElementException("No TypeParameter found
     * for index …")` on the spec-§10.1-canonical `VM : BaseViewModel<S,I,E> : GezginMvi<S,I,E>` pattern
     * (a KSP transitive-type-parameter-substitution bug — the intermediate base FORWARDS its type params
     * to `GezginMvi` rather than binding concrete types). `ModelReader.resultTypeArgOf` never trips it
     * only because `ResultRoute<OrderId>` always binds a CONCRETE arg at the immediate supertype.
     *
     * Instead this walks [KSClassDeclaration.superTypes] level by level, carrying a param→concrete
     * substitution so a `GezginMvi<S,I,E>` reached through any number of type-param-forwarding bases is
     * resolved to the VM's concrete `S/I/E` (both FQ and [com.squareup.kotlinpoet.TypeName] captured
     * downstream, per the load-bearing FQ+TypeName decision on [ViewModelModel]).
     */
    private fun gezginMviArgs(decl: KSClassDeclaration): Triple<KSType, KSType, KSType>? =
        walkForGezginMvi(decl, subst = emptyMap(), visited = mutableSetOf())

    private fun walkForGezginMvi(
        decl: KSClassDeclaration,
        subst: Map<String, KSType>,
        visited: MutableSet<String>,
    ): Triple<KSType, KSType, KSType>? {
        if (!visited.add(decl.qualifiedName?.asString() ?: return null)) return null
        for (superRef in decl.superTypes) {
            val superType = superRef.resolve()
            val superDecl = superType.declaration as? KSClassDeclaration ?: continue
            // This super's actual type args, with any that are references to [decl]'s OWN type params
            // resolved through the incoming substitution to their concrete binding.
            val superArgs = superType.arguments.map { arg ->
                val t = arg.type?.resolve()
                (t?.declaration as? KSTypeParameter)?.let { subst[it.name.asString()] } ?: t
            }
            if (superDecl.qualifiedName?.asString() == GEZGIN_MVI_FQ) {
                if (superArgs.size == 3 && superArgs.all { it != null }) {
                    return Triple(superArgs[0]!!, superArgs[1]!!, superArgs[2]!!)
                }
                return null
            }
            // Recurse, binding THIS super's type params to the (already-substituted) args we resolved.
            val nextSubst = superDecl.typeParameters.mapIndexedNotNull { i, tp ->
                superArgs.getOrNull(i)?.let { tp.name.asString() to it }
            }.toMap()
            walkForGezginMvi(superDecl, nextSubst, visited)?.let { return it }
        }
        return null
    }

    private fun KSType.fqOrString(): String = declaration.qualifiedName?.asString() ?: toString()

    private fun KSAnnotation.fqName(): String? = annotationType.resolve().declaration.qualifiedName?.asString()

    private fun KSAnnotation.arg(name: String): KSValueArgument? =
        arguments.firstOrNull { it.name?.asString() == name } ?: defaultArguments.firstOrNull { it.name?.asString() == name }

    private fun KSAnnotation.classArg(name: String): KSType? = arg(name)?.value as? KSType

    private fun error(code: String, message: String) {
        logger.error("[$code] $message")
        ok = false
    }
}
