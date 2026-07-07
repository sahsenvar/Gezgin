# Gezgin — Adversarial spec review R2: bulgular + çözüm takibi

> Kaynak: Fable 5 adversarial pre-implementation review (2026-07-07), Nav3 kaynak kodu + resmî release notları + JetBrains CMP duyuruları web-doğrulamalı. Önceki tur: [gezgin-review-findings.md](gezgin-review-findings.md) (oradaki çözümler re-report edilmedi; yalnız yanlış çıkan varsa işlendi).
> Her bulgu: **severity · sorun · çözüm önerisi · durum.** Durum: 🔴 açık · 🟡 öneri var (onay bekliyor) · 🟢 çözüldü.
> **2026-07-07 güncelleme:** tüm çözümler kullanıcı tarafından onaylandı ve spec'e işlendi (`gezgin-design.md` + `gezgin-by-example.md`) — tüm durumlar 🟢.

## Verdict
V1 henüz implementation-ready değil — ama mimari çekirdek (state-as-data + Nav3 + edge→metot enforcement) bu turda da ayakta; kırıklar mekanizma katmanında. İki blocker aynı köke iniyor: spec back stack'i "kullanıcının route değerlerinden ibaret düz liste" sayıyor, oysa hem Nav3'ün doğrulanmış `contentKey` semantiği hem de result/flow mekanikleri **per-entry kimlik + meta katmanı** gerektiriyor (R2); o katman olmadan "sonuç PD'ye dayanır" vaadinin re-attach ucu boşta (R1). Sıra: (1) entry-identity katmanı (R2), (2) result re-attach protokolü (R1), (3) §8.1 kural setinin tek tutarlı matriste yeniden yazımı (M1′/M3′/M4′) + `@NoBack` mekanizma çevirisi (M5′); kalanlar mekanik/netleştirme.

---

## 🔴 BLOCKER

### R1 — Result re-attach mekanizması yok; PD'de sonuç kaybolur, recomposition'da çağrı duplike olur 🟢 çözüldü (spec'e işlendi, 2026-07-07)
**Sorun:** Keyed pending-result slotu (§6) sonucu *saklar*, ama sonucu kimin/ne zaman/nasıl yeniden tükettiği spec'te yok. `suspend goToXForResult()` continuation'ı PD'de ölür; slot restore edilir ama okuyacak API yüzeyi tanımsız. by-example §4 "çağrı re-attach olur" diye söz verir — mekanizmasız.
**Somut senaryolar:** (a) VM-driven: Checkout VM `viewModelScope`'ta bekliyor → PD → restore → kullanıcı adres seçer → slot'a `Value` yazılır → VM yeniden yaratılmış, bekleyen coroutine yok → **sonuç sessizce kaybolur**. (b) Composable-driven: çağrı `LaunchedEffect`'te → her config change'te effect yeniden fırlar → **ikinci push** (monotonik id *yeni* istek üretir; "aynı istek" kimliği yok). §6, §10, by-example §4.
**Çözüm önerisi (bu turda sunuldu):** ActivityResult dersini uygula — *launch* ile *receive*'i ayır. `@GoForResult` edge'i üç üye üretir: fire-and-forget `launchX(params)`, re-attach yüzeyi `val xResults: Flow<NavResult<T>>` (replay-until-consumed, her recreation'da yeniden collect edilir), convenience `suspend goToXForResult()` (launch + ilk sonucu bekle; aynı edge'de bekleyen istek varsa **re-attach eder, push etmez** → idempotent). Slot anahtarı = `(callerEntryId, edgeId)`; "bir caller-entry + edge başına en fazla bir in-flight istek" invariant'ı monotonik-id karmaşasını (eski M3) sadeleştirir. R2'nin entry-id'sine dayanır.

