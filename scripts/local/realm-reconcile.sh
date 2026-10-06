#!/usr/bin/env bash
# Idempotent reconciliation of the running Keycloak realm with
# infrastructure/local/keycloak/realm-template.json.
#
# Keycloak's --import-realm only imports a realm that does not exist yet, so any
# later template change (client scopes, redirect URIs, roles, composites,
# service-account privileges, new seed users) has to be applied by this script.
#
# Guarantees:
#   - additive and convergent: creates what is missing, updates client settings,
#     adds missing role composites/scope mappings/role assignments
#   - never deletes users, roles, groups or clients; never resets an existing
#     user's password or enabled flag; never prints a secret value
#   - uses the Keycloak bootstrap admin (master realm) from .env; the runtime
#     user-service-admin service account is deliberately NOT powerful enough
#     for this, which is the point of separating bootstrap from runtime
#
# Usage: bash scripts/local/realm-reconcile.sh     (or: make realm-reconcile)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMPLATE="$ROOT/infrastructure/local/keycloak/realm-template.json"
ENV_FILE="$ROOT/.env"
KC_URL="${KEYCLOAK_URL:-http://localhost:8180}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1" >&2; exit 1; }; }
need jq; need curl
[[ -f "$ENV_FILE" ]] || { echo 'Missing .env. Run: make bootstrap' >&2; exit 1; }

env_value() { grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true; }
KEYCLOAK_ADMIN="$(env_value KEYCLOAK_ADMIN)"; KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-local-admin}"
KEYCLOAK_ADMIN_PASSWORD="$(env_value KEYCLOAK_ADMIN_PASSWORD)"
DEMO_USER_PASSWORD="$(env_value DEMO_USER_PASSWORD)"
USER_SERVICE_ADMIN_SECRET="$(env_value USER_SERVICE_ADMIN_SECRET)"
for v in KEYCLOAK_ADMIN_PASSWORD DEMO_USER_PASSWORD USER_SERVICE_ADMIN_SECRET; do
  [[ -n "${!v}" && "${!v}" != CHANGE* ]] || { echo "Error: $v missing or placeholder in .env" >&2; exit 1; }
done

REALM="$(jq -r .realm "$TEMPLATE")"
ADMIN="$KC_URL/admin/realms/$REALM"
created=0; updated=0; unchanged=0
note() { printf '  %-10s %s\n' "$1" "$2"; case "$1" in created) created=$((created+1));; updated) updated=$((updated+1));; *) unchanged=$((unchanged+1));; esac; }

TOKEN=""
refresh_token() {
  TOKEN="$(curl -sS --fail-with-body -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
    --data-urlencode 'grant_type=password' --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=$KEYCLOAK_ADMIN" --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
    | jq -r '.access_token // empty')"
  [[ -n "$TOKEN" ]] || { echo "Could not obtain a Keycloak admin token from $KC_URL (is Keycloak up? are .env admin credentials the ones used at first boot?)" >&2; exit 1; }
}
# kc METHOD PATH [JSON]  -> prints response body; sets LAST_CODE; never fails the script
LAST_CODE=0
kc() {
  local method=$1 path=$2 body=${3:-}
  local out; out="$(mktemp)"
  if [[ -n "$body" ]]; then
    LAST_CODE="$(curl -sS -o "$out" -w '%{http_code}' -X "$method" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$ADMIN$path" --data-binary "$body")"
  else
    LAST_CODE="$(curl -sS -o "$out" -w '%{http_code}' -X "$method" -H "Authorization: Bearer $TOKEN" "$ADMIN$path")"
  fi
  cat "$out"; rm -f "$out"
}
must() { # must "what" -> checks LAST_CODE < 400
  if (( LAST_CODE >= 400 )); then echo "FAILED ($LAST_CODE): $1" >&2; exit 1; fi
}
client_uuid() { kc GET "/clients?clientId=$(jq -rn --arg v "$1" '$v|@uri')" | jq -r '.[0].id // empty'; }

