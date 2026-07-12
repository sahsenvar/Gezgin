#!/usr/bin/env bash
# Madde 14 — MVI Settings orkestrasyon: toggle -> rotation state-survival + efekt-replay-yok -> logout.
# Maestro + adb (rotation ve logcat efekt-sayımı) birlikte. Booted emülatör + :sample:app kurulu olmalı.
# P0.1 rotation'ın GERÇEKTEN olduğu doğrulanır (dumpsys); P0.3 trap ile user_rotation + accelerometer_rotation
# çıkışta eski haline döner (önceki değerler yakalanır; bugün accelerometer_rotation kalıcı 0 kalmıyor).
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="dev.gezgin.sample.app"
fail=0

mt() { if [ -n "${ANDROID_SERIAL:-}" ]; then maestro --device "$ANDROID_SERIAL" test "$@"; else maestro test "$@"; fi; }

# Landscape/portrait tespiti — CANLI durum token'ı `mRotation=N` (cihazda doğrulandı). Statik config
# satırları (mLandscapeRotation=ROTATION_90, mDemoHdmiRotation=... gibi) HER dumpsys'te bulunur; bu yüzden
# 'ROTATION_90|270' geniş deseni HER İKİ yönde de eşleşir (vacuous). `mRotation=1` yalnız landscape'te,
# `mRotation=0` yalnız portrait'te görünür — guard'ın gerçekten koruması için canlı token'a demirle.
LANDSCAPE_RE='mRotation=(1|3)'
PORTRAIT_RE='mRotation=0'

# P0.3 — trap cleanup: rotation ayarlarını (user_rotation + accelerometer_rotation) eski haline döndür.
orig_user_rotation=$(adb shell settings get system user_rotation 2>/dev/null | tr -d '\r')
case "$orig_user_rotation" in 0|1|2|3) ;; *) orig_user_rotation=0 ;; esac
orig_accel=$(adb shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')
case "$orig_accel" in 0|1) ;; *) orig_accel=1 ;; esac
trap 'adb shell settings put system user_rotation "${orig_user_rotation:-0}" >/dev/null 2>&1; adb shell settings put system accelerometer_rotation "${orig_accel:-1}" >/dev/null 2>&1 || true' EXIT

echo "== [A] logcat temizle + toggle =="
adb logcat -c
mt "$DIR/app-14a-settings-toggle.yaml" || fail=1
c1=$(adb logcat -d | grep -c 'SettingsMvi effect\|SettingsMvi: effect')
echo "   efekt log sayısı (toggle sonrası): $c1  (beklenen: 1)"
[ "$c1" = "1" ] || { echo "   HATA: efekt tam bir kez tetiklenmeliydi"; fail=1; }

echo "== [B] landscape'e döndür -> state-survival + replay-yok =="
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 1
# P0.1 — rotation GERÇEKTEN oldu mu? Olmadıysa 14b un-rotated app'te vacuous geçerdi -> assert'leri atla.
if adb shell dumpsys window displays 2>/dev/null | grep -qE "$LANDSCAPE_RE"; then
  mt "$DIR/app-14b-after-rotate.yaml" || fail=1
  c2=$(adb logcat -d | grep -c 'SettingsMvi effect\|SettingsMvi: effect')
  echo "   efekt log sayısı (rotation sonrası): $c2  (beklenen: hâlâ 1 — replay yok)"
  [ "$c2" = "1" ] || { echo "   HATA: rotation efekti tekrar oynattı (replay)"; fail=1; }
else
  echo "   HATA: rotation gerçekleşmedi (user_rotation=1 yoksayıldı) — 14b assert'leri atlanıyor (vacuous-pass önleme)"
  fail=1
fi

echo "== portrait'e geri döndür =="
adb shell settings put system user_rotation 0
sleep 1
# Opsiyonel geri-dönüş doğrulaması (soft — fail üretmez).
if adb shell dumpsys window displays 2>/dev/null | grep -qE "$PORTRAIT_RE"; then
  echo "   portrait doğrulandı"
else
  echo "   NOT: portrait doğrulanamadı (bilgilendirici; trap yine de eski değere döndürecek)"
fi

echo "== [C] logout + back-stack guard =="
mt "$DIR/app-14c-logout.yaml" || fail=1

if [ "$fail" = "0" ]; then echo "MADDE 14: PASS"; else echo "MADDE 14: FAIL"; fi
exit $fail
