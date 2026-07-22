#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?Usage: extract-release-notes.sh VERSION OUTPUT_FILE}
OUTPUT_FILE=${2:?Usage: extract-release-notes.sh VERSION OUTPUT_FILE}

awk -v version="$VERSION" '
  $0 ~ "^## \\[" version "\\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$" { found = 1; next }
  found && /^## \[/ { exit }
  found { print }
  END { if (!found) exit 1 }
' CHANGELOG.md > "$OUTPUT_FILE"

if [[ ! -s "$OUTPUT_FILE" ]]; then
  echo "Release notes for $VERSION are empty." >&2
  exit 1
fi
