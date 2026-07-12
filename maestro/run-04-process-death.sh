#!/usr/bin/env bash
# Madde 4 — process-death round-trip (shopr). "Etkinlikleri saklama" (Don't keep activities) reçetesi:
# derin stack kur -> Activity'yi arka planda yok et -> geri getir -> stack + bekleyen ResultBus slotu restore.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="dev.gezgin.sample.shopr"
fail=0

echo "== [A] derin stack kur (Feed->Catalog->Cart->Payment) =="
maestro test "$DIR/shopr-04a-checkout-deep.yaml" || fail=1

echo "== 'Etkinlikleri saklama' AÇ + arka plana at (Activity onDestroy) =="
adb shell settings put global always_finish_activities 1
adb shell input keyevent KEYCODE_HOME
sleep 2

echo "== uygulamayı app-switcher/launcher ile geri getir (savedInstanceState restore) =="
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 2

echo "== [B] restore doğrula + tamamla -> OrderPlaced (Value, pendingSlots PD-safe) =="
maestro test "$DIR/shopr-04b-after-restore.yaml" || fail=1

echo "== 'Etkinlikleri saklama' KAPAT (temizlik) =="
adb shell settings put global always_finish_activities 0

if [ "$fail" = "0" ]; then echo "MADDE 4: PASS"; else echo "MADDE 4: FAIL"; fi
exit $fail
