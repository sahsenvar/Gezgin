#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# GPG's agent socket has a short platform path limit, so keep the ephemeral home directly under /tmp.
verification_root="$(mktemp -d "/tmp/gezgin-release-verification.XXXXXX")"
signing_home="$verification_root/gnupg"
verify_home="$verification_root/verify-gnupg"
repository="$verification_root/repository"
public_key="$verification_root/public-key.asc"
identity="Gezgin Release Verification <release-verification@gezgin.invalid>"

cleanup() {
    rm -rf "$verification_root"
}
trap cleanup EXIT

mkdir -m 700 "$signing_home"
mkdir -m 700 "$verify_home"
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

gpg --homedir "$signing_home" --batch --armor --export "$primary_fingerprint" > "$public_key"
gpg --homedir "$verify_home" --batch --import "$public_key" >/dev/null 2>&1
if gpg --homedir "$verify_home" --batch --with-colons --list-secret-keys | grep -q '^sec:'; then
    echo "Verification keyring unexpectedly contains a secret key." >&2
    exit 1
fi

verified_signatures=0
while IFS= read -r -d '' signature; do
    artifact="${signature%.asc}"
    if ! gpg --homedir "$verify_home" --batch --verify "$signature" "$artifact" >/dev/null 2>&1; then
        echo "Cryptographic signature verification failed: $signature" >&2
        exit 1
    fi
    verified_signatures=$((verified_signatures + 1))
done < <(find "$repository" -type f -name '*.asc' -print0)

if [[ "$verified_signatures" -ne 53 ]]; then
    echo "Expected 53 cryptographically verified signatures, found $verified_signatures." >&2
    exit 1
fi
echo "CRYPTOGRAPHIC_SIGNATURES_VERIFIED=53"

corrupted_artifact="$verification_root/corrupted-gezgin-processor.pom"
processor_pom="$repository/io/github/sahsenvar/gezgin-processor/0.1.0/gezgin-processor-0.1.0.pom"
processor_signature="$processor_pom.asc"
cp "$processor_pom" "$corrupted_artifact"
printf '\ncorruption-negative-test\n' >> "$corrupted_artifact"
if gpg --homedir "$verify_home" --batch --verify "$processor_signature" "$corrupted_artifact" >/dev/null 2>&1; then
    echo "Corrupted artifact unexpectedly passed cryptographic signature verification." >&2
    exit 1
fi
echo "CORRUPTION_NEGATIVE=PASS"
