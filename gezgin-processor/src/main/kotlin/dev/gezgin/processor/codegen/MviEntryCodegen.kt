package dev.gezgin.processor.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import dev.gezgin.processor.entry.EntryFunctionModel
import dev.gezgin.processor.entry.MviEntryModel
import dev.gezgin.processor.entry.MviExtraParam
import dev.gezgin.processor.mvi.ViewModelModel
import dev.gezgin.processor.mvi.VmCtorParam
import dev.gezgin.processor.mvi.VmDiClassifier
import dev.gezgin.processor.mvi.VmDiKind

private const val COMPOSE_PKG = "dev.gezgin.core.compose"
private const val COMPOSE_RUNTIME_PKG = "androidx.compose.runtime"

private val ENTRY_SCOPE = ClassName(COMPOSE_PKG, "GezginEntryScope")
private val ENTRY_KIND = ClassName(COMPOSE_PKG, "EntryKind")
private val LOCAL_ENTRY_ID = MemberName(COMPOSE_PKG, "LocalGezginEntryId")
private val LOCAL_RAW_NAVIGATOR = MemberName(COMPOSE_PKG, "LocalGezginRawNavigator")
private val LOCAL_SHEET_CONTROLLER = MemberName(COMPOSE_PKG, "LocalGezginSheetController")
private val COMPOSABLE = ClassName(COMPOSE_RUNTIME_PKG, "Composable")
private val COLUMN = MemberName("androidx.compose.foundation.layout", "Column")
private val FILL_MAX_WIDTH = MemberName("androidx.compose.foundation.layout", "fillMaxWidth")
private val IME = MemberName("androidx.compose.foundation.layout", "ime")
private val WINDOW_INSETS = ClassName("androidx.compose.foundation.layout", "WindowInsets")
private val MODIFIER = ClassName("androidx.compose.ui", "Modifier")
private val LOCAL_DENSITY = ClassName("androidx.compose.ui.platform", "LocalDensity")

// State/effect observation — the JB `androidx.lifecycle.*` coordinates pinned in gezgin-mvi (Faz-5.0);
// emitted as FQ strings only (gezgin-processor gains no dep on them), mirroring EntryCodegen.
private val COLLECT_AS_STATE = MemberName("androidx.lifecycle.compose", "collectAsStateWithLifecycle")

// DI-detected default `viewModel` resolvers — emitted as FQ strings, no compile dep on Hilt/Koin.
private val ANDROIDX_VIEW_MODEL = MemberName("androidx.lifecycle.viewmodel.compose", "viewModel")
private val VIEW_MODEL_FACTORY = MemberName("androidx.lifecycle.viewmodel", "viewModelFactory")
private val INITIALIZER = MemberName("androidx.lifecycle.viewmodel", "initializer")
private val KOIN_VIEW_MODEL = MemberName("org.koin.compose.viewmodel", "koinViewModel")
private val PARAMETERS_OF = MemberName("org.koin.core.parameter", "parametersOf")

// Hilt package pin (§10.1 open question): androidx.hilt:hilt-navigation-compose 1.4.0 deprecates this
// FQ in favor of androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel (new artifact). The OLDER FQ
// is chosen deliberately — it RESOLVES on today's typical (pre-1.4.0) Hilt-BOM pin with no warning; the
// new FQ would be an *unresolved reference* there. Worst case on a 1.4.0+ pin is a deprecation warning,
// which the user silences/migrates in their own module (never here — gezgin-processor emits a string).
private val HILT_VIEW_MODEL = MemberName("androidx.hilt.navigation.compose", "hiltViewModel")

// M3 — the @BottomSheet role extra is now Gezgin's own `GezginSheetController` (fed from
// `LocalGezginSheetController.current`), not the experimental material3 `SheetState`, so no
// `@ExperimentalMaterial3Api` opt-in leaks into generated code.
private const val SHEET_CONTROLLER_FQ = "dev.gezgin.core.compose.GezginSheetController"

