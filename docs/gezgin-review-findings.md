# Gezgin — Adversarial spec review bulguları + çözüm takibi

> Kaynak: Opus 4.8 adversarial pre-implementation review (2026-07-02), Nav3/KSP/Kotlin doğrulamalı.
> Her bulgu: **severity · sorun · çözüm önerim · durum.** Durum: 🔴 açık · 🟡 öneri var (onay bekliyor) · 🟢 çözüldü.

## Verdict
Çekirdek fikir sağlam ve birçok mekanizma doğrulandı (Nav3 back-stack modeli, entry-scoped VM decorator, `ResultRoute<T>` supertype-arg okuma, `DialogSceneStrategy`, edge→generated-method). **Ama iki compile-time blocker headline vaatleri kırıyor, bir tip iddiası yaygın bir durumda geçersiz, ve en zor doğruluk delikleri tip-sisteminin göremediği yerde (back/result/deep-link/brownfield).** "Derleniyorsa doğrudur" burada yanlış.

---

## ⚙️ V1/V2 kapsam (2026-07-05 reorganizasyon)
Graph modeli yeniden düzenlendi (detay: notes-history). **V1 = `@NavGraph` + `@FlowGraph`, tek-stack.** `@TabGraph`/`@SwitchTo` + multiple back stack + **deep-link (tüm özellik)** → **V2**. Sonuç: **M6** ve tab/deep-link ilişkili maddeler (N2 + N1/N4/N5'in deep-link kısımları) artık **V2-kapsamlı** — çözümleri tasarım-yönü olarak kayıtlı, özellik V2'de gelir. V1'de canlı kalan: B1·B2·M1·M2·M3·M4·M5 + N1(transition)·N5(kök-back)·N6(decider)·N8(modal-üstü)·N9(isim/`name=`).

## 🔴 BLOCKER

### B1 — "Multi-module bedava" + "nesting = subtyping" ikisi birden doğru olamaz 🟢 ÇÖZÜLDÜ
**Sorun:** Kotlin'de `sealed interface` alt-tipleri **aynı modül**de olmak zorunda. `:feature-order`'daki `OrderGraph : AppGraph` (AppGraph `:core`'da) **derlenmez.**
**Çözüm (kullanıcı — merkezi nav modülü):** benim runtime-merge önerim **iptal**. Tüm sealed graph'lar **tek modülde** (`core:navigation`) → tek sealed ağaç → derlenir; tüm feature'lar onu gördüğü için her route/navigator görünür (cross-feature `@GoTo` derlenir), deep-link reconstruction + serialization **compile-time kalır** (merge yok). Layered dep:
```
core:domain        (domain modelleri; @Serializable; deps yok)
   ▲
core:navigation    (sealed graph'lar + route'lar + Dialog/BottomSheet/ResultRoute contract'ları; sadece core:domain'i görür)
   ▲
feature:A/B/… + :app   (hepsi core:navigation'ı görür)
```
- **Codegen dağılımı:** `core:navigation` → navigator'lar + deep-link tablosu + topology + `SerializersModule` (tek ağaçtan, compile-time). `feature:X` → entry'ler (`provideXEntry` resolver'ları feature'da) → `xFeatureEntries()`. `:app` → montaj.
- Her feature KSP'si yalnız kendi modülünü işler; cross-module **annotation** okuması gerekmez (ksp#527 yok) — sadece **tip** görünürlüğü (bağımlılıkla gelir).
- **Codegen detayı:** navigator ctor `internal` (core:navigation); feature entry'si navigator'ı inşa etmez, core'un entry-wiring'inden alır.
- **İmplikasyon:** route-arg domain modelleri `@Serializable` olmalı (→ N3 de çözülür; SerializersModule core:navigation'da hazır).
- **Trade-off (kabul):** `core:navigation` bir hub; her feature her yere gidebilir (decoupling azalır) — ama "diğer feature'ın route'unu göremiyorum" problemini bitiren şey bu.

### B2 — `gezgin-koin` "codegen VM constructor'ına bakıp parametersOf şekli" KSP-infeasible 🟢 ÇÖZÜLDÜ
**Sorun:** KSP lambda gövdesi okuyamaz; somut VM tipi yalnız resolver lambda gövdesinde. Auto-`parametersOf` üretilemez.
**Çözüm (kullanıcı — `@ViewModel` + DI-detection):** `@ViewModel(Route)` VM class'ını codegen'e verir → codegen VM'in DI annotation'ını (`@HiltViewModel`/`@KoinViewModel`) + ctor `@Assisted`/`@InjectedParam`'ını **okuyabilir** (KSP concrete class'ı görüyor) → `provideXEntry`'nin `viewModel` param'ına **algılanan-DI default resolver** üretir (Hilt/Koin; androidx fallback). Kullanıcı `provideXEntry()` boş çağırır ya da custom lambda ile override. Ayrı gezgin-koin/hilt add-on'una gerek yok — DI desteği gezgin-mvi'de. (Auto-parametersOf artık feasible: infeasibility "VM tipi lambda gövdesinde"ydi; `@ViewModel` tipi KSP-görünür pozisyona taşıdı.) İşlendi: spec §10.1, §14, §15.

---

## 🟡 MAJOR

### M1 — `GezginMvi<S,I,E>` invariant; "somut VM alt-tip" iddiası E kaynağı olmayınca geçersiz 🟢 ÇÖZÜLDÜ (`@ViewModel` eritti)
**Sorun:** Invariant `GezginMvi` → resolver dönüş tipi VM'in S/I/E'siyle **birebir** eşleşmeli. E yalnız opsiyonel `@ScreenEffect(Flow<E>)`'te var; `@Screen(state, onIntent)`'te yok. `@ScreenEffect` yoksa ama VM effect yayıyorsa codegen E'yi bilemez → derlenmez.
**Çözüm (kullanıcı — `@ViewModel`):** VM'e `@ViewModel(Route::class)` konur → codegen **somut VM tipini** bilir, resolver'ı `-> OrderChainViewModel` (concrete) tipler → imzada `GezginMvi<S,I,E>` up-cast'i **yok** → invariance sorunu **erir.** S/I/E VM'in `GezginMvi` supertype arg'larından **kesin** okunur (content'ten türetme yok, E-kaynağı yok). Variance (`<out S, in I, out E>`) artık yük taşımaz, polish olarak kalır. **Guardrail:** `@ViewModel` var `GezginMvi` yok → derleme hatası. İşlendi: spec §10.1.

### M2 — Bir *flow*'un (ekran değil) result tipini nasıl deklare/döndürdüğü 🟢 ÇÖZÜLDÜ
**Sorun:** (1) `CheckoutFlow : ResultRoute<Order>` graph interface'inde mi, start route'ta mı? `@GoForResult(target)` route'lar üzerine tanımlı; flow'a giriş = start destination'a push, interface'e değil → çifte kimlik. (2) `finish(order)`'ı hangi ekranın navigator'ı expose eder, hangi tiple? (3) sub-stack teardown + deliver, PD'de. "locked" işaretli ama build-ready değil — en az pişmiş alan.
**Çözüm:** flow graph interface'i `: ResultFlow<T>` implement eder (route için `ResultRoute<T>`'nin paraleli). `@GoForResult(CheckoutGraph::class)` → `suspend goToCheckoutForResult(startArgs): NavResult<T>` (flow `@StartDestination`'ını push, keyed slot). Flow içindeki **her** ekranın navigator'ı `finish(result: T)` alır (T = en yakın kapsayan `ResultFlow`'un arg'ı, KSP supertype'tan okur; route tek home-graph'ta → belirsizlik yok). `finish` = flow alt-stack'ini **atomik** pop + caller'a `Value` (state-as-data → atomiklik + PD bedava; slot key = M3 per-request id). `quit()` = `finish` Canceled. `@GoForResult(X)`: X `ResultFlow` graph → flow-mode, `ResultRoute` route → screen-mode. İşlendi: spec §6, §8.

### M3 — Stack'te aynı route tipi iki kez varken result delivery + `backTo` belirsiz 🟢 ÇÖZÜLDÜ
**Sorun:** `@GoTo(singleTop)` değere göre dedup → `Detail(1)`/`Detail(2)` bir arada. `backToDetail()` hangisi? Pending-result slot key'i tanımsız. Aynı hedefe iki eşzamanlı `goToXForResult` → yanlış caller.
**Çözüm:** slot key = **request başına monotonik id** (caller entry'nin saved-state'inde; §6'ya işlendi). `backTo` = **nearest-ancestor** (default; §4.2'ye işlendi). İşlendi: spec §6, §4.2.

### M4 — `@NoBack`'in no-op `BackHandler`'ı predictive-back'i bastırır + undocumented Nav3 dispatch sırası 🟢 ÇÖZÜLDÜ (design; verify implementation'da)
**Sorun:** Nav3 içeride `NavigationEvent` kullanıyor; in-entry BackHandler vs NavDisplay LIFO sırası belgesiz; no-op consuming handler predictive preview'ı öldürür.
**Çözüm (kullanıcı onayladı):** `@NoBack` codegen no-op BackHandler yerine **entry-metadata flag**. `GezginDisplay` back logic'i (tek back-authority) flag'i okur → back'i tüketir + predictive'i explicit kapatır. Ekranın kendi BackHandler'ı (redirect/Gap-3) yine inner. **On-device Nav3 dispatch doğrulaması implementation'a bırakıldı** (design kararı: flag). İşlendi: spec §4.2, §13, §16.

### M5 — DialogFragment "bridge"i Nav3'ün tek-otorite back stack'iyle çatışır; brownfield'ın kırılgan yeri 🟢 ÇÖZÜLDÜ (kapsam kesildi)
**Sorun:** `AndroidFragment` view'ı composition'a koyar; `DialogFragment` kendi Window'unu FragmentManager'la sahiplenir → **çifte otorite**: PD'de duplicate/orphan dialog, back double-fire/desync, predictive yok, dismiss/pop race.
**Çözüm (kullanıcı kararı, 2026-07-04):** bridge tamamen **kaldırıldı**. `@FragmentDialog`/`@FragmentBottomSheet` iptal; fragment interop yalnız `@FragmentScreen` (§11.1). Dialog'lar native `@Dialog`/`@BottomSheet`'e çevrilir (en ucuz çevrilen UI). Çift-otorite / PD çift-restore / dismiss-pop race riski **kökten yok** → savunulacak kırılganlık kalmadı; spec §11.2 buna göre yeniden yazıldı.

### M6 — Nested flow'a deep link, Map-of-stacks modeliyle uyuşmuyor 🟢 ÇÖZÜLDÜ (graph modeli yeniden kuruldu)
**Sorun:** §5 reconstruction düz liste üretir; §8 nested = Map-of-stacks. Bir flow içindeki (bir tab içindeki) route'a deep link → container ağacı kurmalı (hangi tab aktif + nested stack), düz liste değil. Ek: `@NoBack` ekranına deep link (kullanıcı kapana kısılır/app çıkar); flow-input bekleyen flow'a deep link (input yok).
**Çözüm (kullanıcı, 2026-07-05):** kök neden = tab/flow rolünü **edge** belirliyordu; **deklarasyona** taşındı. `@NestedNavGraph`+`@Switch` kaldırıldı; container tipi = annotation: `@NavGraph` (transparan own-stack/tab), `@FlowGraph` (opak transactional, §8.1), `@TabHost` (switcher), gruplama (annotation yok). Reconstruction artık **yapısal tiplerden** container tree kurar (ata `@TabHost`→tab-activate+kardeş-seed, `@NavGraph`→substack) — edge-inference yok, "§5'i §8'le uzlaştır" yaması gerekmedi. `@FlowGraph` kara kutu → iç deeplink zaten yasak. Guardrail'ler: `@DeepLink`+`@NoBack`, iç-üye-deeplink, URI'siz-startArgs flow = derleme hatası. İşlendi: §2.4/§3.1/§4.1/§4.2/§5/§6/§8/§8.1/§14/§16.

---

## 🟢 MINOR — çözüldü / kapsam (2026-07-05)
- **N1** 🟢 — per-call transition override yalnız `raw.navigate(route){ transition = … }`; typed metotta yok (§9).
- **N2** 🔵 V2 — aynı graph tipini iki tab: tab/multi-stack V2'ye taşındı → V1'de mesele değil.
- **N3** 🟢 — `route.toBundle()` polimorfik `SerializersModule` B1 ile `core:navigation`'da codegen; bağımlılık hazır.
- **N4** 🟢 moot — M5'te DialogFragment bridge silindi, yalnız `@FragmentScreen` (view-host `onUpdate`); iki-mod konusu yok.
- **N5** 🟢 — kökte son entry `back()` → `GezginDisplay.onRootBack` (default OS app-exit); root asla programatik boşalmaz (§8/§8.1).
- **N6** 🟢 — koşullu start = **decider route** (argsız start, `LaunchedEffect`'te `replaceTo`); Guardrail 1 kırılmaz (§12).
- **N7** 🟢 — middleware = **observe-only** (events/backStack; log/analytics); veto/rewrite → V2 (§10).
- **N8** 🟢 — modal-üstü-modal = normal stack, Nav3 `OverlayScene`; design destekli, on-device doğrula (§7).
- **N9** 🟢 — aynı hedefe çakışan edge → opsiyonel `name=`; isimsiz çakışma = derleme hatası (§4.1).

---

## 🟢 Sağlam (doğrulandı, dokunma)
- State-as-data çekirdek Nav3'e doğru oturuyor (`SnapshotStateList<NavKey>`); UI'sız test hikâyesi dürüstçe buradan çıkıyor.
- Entry-scoped VM (`rememberViewModelStoreNavEntryDecorator`, `lifecycle-viewmodel-navigation3`) gerçek + doğru; stateless-facade-navigator config-change güvenliği sağlam.
- `ResultRoute<T>` tip okuma, `@Assisted` param okuma, `@DeepLink` placeholder→property doğrulama, S/I'yı content imzasından türetme, `@Screen`+route eşleme — **KSP modelinde doğrulandı** (tüm korele tipler tek modülde çözülebiliyorsa — B1'e bağlanır).
- `DialogSceneStrategy` resmi; modal-as-entry sağlam. BottomSheet recipe (library re-ship eder, sorun değil).
- **Edge→generated-method → undeclared-nav-derlenmez** = asıl güçlü fikir, iddia edildiği gibi enforce edilebilir. Çekirdek değer önermesi tutuyor.

---

## Çözüm sırası (öneri)
1. **B1** (multi-module) — load-bearing, KSP stratejisini yeniden şekillendirir, kod öncesi şart.
2. **B2 + M1** — mekanik/net düzeltmeler (auto-parametersOf düşür; `GezginMvi<out S, in I, out E>`).
3. **M2** (flow result) — dedike tasarım pası.
4. **M3 + M4** — runtime-correctness + Nav3 doğrulama.
5. **M5 + M6** — brownfield reframe + deep-link/flow uzlaştırma.
6. **N1–N9** — netleştirme paketi.
