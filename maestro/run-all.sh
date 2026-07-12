#!/usr/bin/env bash
# Gezgin on-device Maestro suite — tüm otomatikleştirilebilir checklist maddelerini koşar.
# Ön koşul: booted emülatör/cihaz + iki app kurulu (dev.gezgin.sample.app, dev.gezgin.sample.shopr).
# Kurulumu bu script YAPMAZ (gradle/install çalıştırmaz) — sadece kurulu app'leri sürer.
# NOT: app-11 (Madde 11) GESTURE NAVIGATION ister (3-tuş nav'da edge-swipe içerik swipe'ı olur). Bu script
#      gestural overlay'i geçici etkinleştirir, gerçekten aktif olduğunu doğrular ve çıkışta eski nav moduna döner.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"

APP_PKG="dev.gezgin.sample.app"
SHOPR_PKG="dev.gezgin.sample.shopr"

# --- P0.9 preflight: cihaz erişilebilir mi? ---
if ! adb get-state >/dev/null 2>&1; then
  echo "HATA: adb cihazı yok (adb get-state başarısız). Booted emülatör/cihaz gerekli." >&2
  exit 2
fi
# Çoklu cihazda ANDROID_SERIAL zorunlu — aksi halde adb/maestro yanlış cihazı hedefleyip perturbation'lar
# sessizce hiçbir şeyi vurmaz (yanlış-PASS). Tek cihazda gerek yok (geriye dönük uyumlu).
dev_count=$(adb devices | grep -c 'device$')
if [ "$dev_count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
  echo "HATA: birden çok cihaz bağlı — ANDROID_SERIAL ayarlayın (ör. export ANDROID_SERIAL=emulator-5554)." >&2
  exit 2
fi
# İki app de kurulu mu? (kurulumu bu suite yapmaz)
for pkg in "$APP_PKG" "$SHOPR_PKG"; do
  if ! adb shell pm path "$pkg" >/dev/null 2>&1; then
    echo "HATA: '$pkg' kurulu değil. Önce: ./gradlew :sample:app:installDebug :sample:shopr:installDebug" >&2
    exit 2
  fi
done

# maestro cihaz pinleme (ANDROID_SERIAL set ise --device; değilse eski davranış).
mt() { if [ -n "${ANDROID_SERIAL:-}" ]; then maestro --device "$ANDROID_SERIAL" test "$@"; else maestro test "$@"; fi; }

# --- P0.10 gesture-nav: app-11 için gestural nav; çıkışta eski overlay'e dön (idempotent, adb yoksa sessiz) ---
orig_navbar_overlay=$(adb shell cmd overlay list 2>/dev/null | tr -d '\r' | grep -E '\[x\].*navbar\.' | awk '{print $NF}' | head -1)
restore_navbar() {
  if [ -n "$orig_navbar_overlay" ] && [ "$orig_navbar_overlay" != "com.android.internal.systemui.navbar.gestural" ]; then
    adb shell cmd overlay enable "$orig_navbar_overlay" >/dev/null 2>&1 || true
  fi
}
trap restore_navbar EXIT
gesture_nav_active() {
  adb shell cmd overlay list 2>/dev/null | tr -d '\r' | grep -Eq '\[x\].*navbar\.gestural'
}

pass=0; fail=0; failed_list=""
run() {  # run <etiket> <komut...>
  local label="$1"; shift
  echo; echo "############ $label ############"
  if "$@"; then pass=$((pass+1)); else fail=$((fail+1)); failed_list="$failed_list\n  - $label"; fi
}

# --- Tek-flow (kendi launchApp clearState'i olan) maddeler ---
run "Madde 1  (shopr @NoBack LIFO + @BackTo)"      mt "$DIR/shopr-01-noback-backto.yaml"
run "Madde 2  (dialog back-dismiss -> alt ekran)"  mt "$DIR/app-02-modal-back-dismiss.yaml"
run "Madde 3  (R2 per-entry VM store)"             mt "$DIR/app-03-r2-vmstore.yaml"
run "Madde 10a (ForgotDialog dismiss+Canceled+Value)" mt "$DIR/app-10a-forgot-dialog-canceled.yaml"
run "Madde 10b (EditName dialog dismiss+Value)"    mt "$DIR/app-10b-editname-dialog.yaml"
run "Madde 10c (FilterSheet dismiss+hide-then-result)" mt "$DIR/app-10c-filter-sheet.yaml"

# Madde 11 gesture-nav ister: gestural overlay'i etkinleştir + GERÇEKTEN aktif mi doğrula.
echo; echo "== [Madde 11 önkoşulu] gestural navigation etkinleştir =="
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
sleep 1
if gesture_nav_active; then
  run "Madde 11 (geri-jesti modal'ı kapatır)"      mt "$DIR/app-11-back-gesture-modal.yaml"
else
  echo "HATA: gestural navigation etkinleştirilemedi — Madde 11 atlanıyor (3-tuş nav'da edge-swipe vacuous olur)." >&2
  fail=$((fail+1)); failed_list="$failed_list\n  - Madde 11 (gesture-nav etkinleştirilemedi)"
fi

run "Madde 13 (FullscreenModal occlusion+dismiss)" mt "$DIR/app-13-fullscreen-modal.yaml"

# --- P2 kapsama flow'ları (SignUpFlow/Welcome, fragment-back, double-tap dedupe/regresyon) ---
run "Madde 16 (SignUpFlow: @BackToStart/@QuitAndGoTo/@NoBack/@ReplaceTo)" mt "$DIR/app-16-signup-flow.yaml"
run "Madde 19 (fragment yaprağından sistem-back -> Gezgin pop)"          mt "$DIR/app-19-fragment-back.yaml"
run "Madde 21 (@GoForResult double-tap -> tek dialog, slot dedupe)"      mt "$DIR/app-21-goforresult-doubletap.yaml"
run "Madde 22 (@ReplaceTo logout double-tap -> crash YOK, K1 regresyon)" mt "$DIR/app-22-replaceto-doubletap.yaml"

# --- Çok-adımlı runner'lar (adb: process-death / rotation / logcat) ---
run "Madde 4  (shopr process-death round-trip)"    bash "$DIR/run-04-process-death.sh"
run "Madde 14 (MVI rotation+one-shot-effect+logout)" bash "$DIR/run-14-settings-mvi.sh"
run "Madde 15 (Fragment process-death round-trip)" bash "$DIR/run-15-fragment-pd.sh"

echo; echo "================ ÖZET ================"
echo "PASS: $pass   FAIL: $fail"
[ "$fail" -gt 0 ] && echo -e "Başarısız:$failed_list"
exit $fail