/**
 * Faz 5.2 — emits `fun GezginEntryScope.provideXEntry(...)` for every MVI-mode [EntryFunctionModel]
 * (`entry.mvi != null`, spec §10.1). The counterpart to core-mode's [EntryCodegen]: same
 * `GezginEntryScope` extension + `register<Route>(...)` shape (no wrapper type), grouped one [FileSpec]
 * per composable package — but into a SEPARATE `GezginMviEntries.kt` (see [generate]) so a feature
 * module mixing both entry styles gets `GezginEntries.kt` (core) + `GezginMviEntries.kt` (MVI) with no
 * same-name-same-package file collision (function-name clashes across the two are already prevented by
 * `EntryModelReader`'s shared `SC6`).
 *
 * ```kotlin
 * fun GezginEntryScope.provideOrderChainEntry(
 *     viewModel: @Composable (nav: OrderChainNavigator, args: OrderChainRoute) -> OrderChainViewModel =
 *         { nav, args -> koinViewModel { parametersOf(args, nav) } },   // DI-detected default
 * ) {
 *     register<OrderChainRoute>(kind = EntryKind.SCREEN, noBack = false) { route ->
 *         val nav = LocalGezginRawNavigator.current.orderChainNavigator(LocalGezginEntryId.current)
 *         val vm = viewModel(nav, route)
 *         val state by vm.uiState.collectAsStateWithLifecycle()
 *         OrderChainEffects(effects = vm.effects)              // @EffectHandler matched by route
 *         OrderChainContent(state = state, onIntent = vm::onIntent)   // stateless @Screen content
 *     }
 * }
 * ```
 *
 * **DI default resolver (§10.1 rule 1):** a default is emitted ONLY when every DI-relevant ctor param
 * is `route`- or `nav`-typed AND neither role is duplicated (relevant = `@Assisted`/`@InjectedParam` for
 * Hilt/Koin; ALL for androidx; NONE for plain Hilt). Any other relevant param (e.g. `@Assisted userId:
 * String`), or two route- / two nav-typed relevant params (which the resolver couldn't positionally
 * disambiguate) → no default, the `viewModel` param becomes REQUIRED. Always override-able either way.
 * (The route/nav/`emitDefault` classification is shared with [dev.gezgin.processor.entry.EntryModelReader]
 * via [VmDiClassifier] so its `MV7` nav-presence guardrail and this codegen never disagree.)
 *
 * **`nav` wiring (§10.1 open question, resolved: conditional):** unlike the spec's literal always-`nav`
 * example, `nav` is wired only when the VM ctor actually declares a `nav` param OR the matched
 * `@EffectHandler` takes one — mirroring core-mode's conditional `hasNavParam`. An MVI VM with no edges
 * never forces an `xNavigator()` factory call that couldn't resolve. When wired, the factory is
 * qualified against the ROUTE's package (`entry.routePackageName`), exactly like [EntryCodegen].
 *
 * **Problem 2 (§10.1 rule 2):** each `resolverExtraParam` becomes a REQUIRED `@Composable () -> T`
 * param, threaded as `<name>()` into the content call. Role extras (`controller`) read from
 * `LocalGezginSheetController`. All content extras are passed as NAMED args so the split role/resolver
 * lists need not reconstruct the composable's original parameter order.
 */
internal object MviEntryCodegen {

