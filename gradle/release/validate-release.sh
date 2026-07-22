#!/usr/bin/env bash
set -euo pipefail

TAG=${1:-}
if [[ ! "$TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Invalid release tag '$TAG'. Stable releases only: vMAJOR.MINOR.PATCH with no leading zeros." >&2
  exit 1
fi

VERSION=${TAG#v}
PROPERTY_COUNT=0
PROPERTY_VERSION=
while IFS='=' read -r key value; do
  if [[ "$key" == "VERSION_NAME" ]]; then
    PROPERTY_COUNT=$((PROPERTY_COUNT + 1))
    PROPERTY_VERSION=$value
  fi
done < gradle.properties
if [[ "$PROPERTY_COUNT" -ne 1 ]]; then
  echo "gradle.properties must contain exactly one VERSION_NAME entry." >&2
  exit 1
fi
if [[ "$VERSION" != "$PROPERTY_VERSION" ]]; then
  echo "Tag version '$VERSION' does not match VERSION_NAME '$PROPERTY_VERSION'." >&2
  exit 1
fi

HEADING_COUNT=0
HEADING_PREFIX="## [$VERSION] - "
while IFS= read -r line; do
  if [[ "$line" == "$HEADING_PREFIX"* ]]; then
    heading_date=${line#"$HEADING_PREFIX"}
    if [[ "$heading_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
      HEADING_COUNT=$((HEADING_COUNT + 1))
    fi
  fi
done < CHANGELOG.md
if [[ "$HEADING_COUNT" -ne 1 ]]; then
  echo "CHANGELOG.md must contain exactly one dated release heading for [$VERSION]." >&2
  exit 1
fi

printf '%s\n' "$VERSION"
