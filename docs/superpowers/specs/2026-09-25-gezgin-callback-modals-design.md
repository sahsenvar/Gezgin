# Callback taşıyan `@Dialog` / `@BottomSheet` route'ları — tasarım notu

Durum: v2 — onaylandı ve uygulandı. Tüketici: ZAD Android, `feature-contract.md` F11 / K7.

v1 taslağı (callback imzası composable'da, `XOpener` + feature-modülü extension'ı) ZAD incelemesinde
düştü. 40 çağıran-modül/modal çifti modül sınırını geçiyor ve ZAD'da feature→feature bağımlılığı
yasak. Bu sürümde callback'ler route'un kendi alanlarıdır.

## 1. Amaç ve kapsam

Modal, çağıranın tek bir çağrısıyla argümanlar ve lambda'larla açılır. Sonuç doğrudan lambda ile
döner; çağıranda `launchX` / `ObserveEffects(nav.xResults)` ayrımı ve sealed sonuç tipi olmaz.
Modal composable'ı ViewModel'siz, saf bir composable'dır.

- Kapsam: `@Dialog` ve `@BottomSheet`.
- Kapsam dışı: iş mantığı olan (VM'li) modallar `ResultRoute<T>` ile kalır (kullanıcı kararı).
  `@Screen`, `@FullscreenModal` ve flow'lar kapsam dışıdır.

## 2. Kullanıcının yazdığı

```kotlin
// core:navigation — callback'ler route alanı; @Serializable YAZILMAZ (serializer'ı Gezgin üretir)
data class LevelConfirmationDialog(
    val optionLevel: OptionLevel,
    val onConfirm: () -> Unit,
    @OnDismiss val onDismiss: () -> Unit,
) : OptionGraph, DialogContract

@Open(LevelConfirmationDialog::class)
data class OptionDetailScreen(val id: String) : OptionGraph

// feature modülü — ViewModel yok
@Dialog(LevelConfirmationDialog::class)
@Composable
fun LevelConfirmationDialog(optionLevel: OptionLevel, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SimpleDialogContent(..., onPrimaryClick = onConfirm, onSecondaryClick = onDismiss)
}

// çağıran effect handler — tek çağrı
nav.openLevelConfirmationDialog(
    optionLevel = effect.optionLevel,
    onConfirm = { onIntent(LevelUpgradeConfirmed); nav.back() },
    onDismiss = { nav.back() },
)
```

Route alanlarındaki tip ve parametre bilgisi nav modülünde durur. Bu yüzden `openX` navigator'ın
**üyesi** olarak üretilir ve graph'ı gören her modülden çağrılabilir. Modül sınırı sorunu yoktur.

## 3. Tanımlar

- **Callback alanı:** Birincil ctor'da fonksiyon tipli bir parametre. `-> Unit` döner, `suspend`
  değildir, parametre alabilir (`(Item) -> Unit`).
- **Callback route'u:** En az bir callback alanı olan route.
- **`@OnDismiss`:** `AnnotationTarget.VALUE_PARAMETER`; route'ta en fazla bir kez, parametresiz
  `() -> Unit` bir callback alanında kullanılabilir. Kap kapanışını (geri tuşu, dışarı dokunma,
  swipe) bu alana bağlar (§6).

## 4. İşlemci

### 4.1 Nav modülü

- **`@Open(vararg target)`:** `@GoTo` gibi graph arayüzüne ya da route'a konur, `@Repeatable`'dır.
  Hedef bir callback route'u olmalıdır. Her hedef için kaynağın navigator'ına bir üye üretilir:
  ```kotlin
  fun openLevelConfirmationDialog(optionLevel: OptionLevel, onConfirm: () -> Unit, onDismiss: () -> Unit) {
      raw.open(LevelConfirmationDialog(optionLevel, onConfirm, onDismiss))
  }
  ```
  Parametreler `goToX` kuralıyla aynıdır: tüm ctor parametreleri ctor sırasıyla ve zorunlu.
  Metot adı `open` + X'tir; `name=` ile değiştirilebilir.
- **Serializer:** Callback route'ları için serializer üretilmez ve route serializers module'e
  kaydedilmez. Bu route'lar hiç kaydedilmediği için (§5) serializer'a ihtiyaç yoktur.
  `@Serializable` yazılırsa kotlinx lambda alanında zaten derleme hatası verir.
- **Topoloji:** `GezginTopology.transientRoutes: Set<KClass<out Route>>`, callback route'larının
  kümesidir.

### 4.2 Feature modülü (composable)

`@Dialog`/`@BottomSheet` fonksiyonunun parametreleri şöyle eşlenir:
- `route` / `nav`: bugünkü anlamı korunur.
- Route ctor property'siyle aynı ad ve tipteki parametre: o alanın değeri. Callback alanları da
  aynı kuralla eşlenir. Composable alanların bir alt kümesini alabilir.
- Başka bir parametre derleme hatası verir.

Entry codegen callback'leri bir "hâlâ yığında mı" koruyucusuyla sarar:

```kotlin
register<LevelConfirmationDialog>(kind = EntryKind.DIALOG, noBack = false, onDismiss = { it.onDismiss() }) { route ->
    val raw = LocalGezginRawNavigator.current
    val entryId = LocalGezginEntryId.current
    LevelConfirmationDialog(
        optionLevel = route.optionLevel,
        onConfirm = { if (raw.isOnStack(entryId)) route.onConfirm() },
        onDismiss = { if (raw.isOnStack(entryId)) route.onDismiss() },
    )
}
```

`isOnStack` kontrolü, entry pop edildikten sonra gelen çağrıyı sessizce yok sayar. Çift tıklamada ikinci
`nav.back()` alttaki ekranı kapatamaz. Parametreli callback'ler de aynı biçimde sarılır:
`{ p0 -> if (raw.isOnStack(entryId)) route.onSelect(p0) }`.

## 5. Runtime

- **`RawNavigator.open(route)`** (public): `navigate` ile aynıdır; tek fark singleTop
  karşılaştırmasıdır. Data class eşitliği lambda'ları da karşılaştırır ve her açılışta yeni lambda
  geldiği için eşitlik hiç tutmaz. Callback route'larında singleTop bu yüzden **tip** üzerinden
  işler: tepede aynı tipte bir modal varsa ikinci açılış yok sayılır. Böylece çift tıklama modalı iki
  kez açmaz. Tipli navigator'ı olmayan uygulama seviyesi açılışlar (ZAD `AppEffectHandler`,
  `KycApp`) da `raw.open(Route(...))` kullanır; route lambda'ları taşıdığı için ek bir API
  gerekmez.
