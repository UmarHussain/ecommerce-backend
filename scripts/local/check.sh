#!/usr/bin/env bash
# Static repository checks. Fast, offline, no Java build, no containers.
# It does NOT prove that services compile, tests pass, Compose starts, or
# Keycloak login works; use make backend-verify / make smoke for that.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

failures=0
ok()   { echo "ok    $*"; }
fail() { echo "FAIL  $*" >&2; failures=$((failures + 1)); }
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1" >&2; exit 2; }; }
need jq

# --- Shell syntax -----------------------------------------------------------
for f in scripts/local/*.sh infrastructure/local/postgres/*.sh infrastructure/local/manual-startup/*.sh backend/mvnw; do
  if bash -n "$f"; then ok "bash -n $f"; else fail "bash syntax: $f"; fi
done

# --- Maven modules (list comes from backend/pom.xml; count is not hard-coded) --
mapfile -t modules < <(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' backend/pom.xml)
((${#modules[@]})) || fail "backend/pom.xml declares no <module> entries"
dupes="$(printf '%s\n' "${modules[@]}" | sort | uniq -d)"
[[ -z "$dupes" ]] || fail "duplicate modules in backend/pom.xml: $dupes"
for m in "${modules[@]}"; do
  [[ -f "backend/$m/pom.xml" ]] || fail "$m: backend/$m/pom.xml missing"
  [[ -f "backend/$m/src/main/resources/application.yml" ]] || fail "$m: application.yml missing"
  apps="$(find "backend/$m/src/main/java" -name '*Application.java' 2>/dev/null | wc -l)"
  [[ "$apps" -eq 1 ]] || fail "$m: expected exactly one *Application.java, found $apps"
done
for d in backend/*/; do
  m="${d%/}"; m="${m#backend/}"
  if [[ -f "$d/pom.xml" ]] && ! printf '%s\n' "${modules[@]}" | grep -qx "$m"; then
    fail "backend/$m has a pom.xml but is not listed in backend/pom.xml"
  fi
done
ok "${#modules[@]} Maven modules: pom.xml, one *Application.java, application.yml each"

