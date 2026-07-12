#!/usr/bin/env bash
# Madde 4 — process-death round-trip (shopr). İKİ reçete art arda koşulur (her biri stack'i baştan tohumlar):
#   CASE A — "Etkinlikleri saklama" (DKA, always_finish_activities=1): Activity onDestroy edilir ama
#            PROCESS YAŞAR (statikler/singleton'lar hayatta) -> activity-recreation restore yolu.
#   CASE B — gerçek process-death (am kill): fresh-process cold-decode yolu; pid değişimi asserted.
# P0.1 DKA doğrulanır; P0.2 am-kill pid değişimi asserted; P0.3 trap ile DKA çıkışta eski haline döner.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="dev.gezgin.sample.shopr"
fail=0

# Opsiyonel cihaz pinleme: ANDROID_SERIAL set ise maestro'ya --device geç (adb env'den kendi okur).
mt() { if [ -n "${ANDROID_SERIAL:-}" ]; then maestro --device "$ANDROID_SERIAL" test "$@"; else maestro test "$@"; fi; }

# P0.3 — trap cleanup: DKA'yı önceki değerine döndür (yarıda kesilse bile); adb yoksa sessiz/idempotent.
orig_dka=$(adb shell settings get global always_finish_activities 2>/dev/null | tr -d '\r')
case "$orig_dka" in 0|1) ;; *) orig_dka=0 ;; esac
trap 'adb shell settings put global always_finish_activities "${orig_dka:-0}" >/dev/null 2>&1 || true' EXIT

relaunch() {  # launcher ile geri getir (re-install YOK)
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 2
}

# =========================== CASE A — DKA (activity-recreation) ===========================
echo "== [CASE A / DKA] derin stack kur (Feed->Catalog->Cart->Payment) =="
mt "$DIR/shopr-04a-checkout-deep.yaml" || fail=1

echo "== [A] 'Etkinlikleri saklama' AÇ =="
adb shell settings put global always_finish_activities 1
# P0.1 — perturbation doğrula: DKA gerçekten etkinleşti mi? Etkinleşmediyse restore assert'i vacuous geçer.
if [ "$(adb shell settings get global always_finish_activities 2>/dev/null | tr -d '\r')" = "1" ]; then
  echo "   DKA etkin — arka plana at (Activity onDestroy) + geri getir"
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  relaunch
  echo "== [A/restore] restore doğrula + tamamla -> OrderPlaced (pendingSlots PD-safe) =="
  mt "$DIR/shopr-04b-after-restore.yaml" || fail=1
else
  echo "   HATA: DKA (always_finish_activities) etkinleşmedi — CASE A assert'leri atlanıyor (vacuous-pass önleme)"
  fail=1
fi
# CASE B için temiz zemin: DKA'yı eski haline al (process ölümü TEK perturbation olsun).
adb shell settings put global always_finish_activities "${orig_dka:-0}"

# =========================== CASE B — gerçek process-death (am kill) ===========================
echo "== [CASE B / am kill] derin stack yeniden kur =="
mt "$DIR/shopr-04a-checkout-deep.yaml" || fail=1

echo "== [B] HOME + am kill (gerçek process ölümü) =="
adb shell input keyevent KEYCODE_HOME
sleep 1
pid1=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
adb shell am kill "$PKG" >/dev/null 2>&1
sleep 2
relaunch
pid2=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
# P0.2 — process GERÇEKTEN öldü mü? (pid1 vardı, pid2 var VE ondan farklı). Boş pid2 = relaunch'ta çöktü.
if [ -n "$pid1" ] && [ -n "$pid2" ] && [ "$pid1" != "$pid2" ]; then
  echo "   process öldü ve yeniden doğdu (pid $pid1 -> $pid2)"
else
  echo "   HATA: process ölmedi/yeniden doğmadı (pid '$pid1' -> '$pid2')"
  fail=1
fi
echo "== [B/restore] fresh-process restore doğrula + tamamla -> OrderPlaced =="
mt "$DIR/shopr-04b-after-restore.yaml" || fail=1

if [ "$fail" = "0" ]; then echo "MADDE 4: PASS"; else echo "MADDE 4: FAIL"; fi
exit $fail
