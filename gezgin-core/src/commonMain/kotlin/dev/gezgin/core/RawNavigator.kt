@file:OptIn(GezginInternalApi::class)

package dev.gezgin.core

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Raw facade over [GezginState] + [ResultBus] + [NavEvent] — the untyped integration layer Faz 2
 * (codegen) and Faz 3 (display) wrap. Single-writer: all mutation happens through the methods
 * below, each of which keeps [backStack] and [events] in sync with the underlying state.
 *
 * **Threading contract (main-thread confinement, integ-m3):** this type is NOT thread-safe. The
 * inner [GezginState] `_stack` is a plain `MutableList`; all mutation ops
 * (navigate/back/replaceTo/quit/…) AND the display's reads (`keysState`/`backStack` collect) must
 * run on the SAME thread — the app's UI/main thread. Because the Faz-5 pattern moves the navigator
 * into the VM ctor, this boundary matters especially: a VM must NOT write
 * `viewModelScope.launch(Dispatchers.IO) { nav.quit() }` — an off-main mutation races with the
 * composition's reads and silently corrupts the stack. If navigation must happen after background
 * work finishes, the call must be moved to the main thread (e.g. `withContext(Dispatchers.Main) { …
 * }`, or from a main-threaded `viewModelScope` directly). There is no portable, cheap main-thread
 * assert mechanism in commonMain (KMP) (a hook like Android's `Looper` is not available in common)
 * → the contract is carried only by this KDoc; a runtime guard was NOT added.
 *
 * [restored] — the PD (process death) simulation: if non-null, the stack + nextId + pending result
 * slots are restored from [SavedState] and `start` is NOT PUSHED (§1.10). That is, while `restored
 * != null` the [start] parameter passed to the ctor is IGNORED (it is used only on the first,
 * restore-less launch). [json] — for decoding slot payloads on restore (so result types requiring a
 * SerializersModule — open polymorphism/
 *
 * @author @sahsenvar
 * @Contextual — decode SYMMETRICALLY with the module used at encode). Faz 6 (§11): made `internal`
 *   (was `private`) — Fragment interop's `androidMain` `route.toBundle(nav)` helper reuses this
 *   SAME app-Json (with the polymorphic Route module) when encoding the `arguments` Bundle (it does
 *   NOT create a second Json → symmetry with backstack PD). Visible only within the module, it does
 *   not leak to public.
 */
public class RawNavigator
internal constructor(
  start: Route,
  private val topology: GezginTopology,
  internal val onRootBack: () -> Unit,
  internal val json: Json,
  restored: SavedState? = null,
) {
  /**
   * Public construction for tests and integration (`GezginTestNavigator`, custom hosts).
   * Deliberately omits the [json]/[restored] process-death parameters — those belong to the
   * same-module `rememberNavigator` restore path (the app's stable `Json` is supplied there), so
   * [SavedState] and its slot schema stay off the public surface.
   */
  public constructor(
    start: Route,
    topology: GezginTopology,
    onRootBack: () -> Unit = {},
  ) : this(start, topology, onRootBack, Json, restored = null)

  // `var` (C1): identity-stabil facade — config-change'te [adoptRestored] AYNI instance'ın
  // `state`'ini
  // re-point eder (yeni RawNavigator KURMAZ). VM ctor'unda yakalanan navigator referansı böylece
  // rotasyondan sonra da display'in gözlemlediği aynı akışları sürer (spec §225 "stable
  // RawNavigator").
  private var state =
    if (restored != null) GezginState(restored.keys, restored.nextId, topology)
    else GezginState(emptyList(), nextId = 0, topology = topology)
  private val bus = ResultBus()

  /**
   * Modal-kind-at-root reddi için display'in enjekte ettiği kanca (M4): bir route'un kayıtlı kind'ı
   * `SCREEN` DIŞINDA (Dialog/BottomSheet/FullscreenModal) ise `true` döner. Varsayılan `{ false }`
   * — display kablolamadan (saf RawNavigator birim testleri) hiçbir op reddedilmez. `GezginDisplay`
   * registry'yi kurduktan SONRA set eder; [replaceTo] mutasyondan ÖNCE bununla kontrol eder →
   * sonuçtaki stack'in kökü bir modal olacaksa state MUTATE EDİLMEDEN fırlatır (composition-zamanı
   * `toNavEntry` guard'ı emniyet ağı olarak KALIR).
   */
  internal var modalRootGuard: (Route) -> Boolean = { false }

  private val _backStack = MutableStateFlow<List<Route>>(emptyList())
  /**
   * The public, observable back stack (§10) — a devtools / "where are we now" indicator. It carries
   * only `Route` (id-less); the id-aware stream the display needs is [keysState] (internal).
   */
  public val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()

  private val _keysState = MutableStateFlow<List<GezginKey>>(emptyList())
  /**
   * Display-katmanı için `id` TAŞIYAN entry görünümü (R2, §2.1). [backStack] yalnız `Route`
   * (id'siz) taşır → `StateFlow` eşit-değer dedup'ı yüzünden `replaceTo` ile aynı-değer-farklı-id
   * bir hedefe geçiş [backStack]'te YENİ emit ÜRETMEZ (route listesi değişmez), dolayısıyla
   * recompose tetiklemez. `GezginKey` benzersiz `id` taşıdığından bu akış her id değişiminde (yeni
   * instance push/replace) farklı bir liste yayar → `GezginDisplay` bunu `collectAsState` ederek
   * contentKey'i (id) değişen entry'yi yeniden kurar. `internal`: zarf public API'ye sızmaz.
   */
  internal val keysState: StateFlow<List<GezginKey>> = _keysState.asStateFlow()

  private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
  /**
   * The observe-only navigation event stream. `extraBufferCapacity=64` + `tryEmit` — if there is no
   * subscriber, or a slow collector fills the buffer, an event is SILENTLY DROPPED. The source of
   * truth is [backStack]/[keys]; this stream is a signal channel alongside them, not a replacement.
   */
  public val events: Flow<NavEvent> = _events

  /** GezginDisplay adapter'ı için raw entry görünümü. */
  internal val keys: List<GezginKey>
    get() = state.stack

  /** The route at the top of the stack (currently active). */
  public val current: Route
    get() = state.stack.last().route

  init {
    if (restored != null) {
      bus.restore(restored.pendingSlots.map(::decodeSlot))
    } else {
      val enterFlow = topology.flowChain(start::class).isNotEmpty()
      state.push(start, enterFlow = enterFlow, singleTop = false)
    }
    refreshBackStack()
  }

  /**
   * slot payload'ı `topology.edges[edgeId].resultSerializer` ile encode → PD-güvenli anlık görüntü.
   * ctor'daki [json] kullanılır (encode/decode simetrisi için tek kaynak).
   */
  @Suppress("UNCHECKED_CAST")
  internal fun save(): SavedState {
    val pendingSlots =
      bus.slots.map { slot ->
        when (val result = slot.result) {
          null ->
            SavedSlot(
              slot.callerEntryId,
              slot.edgeId,
              slot.targetEntryId,
              payloadJson = null,
              canceled = false,
            )
          NavResult.Canceled ->
            SavedSlot(
              slot.callerEntryId,
              slot.edgeId,
              slot.targetEntryId,
              payloadJson = null,
              canceled = true,
            )
          is NavResult.Value<*> -> {
            val serializer =
              requireNotNull(topology.edges[slot.edgeId]?.resultSerializer) {
                "Edge '${slot.edgeId}' has no resultSerializer; delivered Value slot cannot be serialized."
              }
                as KSerializer<Any?>
            val payload = json.encodeToString(serializer, result.value)
            SavedSlot(
              slot.callerEntryId,
              slot.edgeId,
              slot.targetEntryId,
              payloadJson = payload,
              canceled = false,
            )
          }
        }
      }
    return SavedState(keys = state.stack, nextId = state.nextId, pendingSlots = pendingSlots)
  }

  @Suppress("UNCHECKED_CAST")
  private fun decodeSlot(saved: SavedSlot): ResultBus.Slot {
    val result: NavResult<Any?>? =
      when {
        saved.canceled -> NavResult.Canceled
        saved.payloadJson != null -> {
          val serializer =
            requireNotNull(topology.edges[saved.edgeId]?.resultSerializer) {
              "Edge '${saved.edgeId}' has no resultSerializer; Value payload cannot be decoded."
            }
              as KSerializer<Any?>
          NavResult.Value(json.decodeFromString(serializer, saved.payloadJson))
        }
        else -> null
      }
    return ResultBus.Slot(saved.callerEntryId, saved.edgeId, saved.targetEntryId, result)
  }

  /**
   * C1 — PD (process death) restore: bu AYNI facade'in underlying state'ini [restored]'a re-point
   * eder. Android'de YALNIZ taze holder'ın PD-adopt yolunda çağrılır (config-change'te holder +
   * canlı navigator retained kalır → re-adopt YOK, MN-1). Yeni bir `RawNavigator` KURULMAZ → bu
   * instance'ı ctor'da yakalamış her sahip (özellikle rotasyondan sağ çıkan bir ViewModel)
   * restore'dan sonra da display'in gözlemlediği state'i sürmeye devam eder (spec §225 "stable
   * RawNavigator"). `bus`/StateFlow instance'ları KORUNUR (aynı `keysState`/`backStack` → mevcut
   * collector'lar kopmaz), yalnız içerikleri restore edilmiş snapshot'a döner. Ctor'un `restored !=
   * null` yolunun birebir eşleniği; İDEMPOTENT (aynı snapshot'la tekrar çağrı state'i aynı değere
   * sabitler, bkz. NavigatorIdentityRestoreTest) ve event yayınlamaz — bu bir kuruluş, navigasyon
   * değil.
   */
  internal fun adoptRestored(restored: SavedState) {
    // MJ-B (atomicity) — riskli slot decode'unu MUTASYONDAN ÖNCE yap: `decodeSlot` bir edge
    // silinmiş/
    // yeniden-adlandırılmışsa (IllegalArgumentException) ya da şema değişmişse
    // (SerializationException)
    // fırlatabilir. Decode'u önce yaparak, fırlatırsa `state` (ve dolayısıyla navigator)
    // DOKUNULMADAN
    // `start`'ta kalır → çağıran (Android adopt yolu) istisnayı yutup graceful fresh-start
    // yapabilir
    // (yarı-adopt edilmiş tutarsız bir stack bırakmaz; desktop ctor'unun bütün-ya-hiç semantiğiyle
    // simetrik).
    val decodedSlots = restored.pendingSlots.map(::decodeSlot)
    state = GezginState(restored.keys, restored.nextId, topology)
    bus.restore(decodedSlots)
    refreshBackStack()
  }

  // ---- public ops ----

  /** @GoTo — resolves enterFlow from the topology (is the target a flow-start container entry). */
  public fun navigate(route: Route, singleTop: Boolean = true) {
    val enterFlow = resolveEnterFlow(route)
    val pushed = state.push(route, enterFlow = enterFlow, singleTop = singleTop) ?: return
    refreshBackStack()
    _events.tryEmit(NavEvent.Pushed(pushed.route))
  }

  /**
   * Back: the order (§8.1 / Fix 9) — (1) if the top is a flow ENTRY, delegate ENTIRELY to quit()
   * (even if there is a pending target: settleRemoved delivers Canceled), the event becomes
   * `FlowQuit(canceled=true)`, NO `Popped`; (2) a plain pop + `Popped` — if the top is a
   * pending-target, settleRemoved delivers its Canceled (a single gate). At the bottom →
   * onRootBack.
   */
  public fun back() {
    val top = state.stack.last()
    if (isFlowEntry(top)) { // (1) flow entry → quit() (settleRemoved Canceled'ı teslim eder)
      quit()
      return
    }
    popTopAndEmit() // (2) düz pop — pending-target Canceled'ı da settleRemoved verir
  }

  /**
   * The `@ReplaceTo` runtime: clears up to `clearUpTo` (null = the top only) and pushes `route`;
   * delivers Canceled to the removed pending-targets.
   */
  public fun replaceTo(
    route: Route,
    clearUpTo: KClass<out Route>? = null,
    inclusive: Boolean = true,
  ) {
    // K1 — clearUpTo hedefi stack'te YOKSA MUTASYONDAN/`require`'dan ÖNCE zarif no-op (backTo'nun
    // BackToTargetMissing deseni): aksi halde cutIndex'in require(i >= 0)'ı fırlardı → bir
    // @ReplaceTo
    // edge'ine hızlı çift-tık (ilk çağrı clearUpTo'yu zaten kaldırmış) main-thread'de app'i
    // ÇÖKERTİRDİ.
    if (!state.hasOnStack(clearUpTo)) {
      _events.tryEmit(NavEvent.ReplaceToTargetMissing(clearUpTo?.simpleName ?: "?"))
      return
    }
    // M4 — modal-kind-at-root reddi MUTASYONDAN ÖNCE: replaceTo kökü temizleyip yerine bir modal
    // koyacaksa (sonuçtaki stack'in dibi = bir modal route) state hiç değiştirilmeden fırlat.
    // Aksi halde eski davranış (state önce `[modal]`'a döner, guard SONRAKİ composition'da patlar)
    // error-boundary'li host'ta navigator'ı kalıcı geçersiz bir stack'te bırakırdı.
    val resultingRoot = state.resultingRootAfterReplace(route, clearUpTo, inclusive)
    require(!modalRootGuard(resultingRoot)) {
      "replaceTo: resulting stack root cannot be a modal kind (${resultingRoot::class.simpleName}); " +
        "a modal must have at least one SCREEN entry underneath it (Nav3 OverlayScene invariant, §7). " +
        "Do not place a modal at root with clearUpTo=root."
    }
    val before = state.stack.toList()
    val enterFlow = resolveEnterFlow(route)
    val pushed = state.replaceUpTo(route, clearUpTo, inclusive, enterFlow = enterFlow)
    val afterIds = state.stack.map { it.id }.toSet()
    val removed = before.filter { it.id !in afterIds }
    refreshBackStack()
    _events.tryEmit(NavEvent.Replaced(removed.map { it.route }, pushed.route))
    settleRemoved(removed)
  }

  /**
   * The `@BackTo` runtime: pops up to `target` in the stack (including target if `inclusive`). If
   * the target is absent, a `BackToTargetMissing` event, NO pop.
   */
  public fun backTo(target: KClass<out Route>, inclusive: Boolean = false) {
    val removed = state.backTo(target, inclusive)
    if (removed == null) {
      _events.tryEmit(NavEvent.BackToTargetMissing(target.simpleName ?: "?"))
      return
    }
    refreshBackStack()
    _events.tryEmit(NavEvent.PoppedTo(target.simpleName ?: "?", removed.map { it.route }))
    settleRemoved(removed)
  }

  /** Close the flow with Canceled (onRootBack at the root). */
  public fun quit() {
    val flowId = state.currentFlowId() ?: return
    val removed = state.quitFlow(flowId)
    if (removed == null) {
      onRootBack()
      _events.tryEmit(NavEvent.RootBack)
      return
    }
    refreshBackStack()
    _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = true))
    settleRemoved(removed) // deliverValue=null → hayatta-kalan caller'lı target'lara Canceled
  }

  /**
   * Close atomically with a Value + deliver to the caller. A silent no-op when not inside a flow
   * (symmetric with quit()). In a nested ResultFlow the target = the NEAREST-ENCLOSING ResultFlow
   * (spec §6); quit() stays on the innermost.
   */
  public fun quitWith(result: Any?) {
    // quitWith hedef seçimi: en içteki KAPSAYAN ResultFlow (spec §6);
    // hiç ResultFlow yoksa fallback = en içteki flow (typed katman quitWith'i zaten yalnız
    // ResultFlow'da üretir).
    val top = state.stack.last()
    val chain = topology.flowChain(top.route::class) // flowPath ile paralel (aynı uzunluk)
    val idx = chain.indexOfLast { it.isResultFlow }
    val flowId =
      (if (idx >= 0) top.flowPath.getOrNull(idx) else top.flowPath.lastOrNull()) ?: return
    val removed = state.quitFlow(flowId)
    if (removed == null) {
      onRootBack()
      _events.tryEmit(NavEvent.RootBack)
      return
    }
    refreshBackStack()
    _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = false))
    // Value YALNIZ flow'un KENDİ entry slotuna (removed.first() = flow entry — contiguous blok
    // garanti);
    // diğer hayatta-kalan caller'lı slotlar Canceled; caller'ı da kalkan iç slotlar
    // dropFor→ResultDropped.
    settleRemoved(removed, deliverValue = result, valueTargetId = removed.first().id)
  }

  /**
   * M3 — entry-scoped `backWithResult`: pins THE RESULT to its OWNER entry. If [entryId] is no
   * longer the top (e.g. an async job's result arrived late after the sheet was dismissed by a
   * gesture) it is a SILENT NO-OP → the value is not delivered to the slot of a foreign entry that
   * is not waiting for that slot (that expects a result of a different type) and that entry is not
   * accidentally popped (a dirty-delivery/ double-back race is prevented). The typed
   * `backWithResult(result)` that Faz 2 codegen generates binds the ctor's `entryId` to this
   * overload.
   *
   * **mn-1 — owner top ama pending-target DEĞİL:** owner top iken (`top.id == entryId`) ama onu
   * hedefleyen bekleyen bir slot yoksa (bir `ResultRoute` düz `@GoTo`/`navigate` ile ya da
   * core-mode raw kullanımla açılmışsa), eskiden bu hem teslimi hem pop'u atlar → ekran kapanmaz,
   * kullanıcı sıkışırdı. Artık en güvenli davranış: değeri düşür (kimse dinlemiyor) ama owner
   * ekranı yine de KAPAT (`popTopAndEmit`). `bus.deliver` yalnız gerçek bir pending-target varken
   * çağrılır; ardından `popTopAndEmit`'in [settleRemoved]'ı zaten teslim edilmiş (result != null)
   * slotu atlar (çift-teslim yok).
   */
  @GezginInternalApi
  public fun backWithResult(entryId: Long, result: Any?) {
    val top = state.stack.last()
    if (top.id != entryId) return // sahip entry artık top değil → teslim etme, pop etme
    if (isPendingTarget(top.id)) bus.deliver(top.id, NavResult.Value(result))
    popTopAndEmit() // owner top → değer düşse bile ekranı kapat (mn-1)
  }

  /**
   * Convenience: owner = the call-time top entry. The typed layer pins the entry via the id
   * overload.
   */
  public fun backWithResult(result: Any?): Unit = backWithResult(currentEntryId, result)

  /**
   * C-MJ-1 — entry-pinned `back`: [backWithResult]`(entryId, …)` deseninin sonuçsuz eşleniği.
   * Yalnız [entryId] HÂLÂ top ise normal [back]'i uygular; değilse SESSİZ NO-OP. Modal
   * (dialog/sheet) dismiss'i kendi sahip-entry'sine pinlemek için kullanılır: çifte-dismiss /
   * hide-animasyon penceresinde geç gelen / app-scope coroutine'den gelen bir `back`, modal artık
   * top değilken ALTTAKİ ekranı poplamaz — no-op olur (fail-loud/sahibe-pin felsefesi, spec §7).
   * Ek: modal artık top değilse (kullanıcı zaten kapattı) bu no-op'tur; canlı top'a etki etmez.
   */
  @GezginInternalApi
  public fun back(entryId: Long) {
    if (state.stack.last().id != entryId) return // sahip entry artık top değil → no-op
    back()
  }

  /**
   * The call-time top entry id — the hook generated navigators bind to the explicit-caller
   * overloads.
   */
  @GezginInternalApi
  public val currentEntryId: Long
    get() = state.stack.last().id

  /**
   * Task 2.6 — a minimal public entry point for `:gezgin-test`'s typed `fromX()` access: the id of
   * the NEAREST (topmost in the stack) entry that implements [route], or `null`. `keys` stays
   * `internal`; this is the single [GezginInternalApi] opt-in member built on top of it.
   */
  @GezginInternalApi
  public fun entryIdOf(route: KClass<out Route>): Long? =
    keys.lastOrNull { route.isInstance(it.route) }?.id

  /**
   * Explicit-caller (the Faz 2 hook): idempotent (§6) — do NOT push while a slot exists for the
   * same (caller, edge) (in-flight OR delivered-but-unconsumed). Otherwise a result request would
   * ALWAYS create a new entry.
   */
  @GezginInternalApi
  public fun launchForResult(callerEntryId: Long, edgeId: String, route: Route) {
    // Pre-guard, bus.launch'un predicate'iyle birebir aynı (HERHANGİ bir slot — result durumu fark
    // etmez):
    // teslim edilmiş ama tüketilmemiş slot varken re-launch, slotsuz öksüz bir entry push'lardı.
    if (bus.slots.any { it.callerEntryId == callerEntryId && it.edgeId == edgeId }) return
    // @GoForResult edge'i DAİMA container-entry'dir → hedef flow için taze instance mint (spec §8.1
    // re-entrancy sınırı: aynı flow tipine içten re-entry, dış instance'ın id'sini miras ALMAZ).
    val enterFlow = topology.flowChain(route::class).isNotEmpty()
    val pushed =
      state.push(
        route,
        enterFlow = enterFlow,
        singleTop = false,
      )!! // result isteği = daima yeni entry (singleTop=false → null dönemez)
    bus.launch(callerEntryId, edgeId, pushed.id)
    refreshBackStack()
    _events.tryEmit(NavEvent.Pushed(pushed.route))
  }

  /**
   * Convenience: caller = the top AT CALL TIME. Faz 2 codegen (when the caller is not the top) must
   * use the explicit overload. Two quick top-based calls see different callers (the first push
   * changes the top) → if you want dedupe, use the explicit-caller overload; the typed layer binds
   * the caller to the entry.
   */
  public fun launchForResult(edgeId: String, route: Route): Unit =
    launchForResult(currentEntryId, edgeId, route)

  /** Explicit-caller (the Faz 2 hook): the result stream of the (caller, edge) slot. */
  @GezginInternalApi
  public fun <T> results(callerEntryId: Long, edgeId: String): Flow<NavResult<T>> =
    bus.results(callerEntryId, edgeId)

  /**
   * Convenience: caller = the top entry id AT CALL TIME. A late re-attach after restore therefore
   * only works while the original caller entry is the current top (the call-time-top contract);
   * when the caller is NOT the top (PD re-attach, Faz 2 codegen) the explicit
   * [results]`(callerEntryId, edgeId)` overload MUST be used.
   */
  public fun <T> results(edgeId: String): Flow<NavResult<T>> = results(currentEntryId, edgeId)

  /** Explicit-caller sugar = launch + results.first() (the Faz 2 hook). */
  @GezginInternalApi
  public suspend fun <T> navigateForResult(
    callerEntryId: Long,
    edgeId: String,
    route: Route,
  ): NavResult<T> {
    launchForResult(callerEntryId, edgeId, route)
    return bus.results<T>(callerEntryId, edgeId).first()
  }

  /** Convenience sugar; the caller is captured from the call-time top (BEFORE the push). */
  public suspend fun <T> navigateForResult(edgeId: String, route: Route): NavResult<T> =
    navigateForResult(currentEntryId, edgeId, route)

  /**
   * @QuitAndGoTo (the Faz 2 codegen hook) — tear down the current flow without a result (exactly
   *   the same teardown as quit(): Canceled to surviving caller-bearing pending slots,
   *   `FlowQuit(canceled = true)`) and then navigate to the target. When the source is NOT INSIDE a
   *   flow (no flowId) there is nothing to tear down — it is equivalent to a plain `navigate`. In
   *   the root flow (quitFlow → null), the same rule as quit()/quitWith: fall to onRootBack() +
   *   emit `RootBack`, do NOT navigate (the teardown itself failed).
   */
  public fun quitAndGoTo(route: Route) {
    val flowId = state.currentFlowId()
    if (flowId != null) {
      val removed = state.quitFlow(flowId)
      if (removed == null) {
        onRootBack()
        _events.tryEmit(NavEvent.RootBack)
        return
      }
      refreshBackStack()
      _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = true))
      settleRemoved(removed)
    }
    // K2 — singleTop=true: bir @QuitAndGoTo edge'ine çift-tık idempotent olsun. İkinci çağrıda
    // (flow
    // zaten yıkıldı, top = route) navigate no-op döner → aksi halde ikinci bir (çoğu zaman @NoBack)
    // `route`
    // entry'si push edilir ve back stale entry'de yutulur → kullanıcı sıkışırdı. Meşru durum
    // (post-quit
    // top'tan FARKLI bir hedef) top.route != route olduğundan yine normal push'lanır.
    navigate(route, singleTop = true)
  }

  // ---- internal helpers ----

  private fun refreshBackStack() {
    _backStack.value = state.stack.map { it.route }
    _keysState.value =
      state.stack.toList() // id taşır → replaceTo same-value-diff-id de emit eder (§2.1/R2)
  }

  private fun popTopAndEmit() {
    val popped = state.pop()
    if (popped == null) {
      onRootBack()
      _events.tryEmit(NavEvent.RootBack)
      return
    }
    refreshBackStack()
    _events.tryEmit(NavEvent.Popped(popped.route))
    settleRemoved(listOf(popped))
  }

  private fun resolveEnterFlow(route: Route): Boolean {
    val target = topology.flowChain(route::class)
    if (target.isEmpty()) return false
    val top = state.stack.lastOrNull() ?: return true
    val source = topology.flowChain(top.route::class)
    val common = target.zip(source).takeWhile { (a, b) -> a.id == b.id }.count()
    return common < target.size
  }

  private fun isPendingTarget(id: Long): Boolean =
    bus.slots.any { it.targetEntryId == id && it.result == null }

  /** top, kendi flow segmentinin İLK entry'si mi (altındaki entry o innermost id'yi taşımıyor). */
  private fun isFlowEntry(top: GezginKey): Boolean {
    if (top.flowPath.isEmpty()) return false
    val innermost = top.flowPath.last()
    val below = state.stack.getOrNull(state.stack.size - 2)
    return below == null || innermost !in below.flowPath
  }

  /**
   * Stack'ten kalkan entry'lerin slot/event muhasebesi — tüm removal path'lerinin TEK kapısı. Value
   * YALNIZ [valueTargetId]'nin slotuna (quit edilen flow'un KENDİ entry'si) teslim edilir; diğer
   * hayatta-kalan caller'lı pending target'lar Canceled alır — açık out-of-flow caller'lı
   * yabancı-tipli bir slota asla Value sızmaz. [valueTargetId] == null ise hepsi Canceled.
   */
  private fun settleRemoved(
    removed: List<GezginKey>,
    deliverValue: Any? = null,
    valueTargetId: Long? = null,
  ) {
    if (removed.isEmpty()) return
    val removedIds = removed.map { it.id }.toSet()
    for (slot in bus.slots) {
      if (
        slot.result == null && slot.targetEntryId in removedIds && slot.callerEntryId !in removedIds
      ) {
        // MJ-A — "değer taşıyor muyuz"u [valueTargetId]'in VARLIĞINDAN oku, [deliverValue]'nun
        // içeriğinden DEĞİL: quitWith DAİMA non-null valueTargetId geçirir (Canceled-only
        // çağıranlar —
        // quit/replaceTo/backTo/quitAndGoTo — DAİMA null). Böylece meşru bir `null` DEĞER
        // (ResultFlow<T?>.quitWith(null)) flow-entry slotuna Value(null) teslim eder, Canceled'a
        // çökmez (backWithResult(null) ile tutarlı).
        bus.deliver(
          slot.targetEntryId,
          if (valueTargetId != null && slot.targetEntryId == valueTargetId)
            NavResult.Value(deliverValue)
          else NavResult.Canceled,
        )
      }
    }
    bus.dropFor(removedIds).forEach { _events.tryEmit(NavEvent.ResultDropped(it.edgeId)) }
  }
}
