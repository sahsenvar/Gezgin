# Gezgin — Maestro on-device flow'ları

`docs/gezgin-on-device-checklist.md`'nin **davranışsal olarak otomatikleştirilebilir** maddelerini
gerçek bir Android emülatöründe/cihazında sürer. Bunlar compile/unit katmanının yakalayamadığı
on-device davranışları doğrular: `@NoBack` LIFO, `@BackTo`/`@ReplaceTo`, modal dismiss → `Canceled`
vs sonuç teslimi, `@FullscreenModal` occlusion, per-entry `ViewModelStore` (R2), process-death restore
(Compose entry + `@FragmentScreen`), config-change (rotation) state-survival ve tek-seferlik efekt.

## Ön koşullar

- Booted emülatör/cihaz (`adb devices` bir cihaz göstermeli).
- İki app kurulu olmalı: `dev.gezgin.sample.app` ve `dev.gezgin.sample.shopr`.
  - Kurulum (bu suite'in DIŞINDA, ayrı yapılır): `./gradlew :sample:app:installDebug :sample:shopr:installDebug`
- Maestro CLI (`~/.maestro/bin/maestro`) ve `adb` (`~/Library/Android/sdk/platform-tools`) PATH'te.
  - Runner script'leri bu iki yolu PATH'e kendisi ekler.
- Android 13+ (geri jesti/predictive-back için API 34+ ideal; bu suite API 34+ emülatörde doğrulandı).
- **Gesture navigation (app-11 / Madde 11 zorunlu):** geri-jesti modal testi gesture nav ister; 3-tuş
  nav'da edge-swipe içerik swipe'ına dönüşüp testi vacuous yapar. `run-all.sh` bunu otomatik yönetir:
  gestural overlay'i etkinleştirir, gerçekten aktif olduğunu doğrular (değilse Madde 11'i FAIL ile atlar)
  ve çıkışta cihazın eski nav moduna döner. `app-11-back-gesture-modal.yaml`'ı **tek başına** koşarken
  cihazın gesture navigation'da olduğundan elle emin olun.
- **Çoklu cihaz:** birden çok cihaz/emülatör bağlıysa `run-all.sh` `ANDROID_SERIAL`'ı zorunlu kılar
  (`export ANDROID_SERIAL=emulator-5554`) ve hem adb hem maestro (`--device`) o cihazı hedefler.

Bu suite **gradle çalıştırmaz, app kurmaz/yeniden kurmaz** — yalnız kurulu app'leri sürer.

## Çalıştırma

Hepsi:

```bash
maestro/run-all.sh
```

Tek bir tek-flow madde:

```bash
maestro test maestro/shopr-01-noback-backto.yaml
```

Çok-adımlı (process-death / rotation / logcat) maddeler runner ister:

```bash
maestro/run-04-process-death.sh   # Madde 4  — shopr process-death round-trip
maestro/run-14-settings-mvi.sh    # Madde 14 — MVI rotation state-survival + tek-seferlik efekt + logout
maestro/run-15-fragment-pd.sh     # Madde 15 — @FragmentScreen process-death round-trip
```

## Flow dosyaları → checklist maddesi