refresh_token
kc GET "" >/dev/null
(( LAST_CODE == 200 )) || { echo "Realm '$REALM' not found at $KC_URL (HTTP $LAST_CODE). First boot imports it; reconcile only updates an existing realm." >&2; exit 1; }
echo "Reconciling realm '$REALM' at $KC_URL with $(basename "$TEMPLATE")"

# --- 1. Realm-level settings (selected scalars only) ---------------------------
echo "Realm settings"
realm_patch="$(jq '{registrationAllowed, registrationEmailAsUsername, loginWithEmailAllowed, duplicateEmailsAllowed, resetPasswordAllowed, verifyEmail, accessTokenLifespan, sslRequired, enabled, displayName}' "$TEMPLATE")"
current="$(kc GET "")"
if [[ "$(echo "$current" | jq -S '{registrationAllowed, registrationEmailAsUsername, loginWithEmailAllowed, duplicateEmailsAllowed, resetPasswordAllowed, verifyEmail, accessTokenLifespan, sslRequired, enabled, displayName}')" == "$(echo "$realm_patch" | jq -S .)" ]]; then
  note unchanged "realm scalars"
else
  kc PUT "" "$realm_patch" >/dev/null; must "update realm settings"; note updated "realm scalars"
fi

# --- 2. Clients (create missing, align settings) ---------------------------------
echo "Clients"
declare -A CLIENT_ID
all_scopes="$(kc GET "/client-scopes")"
while IFS= read -r client_json; do
  client_id="$(echo "$client_json" | jq -r .clientId)"
  # secret placeholder -> real value (only user-service-admin has one)
  client_json="$(USER_SERVICE_ADMIN_SECRET="$USER_SERVICE_ADMIN_SECRET" jq 'walk(if . == "__USER_SERVICE_ADMIN_SECRET__" then $ENV.USER_SERVICE_ADMIN_SECRET else . end)' <<<"$client_json")"
  uuid="$(client_uuid "$client_id")"
  if [[ -z "$uuid" ]]; then
    kc POST "/clients" "$client_json" >/dev/null; must "create client $client_id"
    uuid="$(client_uuid "$client_id")"; note created "client $client_id"
  else
    # Align the declared settings; attributes are merged, mappers/scopes handled below.
    patch="$(echo "$client_json" | jq '{enabled, publicClient, bearerOnly, standardFlowEnabled, implicitFlowEnabled, directAccessGrantsEnabled, serviceAccountsEnabled, fullScopeAllowed, redirectUris, webOrigins, attributes, secret} | with_entries(select(.value != null))')"
    existing="$(kc GET "/clients/$uuid")"
    merged="$(jq -n --argjson e "$existing" --argjson p "$patch" '$e * $p | if $p.redirectUris then .redirectUris = $p.redirectUris else . end | if $p.webOrigins then .webOrigins = $p.webOrigins else . end')"
    if [[ "$(echo "$existing" | jq -S 'del(.secret)')" == "$(echo "$merged" | jq -S 'del(.secret)')" && ( "$(echo "$patch" | jq -r '.secret // empty')" == "" || "$(echo "$existing" | jq -r '.secret // empty')" == "$(echo "$patch" | jq -r '.secret // empty')" ) ]]; then
      note unchanged "client $client_id settings"
    else
      kc PUT "/clients/$uuid" "$merged" >/dev/null; must "update client $client_id"; note updated "client $client_id settings"
    fi
  fi
  CLIENT_ID["$client_id"]="$uuid"
  # default client scopes (additive)
  current_defaults="$(kc GET "/clients/$uuid/default-client-scopes" | jq -r '.[].name')"
  for scope in $(echo "$client_json" | jq -r '.defaultClientScopes[]?'); do
    if ! grep -qx "$scope" <<<"$current_defaults"; then
      scope_id="$(echo "$all_scopes" | jq -r --arg n "$scope" '.[] | select(.name == $n) | .id')"
      [[ -n "$scope_id" ]] || { echo "FAILED: client scope '$scope' does not exist in the realm" >&2; exit 1; }
      kc PUT "/clients/$uuid/default-client-scopes/$scope_id" >/dev/null; must "add default scope $scope to $client_id"; note updated "client $client_id default scope +$scope"
    fi
  done
  # protocol mappers (create missing by name)
  current_mappers="$(kc GET "/clients/$uuid/protocol-mappers/models" | jq -r '.[].name')"
  while IFS= read -r mapper; do
    [[ -n "$mapper" ]] || continue
    name="$(echo "$mapper" | jq -r .name)"
    if ! grep -qxF "$name" <<<"$current_mappers"; then
      kc POST "/clients/$uuid/protocol-mappers/models" "$mapper" >/dev/null; must "add mapper $name to $client_id"; note updated "client $client_id mapper +$name"
    fi
  done < <(echo "$client_json" | jq -c '.protocolMappers[]?')
