# Gezgin V1 — Adversarial pre-implementation review brief

> Yeni bir session'da bunu yapıştır (ya da "follow docs/gezgin-v1-review-prompt.md" de). İstersen bir Opus alt-ajanına devret; istersen doğrudan yap.

---

You are a skeptical senior Compose Multiplatform / Android navigation-library architect doing an **ADVERSARIAL pre-implementation review** of a design spec. Find holes BEFORE implementation — gaps, logic errors, internal contradictions, underspecified areas, and edge cases the design does not survive. Assume there ARE problems; a rubber-stamp is a failure. Be concrete and technical, no filler.

## What to review
`docs/gezgin-design.md` — read it FULLY (canonical V1 spec, ~370 lines). For rationale/history you MAY read `docs/gezgin-design-notes.md`; for usage examples `docs/gezgin-by-example.md`. (`docs/gezgin-review-findings.md` lists what a prior review already resolved — skim to avoid re-reporting.)

## What the library is
"Gezgin" — an annotation + KSP-codegen, type-safe, **state-as-data** navigation library for Compose Multiplatform, on **AndroidX Navigation 3 (Nav3)**. Routes are `@Serializable` objects/data-classes nested in `sealed interface` graphs. Codegen generates a **typed per-screen navigator** (each declared edge → one method; undeclared target → won't compile). MVI is an opt-in add-on (`@ViewModel` + `GezginMvi<out S, in I, out E>` + entry-scoped VM via Nav3 decorators).

## V1 scope — do NOT report these as gaps (deliberately deferred to V2, see §17)
- `@TabGraph` / tabs / `@SwitchTo` — none in V1.
- Multiple back stack / per-tab retained stacks — **V1 is SINGLE-STACK**.
- The **entire deep-link feature** — not in V1.
V1 has two graph types only: **`@NavGraph`** (transparent group: enter at any member, container-entry forbidden, NO `@StartDestination`, may be stand-alone/shared) and **`@FlowGraph`** (opaque transactional: `@StartDestination`, `ResultFlow<T>`, strict entry via container/`@GoForResult`, exits `quitWith`/`quit`/`@QuitAndGoTo`/entry-back).

## Focus your adversarial energy on
1. **Internal contradictions** between sections (cite § numbers).
2. **Logic holes** — mechanisms that cannot work as stated.
3. **Edge cases:** process-death restore, config change, `@FlowGraph` strict entry/exit matrix (§8.1) + empty-stack invariant, `quitWith`/`quit`/`@QuitAndGoTo`/entry-`back` interactions, result PD-safety (`ResultRoute`/`ResultFlow`, keyed pending-result slot §6), `@NoBack` (§4.2), `@ReplaceTo` + `Self : NavKey` sentinel, MVI entry-scoping + `@ViewModel` DI-detection, Fragment interop (§11).
4. **Feasibility — verify against REAL 2026 behavior where you can (use web/docs tools):**
   - **KSP:** does codegen only rely on things KSP can read (annotation args = yes; property initializers / expression bodies = no)? Flag any dependency on unreadable info.
   - **Nav3:** does Nav3 actually support the assumed mechanisms — you-own-the-back-stack list, `rememberViewModelStoreNavEntryDecorator` entry-scoping, SceneStrategy overlays, predictive back, and the **"single back-authority"** claim (§4.2 `@NoBack` entry-metadata flag consumed by `GezginDisplay`; M4 flagged Nav3 dispatch order as undocumented — is it actually workable)?
   - **Kotlin:** sealed hierarchy constraints, `Self : NavKey` sentinel as annotation default, `GezginMvi<out S, in I, out E>` variance, cross-module typed visibility.
5. **Underspecified:** where the spec says WHAT but not HOW and the HOW is non-obvious/risky.
6. **`@FlowGraph` rules specifically** — try to BREAK them with concrete sequences (nested flows; a `@GoForResult`-entered flow whose member does `@QuitAndGoTo`; `quitWith` from a flow whose caller died in PD; a `@NavGraph` member reaching a flow's inner member; etc.).

## Prior review already RESOLVED (don't just re-report; aim NEW/DEEPER — but flag if a resolution is actually wrong)
multi-module sealed placement (all graphs in `core:navigation`), KSP DI-detection (`@ViewModel` makes VM class KSP-visible), `GezginMvi` invariance, flow-level result declaration, result delivery with duplicate route types (per-request monotonic id), `@NoBack` predictive-back mechanism (entry-metadata flag, not no-op BackHandler), DialogFragment interop (cut — only `@FragmentScreen`), and the whole graph-model reorganization.

## Output format
Severity-ranked: **🔴 BLOCKER** (breaks a headline promise / unimplementable as specified) / **🟡 MAJOR** (real correctness/feasibility hole with a workaround) / **🟢 MINOR** (clarification/underspec). For EACH: one-line problem · WHY · a CONCRETE failure scenario (specific nav sequence / state / input → wrong outcome or compile/runtime failure) · cite § number(s). End with a one-paragraph **VERDICT**: is V1 implementation-ready, and if not, the top 2–3 things to fix first.
