#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?Usage: extract-release-notes.sh VERSION OUTPUT_FILE}
OUTPUT_FILE=${2:?Usage: extract-release-notes.sh VERSION OUTPUT_FILE}

if [[ ! "$VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Invalid stable release version '$VERSION'." >&2
  exit 1
fi

HEADING_PREFIX="## [$VERSION] - "
found=0
: > "$OUTPUT_FILE"
while IFS= read -r line; do
  if [[ "$found" -eq 0 && "$line" == "$HEADING_PREFIX"* ]]; then
    heading_date=${line#"$HEADING_PREFIX"}
    if [[ "$heading_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
      found=1
    fi
    continue
  fi
  if [[ "$found" -eq 1 && "$line" == "## ["* ]]; then
    break
  fi
  if [[ "$found" -eq 1 ]]; then
    printf '%s\n' "$line" >> "$OUTPUT_FILE"
  fi
done < CHANGELOG.md

if [[ "$found" -ne 1 || ! -s "$OUTPUT_FILE" ]]; then
  echo "Release notes for $VERSION are empty." >&2
  exit 1
fi
