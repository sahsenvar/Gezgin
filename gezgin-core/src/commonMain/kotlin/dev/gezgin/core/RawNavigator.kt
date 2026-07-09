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
 * Raw facade over [GezginState] + [ResultBus] + [NavEvent] — the untyped integration layer
 * Faz 2 (codegen) and Faz 3 (display) wrap. Single-writer: all mutation happens through the
 * methods below, each of which keeps [backStack] and [events] in sync with the underlying state.
 *
 * **Threading sözleşmesi (main-thread confinement, integ-m3):** bu tip thread-safe DEĞİLDİR. İçteki
 * [GezginState] `_stack`'i düz bir `MutableList`'tir; tüm mutasyon op'ları (navigate/back/replaceTo/
 * quit/…) VE display'in okumaları (`keysState`/`backStack` collect) AYNI thread'de — uygulamanın UI/
 * main thread'inde — çalışmalıdır. Faz-5 deseni navigator'ı VM ctor'una taşıdığından bu sınır özellikle
 * önemlidir: bir VM `viewModelScope.launch(Dispatchers.IO) { nav.quit() }` YAZMAMALI — off-main bir
 * mutasyon composition okumalarıyla yarışıp stack'i sessizce bozar. Arka-plan işi bittikten sonra
 * navigasyon yapılacaksa çağrı ana-thread'e taşınmalıdır (ör. `withContext(Dispatchers.Main) { … }` ya da
 * doğrudan ana-thread'li `viewModelScope`'tan). commonMain'de (KMP) taşınabilir ucuz bir main-thread
 * assert mekanizması yok (Android `Looper` gibi bir kanca common'da mevcut değil) → sözleşme yalnız bu
 * KDoc ile taşınır, çalışma-zamanı guard'ı EKLENMEDİ.
 *
 * [restored] — PD (process death) simülasyonu: non-null ise stack + nextId + pending result
 * slot'ları [SavedState]'ten geri yüklenir, `start` PUSH EDİLMEZ (§1.10). Yani `restored != null`
 * iken ctor'a verilen [start] parametresi YOK SAYILIR (yalnız restore'suz ilk açılışta kullanılır).
 * [json] — restore'da slot payload decode'u için (SerializersModule gerektiren result tipleri —
 * açık polimorfizm/@Contextual — encode'daki modülle SİMETRİK decode edilsin diye). Faz 6 (§11):
 * `internal` yapıldı (eskiden `private`) — Fragment interop'un `androidMain` `route.toBundle(nav)`
 * yardımcısı bu AYNI app-Json'ı (polimorfik Route modülüyle) `arguments` Bundle encode'unda yeniden
 * kullanır (ikinci Json ÜRETMEZ → backstack PD'siyle simetri). Yalnız modül-içi görünür, public'e sızmaz.
 */
class RawNavigator(
    start: Route,
    private val topology: GezginTopology,
    internal val onRootBack: () -> Unit = {},
    internal val json: Json = Json,
    restored: SavedState? = null,
) {
    // `var` (C1): identity-stabil facade — config-change'te [adoptRestored] AYNI instance'ın `state`'ini
    // re-point eder (yeni RawNavigator KURMAZ). VM ctor'unda yakalanan navigator referansı böylece
    // rotasyondan sonra da display'in gözlemlediği aynı akışları sürer (spec §225 "stable RawNavigator").
    private var state =
        if (restored != null) GezginState(restored.keys, restored.nextId, topology)
        else GezginState(emptyList(), nextId = 0, topology = topology)
    private val bus = ResultBus()

    /**
     * Modal-kind-at-root reddi için display'in enjekte ettiği kanca (M4): bir route'un kayıtlı kind'ı
     * `SCREEN` DIŞINDA (Dialog/BottomSheet/FullscreenModal) ise `true` döner. Varsayılan `{ false }` —
     * display kablolamadan (saf RawNavigator birim testleri) hiçbir op reddedilmez. [GezginDisplay]
     * registry'yi kurduktan SONRA set eder; [replaceTo] mutasyondan ÖNCE bununla kontrol eder →
     * sonuçtaki stack'in kökü bir modal olacaksa state MUTATE EDİLMEDEN fırlatır (composition-zamanı
     * `toNavEntry` guard'ı emniyet ağı olarak KALIR).
     */
    internal var modalRootGuard: (Route) -> Boolean = { false }

    private val _backStack = MutableStateFlow<List<Route>>(emptyList())
    /**
     * Public, gözlemlenebilir back stack (§10) — devtools / "şu an neredeyiz" göstergesi. Yalnız `Route`
     * taşır (id'siz); display'in ihtiyaç duyduğu id-duyarlı akış [keysState]'tir (internal).
     */
    val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()

    private val _keysState = MutableStateFlow<List<GezginKey>>(emptyList())
    /**
     * Display-katmanı için `id` TAŞIYAN entry görünümü (R2, §2.1). [backStack] yalnız `Route`
     * (id'siz) taşır → `StateFlow` eşit-değer dedup'ı yüzünden `replaceTo` ile aynı-değer-farklı-id
     * bir hedefe geçiş [backStack]'te YENİ emit ÜRETMEZ (route listesi değişmez), dolayısıyla
     * recompose tetiklemez. `GezginKey` benzersiz `id` taşıdığından bu akış her id değişiminde
     * (yeni instance push/replace) farklı bir liste yayar → [GezginDisplay] bunu `collectAsState`
     * ederek contentKey'i (id) değişen entry'yi yeniden kurar. `internal`: zarf public API'ye sızmaz.
     */
    internal val keysState: StateFlow<List<GezginKey>> = _keysState.asStateFlow()

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64)
    /**
     * Gözlem-amaçlı (observe-only) navigasyon olay akışı. `extraBufferCapacity=64` + `tryEmit` —
     * abonesizken ya da yavaş bir collector'da buffer dolarsa event SESSİZCE DÜŞER (drop). Kaynak-
     * doğruluk [backStack]/[keys]'tedir; bu akış onların yerine değil, yanında bir sinyal kanalıdır.
     */
    val events: Flow<NavEvent> = _events

    /** GezginDisplay adapter'ı için raw entry görünümü. */
    internal val keys: List<GezginKey> get() = state.stack

    /** Stack'in tepesindeki (şu an aktif) route. */
    val current: Route get() = state.stack.last().route

    init {
        if (restored != null) {
            bus.restore(restored.pendingSlots.map(::decodeSlot))
        } else {
            val enterFlow = topology.flowChain(start::class).isNotEmpty()
            state.push(start, enterFlow = enterFlow, singleTop = false)
        }
        refreshBackStack()
    }

    /** slot payload'ı topology.edges[edgeId].resultSerializer ile encode → PD-güvenli anlık görüntü.
     *  ctor'daki [json] kullanılır (encode/decode simetrisi için tek kaynak). */
    @Suppress("UNCHECKED_CAST")
    fun save(): SavedState {
        val pendingSlots = bus.slots.map { slot ->
            when (val result = slot.result) {
                null -> SavedSlot(slot.callerEntryId, slot.edgeId, slot.targetEntryId, payloadJson = null, canceled = false)
                NavResult.Canceled -> SavedSlot(slot.callerEntryId, slot.edgeId, slot.targetEntryId, payloadJson = null, canceled = true)
                is NavResult.Value<*> -> {
                    val serializer = requireNotNull(topology.edges[slot.edgeId]?.resultSerializer) {
                        "Edge '${slot.edgeId}' için resultSerializer yok — teslim edilmiş Value slotu serialize edilemez."
                    } as KSerializer<Any?>
                    val payload = json.encodeToString(serializer, result.value)
                    SavedSlot(slot.callerEntryId, slot.edgeId, slot.targetEntryId, payloadJson = payload, canceled = false)
                }
            }
        }
        return SavedState(keys = state.stack, nextId = state.nextId, pendingSlots = pendingSlots)
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeSlot(saved: SavedSlot): ResultBus.Slot {
        val result: NavResult<Any?>? = when {
            saved.canceled -> NavResult.Canceled
            saved.payloadJson != null -> {
                val serializer = requireNotNull(topology.edges[saved.edgeId]?.resultSerializer) {
                    "Edge '${saved.edgeId}' için resultSerializer yok — Value payload'ı decode edilemez."
                } as KSerializer<Any?>
                NavResult.Value(json.decodeFromString(serializer, saved.payloadJson))
            }
            else -> null
        }
        return ResultBus.Slot(saved.callerEntryId, saved.edgeId, saved.targetEntryId, result)
    }

    /**
     * C1 — PD (process death) restore: bu AYNI facade'in underlying state'ini [restored]'a re-point eder.
     * Android'de YALNIZ taze holder'ın PD-adopt yolunda çağrılır (config-change'te holder + canlı navigator
     * retained kalır → re-adopt YOK, MN-1). Yeni bir `RawNavigator` KURULMAZ → bu instance'ı ctor'da
     * yakalamış her sahip (özellikle rotasyondan sağ çıkan bir ViewModel) restore'dan sonra da display'in
     * gözlemlediği state'i sürmeye devam eder (spec §225 "stable RawNavigator"). `bus`/StateFlow
     * instance'ları KORUNUR (aynı `keysState`/`backStack` → mevcut collector'lar kopmaz), yalnız içerikleri
     * restore edilmiş snapshot'a döner. Ctor'un `restored != null` yolunun birebir eşleniği; İDEMPOTENT
     * (aynı snapshot'la tekrar çağrı state'i aynı değere sabitler, bkz. NavigatorIdentityRestoreTest) ve
     * event yayınlamaz — bu bir kuruluş, navigasyon değil.
     */
    internal fun adoptRestored(restored: SavedState) {
        state = GezginState(restored.keys, restored.nextId, topology)
        bus.restore(restored.pendingSlots.map(::decodeSlot))
        refreshBackStack()
    }

    // ---- public ops ----

    /** @GoTo — enterFlow'u topology'den çözer (hedef flow-start container-entry'si mi). */
    fun navigate(route: Route, singleTop: Boolean = true) {
        val enterFlow = resolveEnterFlow(route)
        val pushed = state.push(route, enterFlow = enterFlow, singleTop = singleTop) ?: return
        refreshBackStack()
        _events.tryEmit(NavEvent.Pushed(pushed.route))
    }

    /**
     * Geri: sıra (§8.1 / Fix 9) — (1) top flow-ENTRY ise TAMAMEN quit()'e devret (pending target olsa
     * bile: settleRemoved Canceled teslim eder), event `FlowQuit(canceled=true)` olur, `Popped` YOK;
     * (2) düz pop + `Popped` — top pending-target ise Canceled'ı settleRemoved teslim eder (tek kapı).
     * Dipte → onRootBack.
     */
    fun back() {
        val top = state.stack.last()
        if (isFlowEntry(top)) {          // (1) flow entry → quit() (settleRemoved Canceled'ı teslim eder)
            quit()
            return
        }
        popTopAndEmit()                  // (2) düz pop — pending-target Canceled'ı da settleRemoved verir
    }

    /** `@ReplaceTo` runtime'ı: `clearUpTo`'ya kadar (null = yalnız top) temizleyip `route`'u iter; çıkarılan pending-target'lara Canceled teslim eder. */
    fun replaceTo(route: Route, clearUpTo: KClass<out Route>? = null, inclusive: Boolean = true) {
        // M4 — modal-kind-at-root reddi MUTASYONDAN ÖNCE: replaceTo kökü temizleyip yerine bir modal
        // koyacaksa (sonuçtaki stack'in dibi = bir modal route) state hiç değiştirilmeden fırlat.
        // Aksi halde eski davranış (state önce `[modal]`'a döner, guard SONRAKİ composition'da patlar)
        // error-boundary'li host'ta navigator'ı kalıcı geçersiz bir stack'te bırakırdı.
        val resultingRoot = state.resultingRootAfterReplace(route, clearUpTo, inclusive)
        require(!modalRootGuard(resultingRoot)) {
            "replaceTo: sonuçtaki stack'in kökü modal kind olamaz (${resultingRoot::class.simpleName}) — " +
                "bir modal'ın altında en az bir SCREEN entry olmalı (Nav3 OverlayScene invariant'ı, §7). " +
                "clearUpTo=root ile bir modal'ı köke koymayın."
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

    /** `@BackTo` runtime'ı: stack'te `target`'a kadar pop (`inclusive` ise target da). Target yoksa `BackToTargetMissing` event'i, pop YOK. */
    fun backTo(target: KClass<out Route>, inclusive: Boolean = false) {
        val removed = state.backTo(target, inclusive)
        if (removed == null) {
            _events.tryEmit(NavEvent.BackToTargetMissing(target.simpleName ?: "?"))
            return
        }
        refreshBackStack()
        _events.tryEmit(NavEvent.PoppedTo(target.simpleName ?: "?", removed.map { it.route }))
        settleRemoved(removed)
    }

    /** Canceled ile flow kapat (root'ta onRootBack). */
    fun quit() {
        val flowId = state.currentFlowId() ?: return
        val removed = state.quitFlow(flowId)
        if (removed == null) {
            onRootBack()
            _events.tryEmit(NavEvent.RootBack)
            return
        }
        refreshBackStack()
        _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = true))
        settleRemoved(removed)                       // deliverValue=null → hayatta-kalan caller'lı target'lara Canceled
    }

    /** Value ile atomik kapat + caller'a teslim. Flow içinde değilken sessiz no-op (quit() ile simetrik).
     *  Nested ResultFlow'da hedef = EN-YAKIN-KAPSAYAN ResultFlow (spec §6); quit() ise innermost kalır. */
    fun quitWith(result: Any?) {
        // quitWith hedef seçimi: en içteki KAPSAYAN ResultFlow (spec §6);
        // hiç ResultFlow yoksa fallback = en içteki flow (typed katman quitWith'i zaten yalnız ResultFlow'da üretir).
        val top = state.stack.last()
        val chain = topology.flowChain(top.route::class)          // flowPath ile paralel (aynı uzunluk)
        val idx = chain.indexOfLast { it.isResultFlow }
        val flowId = (if (idx >= 0) top.flowPath.getOrNull(idx) else top.flowPath.lastOrNull()) ?: return
        val removed = state.quitFlow(flowId)
        if (removed == null) {
            onRootBack()
            _events.tryEmit(NavEvent.RootBack)
            return
        }
        refreshBackStack()
        _events.tryEmit(NavEvent.FlowQuit(flowId, canceled = false))
        // Value YALNIZ flow'un KENDİ entry slotuna (removed.first() = flow entry — contiguous blok garanti);
        // diğer hayatta-kalan caller'lı slotlar Canceled; caller'ı da kalkan iç slotlar dropFor→ResultDropped.
        settleRemoved(removed, deliverValue = result, valueTargetId = removed.first().id)
    }

    /**
     * M3 — entry-scoped `backWithResult`: SONUCU SAHİBİ entry'ye pinler. [entryId] artık top DEĞİLSE
     * (ör. sheet jest'le kapatıldıktan sonra async iş sonucu geç geldi) SESSİZ NO-OP → değer, o slotu
     * beklemeyen (başka tipte sonuç bekleyen) yabancı bir entry'nin slotuna teslim edilmez ve o entry
     * yanlışlıkla pop edilmez (kirli-teslim/çifte-back yarışı önlenir). Faz 2 codegen'in ürettiği tipli
     * `backWithResult(result)` ctor'daki `entryId`'yi bu overload'a bağlar.
     */
    fun backWithResult(entryId: Long, result: Any?) {
        val top = state.stack.last()
        if (top.id != entryId) return            // sahip entry artık top değil → teslim etme, pop etme
        if (!isPendingTarget(top.id)) return
        bus.deliver(top.id, NavResult.Value(result))
        popTopAndEmit()
    }

    /** Kolaylık: sahip = ÇAĞRI ANINDAKİ top (call-time-top). Tipli katman entry'ye bağlayan overload'ı kullanır. */
    fun backWithResult(result: Any?) = backWithResult(currentEntryId, result)

    /** Çağrı anındaki top entry id — açık-caller overload'larına Faz 2 codegen'in bağlayacağı kanca. */
    val currentEntryId: Long get() = state.stack.last().id

    /**
     * Task 2.6 — `:gezgin-test`'in tipli `fromX()` erişimi için minimal public kapı: [route]'u
     * uygulayan EN YAKIN (stack'te en üstteki) entry'nin id'si, yoksa `null`. `keys` `internal`
     * kalır; bu, üstüne kurulan tek [GezginInternalApi] opt-in üye.
     */
    @GezginInternalApi
    fun entryIdOf(route: KClass<out Route>): Long? = keys.lastOrNull { route.isInstance(it.route) }?.id

    /**
     * Açık-caller (Faz 2 kancası): idempotent (§6) — aynı (caller, edge) için slot varken (in-flight
     * VEYA teslim edilmiş-tüketilmemiş) push YAPMA. Aksi halde result isteği DAİMA yeni entry yaratır.
     */
    fun launchForResult(callerEntryId: Long, edgeId: String, route: Route) {
        // Pre-guard, bus.launch'un predicate'iyle birebir aynı (HERHANGİ bir slot — result durumu fark etmez):
        // teslim edilmiş ama tüketilmemiş slot varken re-launch, slotsuz öksüz bir entry push'lardı.
        if (bus.slots.any { it.callerEntryId == callerEntryId && it.edgeId == edgeId }) return
        // @GoForResult edge'i DAİMA container-entry'dir → hedef flow için taze instance mint (spec §8.1
        // re-entrancy sınırı: aynı flow tipine içten re-entry, dış instance'ın id'sini miras ALMAZ).
        val enterFlow = topology.flowChain(route::class).isNotEmpty()
        val pushed = state.push(route, enterFlow = enterFlow, singleTop = false)!!  // result isteği = daima yeni entry (singleTop=false → null dönemez)
        bus.launch(callerEntryId, edgeId, pushed.id)
        refreshBackStack()
        _events.tryEmit(NavEvent.Pushed(pushed.route))
    }

    /** Kolaylık: caller = ÇAĞRI ANINDAKİ top. Faz 2 codegen (caller top değilken) açık overload'ı kullanmalı.
     *  İki hızlı top-based çağrı farklı caller görür (ilk push top'u değiştirir) → dedupe istiyorsan
     *  explicit-caller overload'ını kullan; typed katman caller'ı entry'ye bağlar. */
    fun launchForResult(edgeId: String, route: Route) = launchForResult(currentEntryId, edgeId, route)

    /** Açık-caller (Faz 2 kancası): (caller, edge) slotunun sonuç akışı. */
    fun <T> results(callerEntryId: Long, edgeId: String): Flow<NavResult<T>> = bus.results(callerEntryId, edgeId)

    /**
     * Kolaylık: caller = ÇAĞRI ANINDAKİ top entry id. Restore sonrası geç re-attach bu yüzden ancak
     * orijinal caller entry mevcut top iken çalışır (call-time-top sözleşmesi); caller top DEĞİLKEN
     * (PD re-attach, Faz 2 codegen) açık [results]`(callerEntryId, edgeId)` overload'ı KULLANILMALI.
     */
    fun <T> results(edgeId: String): Flow<NavResult<T>> = results(currentEntryId, edgeId)

    /** Açık-caller sugar = launch + results.first() (Faz 2 kancası). */
    suspend fun <T> navigateForResult(callerEntryId: Long, edgeId: String, route: Route): NavResult<T> {
        launchForResult(callerEntryId, edgeId, route)
        return bus.results<T>(callerEntryId, edgeId).first()
    }

    /** Kolaylık sugar; caller çağrı anındaki top'tan (push'tan ÖNCE) yakalanır. */
    suspend fun <T> navigateForResult(edgeId: String, route: Route): NavResult<T> =
        navigateForResult(currentEntryId, edgeId, route)

    /**
     * @QuitAndGoTo (Faz 2 codegen kancası) — mevcut flow'u result'suz yık (quit() ile birebir aynı
     * teardown: hayatta kalan caller'lı pending slotlara Canceled, `FlowQuit(canceled = true)`) ve
     * ardından hedefe navigate et. Kaynak bir flow İÇİNDE DEĞİLKEN (flowId yok) yıkılacak bir şey
     * yoktur — düz `navigate` ile eşdeğerdir. Kök flow'da (quitFlow → null) quit()/quitWith ile aynı
     * kural: onRootBack()'e düş + `RootBack` yay, navigate ETME (teardown'un kendisi başarısız).
     */
    fun quitAndGoTo(route: Route) {
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
        navigate(route, singleTop = false)
    }

    // ---- internal helpers ----

    private fun refreshBackStack() {
        _backStack.value = state.stack.map { it.route }
        _keysState.value = state.stack.toList()   // id taşır → replaceTo same-value-diff-id de emit eder (§2.1/R2)
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

    /** Stack'ten kalkan entry'lerin slot/event muhasebesi — tüm removal path'lerinin TEK kapısı.
     *  Value YALNIZ [valueTargetId]'nin slotuna (quit edilen flow'un KENDİ entry'si) teslim edilir;
     *  diğer hayatta-kalan caller'lı pending target'lar Canceled alır — açık out-of-flow caller'lı
     *  yabancı-tipli bir slota asla Value sızmaz. deliverValue == null ise hepsi Canceled. */
    private fun settleRemoved(removed: List<GezginKey>, deliverValue: Any? = null, valueTargetId: Long? = null) {
        if (removed.isEmpty()) return
        val removedIds = removed.map { it.id }.toSet()
        for (slot in bus.slots) {
            if (slot.result == null && slot.targetEntryId in removedIds && slot.callerEntryId !in removedIds) {
                bus.deliver(slot.targetEntryId,
                    if (deliverValue != null && slot.targetEntryId == valueTargetId) NavResult.Value(deliverValue)
                    else NavResult.Canceled)
            }
        }
        bus.dropFor(removedIds).forEach { _events.tryEmit(NavEvent.ResultDropped(it.edgeId)) }
    }
}