done < <(jq -c '.clients[]' "$TEMPLATE")

# --- 3. Client roles (permissions on owning API clients) ---------------------------
echo "Client roles"
for client_id in $(jq -r '.roles.client | keys[]' "$TEMPLATE"); do
  uuid="${CLIENT_ID[$client_id]:-$(client_uuid "$client_id")}"
  [[ -n "$uuid" ]] || { echo "FAILED: client $client_id missing" >&2; exit 1; }
  existing="$(kc GET "/clients/$uuid/roles" | jq -r '.[].name')"
  for role in $(jq -r --arg c "$client_id" '.roles.client[$c][].name' "$TEMPLATE"); do
    if grep -qxF "$role" <<<"$existing"; then note unchanged "$client_id/$role"; else
      kc POST "/clients/$uuid/roles" "$(jq -n --arg n "$role" '{name: $n}')" >/dev/null; must "create client role $client_id/$role"; note created "$client_id/$role"
    fi
  done
done

# --- 4. Realm roles and composites -----------------------------------------------------
echo "Realm roles and composites"
refresh_token
existing_realm_roles="$(kc GET "/roles?max=1000")"
for role in $(jq -r '.roles.realm[].name' "$TEMPLATE"); do
  if echo "$existing_realm_roles" | jq -e --arg n "$role" 'any(.[]; .name == $n)' >/dev/null; then :; else
    kc POST "/roles" "$(jq -n --arg n "$role" '{name: $n}')" >/dev/null; must "create realm role $role"; note created "realm role $role"
  fi
done
existing_realm_roles="$(kc GET "/roles?max=1000")"
while IFS= read -r role_json; do
  role="$(echo "$role_json" | jq -r .name)"
  current_comp="$(kc GET "/roles/$(jq -rn --arg v "$role" '$v|@uri')/composites")"
  to_add='[]'
  # client-role composites
  while IFS=$'\t' read -r client_id perm; do
    [[ -n "$client_id" ]] || continue
    if echo "$current_comp" | jq -e --arg c "$client_id" --arg p "$perm" 'any(.[]; .clientRole == true and .name == $p and .containerId != null)' >/dev/null \
       && echo "$current_comp" | jq -e --arg cid "${CLIENT_ID[$client_id]}" --arg p "$perm" 'any(.[]; .clientRole == true and .name == $p and .containerId == $cid)' >/dev/null; then
      continue
    fi
    repr="$(kc GET "/clients/${CLIENT_ID[$client_id]}/roles/$(jq -rn --arg v "$perm" '$v|@uri')")"; must "read client role $client_id/$perm"
    to_add="$(jq -n --argjson a "$to_add" --argjson r "$repr" '$a + [$r]')"
  done < <(echo "$role_json" | jq -r '(.composites.client // {}) | to_entries[] | .key as $c | .value[] | [$c, .] | @tsv')
  # realm-role composites (default-roles -> CUSTOMER)
  for sub in $(echo "$role_json" | jq -r '(.composites.realm // [])[]'); do
    if echo "$current_comp" | jq -e --arg n "$sub" 'any(.[]; .clientRole == false and .name == $n)' >/dev/null; then continue; fi
    repr="$(echo "$existing_realm_roles" | jq -c --arg n "$sub" '.[] | select(.name == $n)')"
    to_add="$(jq -n --argjson a "$to_add" --argjson r "$repr" '$a + [$r]')"
  done
  if [[ "$(echo "$to_add" | jq length)" -gt 0 ]]; then
    kc POST "/roles/$(jq -rn --arg v "$role" '$v|@uri')/composites" "$to_add" >/dev/null; must "add composites to $role"
    note updated "realm role $role composites +$(echo "$to_add" | jq -r '[.[].name] | join(",")')"
  else
    note unchanged "realm role $role composites"
  fi
