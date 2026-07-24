#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?Usage: wait-for-maven-central.sh VERSION}
CENTRAL_URL=${GEZGIN_SMOKE_REPOSITORY_URL:-https://repo.maven.apache.org/maven2}
GROUP_PATH=io/github/sahsenvar
MAX_WAIT_SECONDS=${GEZGIN_SMOKE_MAX_WAIT_SECONDS:-1800}
RETRY_SECONDS=${GEZGIN_SMOKE_RETRY_SECONDS:-30}
CURL_MAX_SECONDS=${GEZGIN_SMOKE_CURL_MAX_SECONDS:-10}
NOW_COMMAND=${GEZGIN_SMOKE_NOW_COMMAND:-date}
CURL_COMMAND=${GEZGIN_SMOKE_CURL_COMMAND:-curl}
SLEEP_COMMAND=${GEZGIN_SMOKE_SLEEP_COMMAND:-sleep}
MODULES=(gezgin-core gezgin-processor gezgin-mvi gezgin-test)

now_seconds() {
  "$NOW_COMMAND" +%s
}

START=$(now_seconds)
DEADLINE=$((START + MAX_WAIT_SECONDS))

while true; do
  missing=0
  for module in "${MODULES[@]}"; do
    now=$(now_seconds)
    remaining=$((DEADLINE - now))
    if ((remaining <= 0)); then
      echo "Gezgin $VERSION was not fully visible on Maven Central within 30 minutes." >&2
      exit 1
    fi
    request_timeout=$CURL_MAX_SECONDS
    if ((request_timeout > remaining)); then
      request_timeout=$remaining
    fi
    if [[ "$VERSION" == *-SNAPSHOT ]]; then
      resource="$CENTRAL_URL/$GROUP_PATH/$module/$VERSION/maven-metadata.xml"
    else
      resource="$CENTRAL_URL/$GROUP_PATH/$module/$VERSION/$module-$VERSION.pom"
    fi
    if ! "$CURL_COMMAND" \
      --fail --silent --show-error --location --head \
      --max-time "$request_timeout" "$resource" >/dev/null; then
      missing=1
    fi
  done
  if [[ "$missing" -eq 0 ]]; then
    exit 0
  fi

  now=$(now_seconds)
  remaining=$((DEADLINE - now))
  if ((remaining <= 0)); then
    echo "Gezgin $VERSION was not fully visible on Maven Central within 30 minutes." >&2
    exit 1
  fi
  sleep_seconds=$RETRY_SECONDS
  if ((sleep_seconds > remaining)); then
    sleep_seconds=$remaining
  fi
  "$SLEEP_COMMAND" "$sleep_seconds"
done