    fun generate(entries: List<EntryFunctionModel>): List<FileSpec> =
        entries.filter { it.mvi != null }
            // Reproducible emit order (MN-1) — KSP symbol order isn't contractually stable; sort by
            // (packageName, routeFq[unique]) to match graph-derived codegen's determinism.
            .sortedWith(compareBy({ it.packageName }, { it.routeFq }))
            .groupBy { it.packageName }
            .map { (packageName, group) ->
                FileSpec.builder(packageName, "GezginMviEntries")
                    // `val state by … collectAsStateWithLifecycle()` needs the State delegate operator.
                    .addImport(COMPOSE_RUNTIME_PKG, "getValue")
                    // K4 — a nav-wired register body reads the @GezginInternalApi LocalGezginRawNavigator/
                    // LocalGezginEntryId; opt in the file only when at least one entry wires nav.
                    .apply { if (group.any { navWiredOf(it) }) optInGezginInternalApi() }
                    .apply { group.forEach { addFunction(provideMviEntryFun(it)) } }
                    .build()
            }

    /** Whether an MVI entry's register body reads the gated navigator locals (VM or effect wants nav). */
    private fun navWiredOf(entry: EntryFunctionModel): Boolean {
        val mvi = entry.mvi!!
        val navigatorTypeFq = VmDiClassifier.navigatorTypeFq(entry.routePackageName, entry.x)
        val vmHasNav = VmDiClassifier.classify(mvi.vm, entry.routeFq, navigatorTypeFq).vmHasNav
        val effectWantsNav = mvi.effectFunSimpleName != null && mvi.effectHasNavParam
        return vmHasNav || effectWantsNav
    }

    private fun provideMviEntryFun(entry: EntryFunctionModel): FunSpec {
        val mvi = entry.mvi!!
        val vm = mvi.vm
        val vmClass = ClassName.bestGuess(vm.vmFq)
        val routeClass = ClassName.bestGuess(entry.routeFq)
        val navigatorClass = ClassName(entry.routePackageName, "${entry.x}Navigator")
        val navigatorTypeFq = VmDiClassifier.navigatorTypeFq(entry.routePackageName, entry.x)

        // Shared route/nav/other classification (also used by EntryModelReader's MV7 guardrail so the two
        // never drift). `emitDefault` already folds in Problem-1 (an OTHER param) AND the dup-role guard
        // (two route- or two nav-typed relevant params → no default, never a silent `VM(args, args)`).
        fun roleOf(p: VmCtorParam) = VmDiClassifier.roleOf(p, entry.routeFq, navigatorTypeFq)
        val classification = VmDiClassifier.classify(vm, entry.routeFq, navigatorTypeFq)
        val vmHasNav = classification.vmHasNav
        val vmHasRoute = classification.vmHasRoute
        val emitDefault = classification.emitDefault

        val effectWantsNav = mvi.effectFunSimpleName != null && mvi.effectHasNavParam
        val navWired = vmHasNav || effectWantsNav

        // region `viewModel` resolver param (+ optional DI-detected default)
        val lambdaParams = buildList {
            if (vmHasNav) add(ParameterSpec.builder("nav", navigatorClass).build())
            add(ParameterSpec.builder("args", routeClass).build())
        }
        val vmLambdaType = LambdaTypeName.get(parameters = lambdaParams, returnType = vmClass)
            .copy(annotations = listOf(AnnotationSpec.builder(COMPOSABLE).build()))
        val viewModelParam = ParameterSpec.builder("viewModel", vmLambdaType).apply {
            if (emitDefault) defaultValue(defaultResolver(vm, vmClass, vmHasNav, vmHasRoute, ::roleOf))
        }.build()
        // endregion

        val funBuilder = FunSpec.builder("provide${entry.x}Entry")
            .receiver(ENTRY_SCOPE)
            .addParameter(viewModelParam)
        // Problem-2 resolver params — required (no sensible default), threaded as `name()` into content.
        mvi.resolverExtraParams.forEach { extra ->
            funBuilder.addParameter(
                ParameterSpec.builder(
                    extra.name,
                    LambdaTypeName.get(returnType = extra.typeName)
                        .copy(annotations = listOf(AnnotationSpec.builder(COMPOSABLE).build())),
                ).build(),
            )
        }

        funBuilder.addCode(registerBody(entry, mvi, vmHasNav, navWired, effectWantsNav))
        return funBuilder.build()
    }

