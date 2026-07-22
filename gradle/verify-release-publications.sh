#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# GPG's agent socket has a short platform path limit, so keep the ephemeral home directly under /tmp.
verification_root="$(mktemp -d "/tmp/gezgin-release-verification.XXXXXX")"
signing_home="$verification_root/gnupg"
repository="$verification_root/repository"
identity="Gezgin Release Verification <release-verification@gezgin.invalid>"

cleanup() {
    rm -rf "$verification_root"
}
trap cleanup EXIT

mkdir -m 700 "$signing_home"
mkdir -p "$repository"
export GNUPGHOME="$signing_home"

gpg --batch --passphrase '' --quick-generate-key "$identity" rsa2048 cert 1d
primary_fingerprint="$(gpg --batch --with-colons --list-secret-keys "$identity" | awk -F: '$1 == "fpr" { print $10; exit }')"
gpg --batch --passphrase '' --quick-add-key "$primary_fingerprint" rsa2048 sign 1d
signing_key_id="$(gpg --batch --with-colons --list-secret-keys "$identity" | awk -F: '$1 == "ssb" { key = $5 } END { print key }')"
signing_key_id="${signing_key_id: -8}"
signing_key="$(gpg --batch --armor --export-secret-keys "$primary_fingerprint")"

cd "$project_root"
ORG_GRADLE_PROJECT_signingInMemoryKey="$signing_key" \
ORG_GRADLE_PROJECT_signingInMemoryKeyId="$signing_key_id" \
./gradlew verifyReleasePublications \
    -PreleaseVerificationRepository="$repository" \
    -PverifyReleaseSignatures=true \
    --rerun-tasks \
    --no-daemon
