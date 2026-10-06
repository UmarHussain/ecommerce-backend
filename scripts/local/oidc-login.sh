#!/usr/bin/env bash
# Obtain real Keycloak tokens for a seed user through the Authorization Code +
# PKCE (S256) flow, driving the hosted login page with curl exactly like a browser:
#   GET authorization endpoint -> login form -> POST credentials -> 302 with code
#   -> POST token endpoint with code_verifier.
# No password/direct-access grant is used; the SPA clients keep that disabled.
#
# Usage: bash scripts/local/oidc-login.sh <storefront-spa|admin-spa> <username> [extra scopes]
#   The token response JSON is printed to stdout (access_token, id_token, ...).
#   The password comes from DEMO_USER_PASSWORD in .env (override: OIDC_PASSWORD env).
#   Nothing else is printed; the password never appears in output or argv.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
client="${1:?client id: storefront-spa or admin-spa}"
username="${2:?username, e.g. customer@example.test}"
extra_scopes="${3:-}"

case "$client" in
  storefront-spa) redirect_uri="${OIDC_REDIRECT_URI:-http://localhost:5173/callback}" ;;
  admin-spa)      redirect_uri="${OIDC_REDIRECT_URI:-http://localhost:5174/callback}" ;;
  *) echo "Unknown SPA client '$client' (storefront-spa | admin-spa)" >&2; exit 1 ;;
esac

issuer="${OIDC_ISSUER_URI:-http://localhost:8180/realms/ecommerce-local}"
if [[ -z "${OIDC_PASSWORD:-}" ]]; then
  [[ -f "$ROOT/.env" ]] || { echo 'Missing .env (run make bootstrap) and no OIDC_PASSWORD set.' >&2; exit 1; }
  OIDC_PASSWORD="$(grep -E '^DEMO_USER_PASSWORD=' "$ROOT/.env" | tail -n1 | cut -d= -f2-)"
fi
[[ -n "$OIDC_PASSWORD" ]] || { echo 'DEMO_USER_PASSWORD is empty.' >&2; exit 1; }

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
urlencode() { jq -rn --arg v "$1" '$v|@uri'; }

verifier="$(openssl rand 32 | b64url)"
challenge="$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | b64url)"
state="$(openssl rand -hex 8)"
scope="openid${extra_scopes:+ $extra_scopes}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
jar="$work/cookies"

auth_url="$issuer/protocol/openid-connect/auth?client_id=$(urlencode "$client")&response_type=code&redirect_uri=$(urlencode "$redirect_uri")&scope=$(urlencode "$scope")&state=$state&code_challenge=$challenge&code_challenge_method=S256"

# 1. Login page (sets AUTH_SESSION cookies, contains the form action with session params).
curl -sS -c "$jar" -b "$jar" -o "$work/login.html" "$auth_url"
action="$(grep -oE '<form[^>]+id="kc-form-login"[^>]*action="[^"]+"' "$work/login.html" | sed -E 's/.*action="([^"]+)".*/\1/' | sed 's/&amp;/\&/g')"
if [[ -z "$action" ]]; then
  if grep -q 'code=' <(curl -sS -b "$jar" -o /dev/null -w '%{redirect_url}' "$auth_url"); then
    echo "An SSO session already exists in this cookie jar; unexpected for a fresh run." >&2
  fi
  echo "Could not find the Keycloak login form (is the realm/client configured and Keycloak reachable at $issuer?)." >&2
  exit 1
fi

# 2. Submit credentials. Keycloak answers 302 Location: <redirect_uri>?state=...&code=...
#    The password is passed via --data-urlencode from a variable, never on the command line.
location="$(curl -sS -c "$jar" -b "$jar" -o "$work/post.html" -w '%{redirect_url}' \
  --data-urlencode "username=$username" --data-urlencode "password=$OIDC_PASSWORD" --data-urlencode 'credentialId=' \
  "$action")"
code="$(printf '%s' "$location" | sed -nE 's/.*[?&]code=([^&]+).*/\1/p')"
if [[ -z "$code" ]]; then
  if grep -qiE 'Invalid username or password|kc-error|alert-error' "$work/post.html"; then
    echo "Login rejected for $username (wrong credentials or disabled account)." >&2
  else
    echo "Login did not produce an authorization code (redirect: ${location:-none}). Check client redirect URIs." >&2
  fi
  exit 1
fi
returned_state="$(printf '%s' "$location" | sed -nE 's/.*[?&]state=([^&]+).*/\1/p')"
[[ "$returned_state" == "$state" ]] || { echo 'State mismatch in authorization response.' >&2; exit 1; }

# 3. Exchange the code with the PKCE verifier (public client, no secret).
curl -sS --fail-with-body -X POST "$issuer/protocol/openid-connect/token" \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode "client_id=$client" \
  --data-urlencode "code=$code" \
  --data-urlencode "redirect_uri=$redirect_uri" \
  --data-urlencode "code_verifier=$verifier"
echo
