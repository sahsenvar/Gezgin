#!/usr/bin/env bash
# Gezgin on-device Maestro suite — tüm otomatikleştirilebilir checklist maddelerini koşar.
# Ön koşul: booted emülatör/cihaz + iki app kurulu (dev.gezgin.sample.app, dev.gezgin.sample.shopr).
# Kurulumu bu script YAPMAZ (gradle/install çalıştırmaz) — sadece kurulu app'leri sürer.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"

pass=0; fail=0; failed_list=""
run() {  # run <etiket> <komut...>
  local label="$1"; shift
  echo; echo "############ $label ############"
  if "$@"; then pass=$((pass+1)); else fail=$((fail+1)); failed_list="$failed_list\n  - $label"; fi
}

# --- Tek-flow (kendi launchApp clearState'i olan) maddeler ---
run "Madde 1  (shopr @NoBack LIFO + @BackTo)"      maestro test "$DIR/shopr-01-noback-backto.yaml"
run "Madde 2  (dialog back-dismiss -> alt ekran)"  maestro test "$DIR/app-02-modal-back-dismiss.yaml"
run "Madde 3  (R2 per-entry VM store)"             maestro test "$DIR/app-03-r2-vmstore.yaml"
run "Madde 10a (ForgotDialog dismiss+Canceled+Value)" maestro test "$DIR/app-10a-forgot-dialog-canceled.yaml"
run "Madde 10b (EditName dialog dismiss+Value)"    maestro test "$DIR/app-10b-editname-dialog.yaml"
run "Madde 10c (FilterSheet dismiss+hide-then-result)" maestro test "$DIR/app-10c-filter-sheet.yaml"
run "Madde 11 (geri-jesti modal'ı kapatır)"        maestro test "$DIR/app-11-back-gesture-modal.yaml"
run "Madde 13 (FullscreenModal occlusion+dismiss)" maestro test "$DIR/app-13-fullscreen-modal.yaml"

# --- Çok-adımlı runner'lar (adb: process-death / rotation / logcat) ---
run "Madde 4  (shopr process-death round-trip)"    bash "$DIR/run-04-process-death.sh"
run "Madde 14 (MVI rotation+one-shot-effect+logout)" bash "$DIR/run-14-settings-mvi.sh"
run "Madde 15 (Fragment process-death round-trip)" bash "$DIR/run-15-fragment-pd.sh"

echo; echo "================ ÖZET ================"
echo "PASS: $pass   FAIL: $fail"
[ "$fail" -gt 0 ] && echo -e "Başarısız:$failed_list"
exit $fail
