#!/usr/bin/env bash
set -euo pipefail

umask 077

repo="${MIDROID_GITHUB_REPO:-SMB-Chan/Midroid}"
keystore="${MIDROID_SIGNING_KEYSTORE_PATH:-$HOME/.midroid/midroid-update.jks}"
alias="${MIDROID_SIGNING_KEY_ALIAS:-midroid-update}"
dname="${MIDROID_SIGNING_DNAME:-CN=Midroid Update,O=Midroid,C=JP}"

for command_name in keytool gh base64 tr; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command not found: $command_name" >&2
    exit 1
  fi
done

gh auth status >/dev/null
mkdir -p "$(dirname "$keystore")"

if [[ -e "$keystore" ]]; then
  echo "Refusing to overwrite existing signing key: $keystore" >&2
  echo "Keep the existing key if this update channel has already shipped." >&2
  exit 1
fi

read -rsp "Keystore password (12+ characters): " store_password
echo
if (( ${#store_password} < 12 )); then
  echo "Keystore password must be at least 12 characters." >&2
  exit 1
fi
read -rsp "Confirm keystore password: " store_password_confirm
echo
if [[ "$store_password" != "$store_password_confirm" ]]; then
  echo "Keystore passwords do not match." >&2
  exit 1
fi

read -rsp "Key password (Enter to reuse keystore password): " key_password
echo
if [[ -z "$key_password" ]]; then
  key_password="$store_password"
else
  read -rsp "Confirm key password: " key_password_confirm
  echo
  if [[ "$key_password" != "$key_password_confirm" ]]; then
    echo "Key passwords do not match." >&2
    exit 1
  fi
fi

export MIDROID_BOOTSTRAP_STORE_PASSWORD="$store_password"
export MIDROID_BOOTSTRAP_KEY_PASSWORD="$key_password"

keytool -genkeypair \
  -alias "$alias" \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 10000 \
  -keystore "$keystore" \
  -storetype JKS \
  -storepass:env MIDROID_BOOTSTRAP_STORE_PASSWORD \
  -keypass:env MIDROID_BOOTSTRAP_KEY_PASSWORD \
  -dname "$dname"

base64 < "$keystore" | tr -d '\r\n' \
  | gh secret set MIDROID_SIGNING_KEYSTORE_B64 --repo "$repo"
printf '%s' "$store_password" \
  | gh secret set MIDROID_SIGNING_STORE_PASSWORD --repo "$repo"
printf '%s' "$alias" \
  | gh secret set MIDROID_SIGNING_KEY_ALIAS --repo "$repo"
printf '%s' "$key_password" \
  | gh secret set MIDROID_SIGNING_KEY_PASSWORD --repo "$repo"

echo
echo "Stable update signing secrets were configured for $repo."
echo "Signing key: $keystore"
echo "Back up this keystore securely. Losing it breaks the existing update channel."
echo "Certificate fingerprint:"
keytool -list -v \
  -alias "$alias" \
  -keystore "$keystore" \
  -storepass:env MIDROID_BOOTSTRAP_STORE_PASSWORD \
  | grep -E 'SHA256:|SHA-256:' || true

unset MIDROID_BOOTSTRAP_STORE_PASSWORD MIDROID_BOOTSTRAP_KEY_PASSWORD
store_password=''
store_password_confirm=''
key_password=''
key_password_confirm=''