- **Ayrı bir kayıt yok.** Lambda'lar yığındaki route örneğinin içinde yaşar. Entry pop edilince
  route ile birlikte serbest kalır.
- **Kaydetme:** `save()` callback route'u entry'lerini `SavedState`'e **yazmaz**. Bu entry'lerin
  *caller* olduğu sonuç slotları da yazılmaz. Geri yüklemede bu entry'ler hiç var olmadığı için
  render edilmez, ölü buton oluşmaz.

## 6. Kapanış ve kap

- Kapanışı çağıran yapar: callback içinde `nav.back()`. Gezgin callback'ten sonra otomatik
  kapatmaz.
- Kap kapanışı (route'un `DialogContract` / `BottomSheetContract` değerlerine göre geri tuşu,
  dışarı dokunma, swipe):
  1. Route'ta `@OnDismiss` alanı varsa önce o çağrılır.
  2. Entry hâlâ yığındaysa Gezgin `back(entryId)` ile pop eder.

  `onDismiss = { nav.back() }` iki kez pop etmez; `nav.back()` unutulsa bile görünmez sheet
  kalmaz. `@OnDismiss` yoksa kap sessizce pop eder.
- Callback içindeki `nav.back()` sheet'i animasyonsuz kaldırır. Bu v1'de bilinen bir kısıttır;
  scene seviyesinde ayrı bir işte çözülür.

## 7. Yaşam döngüsü

