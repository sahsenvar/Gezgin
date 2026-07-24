#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?Usage: smoke-maven-central.sh VERSION}
REPOSITORY_URL=${GEZGIN_SMOKE_REPOSITORY_URL:-https://repo.maven.apache.org/maven2}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
"$SCRIPT_DIR/wait-for-maven-central.sh" "$VERSION"

WORK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/gezgin-central-smoke.XXXXXX")
trap 'rm -rf "$WORK_DIR"' EXIT
mkdir -p "$WORK_DIR/consumer"
git archive --format=tar HEAD:compatibility/zad-consumer | tar -xf - -C "$WORK_DIR/consumer"
grep -Fq 'gradle-9.4.1-bin.zip' "$WORK_DIR/consumer/gradle/wrapper/gradle-wrapper.properties"

GRADLE_USER_HOME="$WORK_DIR/gradle-home" \
  "$WORK_DIR/consumer/gradlew" \
  --project-dir="$WORK_DIR/consumer" \
  compileDebugKotlin compileDebugUnitTestKotlin \
  -PgezginVersion="$VERSION" \
  -PgezginRepositoryUrl="$REPOSITORY_URL" \
  --refresh-dependencies \
  --rerun-tasks \
  --no-build-cache \
  --project-cache-dir="$WORK_DIR/project-cache" \
  --no-daemon \
  --console=plain