### R2 — Entry kimliği tanımsız; eşit-değerli iki route Nav3'te (doğrulanmış) aynı ViewModelStore + saved state'i paylaşır 🟢 çözüldü (spec'e işlendi, 2026-07-07)
**Sorun:** NavKey = kullanıcının route *değeri*; instance kimliği yok. **Doğrulandı** (NavEntry.kt KDoc, androidx-main): `contentKey` default = `key.toString()`; aynı contentKey'li entry'ler content + decorator state **paylaşır**; `onPop` paylaşılan store'u temizler. `@GoTo(singleTop)` dedup'u dup'ları engellemez (farklı yollardan oluşur).
**Somut senaryo:** `[Feed, Detail(42), Cart, Detail(42)]` → üstteki `Detail(42)`'den `back()` → paylaşılan VM store `onCleared` → alttaki `Detail(42)`'nin state'i gitmiş. "MVI scoping + PD bedava" (§2.1/§10) kırılır. Ayrıca result request-id ve flow-instance sınırı (M2′) için per-entry meta taşıyacak yer yok — çıplak `List<NavKey>` taşıyamaz.
**Çözüm önerisi (bu turda sunuldu):** iç temsil `List<GezginKey>` — `@Serializable GezginKey(route, id: Long, flowPath: List<Long>)`; `contentKey = id` (duplicate sorunu biter), flow-instance sınırı `flowPath`'ten (re-entrancy dahil kesin), result slot key `id`'den. Public API değişmez (`backStack: StateFlow<List<Route>>` unwrap eder); `provideXEntry` şekli korunur, yalnız iç kayıt Gezgin registry'sine gider (§10.1 "GezginEntry yok" sloganı API seviyesinde yaşar, iç temsilde envelope şart).

---

## 🟡 MAJOR

### M1′ — "Root-start=ResultFlow / root flow quit edemez" derleme hatası olamaz 🟢 çözüldü (spec'e işlendi, 2026-07-07)
§8.1 bunu derleme-hataları listesine koyuyor; ama root `rememberNavigator(start = …)` ile **runtime'da** seçilir (§12), root'u işaretleyen annotation yok, aynı flow hem root hem nested olabilir. **Senaryo:** result'suz `OnboardingFlow` root; üyesi `quit()` → boş stack → empty-stack invariant ihlali. **Öneri:** runtime guard (root flow'da `quit` → `onRootBack`'e yönlenir) + ResultFlow-as-root = navigator kuruluşunda `IllegalArgument`; §8.1 dürüstçe "runtime invariant" diye yeniden sınıflandırılsın (ya da `@AppRoot` compile-time bağı eklensin). §8.1 ↔ §12.

### M2′ — Flow-instance sınırı tanımsız; re-entrancy `quitWith` atomik pop'unu belirsizleştirir 🟢 çözüldü (R2 `flowPath` ile; spec §8.1)
Düz listede "flow alt-stack'i" üyelik-tipine-göre-bitişiklikle bulunamaz. **Senaryo:** flow üyesi `@GoForResult(kendi flow'u)` (hiçbir kural engellemiyor) → `[.., Cart, Payment, Cart′, Payment′]` bitişik → `quitWith` hangi instance'ı yıkar? **Öneri:** R2 `flowPath` instance-id'leri sınırı kesinleştirir; recursion tanımlı hâle gelir. §6/§8.1.