| Olay | Davranış |
|---|---|
| Pop / flow kapanışı / `replaceTo` / `backTo` | Route örneği yığından çıkar, lambda'lar onunla birlikte serbest kalır. |
| Config change | Navigator Activity-scoped holder'da korunur, route ve lambda'ları da korunur. Lambda'lar açılış anındaki referansları tutar: `vm::onIntent` ve `nav` güvenlidir. Composition nesneleri (`LocalContext`, `rememberCoroutineScope`, `ZadEffectScope.context`) bayat kalır; ZAD'ın sızıntı kapısı (F11) bunu engeller. |
| Process death | Modal kaydedilmediği için geri gelmez; kullanıcı arkasındaki ekranı görür ve modalı aynı ekrandan yeniden açabilir (kullanıcı kararı). Modal açıkken VM'de "bekliyorum" durumu tutan akış, bu durumu geri yüklemede sıfırlamalıdır (ZAD kuralı). |
| `restoreKey` değişimi | Eski holder Activity'nin ViewModelStore'unda kalır ve eski yığın, eski lambda'larla birlikte, Activity ömrü boyunca tutulur. Navigator composition'dan config change dışında bir sebeple ayrılınca callback route'u entry'leri düşürülür (Android'de `isChangingConfigurations` kontrolü). |

## 8. Mevcut kenar üretimiyle ilişki

- Callback route'unu yalnızca `@Open` hedefleyebilir. `@GoTo`, `@ReplaceTo`, `@GoForResult` ve
  `@QuitAndGoTo` callback'siz açardı; bu yüzden reddedilir.
- `ResultRoute`, `launchX` / `xResults` ve VM'li modallar olduğu gibi kalır; yeni özellik opt-in'dir.

## 9. Doğrulama kuralları

| Kod | Nerede | Kural |
|---|---|---|
| OP1 | nav | `@Open` hedefi bir callback route'u olmalı (graph ya da callback'siz route olamaz). |
| OP2 | nav | Callback route'unu `@Open` dışında bir ileri edge hedefleyemez. |
| OP3 | nav | Callback route'u `ResultRoute` olamaz ve bir flow'un `@StartDestination`'ı olamaz. |
| OP4 | nav | Callback alanı `-> Unit` dönmeli ve `suspend` olmamalı. |
| OP5 | nav | `@OnDismiss` en fazla bir kez ve yalnızca `() -> Unit` bir callback alanında kullanılabilir. |
| CB1 | feature | Callback route'u yalnızca `@Dialog`/`@BottomSheet` ile bağlanabilir. |
| CB2 | feature | Composable parametresi `route`, `nav` ya da ad ve tip olarak eşleşen bir route alanı olmalı. |

## 10. Test planı (TDD)

- **İşlemci** (`gezgin-processor/src/test`): callback alanı tespiti; `openX` üyesi (golden);
  serializer ve serializers module'den dışlama; `transientRoutes`; alan eşlemeli entry codegen ve
  guard sarımı (golden); parametreli callback; modüller arası (classpath) route okuma; OP1–OP5 ve
  CB1–CB2 hata mesajları.
- **Runtime** (`gezgin-core`): `open` ile push; tip bazlı singleTop; `save()` dışlaması (tepede,
  ortada, iç içe, üstünde ekran varken); caller'ı düşen slotların yazılmaması; guard'ın pop sonrası
  no-op olması; `@OnDismiss` sırası ve "hâlâ yığında" kontrolü (dialog ve sheet); `restoreKey`
  değişiminde düşürme.
- **Sample:** bir akışa bir onay dialog'u (`@OnDismiss` ile) ve bir seçici sheet
  (`onSelect: (Item) -> Unit`) eklenir. Sample dosya yapısı kuralına uyulur.

## 11. Kararlar (kullanıcı onaylı, 2026-09-25)

- Kapanışı çağıran yapar; `@OnDismiss` (§6); sheet animasyonu ayrı iş; adlandırma `@Open` / `openX`.
- VM'li modallar `ResultRoute`'ta kalır.
- Callback'ler route alanıdır (Y3).
- Process death'te modal düşer; kullanıcı arkasındaki ekranı görür.

## 12. ZAD tarafına notlar

- `NavigationOwnershipContractTest` route'larda ctor lambda'sını yasaklıyor. Callback route'ları
  için istisna eklenmeli.
- Sızıntı kapısı: callback yalnızca `onIntent` / `nav` çağırır.
- Dismiss'i `onDispose`'ta gönderen içerikler (`YesNoDialogContent`,
  `SearchableMultiSelectionDialogContent`) `@OnDismiss`'e geçmeli; aksi halde rotation `onDismiss`
  tetikler.
- `legacyPopCount` gibi "N ekran geri" kalıpları `@BackTo` edge'lerine çevrilmeli.
