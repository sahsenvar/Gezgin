#!/usr/bin/env bash
# Gezgin iOS Maestro suite — hello örneğinin iOS simülatöründeki geri davranışını sürer.
# Ön koşul: booted iOS simülatörü + `dev.gezgin.sample.hello` kurulu.
# Kurulumu bu script YAPMAZ: `sample/iosApp`'i Xcode'da bir kez simülatöre çalıştırın.
# Android suite'i gibi CI'da koşmaz; elle sürülür.
set -uo pipefail
export PATH="$PATH:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"

APP_ID="dev.gezgin.sample.hello"

if ! command -v xcrun >/dev/null 2>&1; then
  echo "HATA: xcrun yok — bu suite yalnız macOS'ta koşar." >&2
  exit 2
fi

# Booted simülatör zorunlu; aksi halde maestro hedefsiz kalır ve akışlar sessizce hiçbir şeyi vurmaz.
booted="$(xcrun simctl list devices booted | grep -c 'Booted')"
if [ "$booted" -eq 0 ]; then
  echo "HATA: booted iOS simülatörü yok. Önce bir simülatör başlatın (Xcode veya 'xcrun simctl boot')." >&2
  exit 2
fi
# Çoklu simülatörde hedef belirsizleşir — Android suite'indeki ANDROID_SERIAL ile aynı sözleşme.
if [ "$booted" -gt 1 ] && [ -z "${MAESTRO_DEVICE:-}" ]; then
  echo "HATA: birden çok simülatör booted — MAESTRO_DEVICE ile UDID verin (xcrun simctl list devices booted)." >&2
  exit 2
fi

# App kurulu mu? (kurulumu bu suite yapmaz)
if ! xcrun simctl get_app_container booted "$APP_ID" >/dev/null 2>&1; then
  echo "HATA: '$APP_ID' simülatörde kurulu değil. Önce sample/iosApp'i Xcode'da simülatöre çalıştırın." >&2
  exit 2
fi

mt() { if [ -n "${MAESTRO_DEVICE:-}" ]; then maestro --device "$MAESTRO_DEVICE" test "$@"; else maestro test "$@"; fi; }

pass=0; fail=0; failed_list=""
run() {  # run <etiket> <akış>
  local label="$1"; shift
  echo; echo "############ $label ############"
  if mt "$@"; then pass=$((pass+1)); else fail=$((fail+1)); failed_list="$failed_list\n  - $label"; fi
}

run "iOS 1 (liste->detay push + üst-bar geri)" "$DIR/hello-ios-01-push-back.yaml"
run "iOS 2 (kenar-çekme pop)"                  "$DIR/hello-ios-02-edge-swipe.yaml"
run "iOS 3 (kökte geri no-op)"                 "$DIR/hello-ios-03-root-back.yaml"
run "iOS 4 (arka plan turu durumu korur)"      "$DIR/hello-ios-04-background-restore.yaml"

echo
echo "=================== ÖZET ==================="
echo "PASS: $pass   FAIL: $fail"
if [ "$fail" != "0" ]; then printf 'Başarısızlar:%b\n' "$failed_list"; fi
exit $([ "$fail" = "0" ] && echo 0 || echo 1)
