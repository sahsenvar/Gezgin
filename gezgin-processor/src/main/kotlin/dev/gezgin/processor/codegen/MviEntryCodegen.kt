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
import dev.gezgin.processor.mvi.VmDiKind

private const val COMPOSE_PKG = "dev.gezgin.core.compose"
private const val COMPOSE_RUNTIME_PKG = "androidx.compose.runtime"

private val ENTRY_SCOPE = ClassName(COMPOSE_PKG, "GezginEntryScope")
private val ENTRY_KIND = ClassName(COMPOSE_PKG, "EntryKind")
private val LOCAL_ENTRY_ID = MemberName(COMPOSE_PKG, "LocalGezginEntryId")
private val LOCAL_RAW_NAVIGATOR = MemberName(COMPOSE_PKG, "LocalGezginRawNavigator")
private val LOCAL_SHEET_STATE = MemberName(COMPOSE_PKG, "LocalGezginSheetState")
private val COMPOSABLE = ClassName(COMPOSE_RUNTIME_PKG, "Composable")

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

private const val SHEET_STATE_FQ = "androidx.compose.material3.SheetState"

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
 *         OrderChainEffects(vm.effects)              // @ScreenEffect matched (by effect type)
 *         OrderChainContent(state, vm::onIntent)     // stateless @Screen content
 *     }
 * }
 * ```
 *
 * **DI default resolver (§10.1 rule 1):** a default is emitted ONLY when every DI-relevant ctor param
 * is `route`- or `nav`-typed (relevant = `@Assisted`/`@InjectedParam` for Hilt/Koin; ALL for androidx;
 * NONE for plain Hilt). Any other relevant param (e.g. `@Assisted userId: String`) → no default, the
 * `viewModel` param becomes REQUIRED. Always override-able either way.
 *
 * **`nav` wiring (§10.1 open question, resolved: conditional):** unlike the spec's literal always-`nav`
 * example, `nav` is wired only when the VM ctor actually declares a `nav` param OR the matched
 * `@ScreenEffect` takes one — mirroring core-mode's conditional `hasNavParam`. An MVI VM with no edges
 * never forces an `xNavigator()` factory call that couldn't resolve. When wired, the factory is
 * qualified against the ROUTE's package (`entry.routePackageName`), exactly like [EntryCodegen].
 *
 * **Problem 2 (§10.1 rule 2):** each `resolverExtraParam` becomes a REQUIRED `@Composable () -> T`
 * param, threaded as `<name>()` into the content call. Role extras (`sheetState`) read from
 * `LocalGezginSheetState`. All content extras are passed as NAMED args so the split role/resolver
 * lists need not reconstruct the composable's original parameter order.
 */
object MviEntryCodegen {

    fun generate(entries: List<EntryFunctionModel>): List<FileSpec> =
        entries.filter { it.mvi != null }
            .groupBy { it.packageName }
            .map { (packageName, group) ->
                FileSpec.builder(packageName, "GezginMviEntries")
                    // `val state by … collectAsStateWithLifecycle()` needs the State delegate operator.
                    .addImport(COMPOSE_RUNTIME_PKG, "getValue")
                    .apply { group.forEach { addFunction(provideMviEntryFun(it)) } }
                    .build()
            }

    private enum class Role { ROUTE, NAV, OTHER }

    private fun provideMviEntryFun(entry: EntryFunctionModel): FunSpec {
        val mvi = entry.mvi!!
        val vm = mvi.vm
        val vmClass = ClassName.bestGuess(vm.vmFq)
        val routeClass = ClassName.bestGuess(entry.routeFq)
        val navigatorClass = ClassName(entry.routePackageName, "${entry.x}Navigator")
        val navigatorTypeFq = "${entry.routePackageName}.${entry.x}Navigator"

        fun roleOf(p: VmCtorParam): Role = when {
            p.typeFq == entry.routeFq -> Role.ROUTE
            // A same-module nav type is unresolved in this KSP round (best-effort typeFq), so match by
            // the `nav` name convention too (mirrors core-mode's `nav:` param naming).
            p.name == "nav" || p.typeFq == navigatorTypeFq -> Role.NAV
            else -> Role.OTHER
        }

        // DI-relevant params: what the resolver default must supply itself.
        val relevantParams = when (vm.di) {
            VmDiKind.ANDROIDX -> vm.ctorParams
            VmDiKind.HILT_ASSISTED, VmDiKind.KOIN -> vm.ctorParams.filter { it.diAnnotated }
            VmDiKind.HILT_PLAIN -> emptyList() // Hilt injects everything; Gezgin supplies nothing.
        }
        val vmHasNav = relevantParams.any { roleOf(it) == Role.NAV }
        val vmHasRoute = relevantParams.any { roleOf(it) == Role.ROUTE }
        // A default is possible only when every relevant param is route/nav (else the user must resolve).
        val emitDefault = relevantParams.none { roleOf(it) == Role.OTHER }

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
        roleOf: (VmCtorParam) -> Role,
    ): CodeBlock {
        val header = if (vmHasNav) "nav, args" else "args"
        // Values Gezgin supplies, in the documented (args, nav) order — matches the AssistedFactory
        // `create(route, nav)` / `parametersOf(args, nav)` conventions (Faz-5.0 spike).
        val suppliedArgs = buildList {
            if (vmHasRoute) add("args")
            if (vmHasNav) add("nav")
        }.joinToString(", ")

        return when (vm.di) {
            VmDiKind.ANDROIDX -> {
                // Positional constructor call — args in the VM's DECLARED ctor order (all route/nav here).
                val ctorArgs = vm.ctorParams.joinToString(", ") { if (roleOf(it) == Role.NAV) "nav" else "args" }
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

            // Plain Hilt supplies nothing itself (route via SavedStateHandle) — ignore args, no nav.
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
            body.add(if (effectWantsNav) "%M(vm.effects, nav)\n" else "%M(vm.effects)\n", effectFun)
        }

        body.add("%M(%L)\n", contentFun, contentArgs(mvi))
        return body.unindent().add("}\n").build()
    }

    /** `state, vm::onIntent[, <extra> = <value>…]` — extras as NAMED args (order-independent). */
    private fun contentArgs(mvi: MviEntryModel): CodeBlock {
        val args = CodeBlock.builder().add("state, vm::onIntent")
        mvi.roleExtraParams.forEach { role -> args.add(roleExtraArg(role)) }
        mvi.resolverExtraParams.forEach { extra -> args.add(", %L = %L()", extra.name, extra.name) }
        return args.build()
    }

    /** Role extras are Gezgin-provided; the only role type is `sheetState`, read from its Local. */
    private fun roleExtraArg(role: MviExtraParam): CodeBlock =
        if (role.typeFq == SHEET_STATE_FQ) {
            CodeBlock.of(", %L = %M.current", role.name, LOCAL_SHEET_STATE)
        } else {
            // Defensive: no other role type exists today (EntryModelReader classifies only sheetState
            // as a role extra). If one is ever added, fail loudly rather than emit a bad reference.
            error("Unknown MVI role extra type: ${role.typeFq} (${role.name})")
        }
}
