#!/usr/bin/env bash
# Madde 18 — bir MODAL (@GoForResult EditNameDialog) TOP entry iken gerçek process-death, sonra modal'ı tamamla.
# run-04 yalnız düz ekranları kapsar; bu, modal entry + pending result slot'un GERÇEK process ölümünden
# sağ çıkışını kapsar. İKİ reçete (her biri stack'i baştan tohumlar):
#   CASE A — DKA (always_finish_activities=1): Activity onDestroy, PROCESS YAŞAR -> activity-recreation restore.
#   CASE B — gerçek process-death (am kill): fresh-process cold-decode; pid değişimi asserted.
# İSPAT: modal entry serialize edilir + fresh-process'te YENİDEN gösterilir; pending slot PD-safe -> tamamlanınca
#   sonuç çağıran ProfileViewModel'e teslim edilir ("Profil: PD Sonrasi").
# P0.1 DKA doğrulanır; P0.2 am-kill pid değişimi asserted; P0.3 trap ile DKA çıkışta eski haline döner.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="dev.gezgin.sample.app"
fail=0

mt() { if [ -n "${ANDROID_SERIAL:-}" ]; then maestro --device "$ANDROID_SERIAL" test "$@"; else maestro test "$@"; fi; }

# P0.3 — trap cleanup: DKA'yı önceki değerine döndür (yarıda kesilse bile); adb yoksa sessiz/idempotent.
orig_dka=$(adb shell settings get global always_finish_activities 2>/dev/null | tr -d '\r')
case "$orig_dka" in 0|1) ;; *) orig_dka=0 ;; esac
trap 'adb shell settings put global always_finish_activities "${orig_dka:-0}" >/dev/null 2>&1 || true' EXIT

relaunch() {  # launcher ile geri getir (backstack + modal entry restore); re-install YOK
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  for _r in 1 2 3 4 5 6; do
    sleep 1
    [ -n "$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')" ] && return 0
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  done
}

adb logcat -b crash -c >/dev/null 2>&1 || true   # PD öncesi crash buffer temiz

# =========================== CASE A — DKA (activity-recreation) ===========================
# Seed walk'u DKA KAPALI zeminde koş (orig 1 kalmışsa activity'ler anında finish olup walk'u bozardı).
adb shell settings put global always_finish_activities 0 >/dev/null 2>&1; sleep 1
echo "== [CASE A / DKA] modal'ı aç (Login->Dashboard->Profil->Adı düzenle) =="
mt "$DIR/app-18-modal-open.yaml" || fail=1

echo "== [A] 'Etkinlikleri saklama' AÇ =="
adb shell settings put global always_finish_activities 1
if [ "$(adb shell settings get global always_finish_activities 2>/dev/null | tr -d '\r')" = "1" ]; then
  echo "   DKA etkin — arka plana at (Activity onDestroy) + geri getir"
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  relaunch
  echo "== [A/restore] modal yeniden gösterildi mi + tamamla -> Profile teslim doğrula =="
  mt "$DIR/app-18-modal-after-pd.yaml" || fail=1
else
  echo "   HATA: DKA (always_finish_activities) etkinleşmedi — CASE A assert'leri atlanıyor (vacuous-pass önleme)"
  fail=1
fi
# CASE B için temiz zemin: DKA KAPAT (process ölümü TEK perturbation olsun); gerçek orig trap'te geri gelir.
adb shell settings put global always_finish_activities 0 >/dev/null 2>&1; sleep 1

# =========================== CASE B — gerçek process-death (am kill) ===========================
echo "== [CASE B / am kill] modal'ı yeniden aç =="
mt "$DIR/app-18-modal-open.yaml" || fail=1

echo "== [B] HOME + am kill (gerçek process ölümü) =="
adb shell input keyevent KEYCODE_HOME
sleep 2
pid1=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
# am kill YALNIZ cached/killable process'i öldürür; HOME'dan hemen sonra process henüz cached olmayabilir.
# Ölene (pid kaybolana/değişene) kadar birkaç kez dene — timing-robust.
adb shell am kill "$PKG" >/dev/null 2>&1
for _i in 1 2 3 4 5 6; do
  sleep 1
  cur=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
  if [ -z "$cur" ] || [ "$cur" != "$pid1" ]; then break; fi
  adb shell am kill "$PKG" >/dev/null 2>&1
done
relaunch
pid2=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
# P0.2 — process GERÇEKTEN öldü mü? (pid1 vardı, pid2 var VE ondan farklı). Boş pid2 = relaunch'ta çöktü.
if [ -n "$pid1" ] && [ -n "$pid2" ] && [ "$pid1" != "$pid2" ]; then
  echo "   process öldü ve yeniden doğdu (pid $pid1 -> $pid2)"
else
  echo "   HATA: process ölmedi/yeniden doğmadı (pid '$pid1' -> '$pid2')"
  fail=1
fi
echo "== [B/restore] fresh-process: modal yeniden gösterildi mi + tamamla -> Profile teslim doğrula =="
mt "$DIR/app-18-modal-after-pd.yaml" || fail=1

if [ "$fail" = "0" ]; then echo "MADDE 18: PASS"; else echo "MADDE 18: FAIL"; fi
exit $fail