| Dosya | Madde | App | Kapsam |
|---|---|---|---|
| `shopr-01-noback-backto.yaml` | 1 | shopr | `@NoBack` sistem-back no-op; `@BackTo` (Feed'e dön) |
| `app-02-modal-back-dismiss.yaml` | 2 | app | Dialog back-dismiss → alt ekran canlı geri gelir |
| `app-03-r2-vmstore.yaml` | 3 | app | Per-entry `ViewModelStore` (visits dizisi 1→1→2) |
| `shopr-04a-checkout-deep.yaml` + `shopr-04b-after-restore.yaml` + `run-04-process-death.sh` | 4 | shopr | Process-death restore + `pendingSlots` (ResultBus) PD-safe |
| `app-10a-forgot-dialog-canceled.yaml` | 10 | app | ForgotDialog: dış-tık no-op, back→Canceled, Gönder→Value |
| `app-10b-editname-dialog.yaml` | 10 | app | EditName: dış-tık→Canceled, Kaydet→Value (ad güncellenir) |
| `app-10c-filter-sheet.yaml` | 10, 12 | app | Sheet swipe/scrim→Canceled; seçim→hide-then-result |
| `app-11-back-gesture-modal.yaml` | 11 | app | Tam geri-jesti modal'ı kapatır, alttaki ekranı navige etmez |
| `app-13-fullscreen-modal.yaml` | 13 | app | `@FullscreenModal` occlusion + back/Kapat dismiss |
| `app-14a/b/c-*.yaml` + `run-14-settings-mvi.sh` | 14 | app | MVI: rotation state-survival, tek-seferlik efekt (logcat), logout |
| `app-15a/b-*.yaml` + `run-15-fragment-pd.sh` | 15 | app | `@FragmentScreen` PD: `gezginArgs` decode + `gezginNav` re-bind |

`*-a`/`*-b`/`*-c` parça-flow'lar tek başına çalıştırılmaz; ilgili runner (`run-04/14/15`) tarafından
process-death/rotation adımlarının arasına yerleştirilerek çağrılır. Parça-B flow'ları bilinçli olarak
`launchApp` İÇERMEZ (app'i durdurup saved-state'i yok etmemek için) — mevcut foreground ekran üzerinde
assert eder.

## iOS akışları (hello, simülatör)

Android suite'inden ayrı, kendi runner'ı olan küçük bir set. Gezgin'in iOS'taki geri sözleşmesini
gerçek bir simülatörde sürer: kenar-çekme jesti, üst-bar geri'siyle aynı sonucu vermeli ve kökte
no-op kalmalı.

**Android suite'inin aksine bu set CI'da koşar** (`ios-maestro-e2e` job'ı): macOS runner'ında bir
simülatör boot edilir, `sample/iosApp` derlenip kurulur ve aşağıdaki runner sürülür. Aşağıdaki
ön koşullar yerel koşum içindir.

Ön koşullar (yerel koşum; Android suite'inden bağımsız):

- macOS + booted iOS simülatörü (`xcrun simctl list devices booted`).
- `dev.gezgin.sample.hello` simülatörde kurulu. Kurulumu bu suite YAPMAZ: `sample/iosApp`'i Xcode'da
  bir kez simülatöre çalıştırın.
- Maestro CLI (`~/.maestro/bin/maestro`) PATH'te — runner bu yolu kendisi ekler.
- Birden çok simülatör booted ise `MAESTRO_DEVICE` ile UDID zorunlu (Android'deki `ANDROID_SERIAL`
  ile aynı sözleşme).

```bash
maestro/run-ios-all.sh
```

| Dosya | App | Kapsam |
|---|---|---|
| `hello-ios-01-push-back.yaml` | hello | liste→detay push; üst-bar geri'si pop'lar, liste canlı döner |
| `hello-ios-02-edge-swipe.yaml` | hello | kenar-çekme jesti detayı pop'lar (üst-bar geri'siyle aynı sonuç) |
| `hello-ios-03-root-back.yaml` | hello | kökte geri no-op: uygulama kapanmaz, yığın bozulmaz |
| `hello-ios-04-background-restore.yaml` | hello | arka plan→ön plan turunda yığın ve ekran durumu korunur |

**Kapsam sınırı (bilinçli).** `hello` graph'ında `@NoBack`, `@Dialog`, `@BottomSheet` ve
`@GoForResult` yoktur; bu davranışlar iOS'ta simülatör UI testleriyle (`iosSimulatorArm64Test`)
kanıtlanır, e2e düzeyinde değil. Ayrıca iOS'ta Android'in process-death restore'unun karşılığı
**yoktur**: `rememberSaveable` durumunu kurtaracak bir platform host'u bulunmadığından madde 4'ün iOS
karşılığı, process'in yaşadığı arka plan turudur (`hello-ios-04`).

## Otomatikleştirilemeyen / görsel maddeler

- **Görsel — insan gözü:** 9 (scrim/z-order opaklığı), 11 preview-frame yarısı (yarım-jest önizlemesi),
  12 animasyon akıcılığı (slide-down). Bunlar assertion'la doğrulanamaz; checklist'te öyle işaretlidir.
- **Kod-değişikliği + rebuild gerektirir (bu suite gradle çalıştırmadığı için kapsam dışı):** 7 (bozuk
  state→fresh fallback için serializer'ı geçici bozmak gerekir), 17 (Fragment→Composable swap).
- **Bilgilendirici, cihaz gerektirmez:** 5, 6, 8, 16, 18.