### M3′ — §8.1 giriş/çıkış matrisi kendi içinde tutarsız; derleme-hata listesi eksik 🟢 çözüldü (spec'e işlendi, 2026-07-07)
(a) Matris result'suz flow için "giriş yalnız `@GoTo`" der; `@QuitAndGoTo(X)` bülleti "X = container-start" ile flow'a girişe izin verir — çelişki. (b) Hata listesinde `@ReplaceTo(ResultFlow)` ve `@QuitAndGoTo(ResultFlow)` yok → `@ReplaceTo(CheckoutFlow::class)` derlenir, bekleyen caller olmadan ResultFlow'a girilir. (c) `@ReplaceTo(result'suz flow container)` hiç ele alınmamış. **Öneri:** giriş/çıkış kurallarını tüm edge türleri × hedef türleri tek tabloda kapatan yeniden yazım. §8.1, §4.1.

### M4′ — Flow içinden `clearUpTo` flow sınırını aşabilir → bekleyen caller yıkılır 🟢 çözüldü (spec'e işlendi, 2026-07-07)
İç-hedefli `@ReplaceTo` serbest ama `clearUpTo`'nun flow-dışı ataya işaret etmesini engelleyen kural yok. **Senaryo:** `Payment`'ta `@ReplaceTo(Error::class, clearUpTo = HomeRoute::class)` → temizlik `@GoForResult` caller'ını da siler → stranded continuation, orphan slot. **Öneri:** "flow üyesinde `clearUpTo` flow-dışına işaret edemez" = derleme hatası. §4.1 ↔ §8.1 ↔ §6.

### M5′ — `@NoBack`'in seçilen mekanizmasının public Nav3 API'si yok; reddedilen seçenek doğrulanmış çalışan yol 🟢 çözüldü (spec'e işlendi, 2026-07-07)
**Doğrulandı** (NavDisplay.kt): back handler içeride `isBackEnabled = previousEntries.isNotEmpty()`; per-entry "back'i tüket"/"predictive preview kapat" API'si yok (metadata yalnız transition spec'leri). `onBack` no-op'lansa preview oynar + geri yaylanır — "predictive'i explicit kapatır" (§4.2) uygulanamaz. Reddedilen entry-içi enabled `BackHandler` ise: alpha09'dan beri nested dispatcher'a saygı (LIFO, en içteki kazanır — kaynak-doğrulamalı, resmî dokümanda yok) + preview bastırması @NoBack'te zaten istenen davranış. **Öneri:** mekanizmayı çevir — codegen @NoBack entry content'ine Gezgin-sahipli inner handler enjekte etsin. §4.2/§14/§16 (eski M4 kararının revizyonu).

### M6′ — §2.2 "Nav3 Android/iOS/desktop stable, web beta" iddiası yanlış: non-Android Nav3 alpha 🟢 çözüldü (spec'e işlendi, 2026-07-07)
**Doğrulandı:** CMP 1.10.0 (Oca 2026) Nav3'ü tüm hedeflere getirdi ama `org.jetbrains.androidx.navigation3:navigation3-ui` = **1.0.0-alpha05** (CMP 1.11.1'de hâlâ alpha). "Stable/beta" ayrımı CMP framework'ünün platform stabilitesi, Nav3'ün değil. Android'de androidx navigation3 **1.1.4 stable** — o kısım doğru. Churn gerçek: `onBack` imzası alpha11'de değişti (`(Int)->Unit` → N kez çağrılan `()->Unit`), `EntryProviderBuilder`→`EntryProviderScope`. iOS/desktop'ta predictive-back *preview* doğrulanamadı (iOS: edge-swipe; desktop/web: Esc=back) → §9 `predictive` slotu fiilen Android-ağırlıklı. **Öneri:** §2.2'yi düzelt, min-sürümleri sabitle, `GezginDisplay` adapter'ını churn sigortası olarak vurgula; by-example kapanış tablosunu da düzelt. §2.2/§9.

### M7′ — §3.3 "navigator ctor `internal`" ↔ §10.1 üretilen feature kodu ctor'u doğrudan çağırıyor 🟢 çözüldü (spec'e işlendi, 2026-07-07)
§10.1'in üretilen `provideOrderChainEntry`'si feature modülünde `OrderChainNavigator(…)` çağırır — cross-module `internal` ctor **derlenmez** (ayrıca snippet'te `raw` tanımsız). "Core'un entry-wiring'i" mekanizması spec'lenmemiş. **Öneri:** core:navigation'da public factory (`fun RawNavigator.orderChainNavigator()`), §10.1/§14 örnekleri ona geçsin. §3.3 ↔ §10.1/§14.

---

## 🟢 MINOR

- **N-a** 🟢 — `clearUpTo = Self` geçersiz Kotlin: annotation param'ı object instance alamaz; `KClass<out NavKey> = Self::class` olmalı (mekanizma sağlam, notasyon düzeltilsin). §4.1.
- **N-b** 🟢 — `override val transition = transition {…}` derlenmez: `@Serializable` sınıfta initializer'lı gövde property'si serialize edilmeye çalışılır; `Transition` serializable değil. Örnekler `get() =` ya da `@Transient` kullanmalı. §3.1/§9, by-example §9.
- **N-c** 🟢 — Modal root olamaz (**doğrulandı**: `SceneState` `require(overlaidEntries.isNotEmpty())`): dialog/sheet stack'teki tek entry ise crash; start=modal-kind + "replace ile yalnız modal kalması" guardrail'i yok. §7/§12.
- **N-d** 🟢 — Dialog back'i GezginDisplay'i bypass eder (**doğrulandı**: `DialogScene` → `Dialog(onDismissRequest = onBack)`, window tüketir, predictive yok): "tek back authority" (§4.2) modal'lar için geçerli değil; §7 ile uzlaştırılmalı.
- **N-e** 🟢 — `@BackTo(Y)` ve Y stack'te yoksa davranış tanımsız (no-op mu hata mı). §4.2.
- **N-f** 🟢 — `@NoBack` start/kök ekranda serbest → kullanıcı back ile app'ten çıkamaz; guardrail yok. §4.2/§8.1.
- **N-g** 🟢 — Fragment interop: (1) `gezginNav`'ın `onUpdate { bindGezgin }` öncesi çağrısı (PD sonrası `onViewCreated`) — geçerlilik penceresi tanımsız; (2) legacy fragment'ın kendi `OnBackPressedDispatcher` callback'i NavDisplay'i LIFO'da geçer. §11.1.
- **N-h** 🟢 — Graph üyeliği tanımı (lexical nesting mi direct supertype mı) + "tek home-graph" kuralı spec gövdesinde yok; flow opaklığı buna dayanıyor. §3.1/§8.1.
- **N-i** 🟢 — `@ViewModel` + `@Screen` üçlüsünün aynı modülde olma zorunluluğu yazılmamış (per-module KSP eşleşmesi gerektirir). §10.1/§14.
- **N-j** 🟢 — `onRootBack` "OS app-exit" Android-terimli; desktop (Esc=back) ve iOS'ta kök-back semantiği tanımsız. §8.
- **N-k** 🟢 — `singleTop` "değere göre dedup" belirsiz: yalnız top mu, tüm stack mi (move-to-top?). R2 ile etkileşir. §4.1.
- **N-l** 🟢 — by-example drift: `@NoBack @Quit`'li route `@NavGraph` üyesi (spec `@Quit`'i yalnız FlowGraph'ta üretir → örnek derlenmez); `GezginDisplay(entries = …)` imzası §12 ile çelişir; kapanış tablosunda "Nav3 stable" tekrarı.

---

## 🟢 Sağlam (bu turda doğrulandı, dokunma)
- `entryProvider { entry<T>{} }` + `EntryProviderScope<T>` adı güncel API ile birebir (alpha11 rename).
- `DialogSceneStrategy` resmî; stacked overlay (dialog-üstü-dialog) kaynak kodda destekli (`SceneState` peel loop) — N8 ✓.
- `lifecycle-viewmodel-navigation3` MP artefaktı mevcut (`org.jetbrains.androidx.lifecycle` 2.10.0-alpha05, `rememberViewModelStoreNavEntryDecorator` ✓).
- `GezginMvi<out S, in I, out E>` variance-geçerli; KSP tarafında V1'in dayandığı her şey okunabilir (annotation KClass arg'ları, `ResultFlow<T>`/`GezginMvi` supertype tip-arg'ları — `BaseViewModel` substitution dahil); V1 property-initializer'a bağımlı değil.
- iOS'ta back gesture (start-edge swipe) CMP 1.10'da var; desktop/web Esc=back.

## Çözüm sırası (öneri)
1. **R2** (entry-identity/meta katmanı) — load-bearing; R1, M2′ ve eski-M3 slot'unu tek hamlede besler.
2. **R1** (re-attach protokolü) — API yüzeyi kararı (launch/collect/suspend üçlüsü).
3. **M1′+M3′+M4′** — §8.1 kural setinin tek matriste yeniden yazımı.
4. **M5′** — `@NoBack` mekanizma çevirisi (inner handler).
5. **M6′+M7′** — stable-iddiası düzeltmesi + navigator factory.
6. **N-a…N-l** — netleştirme paketi.
