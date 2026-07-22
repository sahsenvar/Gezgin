#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?Usage: smoke-maven-central.sh VERSION}
CENTRAL_URL=https://repo.maven.apache.org/maven2
GROUP_PATH=io/github/sahsenvar
MAX_WAIT_SECONDS=1800
RETRY_SECONDS=30
MODULES=(gezgin-core gezgin-processor gezgin-mvi gezgin-test)
DEADLINE=$((SECONDS + MAX_WAIT_SECONDS))

while true; do
  missing=0
  for module in "${MODULES[@]}"; do
    pom="$CENTRAL_URL/$GROUP_PATH/$module/$VERSION/$module-$VERSION.pom"
    if ! curl --fail --silent --show-error --location --head --max-time 10 "$pom" >/dev/null; then
      missing=1
    fi
  done
  if [[ "$missing" -eq 0 ]]; then
    break
  fi
  if ((SECONDS + RETRY_SECONDS >= DEADLINE)); then
    echo "Gezgin $VERSION was not fully visible on Maven Central within 30 minutes." >&2
    exit 1
  fi
  sleep "$RETRY_SECONDS"
done

WORK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/gezgin-central-smoke.XXXXXX")
trap 'rm -rf "$WORK_DIR"' EXIT
mkdir -p "$WORK_DIR/consumer"
git archive --format=tar HEAD:compatibility/zad-consumer | tar -xf - -C "$WORK_DIR/consumer"
grep -Fq 'gradle-9.4.1-bin.zip' "$WORK_DIR/consumer/gradle/wrapper/gradle-wrapper.properties"

GRADLE_USER_HOME="$WORK_DIR/gradle-home" \
  "$WORK_DIR/consumer/gradlew" \
  compileDebugKotlin compileDebugUnitTestKotlin \
  -PgezginVersion="$VERSION" \
  --refresh-dependencies \
  --rerun-tasks \
  --no-build-cache \
  --project-cache-dir="$WORK_DIR/project-cache" \
  --no-daemon \
  --console=plain
