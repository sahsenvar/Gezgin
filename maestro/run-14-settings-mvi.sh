#!/usr/bin/env bash
# Madde 14 — MVI Settings orkestrasyon: toggle -> rotation state-survival + efekt-replay-yok -> logout.
# Maestro + adb (rotation ve logcat efekt-sayımı) birlikte. Booted emülatör + :sample:app kurulu olmalı.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="dev.gezgin.sample.app"
fail=0

echo "== [A] logcat temizle + toggle =="
adb logcat -c
maestro test "$DIR/app-14a-settings-toggle.yaml" || fail=1
c1=$(adb logcat -d | grep -c 'SettingsMvi effect\|SettingsMvi: effect')
echo "   efekt log sayısı (toggle sonrası): $c1  (beklenen: 1)"
[ "$c1" = "1" ] || { echo "   HATA: efekt tam bir kez tetiklenmeliydi"; fail=1; }

echo "== [B] landscape'e döndür -> state-survival + replay-yok =="
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 1
maestro test "$DIR/app-14b-after-rotate.yaml" || fail=1
c2=$(adb logcat -d | grep -c 'SettingsMvi effect\|SettingsMvi: effect')
echo "   efekt log sayısı (rotation sonrası): $c2  (beklenen: hâlâ 1 — replay yok)"
[ "$c2" = "1" ] || { echo "   HATA: rotation efekti tekrar oynattı (replay)"; fail=1; }

echo "== portrait'e geri döndür =="
adb shell settings put system user_rotation 0
sleep 1

echo "== [C] logout + back-stack guard =="
maestro test "$DIR/app-14c-logout.yaml" || fail=1

if [ "$fail" = "0" ]; then echo "MADDE 14: PASS"; else echo "MADDE 14: FAIL"; fi
exit $fail
