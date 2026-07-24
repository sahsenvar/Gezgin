#!/usr/bin/env bash
# shellcheck disable=SC2016 # Test fixtures intentionally preserve literal shell expressions.
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
TEST_DIR=$(mktemp -d "${TMPDIR:-/tmp}/gezgin-release-script-tests.XXXXXX")
trap 'rm -rf "$TEST_DIR"' EXIT

PROJECT_DIR="$TEST_DIR/project"
mkdir -p "$PROJECT_DIR"

printf '%s\n' 'VERSION_NAME=0.1.0' > "$PROJECT_DIR/gradle.properties"
printf '%s\n' '# Changelog' '' '## [0x1x0] - 2026-07-22' '' '- malformed' > "$PROJECT_DIR/CHANGELOG.md"
if (cd "$PROJECT_DIR" && "$ROOT_DIR/gradle/release/validate-release.sh" v0.1.0 >/dev/null 2>&1); then
  echo "Malformed CHANGELOG heading was accepted." >&2
  exit 1
fi
if (cd "$PROJECT_DIR" && "$ROOT_DIR/gradle/release/extract-release-notes.sh" 0.1.0 notes.md >/dev/null 2>&1); then
  echo "Malformed CHANGELOG heading was extracted." >&2
  exit 1
fi

printf '%s\n' 'VERSION_NAME=1.2.3' > "$PROJECT_DIR/gradle.properties"
printf '%s\n' \
  '# Changelog' '' \
  '## [1x2x3] - 2026-07-21' '' '- regex-shaped wrong heading' '' \
  '## [1.2.3] - 2026-07-22' '' '- exact release notes' '' \
  '## [1.2.2] - 2026-07-20' '' '- older notes' > "$PROJECT_DIR/CHANGELOG.md"
(cd "$PROJECT_DIR" && "$ROOT_DIR/gradle/release/validate-release.sh" v1.2.3 >/dev/null)
(cd "$PROJECT_DIR" && "$ROOT_DIR/gradle/release/extract-release-notes.sh" 1.2.3 notes.md)
grep -Fq -- '- exact release notes' "$PROJECT_DIR/notes.md"
if grep -Fq 'regex-shaped' "$PROJECT_DIR/notes.md"; then
  echo "Release notes used regex rather than literal version matching." >&2
  exit 1
fi

marker="$TEST_DIR/untrusted-expression-executed"
adversarial_tag='v$(touch '"$marker"')'
if (cd "$PROJECT_DIR" && "$ROOT_DIR/gradle/release/validate-release.sh" "$adversarial_tag" >/dev/null 2>&1); then
  echo "Adversarial tag was accepted." >&2
  exit 1
fi
test ! -e "$marker"

CLOCK_FILE="$TEST_DIR/clock"
CURL_LOG="$TEST_DIR/curl.log"
SLEEP_LOG="$TEST_DIR/sleep.log"
NOW_FAKE="$TEST_DIR/now"
CURL_FAKE="$TEST_DIR/curl"
SLEEP_FAKE="$TEST_DIR/sleep"

printf '%s\n' '#!/usr/bin/env bash' 'cat "$GEZGIN_SMOKE_TEST_CLOCK_FILE"' > "$NOW_FAKE"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'timeout=' \
  'while (($#)); do' \
  '  if [[ "$1" == "--max-time" ]]; then timeout=$2; shift 2; else shift; fi' \
  'done' \
  'now=$(cat "$GEZGIN_SMOKE_TEST_CLOCK_FILE")' \
  'printf "%s:%s\n" "$now" "$timeout" >> "$GEZGIN_SMOKE_TEST_CURL_LOG"' \
  'advance=${GEZGIN_SMOKE_TEST_CURL_ADVANCE:-0}' \
  'if [[ "$advance" == max ]]; then advance=$timeout; fi' \
  'printf "%s\n" "$((now + advance))" > "$GEZGIN_SMOKE_TEST_CLOCK_FILE"' \
  'exit 1' > "$CURL_FAKE"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'now=$(cat "$GEZGIN_SMOKE_TEST_CLOCK_FILE")' \
  'printf "%s\n" "$1" >> "$GEZGIN_SMOKE_TEST_SLEEP_LOG"' \
  'printf "%s\n" "$((now + $1))" > "$GEZGIN_SMOKE_TEST_CLOCK_FILE"' > "$SLEEP_FAKE"
chmod +x "$NOW_FAKE" "$CURL_FAKE" "$SLEEP_FAKE"

run_deadline_case() {
  GEZGIN_SMOKE_MAX_WAIT_SECONDS=25 \
  GEZGIN_SMOKE_RETRY_SECONDS=30 \
  GEZGIN_SMOKE_CURL_MAX_SECONDS=10 \
  GEZGIN_SMOKE_NOW_COMMAND="$NOW_FAKE" \
  GEZGIN_SMOKE_CURL_COMMAND="$CURL_FAKE" \
  GEZGIN_SMOKE_SLEEP_COMMAND="$SLEEP_FAKE" \
  GEZGIN_SMOKE_TEST_CLOCK_FILE="$CLOCK_FILE" \
  GEZGIN_SMOKE_TEST_CURL_LOG="$CURL_LOG" \
  GEZGIN_SMOKE_TEST_SLEEP_LOG="$SLEEP_LOG" \
  GEZGIN_SMOKE_TEST_CURL_ADVANCE="$1" \
    "$ROOT_DIR/gradle/release/wait-for-maven-central.sh" 0.1.0 >/dev/null 2>&1
}

printf '0\n' > "$CLOCK_FILE"
: > "$CURL_LOG"
: > "$SLEEP_LOG"
if run_deadline_case max; then
  echo "Deadline curl case unexpectedly succeeded." >&2
  exit 1
fi
test "$(cat "$CLOCK_FILE")" = 25
test "$(wc -l < "$CURL_LOG" | tr -d ' ')" = 3
test "$(sed -n '1p' "$CURL_LOG")" = '0:10'
test "$(sed -n '2p' "$CURL_LOG")" = '10:10'
test "$(sed -n '3p' "$CURL_LOG")" = '20:5'
test ! -s "$SLEEP_LOG"

printf '0\n' > "$CLOCK_FILE"
: > "$CURL_LOG"
: > "$SLEEP_LOG"
if run_deadline_case 0; then
  echo "Deadline sleep case unexpectedly succeeded." >&2
  exit 1
fi
test "$(cat "$CLOCK_FILE")" = 25
test "$(wc -l < "$CURL_LOG" | tr -d ' ')" = 4
test "$(cat "$SLEEP_LOG")" = 25

SMOKE_SCRIPT="$ROOT_DIR/gradle/release/smoke-maven-central.sh"
grep -Fq -- '--project-dir="$WORK_DIR/consumer"' "$SMOKE_SCRIPT"

echo "release-script-tests=PASS"