    /** The DI-detected default `{ [nav, ]args -> … }` resolver lambda (only reached when [emitDefault]). */
    private fun defaultResolver(
        vm: ViewModelModel,
        vmClass: ClassName,
        vmHasNav: Boolean,
        vmHasRoute: Boolean,
        roleOf: (VmCtorParam) -> VmDiClassifier.Role,
    ): CodeBlock {
        val header = if (vmHasNav) "nav, args" else "args"
        // Values Gezgin supplies. The fixed (args, nav) order here is SAFE regardless of the user's
        // declared param order — NOT because the user must match a convention:
        //  • Koin's parametersOf/@InjectedParam resolves injected params by TYPE, not position (route and
        //    nav are always distinct types), so this list's order is irrelevant to the Koin lookup.
        //  • Hilt's `factory.create(args, nav)` is a plain, compiler-type-checked call against the user's
        //    own @AssistedFactory method — a wrong-order factory declaration fails to COMPILE, it never
        //    silently miswires. (androidx uses `ctorArgs` below, which follows the VM's declared order.)
        val suppliedArgs = buildList {
            if (vmHasRoute) add("args")
            if (vmHasNav) add("nav")
        }.joinToString(", ")

        return when (vm.di) {
            VmDiKind.ANDROIDX -> {
                // NAMED constructor call (MN1) over the route/nav params ONLY — order-independent, and any
                // defaulted OTHER param (MN4, e.g. `retries: Int = 3`) is OMITTED so the VM's own default
                // applies. `emitDefault` guarantees there are no NON-defaulted OTHER params here.
                val ctorArgs = vm.ctorParams
                    .filter { roleOf(it) != VmDiClassifier.Role.OTHER }
                    .joinToString(", ") { "${it.name} = ${if (roleOf(it) == VmDiClassifier.Role.NAV) "nav" else "args"}" }
                CodeBlock.of(
                    "{ %L -> %M(factory = %M { %M { %T(%L) } }) }",
                    header, ANDROIDX_VIEW_MODEL, VIEW_MODEL_FACTORY, INITIALIZER, vmClass, ctorArgs,
                )
            }

            VmDiKind.KOIN ->
                CodeBlock.of("{ %L -> %M { %M(%L) } }", header, KOIN_VIEW_MODEL, PARAMETERS_OF, suppliedArgs)

            VmDiKind.HILT_ASSISTED -> {
                val factoryClass = ClassName.bestGuess(requireNotNull(vm.assistedFactoryFq))
                CodeBlock.of(
                    "{ %L -> %M<%T, %T>(creationCallback = { factory -> factory.create(%L) }) }",
                    header, HILT_VIEW_MODEL, vmClass, factoryClass, suppliedArgs,
                )
            }

            // Plain Hilt supplies nothing itself — Hilt injects everything; Gezgin passes no route/nav
            // (a route-data-carrying route is rejected as MV12, since Nav3 can't feed SavedStateHandle).
            VmDiKind.HILT_PLAIN -> CodeBlock.of("{ %M<%T>() }", HILT_VIEW_MODEL, vmClass)
        }
    }