done < <(jq -c '.roles.realm[] | select(.composite == true)' "$TEMPLATE")

# --- 5. Scope mappings (which roles a client may carry in its tokens) ---------------------
echo "Scope mappings"
while IFS= read -r sm; do
  client_id="$(echo "$sm" | jq -r .client)"; uuid="${CLIENT_ID[$client_id]}"
  current="$(kc GET "/clients/$uuid/scope-mappings/realm" | jq -r '.[].name')"
  to_add='[]'
  for role in $(echo "$sm" | jq -r '.roles[]'); do
    grep -qxF "$role" <<<"$current" && continue
    to_add="$(jq -n --argjson a "$to_add" --argjson r "$(echo "$existing_realm_roles" | jq -c --arg n "$role" '.[] | select(.name == $n)')" '$a + [$r]')"
  done
  if [[ "$(echo "$to_add" | jq length)" -gt 0 ]]; then
    kc POST "/clients/$uuid/scope-mappings/realm" "$to_add" >/dev/null; must "realm scope mappings for $client_id"; note updated "$client_id realm scope +$(echo "$to_add" | jq -r '[.[].name]|join(",")')"
  else note unchanged "$client_id realm scope mappings"; fi
done < <(jq -c '.scopeMappings[]' "$TEMPLATE")
while IFS= read -r entry; do
  source_client="$(echo "$entry" | jq -r .source)"; client_id="$(echo "$entry" | jq -r .client)"
  src_uuid="$(client_uuid "$source_client")"; uuid="${CLIENT_ID[$client_id]}"
  current="$(kc GET "/clients/$uuid/scope-mappings/clients/$src_uuid" | jq -r '.[].name')"
  to_add='[]'
  for role in $(echo "$entry" | jq -r '.roles[]'); do
    grep -qxF "$role" <<<"$current" && continue
    repr="$(kc GET "/clients/$src_uuid/roles/$(jq -rn --arg v "$role" '$v|@uri')")"; must "read $source_client/$role"
    to_add="$(jq -n --argjson a "$to_add" --argjson r "$repr" '$a + [$r]')"
  done
  if [[ "$(echo "$to_add" | jq length)" -gt 0 ]]; then
    kc POST "/clients/$uuid/scope-mappings/clients/$src_uuid" "$to_add" >/dev/null; must "client scope mappings $source_client -> $client_id"; note updated "$client_id scope on $source_client +$(echo "$to_add" | jq -r '[.[].name]|join(",")')"
  else note unchanged "$client_id scope on $source_client"; fi
done < <(jq -c '(.clientScopeMappings // {}) | to_entries[] | .key as $s | .value[] | {source: $s, client: .client, roles: .roles}' "$TEMPLATE")

