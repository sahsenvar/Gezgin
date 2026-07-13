#!/usr/bin/env bash
# Madde 7 — BOZUK PD snapshot -> TAZE başlangıç (crash-loop YOK). Kaynak-hack + rebuild GEREKTİRMEZ:
#   shopr debug build'inde `--ez corrupt_state true` ile açılışta devreye giren debug-only
#   CorruptingSaveableStateRegistry, restore edilen Gezgin PD snapshot'ını bozar -> library'nin
#   decodeSavedStateOrNull(...) == null fresh-fallback yolu (RememberNavigator.android.kt:84) tetiklenir.
#
# KRİTİK reçete detayı (cihazda doğrulandı): extra RESUME anındaki intent'ten OKUNAMAZ — PD sonrası
# launcher-resume, task'ın ORİJİNAL intent'ini replay eder; TAZE bir `am start --ez` extra'sı DÜŞER (fresh
# task başlatır, restore YOK -> vacuous). Bu yüzden extra ORİJİNAL launch intent'ine yazılır (adım b) ve
# restore, run-04'ün kanıtlı `monkey LAUNCHER` resume'u ile yapılır (adım e) -> orijinal intent + extra +
# savedState birlikte gelir -> hook restore edilen snapshot'ı bozar.
#
# Reçete: (a) force-stop; (b) `--ez corrupt_state true` ile TAZE başlat (extra orijinal intent'e); (c) derin
#   stack (Feed->Catalog->Cart->Payment) kur — hook atıl; (d) GERÇEK process death (am kill, run-04 reçetesi,
#   pid değişimi asserted); (e) `monkey LAUNCHER` ile resume (orijinal intent+extra replay) -> hook snapshot'ı
#   bozar; (f) TAZE Feed doğrula (derin Payment stack GİTMİŞ); (g) ShoprCorrupt logu (non-vacuous gate);
#   (h) crash buffer temiz. PASS = taze Feed + crash yok + corruption gerçekten çalıştı.
# ANDROID_SERIAL onurlandırılır. Bu run cihaz ayarı DEĞİŞTİRMEZ (DKA yok) -> trap gerekmez.
set -uo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools:$HOME/.maestro/bin"
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="dev.gezgin.sample.shopr"
fail=0

# Opsiyonel cihaz pinleme: ANDROID_SERIAL set ise maestro'ya --device geç (adb env'den kendi okur).
mt() { if [ -n "${ANDROID_SERIAL:-}" ]; then maestro --device "$ANDROID_SERIAL" test "$@"; else maestro test "$@"; fi; }

relaunch() {  # run-04 reçetesi: LAUNCHER ile resume (orijinal intent replay); process gelene kadar bekle
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  for _r in 1 2 3 4 5 6; do
    sleep 1
    [ -n "$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')" ] && return 0
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  done
}

# =========================== (a)+(b) extra'yı ORİJİNAL intent'e yazarak TAZE başlat ===========================
echo "== [madde-7] force-stop + --ez corrupt_state true ile TAZE başlat (extra orijinal intent'e) =="
adb shell am force-stop "$PKG" >/dev/null 2>&1
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  -n "$PKG/.MainActivity" --ez corrupt_state true >/dev/null 2>&1
for _r in 1 2 3 4 5 6; do sleep 1; [ -n "$(adb shell pidof -s "$PKG"|tr -d '\r')" ] && break; done

# =========================== (c) derin stack kur (launchApp YOK ki extra'lı intent korunsun) ===========================
echo "== [madde-7] derin stack kur (Feed->Catalog->Cart->Payment; hook atıl) =="
mt "$DIR/shopr-07a-deep-with-extra.yaml" || fail=1

# assert-no-crash için crash buffer'ı SIFIRLA (önceki koşuların artıkları false-FAIL yapmasın);
# ShoprCorrupt onayı da temiz main buffer'dan gelsin.
adb shell logcat -b crash -c >/dev/null 2>&1 || true
adb shell logcat -c >/dev/null 2>&1 || true

# =========================== (d) GERÇEK process death (am kill) ===========================
echo "== [madde-7] HOME + am kill (gerçek process ölümü) =="
adb shell input keyevent KEYCODE_HOME
sleep 2
pid1=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
# am kill YALNIZ cached/killable process'i öldürür; HOME'dan hemen sonra henüz cached olmayabilir -> retry.
adb shell am kill "$PKG" >/dev/null 2>&1
for _i in 1 2 3 4 5 6; do
  sleep 1
  cur=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
  if [ -z "$cur" ] || [ "$cur" != "$pid1" ]; then break; fi
  adb shell am kill "$PKG" >/dev/null 2>&1
done

# =========================== (e) monkey LAUNCHER ile resume (orijinal intent+extra replay) ===========================
echo "== [madde-7] monkey LAUNCHER ile resume (orijinal intent+extra replay -> restore anında boz) =="
relaunch
pid2=$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')
# process GERÇEKTEN öldü mü? pid1 vardı VE pid DEĞİŞTİ. pid2 boş = "değişti" sayılır (öldü, cold-start geç).
if [ -n "$pid1" ] && [ "$pid1" != "$pid2" ]; then
  echo "   process öldürüldü (pid1=$pid1, pid2=${pid2:-<geç>})"
else
  echo "   HATA: process ÖLMEDİ (am kill no-op; pid '$pid1' -> '$pid2')"
  fail=1
fi

# NON-VACUOUS onayını HEMEN yakala (maestro after-corrupt akışı buffer'ı taşmadan ÖNCE). Corruption
# resume-onCreate'te olur; ShoprCorrupt'ı burada saymazsak, sonraki maestro log spam'i onu -t penceresinden
# ittirir (ilk yanlış-teşhis buydu). logcat -c HOME'dan önce yapıldığından tüm buffer'da tek ShoprCorrupt bu run'a ait.
sleep 2
saw_corrupt=$(adb shell logcat -d 2>/dev/null | grep -c "ShoprCorrupt" | tr -d '\r')
saw_corrupt=${saw_corrupt:-0}

# =========================== (f) TAZE başlangıç doğrula ===========================
echo "== [madde-7] TAZE Feed doğrula (derin Payment stack GİTMİŞ) =="
mt "$DIR/shopr-07-after-corrupt.yaml" || fail=1

# =========================== (g) NON-VACUOUS gate ===========================
# corruption GERÇEKTEN çalıştı mı? (restore oldu + predicate eşleşti). ShoprCorrupt logu yoksa ya restore
# olmadı ya predicate kaçtı -> taze-Feed assert'i vacuous geçerdi; bunu FAIL say.
if [ "${saw_corrupt}" -ge 1 ]; then
  echo "   corrupt-hook tetiklendi (ShoprCorrupt x$saw_corrupt) — non-vacuous (restore + predicate eşleşti)"
else
  echo "   HATA: ShoprCorrupt logu yok — restore olmadı ya da predicate eşleşmedi (vacuous risk)"
  fail=1
fi

# =========================== (h) crash yok ===========================
echo "== [madde-7] crash buffer temiz mi? =="
crash=$(adb shell logcat -d -b crash -t 80 2>/dev/null | grep -E "FATAL|AndroidRuntime|$PKG" || true)
if [ -n "$crash" ]; then
  echo "   HATA: crash bulundu:"; echo "$crash" | head -20; fail=1
else
  echo "   crash buffer temiz (crash-loop yok)"
fi

# temizlik: extra'lı task sonraki koşulara sızmasın (run-04 zaten clearState ile başlar ama yine de).
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true

if [ "$fail" = "0" ]; then echo "MADDE 7: PASS"; else echo "MADDE 7: FAIL"; fi
exit $fail