    private fun registerBody(
        entry: EntryFunctionModel,
        mvi: MviEntryModel,
        vmHasNav: Boolean,
        navWired: Boolean,
        effectWantsNav: Boolean,
    ): CodeBlock {
        val routeClass = ClassName.bestGuess(entry.routeFq)
        val contentFun = MemberName(entry.packageName, entry.functionSimpleName)

        val body = CodeBlock.builder()
            .add(
                "register<%T>(kind = %T.%L, noBack = %L) { route ->\n",
                routeClass, ENTRY_KIND, entry.kind.name, entry.noBack,
            )
            .indent()

        if (navWired) {
            // Identical to core-mode: the factory extension lives in the route's own package.
            val factoryFun = MemberName(entry.routePackageName, NavigatorCodegen.rawFactoryFunName(entry.x))
            body.add("val nav = %M.current.%M(%M.current)\n", LOCAL_RAW_NAVIGATOR, factoryFun, LOCAL_ENTRY_ID)
        }

        body.add("val vm = viewModel(%L)\n", if (vmHasNav) "nav, route" else "route")
        body.add("val state by vm.uiState.%M()\n", COLLECT_AS_STATE)

        if (mvi.effectFunSimpleName != null) {
            val effectFun = MemberName(requireNotNull(mvi.effectFunPackageName), mvi.effectFunSimpleName)
            // MN1 — NAMED args so a `fun XEffects(nav: …, effects: Flow<E>)` (nav-first) binder wires
            // correctly regardless of declared order. Flow param name is captured off the binder.
            val flowName = requireNotNull(mvi.effectFlowParamName)
            val effectArgs = CodeBlock.builder().add("%L = vm.effects", flowName)
            if (effectWantsNav) effectArgs.add(", nav = nav")
            if (mvi.effectHasIntentParam) effectArgs.add(", onIntent = vm::onIntent")
            body.add("%M(%L)\n", effectFun, effectArgs.build())
        }

        if (mvi.bottomBar != null) {
            body.add("val imeVisible = %T.%M.getBottom(%T.current) > 0\n", WINDOW_INSETS, IME, LOCAL_DENSITY)
        }

        // Migration-only ZAD compatibility wrapper. The nested Column preserves a ColumnScope receiver
        // for existing screen bodies while keeping top/content/bottom ordering route-local.
        body.add("%M {\n", COLUMN).indent()
        mvi.topBar?.let { topBar ->
            body.add("%M(%L)\n", MemberName(topBar.packageName, topBar.functionSimpleName), chromeArgs())
        }
        // `weight` is a ColumnScope member extension. Emitting a top-level import resolves Compose's
        // internal RowColumnParentData property on Android; keep the call literal so the outer Column
        // receiver supplies the public ColumnScope.weight extension.
        body.add("%M(%T.%M().weight(1f)) {\n", COLUMN, MODIFIER, FILL_MAX_WIDTH).indent()
        body.add("%M(%L)\n", contentFun, contentArgs(mvi))
        body.unindent().add("}\n")
        mvi.bottomBar?.let { bottomBar ->
            body.add("if (!imeVisible) {\n").indent()
            body.add("%M(%L)\n", MemberName(bottomBar.packageName, bottomBar.functionSimpleName), chromeArgs())
            body.unindent().add("}\n")
        }
        body.unindent().add("}\n")
        return body.unindent().add("}\n").build()
    }

    private fun chromeArgs(): CodeBlock = CodeBlock.of("state = state, onIntent = vm::onIntent")

    /** `state = state, onIntent = vm::onIntent[, <extra> = <value>…]` — ALL NAMED (MN1, order-independent). */
    private fun contentArgs(mvi: MviEntryModel): CodeBlock {
        val args = CodeBlock.builder().add("state = state, onIntent = vm::onIntent")
        mvi.roleExtraParams.forEach { role -> args.add(roleExtraArg(role)) }
        mvi.resolverExtraParams.forEach { extra -> args.add(", %L = %L()", extra.name, extra.name) }
        return args.build()
    }

    /** Role extras are Gezgin-provided; the only role type is a `GezginSheetController`, read from its Local. */
    private fun roleExtraArg(role: MviExtraParam): CodeBlock =
        if (role.typeFq == SHEET_CONTROLLER_FQ) {
            CodeBlock.of(", %L = %M.current", role.name, LOCAL_SHEET_CONTROLLER)
        } else {
            // Defensive: no other role type exists today (EntryModelReader classifies only a
            // GezginSheetController as a role extra). If one is ever added, fail loudly rather than emit
            // a bad reference.
            error("Unknown MVI role extra type: ${role.typeFq} (${role.name})")
        }
}