# --- 6. Users: service accounts and seed people -----------------------------------------------
echo "Users"
refresh_token
while IFS= read -r user_json; do
  username="$(echo "$user_json" | jq -r .username)"
  sa_client="$(echo "$user_json" | jq -r '.serviceAccountClientId // empty')"
  if [[ -n "$sa_client" ]]; then
    uuid="${CLIENT_ID[$sa_client]}"
    sa_id="$(kc GET "/clients/$uuid/service-account-user" | jq -r '.id // empty')"; must "service account user of $sa_client"
    [[ -n "$sa_id" ]] || { echo "FAILED: $sa_client has no service-account user (serviceAccountsEnabled?)" >&2; exit 1; }
    # client roles (realm-management privileges) — additive and restricted to the template
    for source_client in $(echo "$user_json" | jq -r '(.clientRoles // {}) | keys[]'); do
      src_uuid="$(client_uuid "$source_client")"
      current="$(kc GET "/users/$sa_id/role-mappings/clients/$src_uuid" | jq -r '.[].name')"
      to_add='[]'
      for role in $(echo "$user_json" | jq -r --arg c "$source_client" '.clientRoles[$c][]'); do
        grep -qxF "$role" <<<"$current" && continue
        repr="$(kc GET "/clients/$src_uuid/roles/$(jq -rn --arg v "$role" '$v|@uri')")"; must "read $source_client/$role"
        to_add="$(jq -n --argjson a "$to_add" --argjson r "$repr" '$a + [$r]')"
      done
      if [[ "$(echo "$to_add" | jq length)" -gt 0 ]]; then
        kc POST "/users/$sa_id/role-mappings/clients/$src_uuid" "$to_add" >/dev/null; must "grant $source_client roles to $username"; note updated "$username $source_client +$(echo "$to_add" | jq -r '[.[].name]|join(",")')"
      else note unchanged "$username $source_client roles"; fi
    done
    # a service account is not a person: drop the realm default role (CUSTOMER bundle) if present
    default_role="$(kc GET "/users/$sa_id/role-mappings/realm" | jq -c '[.[] | select(.name | startswith("default-roles-"))]')"
    if [[ "$(echo "$default_role" | jq length)" -gt 0 ]]; then
      kc DELETE "/users/$sa_id/role-mappings/realm" "$default_role" >/dev/null; must "remove default realm role from $username"; note updated "$username default realm role removed"
    fi
    continue
  fi
  existing="$(kc GET "/users?username=$(jq -rn --arg v "$username" '$v|@uri')&exact=true" | jq -c '.[0] // empty')"
  if [[ -z "$existing" ]]; then
    body="$(DEMO_USER_PASSWORD="$DEMO_USER_PASSWORD" jq 'walk(if . == "__DEMO_USER_PASSWORD__" then $ENV.DEMO_USER_PASSWORD else . end) | del(.realmRoles)' <<<"$user_json")"
    kc POST "/users" "$body" >/dev/null; must "create user $username"
    existing="$(kc GET "/users?username=$(jq -rn --arg v "$username" '$v|@uri')&exact=true" | jq -c '.[0]')"
    note created "user $username"
  fi
  user_id="$(echo "$existing" | jq -r .id)"
  current_roles="$(kc GET "/users/$user_id/role-mappings/realm" | jq -r '.[].name')"
  to_add='[]'
  for role in $(echo "$user_json" | jq -r '.realmRoles[]?'); do
    grep -qxF "$role" <<<"$current_roles" && continue
    to_add="$(jq -n --argjson a "$to_add" --argjson r "$(echo "$existing_realm_roles" | jq -c --arg n "$role" '.[] | select(.name == $n)')" '$a + [$r]')"
  done
  if [[ "$(echo "$to_add" | jq length)" -gt 0 ]]; then
    kc POST "/users/$user_id/role-mappings/realm" "$to_add" >/dev/null; must "assign roles to $username"; note updated "user $username roles +$(echo "$to_add" | jq -r '[.[].name]|join(",")')"
  else note unchanged "user $username roles"; fi
done < <(jq -c '.users[]' "$TEMPLATE")

echo
echo "Reconcile finished: $created created, $updated updated, $unchanged unchanged. Nothing was deleted; no existing password was changed."
echo "Already issued tokens keep their old claims until they expire (access token lifespan: $(jq -r .accessTokenLifespan "$TEMPLATE") s)."