# --- Frontend workspaces --------------------------------------------------------
for f in frontend/package.json frontend/*/package.json; do
  jq -e . "$f" >/dev/null 2>&1 || fail "invalid JSON: $f"
done
for ws in $(jq -r '.workspaces[]' frontend/package.json); do
  [[ -f "frontend/$ws/package.json" ]] || fail "workspace frontend/$ws has no package.json"
done
ok "frontend package.json files parse and workspaces exist"

# --- Keycloak realm template -------------------------------------------------------
realm=infrastructure/local/keycloak/realm-template.json
realm_check() { # realm_check "description" 'jq boolean filter'
  if jq -e "$2" "$realm" >/dev/null 2>&1; then ok "realm: $1"; else fail "realm: $1"; fi
}
realm_check "valid JSON" '.'
realm_check "seed usernames are unique" \
  '([.users[].username] | length) == ([.users[].username] | unique | length)'
realm_check "at least one seed person and all seed people use the DEMO_USER_PASSWORD placeholder" \
  '([.users[] | select(.serviceAccountClientId == null)] | length) > 0
   and all(.users[] | select(.serviceAccountClientId == null); .credentials[0].value == "__DEMO_USER_PASSWORD__")'
realm_check "service-account users carry no password credentials" \
  'all(.users[] | select(.serviceAccountClientId != null); (.credentials // []) | length == 0)'
realm_check "every user realm role is a defined realm role" \
  '[.roles.realm[].name] as $r | all(.users[].realmRoles[]; IN($r[]))'
realm_check "storefront-spa and admin-spa clients exist" \
  '[.clients[].clientId] | contains(["storefront-spa", "admin-spa"])'
realm_check "SPA clients: public, auth-code flow, no direct grants, no full scope, PKCE S256" \
  'all(.clients[] | select(.clientId == "storefront-spa" or .clientId == "admin-spa");
       .publicClient and .standardFlowEnabled
       and (.directAccessGrantsEnabled | not) and (.fullScopeAllowed | not)
       and .attributes["pkce.code.challenge.method"] == "S256")'
realm_check "SPA clients: basic scope present (sub claim) and exact redirect URIs without wildcards" \
  'all(.clients[] | select(.clientId == "storefront-spa" or .clientId == "admin-spa");
       (.defaultClientScopes | index("basic")) != null
       and all(.redirectUris[]; test("\\*") | not)
       and (.attributes["post.logout.redirect.uris"] | test("\\*") | not))'
realm_check "storefront-spa scope mapping grants CUSTOMER only" \
  '(.scopeMappings[] | select(.client == "storefront-spa") | .roles) == ["CUSTOMER"]'
realm_check "user-service-admin: confidential service account, no login flows, least-privilege realm-management roles" \
  '["manage-users", "view-users", "query-clients", "view-clients", "view-realm"] as $allowed
   | (.clients[] | select(.clientId == "user-service-admin")
      | (.publicClient | not) and .serviceAccountsEnabled and (.standardFlowEnabled | not)
        and (.directAccessGrantsEnabled | not) and (.fullScopeAllowed | not))
   and ([.clientScopeMappings["realm-management"][] | select(.client == "user-service-admin") | .roles[]] | all(IN($allowed[])))
   and ([.users[] | select(.serviceAccountClientId == "user-service-admin") | .clientRoles["realm-management"][]] | all(IN($allowed[])))'
realm_check "no realm-admin, manage-realm, manage-clients or impersonation grant anywhere in the template" \
  '[.. | strings | select(. == "realm-admin" or . == "manage-realm" or . == "manage-clients" or . == "impersonation")] | length == 0'
realm_check "removed portal backend clients are not in the template" \
  '[.. | strings | select(. == "storefront-backend" or . == "admin-portal-backend")] | length == 0'
realm_check "admin.access is an api-gateway client role" \
  'any(.roles.client["api-gateway"][]?; .name == "admin.access")'
realm_check "staff realm roles composite api-gateway/admin.access" \
  '. as $realm
   | ["CATALOG_VIEWER","CATALOG_CREATOR","CATALOG_EDITOR","INVENTORY_MANAGER","ORDER_MANAGER","USER_ADMIN","PLATFORM_ADMIN"]
   | all(.[]; . as $name | $realm.roles.realm | any(.[]; .name == $name and ((.composites.client["api-gateway"] // []) | index("admin.access") != null)))'
realm_check "composite realm roles reference defined client roles" \
  '. as $realm
   | all(.roles.realm[] | (.composites.client // {}) | to_entries[] | .key as $c | .value[] | {c: $c, p: .};
         . as $x | ($realm.roles.client[$x.c] // []) | any(.name == $x.p))'
realm_check "only the two expected secret placeholders are used" \
  '[.. | strings | select(test("^__[A-Z_]+__$"))] | unique
   == ["__DEMO_USER_PASSWORD__", "__USER_SERVICE_ADMIN_SECRET__"]'

# --- Cursor rules --------------------------------------------------------------------
bad_rules=0
for rule in .cursor/rules/*.mdc; do
  if [[ "$(head -n 1 "$rule")" != "---" ]] || ! grep -q '^alwaysApply:' "$rule"; then
    fail "Cursor rule missing front matter or alwaysApply: $rule"; bad_rules=$((bad_rules + 1))
  fi
done
((bad_rules)) || ok "Cursor rule files have front matter with alwaysApply"

# --- Required documents and relative Markdown links -----------------------------------
for required in START_HERE.md AGENTS.md MASTER_PROMPT.md IMPLEMENTATION_PLAN.md \
                docs/verification.md docs/seed-users.md docs/local-development.md; do
  [[ -f "$required" ]] || fail "required document missing: $required"
done
broken=0
while IFS= read -r doc; do
  dir="$(dirname "$doc")"
  while IFS= read -r link; do
    [[ "$link" == *"://"* || "$link" == "#"* || "$link" == "mailto:"* ]] && continue
    target="${link%%#*}"
    [[ -e "$dir/$target" ]] || { fail "broken link in $doc -> $link"; broken=$((broken + 1)); }
  done < <(grep -oE '\]\([^)]+\)' "$doc" | sed -E 's/^\]\((.*)\)$/\1/')
done < <(find README.md START_HERE.md docs -name '*.md')
((broken)) || ok "required documents exist and relative Markdown links resolve"

# --- Compose file (only when Docker and a bootstrapped .env are available) --------------
if command -v docker >/dev/null 2>&1 && [[ -f .env ]]; then
  if docker compose --env-file .env -f infrastructure/local/compose.yaml \
       -f infrastructure/local/compose.debug.yaml --profile apps --profile later \
       --profile cache --profile events --profile mail config -q 2>/dev/null; then
    ok "docker compose config (main file, all profiles) is valid"
  else
    fail "docker compose config (main file) reported errors (run it manually for details)"
  fi
  if docker compose --env-file .env -f infrastructure/local/manual-startup/compose.yaml config -q 2>/dev/null; then
    ok "docker compose config (manual-startup infrastructure file) is valid"
  else
    fail "docker compose config (manual-startup file) reported errors"
  fi
else
  echo "skip  docker compose config (needs docker on PATH and a bootstrapped .env)"
fi

# --- Summary ---------------------------------------------------------------------------
echo
if ((failures)); then
  echo "check: $failures failure(s)." >&2
  exit 1
fi
echo "check: all static checks passed."
echo "This is a static check. Builds, tests, Compose startup, and Keycloak login need make backend-verify / make infra-up / make smoke."
