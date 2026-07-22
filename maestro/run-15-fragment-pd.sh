#!/usr/bin/env bash
# Madde 15 — @FragmentScreen (HelpFragment) process-death round-trip. Madde 4'ün İKİ reçetesini Fragment
# yaprağı için tekrarlar: gezginArgs route-decode + gezginNav re-bind (onUpdate->bindGezgin).
#   CASE A — DKA (activity-recreation): PROCESS yaşar; yalnız FM-restore branch'i (ii) koşar.
#   CASE B — gerçek process-death (am kill): fresh-process ilk-yaratım re-encode branch'ini İLK KEZ koşturur —
#            statik gezginFragmentJson (FragmentRouteBundle.android.kt) sıfırdan yeniden doldurulur.
# P0.1 DKA doğrulanır; P0.2 am-kill pid değişimi asserted; P0.3 trap ile DKA çıkışta eski haline döner.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="dev.gezgin.sample.app"
ACTIVITY="$PKG/.MainActivity"
fail=0

mt() { if [ -n "${ANDROID_SERIAL:-}" ]; then maestro --device "$ANDROID_SERIAL" test "$@"; else maestro test "$@"; fi; }

# P0.3 — trap cleanup: DKA'yı önceki değerine döndür (yarıda kesilse bile); adb yoksa sessiz/idempotent.
orig_dka=$(adb shell settings get global always_finish_activities 2>/dev/null | tr -d '\r')
case "$orig_dka" in 0|1) ;; *) orig_dka=0 ;; esac
trap 'adb shell settings put global always_finish_activities "${orig_dka:-0}" >/dev/null 2>&1 || true' EXIT

relaunch() {  # mevcut task'i launcher intent'iyle geri getir; re-install/clear-state YOK
  # Android 16 emulator'da `monkey -p ... -c LAUNCHER 1` exit -5 ile event üretmeden dönebiliyor.
  # Explicit launcher Activity aynı mevcut task'i öne getirir ve onun saved instance state'ini restore eder.
  for _r in 1 2 3 4 5 6; do
    adb shell am start -W \
      -a android.intent.action.MAIN \
      -c android.intent.category.LAUNCHER \
      -n "$ACTIVITY" >/dev/null 2>&1 || true
    sleep 1
    pid=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
    resumed=$(adb shell dumpsys activity activities 2>/dev/null | sed -n '/topResumedActivity=/p' | head -1)
    if [ -n "$pid" ] && printf '%s' "$resumed" | grep -Fq "$ACTIVITY"; then
      return 0
    fi
  done
  echo "   HATA: uygulama relaunch sonrası foreground'a gelmedi"
  return 1
}

# =========================== CASE A — DKA (activity-recreation) ===========================
echo "== [CASE A / DKA] HelpFragment ekranına git (Login->Dashboard->Yardım) =="
mt "$DIR/app-15a-help-fragment.yaml" || fail=1

echo "== [A] 'Etkinlikleri saklama' AÇ =="
adb shell settings put global always_finish_activities 1
# P0.1 — perturbation doğrula: DKA gerçekten etkinleşti mi?
if [ "$(adb shell settings get global always_finish_activities 2>/dev/null | tr -d '\r')" = "1" ]; then
  echo "   DKA etkin — arka plana at (Activity onDestroy) + geri getir"
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  relaunch || fail=1
  echo "== [A/restore] args decode + nav re-bind doğrula =="
  mt "$DIR/app-15b-help-after-restore.yaml" || fail=1
else
  echo "   HATA: DKA (always_finish_activities) etkinleşmedi — CASE A assert'leri atlanıyor (vacuous-pass önleme)"
  fail=1
fi
# CASE B için temiz zemin: DKA'yı eski haline al (process ölümü TEK perturbation olsun).
adb shell settings put global always_finish_activities "${orig_dka:-0}"

# =========================== CASE B — gerçek process-death (am kill) ===========================
# Bu case, Fragment'ın fresh-process restore branch'ini (statik gezginFragmentJson yeniden doldurulur) İLK KEZ koşturur.
echo "== [CASE B / am kill] HelpFragment ekranına yeniden git =="
mt "$DIR/app-15a-help-fragment.yaml" || fail=1

echo "== [B] HOME + am kill (gerçek process ölümü) =="
adb shell input keyevent KEYCODE_HOME
sleep 2
pid1=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
# am kill YALNIZ cached/killable process'i öldürür; HOME'dan hemen sonra process henüz cached olmayabilir.
# Ölene (pid kaybolana/değişene) kadar birkaç kez dene — timing-robust (aksi halde am-kill no-op → flaky).
adb shell am kill "$PKG" >/dev/null 2>&1
for _i in 1 2 3 4 5 6; do
  sleep 1
  cur=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
  if [ -z "$cur" ] || [ "$cur" != "$pid1" ]; then break; fi
  adb shell am kill "$PKG" >/dev/null 2>&1
done
relaunch || fail=1
pid2=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
# P0.2 — process GERÇEKTEN öldü mü? (pid1 vardı ve yeni pid2 ondan farklı).
if [ -n "$pid1" ] && [ "$pid1" != "$pid2" ]; then   # pid2 boş=öldü+geç cold-start (yük); rebirth'i restore flow kanıtlar
  echo "   process öldürüldü (pid1=$pid1, pid2=${pid2:-<geç>})"
else
  echo "   HATA: process ÖLMEDİ (am kill no-op; pid '$pid1' -> '$pid2')"
  fail=1
fi
echo "== [B/restore] fresh-process args decode + nav re-bind doğrula =="
mt "$DIR/app-15b-help-after-restore.yaml" || fail=1

if [ "$fail" = "0" ]; then echo "MADDE 15: PASS"; else echo "MADDE 15: FAIL"; fi
exit $fail
