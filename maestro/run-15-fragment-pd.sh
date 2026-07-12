#!/usr/bin/env bash
# Madde 15 — @FragmentScreen (HelpFragment) process-death round-trip. Madde 4'ün reçetesini Fragment yaprağı
# için tekrarlar: gezginArgs route-decode + gezginNav re-bind (onUpdate->bindGezgin) gerçek PD'de gözlenir.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="dev.gezgin.sample.app"
fail=0

echo "== [A] HelpFragment ekranına git (Login->Dashboard->Yardım) =="
maestro test "$DIR/app-15a-help-fragment.yaml" || fail=1

echo "== 'Etkinlikleri saklama' AÇ + arka plana at (Activity onDestroy) =="
adb shell settings put global always_finish_activities 1
adb shell input keyevent KEYCODE_HOME
sleep 2

echo "== uygulamayı launcher ile geri getir (FragmentState + Gezgin backstack restore) =="
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 2

echo "== [B] args decode + nav re-bind doğrula =="
maestro test "$DIR/app-15b-help-after-restore.yaml" || fail=1

echo "== 'Etkinlikleri saklama' KAPAT (temizlik) =="
adb shell settings put global always_finish_activities 0

if [ "$fail" = "0" ]; then echo "MADDE 15: PASS"; else echo "MADDE 15: FAIL"; fi
exit $fail
