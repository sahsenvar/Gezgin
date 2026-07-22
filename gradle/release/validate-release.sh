#!/usr/bin/env bash
set -euo pipefail

TAG=${1:-}
if [[ ! "$TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Invalid release tag '$TAG'. Stable releases only: vMAJOR.MINOR.PATCH with no leading zeros." >&2
  exit 1
fi

VERSION=${TAG#v}
PROPERTY_COUNT=$(grep -Ec '^VERSION_NAME=' gradle.properties)
if [[ "$PROPERTY_COUNT" -ne 1 ]]; then
  echo "gradle.properties must contain exactly one VERSION_NAME entry." >&2
  exit 1
fi
PROPERTY_VERSION=$(sed -n 's/^VERSION_NAME=//p' gradle.properties)
if [[ "$VERSION" != "$PROPERTY_VERSION" ]]; then
  echo "Tag version '$VERSION' does not match VERSION_NAME '$PROPERTY_VERSION'." >&2
  exit 1
fi

HEADING_COUNT=$(grep -Ec "^## \[$VERSION\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$" CHANGELOG.md)
if [[ "$HEADING_COUNT" -ne 1 ]]; then
  echo "CHANGELOG.md must contain exactly one dated release heading for [$VERSION]." >&2
  exit 1
fi

if grep -Eq "^## \[$VERSION\] - Unreleased$" CHANGELOG.md; then
  echo "CHANGELOG.md release [$VERSION] must be dated, not Unreleased." >&2
  exit 1
fi

printf '%s\n' "$VERSION"
